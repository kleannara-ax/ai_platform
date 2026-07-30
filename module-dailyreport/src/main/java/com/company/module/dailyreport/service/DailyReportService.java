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
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final CellOwnershipSyncService cellOwnershipSyncService;

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
                        // ★ 값 이어받기: 보충되는 표에도 직전 일보의 값을 초기값으로 반영
                        Map<String, String> previousValues = findPreviousCellValues(reportDate);
                        for (DailyReportTable table : report.getTables()) {
                            if (table.getCells().isEmpty()) {
                                DefaultCellTemplate.populateDefaultCells(table, reportDate, historicalValueLookup());
                                applyCarriedOverValues(table, previousValues);
                                // ★ 하드코딩 제거: 생성 즉시 현재 활성 CellAuth 담당자를 반영
                                cellOwnershipSyncService.applyCurrentOwnersToNewTable(table);
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
                    // ★ 값 이어받기: 직전 일보의 DATA 셀 값을 새 표의 초기값으로 반영
                    Map<String, String> previousValues = findPreviousCellValues(reportDate);
                    createDefaultTables(report, previousValues);
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

        // 4개 기본 표 구조 생성 (★ 값 이어받기: 직전 일보의 DATA 셀 값을 초기값으로 반영)
        Map<String, String> previousValues = findPreviousCellValues(request.getReportDate());
        createDefaultTables(report, previousValues);

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
     * 일보에 등록된 이미지 개수 (업로드 개수 제한 검증용)
     */
    public long countImages(Long reportId) {
        return imageRepository.countByDailyReport_ReportId(reportId);
    }

    /**
     * ★ 다운로드 전용 — reportId + imageId가 모두 일치하는 이미지 메타 조회
     * (다른 일보의 이미지를 imageId만으로 조회하는 것을 방지)
     */
    public ImageResponse getImageForDownload(Long reportId, Long imageId) {
        DailyReportImage image = imageRepository
                .findByImageIdAndDailyReport_ReportId(imageId, reportId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));
        return ImageResponse.from(image);
    }

    /** 일보당 최대 첨부 이미지 개수 (프론트엔드 MAX_UPLOAD_IMAGES와 동일하게 서버에서도 강제) */
    private static final int MAX_IMAGES_PER_REPORT = 8;

    /**
     * 이미지 메타 저장
     * - ★ 실제 파일 바이너리는 ImageController가 로컬 디스크에 먼저 저장한 뒤,
     *   그 결과(storedPath 등)를 파라미터로 넘겨 이 메서드가 DB 메타 레코드만 생성한다.
     */
    @Transactional
    public ImageResponse addImage(Long reportId, String originalName, String storedPath,
                                  Long fileSize, String contentType, String description,
                                  String tableCode, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateReportEditable(report);
        validateImageEditableDate(report);

        long currentCount = imageRepository.countByDailyReport_ReportId(reportId);
        if (currentCount >= MAX_IMAGES_PER_REPORT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    String.format("이미지는 일보당 최대 %d장까지 등록할 수 있습니다.", MAX_IMAGES_PER_REPORT));
        }

        int sortOrder = (int) currentCount + 1;

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
        validateImageEditableDate(image.getDailyReport());
        image.updateDescription(description);
        return ImageResponse.from(image);
    }

    /**
     * 이미지 삭제
     * - DB 메타 레코드를 삭제하고, 실제 파일도 함께 지울 수 있도록 storedPath를 반환한다.
     *   (물리 파일 삭제는 ImageController가 이 값을 받아 처리 — 파일시스템 접근은
     *   컨트롤러/전용 헬퍼에서만 하도록 서비스 계층 책임을 분리)
     */
    @Transactional
    public String deleteImage(Long imageId) {
        DailyReportImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));

        validateReportEditable(image.getDailyReport());
        validateImageEditableDate(image.getDailyReport());
        String storedPath = image.getStoredPath();
        imageRepository.delete(image);
        return storedPath;
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
    private void createDefaultTables(DailyReport report, Map<String, String> previousValues) {
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
            // ★ 담당자(OWNER_IDS/OWNER_NAMES)는 하드코딩하지 않음 — 항상 NULL로 시작
            // ★ 표1/표2 "진짜 값 롤링" — 과거 달의 월말 실측값을 DB에서 조회하는
            //   콜백(historicalValueLookup)을 넘긴다 (커트오프 이전 달은 템플릿
            //   내부에서 실측 조회를 시도하지 않고 하드코딩 샘플로 대체하므로
            //   이 콜백은 호출되지 않음)
            DefaultCellTemplate.populateDefaultCells(table, report.getReportDate(), historicalValueLookup());
            // ★ 값 이어받기: 직전 일보에 입력되어 있던 DATA 셀 값을 새 표의 초기값으로 반영
            applyCarriedOverValues(table, previousValues);
            // ★ 생성 즉시 현재 활성 CellAuth 담당자를 반영 (코드 수정/재배포 불필요)
            cellOwnershipSyncService.applyCurrentOwnersToNewTable(table);
        }
    }

    /**
     * ★ 표1/표2 롤링 과거 컬럼의 실측(월말 대표값=누적값) 조회 콜백 생성.
     *
     * - {@link DefaultCellTemplate}이 커트오프(2026-07-22) 이후 달에 대해서만
     *   이 콜백을 호출한다 — 커트오프 이전 달은 이 메서드에 도달하지 않는다.
     * - "그 달의 월말 대표값"은 해당 연월 범위 내에서 가장 최근 날짜에 기록된
     *   실측(라이브 DATA 컬럼) 값으로 정의한다(사용자 확인: 월말값=그 달의
     *   누적/대표값). 조회 시작일은 커트오프 날짜로 하한을 두어, 월초가
     *   커트오프보다 이르더라도 커트오프 이전 데이터는 절대 사용하지 않는다.
     */
    private DefaultCellTemplate.HistoricalValueLookup historicalValueLookup() {
        return (tableCode, rowIndex, liveColIndex, targetMonth) -> {
            LocalDate monthStart = targetMonth.atDay(1);
            LocalDate monthEnd = targetMonth.atEndOfMonth();
            LocalDate rangeStart = monthStart.isBefore(DefaultCellTemplate.FEATURE_CUTOFF_DATE)
                    ? DefaultCellTemplate.FEATURE_CUTOFF_DATE
                    : monthStart;
            if (rangeStart.isAfter(monthEnd)) {
                return null; // 이론상 도달 불가 (호출측이 커트오프 이전 달을 걸러냄)
            }
            List<DailyReportCell> candidates = cellRepository.findMonthlyValueCandidates(
                    tableCode, rowIndex, liveColIndex, rangeStart, monthEnd);
            return candidates.isEmpty() ? null : candidates.get(0).getCellValue();
        };
    }

    /**
     * ★ 값 이어받기(carry-over) — 주어진 날짜 이전(과거)의 가장 최근 일보에서
     *   모든 표의 DATA 셀 값을 (tableCode + excelCoord) 키로 모아 반환한다.
     *
     * - HEADER/READONLY 셀은 대상이 아니며, DefaultCellTemplate이 자체적으로
     *   고정값을 채우므로 여기서는 조회조차 하지 않는다 (findEditableCellsByTableId는
     *   cellType='DATA' 조건이 있어 자동으로 제외됨 — 단, 이 메서드는 표 단위가
     *   아니라 일보 전체 단위 조회이므로 findDataCellsByTableCode가 아닌
     *   report.getTables() 순회로 직접 필터링한다).
     * - 빈 값("")이나 null인 셀은 이어받을 필요가 없으므로 맵에서 제외한다.
     *
     * @return key = "tableCode:excelCoord", value = 직전 일보에 입력된 값
     */
    private Map<String, String> findPreviousCellValues(LocalDate reportDate) {
        Optional<DailyReport> previous = reportRepository
                .findTopByReportDateLessThanOrderByReportDateDesc(reportDate);
        if (previous.isEmpty()) {
            return Map.of();
        }

        return previous.get().getTables().stream()
                .flatMap(table -> table.getCells().stream()
                        .filter(cell -> "DATA".equals(cell.getCellType()))
                        .filter(cell -> cell.getCellValue() != null && !cell.getCellValue().isBlank())
                        .filter(cell -> cell.getExcelCoord() != null)
                        .map(cell -> Map.entry(
                                carryOverKey(table.getTableCode(), cell.getExcelCoord()),
                                cell.getCellValue())))
                // 동일 키가 중복될 경우(이론상 발생하지 않아야 하나 방어적으로) 나중 값 우선
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (existing, replacement) -> replacement));
    }

    /**
     * ★ 값 이어받기 — 새로 생성된 표의 DATA 셀 중, previousValues 맵에 대응하는
     *   값이 있는 셀에 한해 초기값을 채워 넣는다.
     *
     * - DefaultCellTemplate이 생성한 직후의 셀은 항상 cellValue=""(빈 값)이므로,
     *   "이미 값이 있는 셀을 덮어쓸 위험"은 이 호출 시점에는 없다. 그래도 향후
     *   호출 순서가 바뀔 가능성에 대비해 방어적으로 빈 값인 셀만 채운다.
     * - HEADER/READONLY 셀은 cellType이 DATA가 아니므로 자동으로 건너뛴다.
     * - 수정자(LAST_EDITOR_ID)/수정일시(LAST_EDITED_AT)는 절대 건드리지 않는다
     *   (carryOverValue()가 값만 변경) — 오늘 아직 누구도 입력하지 않았다는
     *   사실은 그대로 유지된다.
     */
    private void applyCarriedOverValues(DailyReportTable table, Map<String, String> previousValues) {
        if (previousValues.isEmpty()) {
            return;
        }
        for (DailyReportCell cell : table.getCells()) {
            if (!"DATA".equals(cell.getCellType())) {
                continue;
            }
            if (cell.getCellValue() != null && !cell.getCellValue().isBlank()) {
                continue; // 이미 값이 있으면 덮어쓰지 않음
            }
            String key = carryOverKey(table.getTableCode(), cell.getExcelCoord());
            String previousValue = previousValues.get(key);
            if (previousValue != null) {
                cell.carryOverValue(previousValue);
            }
        }
    }

    private String carryOverKey(String tableCode, String excelCoord) {
        return tableCode + ":" + excelCoord;
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

    /**
     * ★★ 이미지 첨부(업로드/삭제/설명수정)는 "오늘 또는 어제" 날짜의 일보에서만
     * 허용한다 — 셀 편집 가능 기간(CellService.isCellEditableForUser)과 동일한
     * 기준. 그 외 과거/미래 일보의 이미지는 화면에는 계속 표시되지만(조회+다운로드는
     * 항상 가능) 추가/삭제/설명수정은 서버에서 거부한다.
     */
    private void validateImageEditableDate(DailyReport report) {
        LocalDate reportDate = report.getReportDate();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        if (reportDate == null || (!reportDate.isEqual(today) && !reportDate.isEqual(yesterday))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "이미지는 오늘 또는 어제 날짜의 일보에서만 등록/삭제할 수 있습니다. 그 외 날짜는 다운로드만 가능합니다.");
        }
    }
}
