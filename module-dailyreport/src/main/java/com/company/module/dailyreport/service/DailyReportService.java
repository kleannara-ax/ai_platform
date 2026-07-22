package com.company.module.dailyreport.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dailyreport.dto.*;
import com.company.module.dailyreport.entity.*;
import com.company.module.dailyreport.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 세부공장일보 핵심 비즈니스 로직
 * - 4개 표: 주요 생산 지표 현황 / 제지 재공품 및 야적현황 / 에너지 원단위 / 보일러 운영 현황
 * - 특이사항 + 이미지 첨부
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final DailyReportTableRepository tableRepository;
    private final DailyReportCellRepository cellRepository;
    private final DailyReportRemarkRepository remarkRepository;
    private final DailyReportImageRepository imageRepository;

    // ─────────────────────────────────────────────
    // 일보 CRUD
    // ─────────────────────────────────────────────

    /**
     * 일보 목록 조회 (기간·상태 필터 + 페이징)
     */
    public Page<DailyReportResponse> getReportList(LocalDate startDate, LocalDate endDate,
                                                    String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportRepository.findByConditions(startDate, endDate, status, pageable)
                .map(DailyReportResponse::from);
    }

    /**
     * 일보 상세 조회 (표 + 셀 + 특이사항 + 이미지 포함)
     */
    public DailyReportResponse getReport(Long reportId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));
        return DailyReportResponse.fromWithDetails(report);
    }

    /**
     * 날짜별 일보 조회 — 해당 날짜에 일보가 없으면 자동 생성 (DRAFT 상태)
     *
     * 세부공장일보는 매일 입력하는 문서이므로, 사용자가 해당 날짜 페이지를
     * 열었을 때 빈 일보가 자동으로 준비되어야 한다.
     */
    @Transactional
    public DailyReportResponse getReportByDate(LocalDate reportDate, Long userId) {
        return reportRepository.findByReportDate(reportDate)
                .map(report -> {
                    // 기존 일보가 있지만 셀이 비어 있는 경우 기본 셀 보충
                    // (이전 버전에서 테이블만 생성하고 셀을 누락한 데이터 보완)
                    boolean cellsMissing = report.getTables().stream()
                            .anyMatch(t -> t.getCells().isEmpty());
                    if (cellsMissing) {
                        for (DailyReportTable table : report.getTables()) {
                            if (table.getCells().isEmpty()) {
                                DefaultCellTemplate.populateDefaultCells(table, reportDate);
                            }
                        }
                    }
                    return DailyReportResponse.fromWithDetails(report);
                })
                .orElseGet(() -> {
                    // 자동 생성: 해당 날짜 일보 + 4개 기본 표 + 기본 셀
                    DailyReport report = DailyReport.builder()
                            .reportDate(reportDate)
                            .title(reportDate + " 세부공장일보")
                            .status("DRAFT")
                            .createdBy(userId)
                            .build();
                    createDefaultTables(report);
                    reportRepository.save(report);
                    return DailyReportResponse.fromWithDetails(report);
                });
    }

    /**
     * 일보 생성 (표 4개 기본 구조 자동 생성)
     */
    @Transactional
    public DailyReportResponse createReport(DailyReportRequest request, Long userId) {
        // 중복 날짜 검증
        if (reportRepository.existsByReportDate(request.getReportDate())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "해당 날짜의 일보가 이미 존재합니다: " + request.getReportDate());
        }

        String title = request.getTitle() != null
                ? request.getTitle()
                : request.getReportDate() + " 세부공장일보";

        DailyReport report = DailyReport.builder()
                .reportDate(request.getReportDate())
                .title(title)
                .status("DRAFT")
                .createdBy(userId)
                .build();

        // 4개 기본 표 구조 생성
        createDefaultTables(report);

        reportRepository.save(report);
        return DailyReportResponse.fromWithDetails(report);
    }

    /**
     * 일보 상태 변경 (DRAFT → SUBMITTED → CONFIRMED)
     */
    @Transactional
    public DailyReportResponse updateReportStatus(Long reportId, String status, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateStatusTransition(report.getStatus(), status);
        report.updateStatus(status, userId);

        // CONFIRMED 상태 → 전체 셀 잠금
        if ("CONFIRMED".equals(status)) {
            cellRepository.lockAllCellsByReportId(reportId);
        }

        return DailyReportResponse.from(report);
    }

    /**
     * 일보 삭제 (DRAFT 상태만 삭제 가능)
     */
    @Transactional
    public void deleteReport(Long reportId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        if (!"DRAFT".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "DRAFT 상태의 일보만 삭제할 수 있습니다. 현재 상태: " + report.getStatus());
        }

        reportRepository.delete(report);
    }

    // ─────────────────────────────────────────────
    // 표별 셀 데이터 조회
    // ─────────────────────────────────────────────

    /**
     * 특정 표의 셀 데이터 조회 (사용자별 편집 가능 여부 미포함)
     */
    public ReportTableResponse getTableData(Long reportId, String tableCode) {
        DailyReportTable table = tableRepository
                .findByDailyReport_ReportIdAndTableCode(reportId, tableCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "표를 찾을 수 없습니다. reportId=" + reportId + ", tableCode=" + tableCode));
        return ReportTableResponse.from(table);
    }

    // ─────────────────────────────────────────────
    // 특이사항 CRUD
    // ─────────────────────────────────────────────

    /**
     * 특이사항 목록 조회
     */
    public List<RemarkResponse> getRemarks(Long reportId) {
        return remarkRepository.findByDailyReport_ReportIdOrderBySortOrderAsc(reportId)
                .stream()
                .map(RemarkResponse::from)
                .toList();
    }

    /**
     * 특이사항 추가
     */
    @Transactional
    public RemarkResponse addRemark(Long reportId, RemarkRequest request, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateReportEditable(report);

        int sortOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : (int) remarkRepository.findByDailyReport_ReportIdOrderBySortOrderAsc(reportId).size() + 1;

        DailyReportRemark remark = DailyReportRemark.builder()
                .tableCode(request.getTableCode())
                .category(request.getCategory())
                .content(request.getContent())
                .sortOrder(sortOrder)
                .createdBy(userId)
                .build();

        report.addRemark(remark);
        remarkRepository.save(remark);
        return RemarkResponse.from(remark);
    }

    /**
     * 특이사항 수정
     */
    @Transactional
    public RemarkResponse updateRemark(Long remarkId, RemarkRequest request) {
        DailyReportRemark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new EntityNotFoundException("특이사항을 찾을 수 없습니다. ID: " + remarkId));

        validateReportEditable(remark.getDailyReport());
        remark.updateContent(request.getContent(), request.getCategory());
        return RemarkResponse.from(remark);
    }

    /**
     * 특이사항 삭제
     */
    @Transactional
    public void deleteRemark(Long remarkId) {
        DailyReportRemark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new EntityNotFoundException("특이사항을 찾을 수 없습니다. ID: " + remarkId));

        validateReportEditable(remark.getDailyReport());
        remarkRepository.delete(remark);
    }

    // ─────────────────────────────────────────────
    // 이미지 관리
    // ─────────────────────────────────────────────

    /**
     * 이미지 목록 조회
     */
    public List<ImageResponse> getImages(Long reportId) {
        return imageRepository.findByDailyReport_ReportIdOrderBySortOrderAsc(reportId)
                .stream()
                .map(ImageResponse::from)
                .toList();
    }

    /**
     * 이미지 메타 저장 (실제 파일 업로드는 별도 처리)
     */
    @Transactional
    public ImageResponse addImage(Long reportId, String originalName, String storedPath,
                                  Long fileSize, String contentType, String description,
                                  String tableCode, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateReportEditable(report);

        int sortOrder = (int) imageRepository.countByDailyReport_ReportId(reportId) + 1;

        DailyReportImage image = DailyReportImage.builder()
                .originalName(originalName)
                .storedPath(storedPath)
                .fileSize(fileSize)
                .contentType(contentType)
                .description(description)
                .tableCode(tableCode)
                .sortOrder(sortOrder)
                .uploadedBy(userId)
                .build();

        report.addImage(image);
        imageRepository.save(image);
        return ImageResponse.from(image);
    }

    /**
     * 이미지 설명 수정
     */
    @Transactional
    public ImageResponse updateImageDescription(Long imageId, String description) {
        DailyReportImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));

        validateReportEditable(image.getDailyReport());
        image.updateDescription(description);
        return ImageResponse.from(image);
    }

    /**
     * 이미지 삭제
     */
    @Transactional
    public void deleteImage(Long imageId) {
        DailyReportImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));

        validateReportEditable(image.getDailyReport());
        imageRepository.delete(image);
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * 4개 기본 표 구조 생성
     * HTML 원본 기준:
     *   1. 주요 생산 지표 현황 (table1) — 10행 15열
     *   2. 제지 재공품 및 야적현황 (table2) — 10행 13열
     *   3. 에너지 원단위 (table3) — 8행 6열
     *   4. 보일러 운영 현황 (table4) — 7행 8열
     */
    private void createDefaultTables(DailyReport report) {
        String[][] tableDefinitions = {
                {"TBL_PRODUCTION_INDEX", "주요 생산 지표 현황",     "10", "15"},
                {"TBL_INVENTORY",        "제지 재공품 및 야적현황", "10", "13"},
                {"TBL_ENERGY",           "에너지 원단위",           "8",  "6"},
                {"TBL_BOILER",           "보일러 운영 현황",        "7",  "8"},
        };

        for (int i = 0; i < tableDefinitions.length; i++) {
            DailyReportTable table = DailyReportTable.builder()
                    .tableCode(tableDefinitions[i][0])
                    .tableName(tableDefinitions[i][1])
                    .sortOrder(i + 1)
                    .rowCount(Integer.parseInt(tableDefinitions[i][2]))
                    .colCount(Integer.parseInt(tableDefinitions[i][3]))
                    .build();
            report.addTable(table);

            // 기본 셀(HEADER + READONLY + DATA) 생성 — 프론트엔드 표 렌더링에 필수
            DefaultCellTemplate.populateDefaultCells(table, report.getReportDate());
        }
    }

    /**
     * 상태 전환 유효성 검증
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        boolean valid = switch (currentStatus) {
            case "DRAFT" -> "SUBMITTED".equals(newStatus);
            case "SUBMITTED" -> "CONFIRMED".equals(newStatus) || "DRAFT".equals(newStatus);
            case "CONFIRMED" -> false; // 확정 후 변경 불가
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "잘못된 상태 전환입니다: " + currentStatus + " → " + newStatus);
        }
    }

    /**
     * 일보가 편집 가능 상태인지 검증 (CONFIRMED이면 수정 불가)
     */
    private void validateReportEditable(DailyReport report) {
        if ("CONFIRMED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "확정된 일보는 수정할 수 없습니다.");
        }
    }
}
