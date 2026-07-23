package com.company.module.dailyreport.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dailyreport.dto.CellResponse;
import com.company.module.dailyreport.dto.CellSaveRequest;
import com.company.module.dailyreport.dto.ReportTableResponse;
import com.company.module.dailyreport.entity.CellAuth;
import com.company.module.dailyreport.entity.DailyReport;
import com.company.module.dailyreport.entity.DailyReportCell;
import com.company.module.dailyreport.entity.DailyReportTable;
import com.company.module.dailyreport.repository.CellAuthRepository;
import com.company.module.dailyreport.repository.DailyReportCellRepository;
import com.company.module.dailyreport.repository.DailyReportRepository;
import com.company.module.dailyreport.repository.DailyReportTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 셀 데이터 입력 및 권한 기반 편집 관리 서비스
 *
 * ★ Phase 4 변경사항:
 * - 레거시 CellPermission(행/열 범위) → CellAuth(엑셀 좌표 JSON) 기반으로 전환
 * - 소유권(OWNER_IDS) 1차 확인 → CellAuth 2차 확인 (두 경로 모두 지원)
 * - 입력 주기(FREQ_CODE): daily/monthly/yearly/event에 따라 활성화
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellService {

    private final DailyReportRepository reportRepository;
    private final DailyReportTableRepository tableRepository;
    private final DailyReportCellRepository cellRepository;
    private final CellAuthRepository cellAuthRepository;

    /**
     * 사용자 기준 표 데이터 조회 (편집 가능 여부 포함)
     * - OWNER_IDS 기반 소유권 확인
     * - CellAuth 기반 좌표 권한 확인
     * - freqCode 기반 입력 가능 시점 확인
     */
    public ReportTableResponse getTableDataForUser(Long reportId, String tableCode,
                                                    Long userId, String loginId) {
        DailyReportTable table = tableRepository
                .findByDailyReport_ReportIdAndTableCode(reportId, tableCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "표를 찾을 수 없습니다. reportId=" + reportId + ", tableCode=" + tableCode));

        // CellAuth 기반 권한 조회 (JSON 좌표 목록)
        CellAuth cellAuth = cellAuthRepository
                .findByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode)
                .orElse(null);

        // 각 셀에 편집 가능 여부 설정
        List<CellResponse> cellResponses = table.getCells().stream()
                .map(cell -> {
                    boolean editable = isCellEditableForUser(cell, loginId, cellAuth);
                    return CellResponse.fromWithEditability(cell, editable);
                })
                .toList();

        return ReportTableResponse.builder()
                .tableId(table.getTableId())
                .tableCode(table.getTableCode())
                .tableName(table.getTableName())
                .sortOrder(table.getSortOrder())
                .rowCount(table.getRowCount())
                .colCount(table.getColCount())
                .cells(cellResponses)
                .build();
    }

    /**
     * 셀 값 일괄 저장 (사용자 권한 검증 포함)
     */
    @Transactional
    public List<CellResponse> saveCells(Long reportId, CellSaveRequest request,
                                         Long userId, String loginId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        // 확정된 일보는 수정 불가
        if ("CONFIRMED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "확정된 일보는 수정할 수 없습니다.");
        }

        DailyReportTable table = tableRepository
                .findByDailyReport_ReportIdAndTableCode(reportId, request.getTableCode())
                .orElseThrow(() -> new EntityNotFoundException(
                        "표를 찾을 수 없습니다: " + request.getTableCode()));

        // CellAuth 기반 권한 조회
        CellAuth cellAuth = cellAuthRepository
                .findByUserIdAndTableCodeAndIsActiveTrue(userId, request.getTableCode())
                .orElse(null);

        List<CellResponse> savedCells = new ArrayList<>();

        for (CellSaveRequest.CellValueItem item : request.getCells()) {
            DailyReportCell cell = cellRepository
                    .findByReportTable_TableIdAndRowIndexAndColIndex(
                            table.getTableId(), item.getRowIndex(), item.getColIndex())
                    .orElseThrow(() -> new EntityNotFoundException(
                            String.format("셀을 찾을 수 없습니다. row=%d, col=%d",
                                    item.getRowIndex(), item.getColIndex())));

            // 잠금 상태 확인
            if (cell.getIsLocked()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("잠금된 셀은 수정할 수 없습니다. row=%d, col=%d",
                                item.getRowIndex(), item.getColIndex()));
            }

            // 소유권 + CellAuth 권한 검증
            if (!isCellEditableForUser(cell, loginId, cellAuth)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        String.format("해당 셀에 대한 편집 권한이 없습니다. row=%d, col=%d (%s)",
                                item.getRowIndex(), item.getColIndex(),
                                cell.getExcelCoord() != null ? cell.getExcelCoord() : ""));
            }

            cell.updateValue(item.getCellValue(), userId);
            savedCells.add(CellResponse.from(cell));
        }

        return savedCells;
    }

    /**
     * 입력 주기별 셀 잠금/해제 (스케줄러 또는 관리자 호출)
     */
    @Transactional
    public int toggleCellLockByCycle(Long tableId, String inputCycle, boolean locked) {
        return cellRepository.updateLockByCycle(tableId, inputCycle, locked);
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    /**
     * 셀이 사용자에게 편집 가능한지 판단 (★ Phase 4 개선)
     *
     * 판단 순서:
     * 1. 잠금 셀이면 불가
     * 2. OWNER_IDS 존재 + 본인 소유 → 소유권 주기 확인으로 결정
     * 3. DATA 셀 + CellAuth 좌표 매칭 → CellAuth 주기 확인으로 결정
     * 4. 그 외 → 불가
     *
     * ※ 2순위 → 3순위 fallback: OWNER_IDS가 있지만 본인 소유가 아닌 셀도
     *   CellAuth에 좌표가 등록되어 있으면 편집 가능 (관리자가 다른 사용자에게
     *   특정 셀 편집 권한을 부여하는 유스케이스 지원)
     */
    private boolean isCellEditableForUser(DailyReportCell cell,
                                           String loginId,
                                           CellAuth cellAuth) {
        // 잠금 상태면 편집 불가
        if (Boolean.TRUE.equals(cell.getIsLocked())) {
            return false;
        }

        // 1순위: 소유권 기반 확인 (OWNER_IDS 존재 + 본인 소유)
        if (cell.isAssignable() && cell.isOwnedBy(loginId)) {
            return canEditByFrequency(cell.getFreqCode());
        }

        // 2순위: CellAuth 좌표 기반 권한 확인
        // (OWNER_IDS가 있지만 본인 소유가 아닌 경우에도 CellAuth로 편집 가능)
        if ("DATA".equals(cell.getCellType()) && cellAuth != null) {
            String coord = cell.getExcelCoord();
            if (coord != null && cellAuth.coversCoord(coord)) {
                return canEditByFrequency(cellAuth.getFreqCode());
            }
        }

        return false;
    }

    /**
     * 주기(freqCode)에 따라 오늘 입력 가능한지 판단
     * - daily: 매일 입력 가능
     * - event: 발생 시 입력 = 매일 입력 가능
     * - monthly: 매월 1일만 입력 가능
     * - yearly: 매년 1월 1일만 입력 가능
     */
    private boolean canEditByFrequency(String freqCode) {
        if (freqCode == null) return false;
        LocalDate today = LocalDate.now();
        return switch (freqCode.toLowerCase()) {
            case "daily", "event" -> true;
            case "monthly" -> today.getDayOfMonth() == 1;
            case "yearly" -> today.getMonthValue() == 1 && today.getDayOfMonth() == 1;
            default -> false;
        };
    }
}
