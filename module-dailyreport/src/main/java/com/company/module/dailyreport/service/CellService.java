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
        // ★★ 다중 주기 지원: 한 사용자가 같은 표에서 여러 CellAuth(주기별 좌표 그룹)를
        // 가질 수 있으므로 List로 조회한다.
        List<CellAuth> cellAuths = cellAuthRepository
                .findAllByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode);

        LocalDate reportDate = table.getDailyReport().getReportDate();

        // 각 셀에 편집 가능 여부 설정
        List<CellResponse> cellResponses = table.getCells().stream()
                .map(cell -> {
                    boolean editable = isCellEditableForUser(cell, loginId, cellAuths, reportDate);
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

        // CellAuth 기반 권한 조회 (★★ 다중 주기 지원: List로 조회)
        List<CellAuth> cellAuths = cellAuthRepository
                .findAllByUserIdAndTableCodeAndIsActiveTrue(userId, request.getTableCode());

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
            if (!isCellEditableForUser(cell, loginId, cellAuths, report.getReportDate())) {
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
     * 셀이 사용자에게 편집 가능한지 판단 (★ Phase 4 개선 → ★★ CellAuth 단일 소스 전환
     * → ★★★ 다중 주기 지원, 2026-07)
     *
     * 판단 순서:
     * 1. 잠금 셀이면 불가
     * 2. DATA 셀 + 이 좌표를 커버하는 CellAuth를 목록에서 찾음 → 그 CellAuth의
     *    주기(FREQ_CODE) 확인으로 결정
     * 3. 그 외(CellAuth 없음/좌표 불일치) → 불가 (빈값 취급)
     *
     * ※ daily_report_cell.OWNER_IDS/FREQ_CODE는 화면 표시용 캐시일 뿐이며
     *   진짜 신뢰 소스(single source of truth)는 항상 daily_report_cell_auth이다.
     *   과거에는 "OWNER_IDS 존재 + 본인 소유"를 1순위로 셀 자체의 낡은 FREQ_CODE로
     *   판단했는데, 이 캐시는 CellOwnershipSyncService.syncOwnerCache()가
     *   OWNER_IDS/OWNER_NAMES만 갱신하고 FREQ_CODE는 건드리지 않으므로, 관리자가
     *   컬럼관리 대시보드에서 주기를 변경해도(CellAuth.FREQ_CODE만 바뀜) 셀의 낡은
     *   FREQ_CODE로 먼저 매칭되어 최신 주기가 반영되지 않는 버그가 있었다.
     *   → CellAuth 매칭 하나로 판단을 단일화하여 이 불일치를 제거한다.
     *
     * ★★★ 다중 주기 지원: 한 사용자가 같은 표에서 서로 다른 주기(예: 매일 담당 셀 +
     * 매년 담당 셀)를 나눠서 담당할 수 있으므로, 이제 CellAuth를 단일 객체가 아닌
     * List로 받아 "이 좌표를 커버하는" 항목을 찾아 그 항목의 FREQ_CODE로 판단한다.
     * (좌표 그룹은 등록 시 서로 겹치지 않도록 CellAuthService에서 검증하므로,
     * 정상적으로는 최대 1건만 매칭된다.)
     *
     * ★★★★ 편집 가능 일보를 "오늘"로 한정 (2026-07): 이 셀이 속한 일보의
     * REPORT_DATE가 실제 오늘(LocalDate.now())과 정확히 일치하지 않으면 —
     * 과거든 미래든 상관없이 — freqCode(daily/event/monthly/yearly)와 무관하게
     * 항상 편집 불가 처리한다. 오늘 날짜 일보에 한해서만 아래 주기별 규칙이
     * 적용된다:
     *   - daily/event: 오늘이면 무조건 활성화
     *   - monthly: 오늘이 매월 1일인 경우에만 활성화
     *   - yearly: 오늘이 매년 1월 1일인 경우에만 활성화
     */
    private boolean isCellEditableForUser(DailyReportCell cell,
                                           String loginId,
                                           List<CellAuth> cellAuths,
                                           LocalDate reportDate) {
        // 잠금 상태면 편집 불가
        if (Boolean.TRUE.equals(cell.getIsLocked())) {
            return false;
        }

        // 오늘 날짜의 일보가 아니면(과거/미래 모두) 주기 불문 항상 편집 불가
        LocalDate today = LocalDate.now();
        if (reportDate == null || !reportDate.isEqual(today)) {
            return false;
        }

        // CellAuth 좌표 기반 권한 확인 (유일한 판단 기준)
        if ("DATA".equals(cell.getCellType()) && cellAuths != null && !cellAuths.isEmpty()) {
            String coord = cell.getExcelCoord();
            if (coord != null) {
                for (CellAuth cellAuth : cellAuths) {
                    if (cellAuth.coversCoord(coord)) {
                        return canEditByFrequency(cellAuth.getFreqCode(), today);
                    }
                }
            }
        }

        // CellAuth가 없거나 이 좌표를 커버하지 않으면 빈값(편집 불가) 취급
        return false;
    }

    /**
     * 주기(freqCode)에 따라, 오늘(today) 기준으로 입력 가능한지 판단.
     * (호출측에서 이미 "이 일보가 오늘 날짜인지"를 확인했으므로, 여기서는
     * today == reportDate == 오늘이 보장된 상태로 호출된다)
     * - daily: 무조건 활성화
     * - event: 발생 시 입력 = 무조건 활성화
     * - monthly: 오늘이 매월 1일인 경우만 활성화
     * - yearly: 오늘이 매년 1월 1일인 경우만 활성화
     */
    private boolean canEditByFrequency(String freqCode, LocalDate today) {
        if (freqCode == null || today == null) return false;
        return switch (freqCode.toLowerCase()) {
            case "daily", "event" -> true;
            case "monthly" -> today.getDayOfMonth() == 1;
            case "yearly" -> today.getMonthValue() == 1 && today.getDayOfMonth() == 1;
            default -> false;
        };
    }
}
