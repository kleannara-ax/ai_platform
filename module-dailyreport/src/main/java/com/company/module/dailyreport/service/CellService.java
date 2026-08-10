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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 셀 데이터 입력 및 권한 기반 편집 관리 서비스
 *
 * ★ Phase 4 변경사항:
 * - 레거시 CellPermission(행/열 범위) → CellAuth(엑셀 좌표 JSON) 기반으로 전환
 * - 소유권(OWNER_IDS) 1차 확인 → CellAuth 2차 확인 (두 경로 모두 지원)
 * - 입력 주기(FREQ_CODE): daily/event (2026-08: monthly/yearly 폐기, 둘 다 항상 활성화)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellService {

    private final DailyReportRepository reportRepository;
    private final DailyReportTableRepository tableRepository;
    private final DailyReportCellRepository cellRepository;
    private final CellAuthRepository cellAuthRepository;
    private final DailyReportService dailyReportService;

    /**
     * ★★ 롤링 헤더가 참조하는 "실측(라이브 입력)" 컬럼의 colIndex — 표코드별로 1개씩.
     * {@link DefaultCellTemplate}의 liveCol 상수와 반드시 동일하게 유지해야 한다
     * (표1=O열=13, 표2=M열=11, 표4=P열=6). 표3(에너지)은 롤링 헤더 자체가 없으므로
     * 이 맵에 없다 — 이 맵에 없는 표코드는 롤링 재계산 대상에서 자동 제외된다.
     */
    private static final Map<String, Integer> ROLLING_LIVE_COL_BY_TABLE = Map.of(
            "TBL_PRODUCTION_INDEX", 13,
            "TBL_INVENTORY", 11,
            "TBL_BOILER", 6
    );

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

        // ★ hover "최종 저장자" 표시용 fallback 조회 (2026-08, 표당 1회 배치 조회)
        Map<String, Object[]> fallbackByCoord = loadFallbackEditorInfo(table.getTableCode(), reportDate);

        // 각 셀에 편집 가능 여부 설정 + hover 표시용 최종 저장자 정보 계산
        List<CellResponse> cellResponses = table.getCells().stream()
                .map(cell -> {
                    boolean editable = isCellEditableForUser(cell, loginId, cellAuths, reportDate);
                    CellResponse response = CellResponse.fromWithEditability(cell, editable);
                    return response;
                })
                .toList();

        applyDisplayEditorInfo(cellResponses, fallbackByCoord);

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
     * ★ 셀 hover "최종 저장자/시각" 표시 fallback (2026-08 추가).
     * - lastEditorId가 이미 있는 셀은 그 값을 그대로 표시용으로 쓴다.
     * - lastEditorId가 null인(이월/carry-over 상태) 셀은, 같은 좌표에서 조회
     *   기준일(reportDate) 이전 날짜 중 실제로 사람이 입력한 가장 최근 값을
     *   {@link DailyReportCellRepository#findLastRealEditorByCoordUpToDate}로
     *   표(tableCode) 단위 1회 배치 조회하여 대신 채운다.
     * - 저장 기록이 전혀 없는 좌표는 표시할 정보가 없으므로 null로 남는다.
     */
    private Map<String, Object[]> loadFallbackEditorInfo(String tableCode, LocalDate reportDate) {
        List<Object[]> rows = cellRepository.findLastRealEditorByCoordUpToDate(tableCode, reportDate);
        Map<String, Object[]> byCoord = new LinkedHashMap<>();
        for (Object[] row : rows) {
            // row = [rowIndex, colIndex, lastEditorId, lastEditedAt]
            String key = row[0] + "," + row[1];
            byCoord.put(key, row);
        }
        return byCoord;
    }

    /**
     * cellResponses에 표시용 displayEditorName/displayEditedAt을 채운다.
     * - 원본 lastEditorId가 있으면 그 값을 우선 사용, 없으면 fallback 맵에서 채움.
     * - 필요한 userId만 모아 DailyReportService.resolveUserNames()로 이름 일괄 조회
     *   (셀당 개별 조회 없이 한 번에 처리 — N+1 방지).
     */
    private void applyDisplayEditorInfo(List<CellResponse> cellResponses,
                                         Map<String, Object[]> fallbackByCoord) {
        // 각 셀이 표시에 사용할 (editorId, editedAt) 결정
        Map<CellResponse, Long> resolvedEditorIdByCell = new LinkedHashMap<>();
        Map<CellResponse, LocalDateTime> resolvedEditedAtByCell = new LinkedHashMap<>();
        Set<Long> userIdsToResolve = new HashSet<>();

        for (CellResponse cell : cellResponses) {
            Long editorId = cell.getLastEditorId();
            LocalDateTime editedAt = cell.getLastEditedAt();

            if (editorId == null) {
                String key = cell.getRowIndex() + "," + cell.getColIndex();
                Object[] fallback = fallbackByCoord.get(key);
                if (fallback != null) {
                    editorId = ((Number) fallback[2]).longValue();
                    editedAt = toLocalDateTime(fallback[3]);
                }
            }

            if (editorId != null) {
                resolvedEditorIdByCell.put(cell, editorId);
                resolvedEditedAtByCell.put(cell, editedAt);
                userIdsToResolve.add(editorId);
            }
        }

        Map<Long, String> userNames = dailyReportService.resolveUserNames(userIdsToResolve);

        for (CellResponse cell : cellResponses) {
            Long editorId = resolvedEditorIdByCell.get(cell);
            if (editorId == null) {
                continue;
            }
            cell.setDisplayEditorName(userNames.get(editorId));
            cell.setDisplayEditedAt(resolvedEditedAtByCell.get(cell));
        }
    }

    /** JDBC 네이티브 쿼리 결과(java.sql.Timestamp 등)를 LocalDateTime으로 안전 변환 */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
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

            // ★★ 값 전파 안전장치(2026-08): 프론트가 "저장" 클릭 시 화면에 보이는
            // 편집 가능한 셀을 전부 다시 전송하므로(변경 여부와 무관), 실제로 값이
            // 바뀌지 않았다면 편집자 도장(LAST_EDITOR_ID)을 찍지 않고 그대로 둔다.
            // 그렇지 않으면 한 사용자가 여러 셀을 담당할 때, 그중 하나만 고쳐도
            // 나머지 안 건드린(이어받기 상태인) 담당 셀까지 매 저장마다 "사람이
            // 직접 입력한 값"으로 바뀌어 버려, 그 셀은 이후 더 이전 날짜에서
            // 실제로 값을 고쳐도 propagateValueForward가 여기서 멈춰버리는
            // 문제가 있었다 (특이사항/remark 쪽의 updateRemark()에 있던 동일한
            // unchanged 가드를 셀에도 동일하게 적용).
            String newValue = item.getCellValue();
            String previousValue = cell.getCellValue();
            boolean valueChanged = !Objects.equals(
                    previousValue == null ? "" : previousValue,
                    newValue == null ? "" : newValue);

            if (valueChanged) {
                cell.updateValue(newValue, userId);

                // ★★ 값 전파: 이 저장으로 미래에 이미 만들어져 있는 일보의 이어받기 값도 최신화
                propagateValueForward(report.getReportDate(), table.getTableCode(),
                        cell.getExcelCoord(), newValue);

                // ★★ 롤링 헤더 즉시 재계산(2026-08 추가): 이 셀이 표1/표2/표4의 "실측
                // (라이브 입력)" 컬럼이면, 이미 만들어져 있는 미래 일보들의 과거월/연평균
                // 헤더값(H~N, F/G, M/N 등)도 그 자리에서 바로 다시 계산해 반영한다.
                // (관리자용 "월 롤링 헤더 재계산" 전체 배치와 달리, 이 저장으로 실제
                // 영향받을 수 있는 좁은 범위만 훑으므로 데이터가 아무리 많아져도 느려지지 않는다.)
                propagateRollingHeadersForward(report.getReportDate(), table.getTableCode(),
                        cell.getColIndex());
            }
            // 값이 바뀌지 않았다면 위 두 동작(도장 찍기/전파) 모두 건너뛴다 —
            // 이 셀은 여전히 "이어받기 상태"로 남아, 향후 더 이전 날짜에서의
            // 실제 수정이 이 셀까지 정상적으로 전파될 수 있다.

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

    /**
     * ★★ 값 전파(forward propagation, 2026-08 추가) — 이미 만들어져 있는 미래 일보 중
     * "아직 아무도 직접 수정하지 않은"(=이어받기 상태 그대로인) 동일 좌표 셀 값을
     * 방금 입력한 값으로 갱신한다.
     *
     * 배경: 값 이어받기(carry-over, {@link DailyReportService#createDefaultTables})는
     * 일보가 "처음 생성되는 시점"에 그 시점 기준 가장 최근 일보의 값을 1회 복사하는
     * 방식이다. 그런데 미래 일보를 자유롭게 미리 열람/편집할 수 있게 되면서 다음과
     * 같은 문제가 생긴다:
     *   예) 오늘이 8/31인데 9/5 일보를 미리 열어보면(=자동 생성) 9/5는 아직 존재하지
     *   않는 9/1~9/4가 아니라 그 시점 가장 최근 일보(8/31)의 값을 이어받는다.
     *   이후 9/4가 되어 9/4 일보에 실제 값을 입력해도, 9/5는 이미 만들어져 있으므로
     *   carry-over가 다시 실행되지 않아 예전(8/31 기준) 값에 멈춰 있게 된다.
     *
     * 해결: 셀 값을 저장할 때마다 그 날짜 "다음"에 이미 존재하는 일보들을 날짜
     * 순서대로 순회하며, 동일 표(tableCode)/좌표(excelCoord)의 셀이 아직 사람이
     * 직접 입력한 적 없는(LAST_EDITOR_ID가 null인, 즉 이어받기 값 그대로인) 상태라면
     * 방금 입력한 값으로 갱신하고 계속 다음 날짜로 전파한다.
     *
     * 반대로 어느 미래 일보에서든 그 셀에 사람이 이미 직접 값을 입력해 둔 경우
     * (LAST_EDITOR_ID != null)라면 — 예: 9/5에 장기재고 값을 미리 입력해 둔 경우 —
     * 그 값은 의도적인 사전 입력/오버라이드이므로 그 시점에서 전파를 멈추고 절대
     * 덮어쓰지 않으며, 그보다 더 뒤(9/6 이후)로도 전파하지 않는다(그 뒤 일보들은
     * 이미 그 의도적인 값을 기준으로 이어받았을 것이기 때문).
     *
     * ※ {@link DailyReportCell#carryOverValue}를 사용하므로 LAST_EDITOR_ID/
     *   LAST_EDITED_AT은 변경되지 않는다 — 여전히 "이어받은 값일 뿐 아직 아무도
     *   직접 입력하지 않았다"는 상태가 그대로 유지되어, 이후 또 다른 과거 날짜
     *   수정이 있어도 계속 전파 대상이 될 수 있다.
     * ※ 무한 루프/과도한 조회 방지를 위해 최대 366일(약 1년)까지만 전파한다.
     */
    private void propagateValueForward(LocalDate fromDate, String tableCode,
                                        String excelCoord, String newValue) {
        if (excelCoord == null) {
            return;
        }
        LocalDate cursor = fromDate;
        for (int hop = 0; hop < 366; hop++) {
            DailyReport nextReport = reportRepository
                    .findTopByReportDateGreaterThanOrderByReportDateAsc(cursor)
                    .orElse(null);
            if (nextReport == null) {
                break; // 더 이상 미래에 생성된 일보가 없음
            }
            cursor = nextReport.getReportDate();

            DailyReportTable nextTable = nextReport.getTables().stream()
                    .filter(t -> tableCode.equals(t.getTableCode()))
                    .findFirst()
                    .orElse(null);
            if (nextTable == null) {
                continue; // 이 표가 없는 일보(구조 변경 등) — 건너뛰고 다음 날짜로 계속
            }

            DailyReportCell nextCell = nextTable.getCells().stream()
                    .filter(c -> excelCoord.equals(c.getExcelCoord()))
                    .findFirst()
                    .orElse(null);
            if (nextCell == null || !"DATA".equals(nextCell.getCellType())) {
                continue; // 좌표 불일치/DATA 아님 — 건너뛰고 다음 날짜로 계속
            }

            if (nextCell.getLastEditorId() != null) {
                break; // 이미 사람이 직접 입력해 둔 값 — 의도적 오버라이드이므로 전파 중단
            }

            if (!Objects.equals(nextCell.getCellValue(), newValue)) {
                nextCell.carryOverValue(newValue);
            }
            // 이 셀은 여전히 "이어받기 상태" — 계속 다음 날짜로 전파
        }
    }

    /**
     * ★★ 롤링 헤더 즉시 전파(2026-08 추가) — 표1(생산지표)/표2(재공품)/표4(보일러)의
     * "실측(라이브 입력)" 컬럼(O/M/P열)이 저장될 때마다, 이미 만들어져 있는 미래
     * 일보들 중 이 값을 과거월 헤더/연평균으로 참조할 수 있는 표만 그 자리에서
     * 즉시 다시 계산해 반영한다.
     *
     * 배경: 관리자용 "월 롤링 헤더 재계산"({@link DailyReportService#refreshRollingHeaders})은
     * 전체 일보×전체 표를 매번 통째로 훑기 때문에, 운영 데이터가 쌓일수록
     * (일보 수 × 표 수 × 표당 실측 조회 횟수) 점점 느려져 API 타임아웃 위험이
     * 커진다. 반면 이 메서드는 "지금 저장한 값이 실제로 영향을 줄 수 있는
     * 이미 존재하는 미래 일보"만 좁게 훑으므로, 과거 데이터가 아무리 많이
     * 누적되어도(=이 저장과 무관) 항상 빠르게 끝난다.
     *
     * ※ 어느 지점까지 영향을 줄 수 있는지(maxHorizonMonths)는 표별 롤링 계산
     *   범위에 따라 다르다:
     *   - 표1(TBL_PRODUCTION_INDEX): 과거 7개월 롤링 창(최대 +7개월) + '24/'25년
     *     스타일의 연평균(F/G열, 최대 +2년) → 넉넉하게 +36개월까지 확인
     *   - 표2(TBL_INVENTORY): 과거 7개월 롤링 창만 있음 → +8개월까지 확인
     *   - 표4(TBL_BOILER): 전전월/전월 실적 2칸뿐 → +3개월까지 확인
     * ※ 무한 루프/과도한 조회 방지를 위해 hop 수 자체도 최대 1200회(약 3년치
     *   일보)로 상한을 둔다 — maxHorizonMonths 조건이 먼저 걸려 실제로는
     *   훨씬 일찍 끝난다.
     */
    private void propagateRollingHeadersForward(LocalDate fromDate, String tableCode, Integer colIndex) {
        Integer liveCol = ROLLING_LIVE_COL_BY_TABLE.get(tableCode);
        if (liveCol == null || colIndex == null || !liveCol.equals(colIndex)) {
            return; // 이 표에 롤링 헤더가 없거나, 저장된 셀이 실측(라이브) 컬럼이 아님
        }

        int maxHorizonMonths = switch (tableCode) {
            case "TBL_PRODUCTION_INDEX" -> 36;
            case "TBL_INVENTORY" -> 8;
            case "TBL_BOILER" -> 3;
            default -> 0;
        };
        if (maxHorizonMonths <= 0) {
            return;
        }

        YearMonth changedMonth = YearMonth.from(fromDate);
        LocalDate cursor = fromDate;
        for (int hop = 0; hop < 1200; hop++) {
            DailyReport nextReport = reportRepository
                    .findTopByReportDateGreaterThanOrderByReportDateAsc(cursor)
                    .orElse(null);
            if (nextReport == null) {
                break; // 더 이상 미래에 생성된 일보가 없음
            }
            cursor = nextReport.getReportDate();

            YearMonth nextMonth = YearMonth.from(cursor);
            if (ChronoUnit.MONTHS.between(changedMonth, nextMonth) > maxHorizonMonths) {
                break; // 이 표의 롤링 계산이 더 이상 changedMonth를 참조할 수 없는 시점 이후
            }

            DailyReportTable nextTable = nextReport.getTables().stream()
                    .filter(t -> tableCode.equals(t.getTableCode()))
                    .findFirst()
                    .orElse(null);
            if (nextTable == null) {
                continue; // 이 표가 없는 일보(구조 변경 등) — 건너뛰고 다음 날짜로 계속
            }

            dailyReportService.refreshTableRollingHeaders(nextTable, cursor);
        }
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
     * ★★★★★ 편집 가능 일보를 "어제 이후(어제/오늘/미래 전체) + 매월 말일"로 한정
     * (2026-07 → 오늘·어제로 최초 도입 → 2026-08 미래 전체로 확장 → 2026-08
     * 매월 말일 예외 추가):
     * 이 셀이 속한 일보의 REPORT_DATE가 어제(today.minusDays(1))보다 이전(더
     * 오래된 과거)이면서 그 REPORT_DATE가 해당 월의 "말일"도 아니라면 —
     * freqCode(daily/event/monthly/yearly)와 무관하게 — 항상 편집 불가(조회
     * 전용) 처리한다. 어제/오늘/미래 일보, 그리고 과거라도 그 달의 말일(28~31일,
     * 월별 실제 마지막 날. 2월은 28일 또는 윤년 29일)인 일보는 모두 아래
     * 주기별 규칙이 적용된다(미래 일보를 미리 열어 값을 채워 넣을 수 있어야
     * 하므로 과거만 차단하고 미래는 막지 않는다. 말일은 월 마감 결산 특성상
     * 예외적으로 계속 열어둔다):
     *   - daily/event: 무조건 활성화
     *   - monthly: 해당 일보의 REPORT_DATE가 매월 1일인 경우에만 활성화
     *   - yearly: 해당 일보의 REPORT_DATE가 매년 1월 1일인 경우에만 활성화
     * ※ 주기 판정은 "실제 오늘"이 아니라 "편집하려는 리포트의 REPORT_DATE"
     *   기준으로 한다. 예) 오늘이 4/2이고 어제(4/1)치 리포트를 늦게 입력하는
     *   경우, 어제(4/1)가 매월 1일이므로 월간 셀도 정상적으로 편집 가능해야
     *   한다. 같은 원리로 미래 리포트도 그 리포트 자신의 날짜로 주기를 판정한다.
     */
    private boolean isCellEditableForUser(DailyReportCell cell,
                                           String loginId,
                                           List<CellAuth> cellAuths,
                                           LocalDate reportDate) {
        // 잠금 상태면 편집 불가
        if (Boolean.TRUE.equals(cell.getIsLocked())) {
            return false;
        }

        // 어제보다 이전(과거)의 일보면 — 그 달의 말일이 아닌 한 — 주기 불문
        // 항상 편집 불가(조회만 가능). 어제/오늘/미래(그 이후 모든 날짜),
        // 그리고 과거라도 매월 말일인 일보는 차단하지 않고 아래 주기 규칙으로
        // 판정한다.
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        if (reportDate == null) {
            return false;
        }
        if (reportDate.isBefore(yesterday) && !isLastDayOfMonth(reportDate)) {
            return false;
        }

        // CellAuth 좌표 기반 권한 확인 (유일한 판단 기준)
        if ("DATA".equals(cell.getCellType()) && cellAuths != null && !cellAuths.isEmpty()) {
            String coord = cell.getExcelCoord();
            if (coord != null) {
                for (CellAuth cellAuth : cellAuths) {
                    if (cellAuth.coversCoord(coord)) {
                        return canEditByFrequency(cellAuth.getFreqCode(), reportDate);
                    }
                }
            }
        }

        // CellAuth가 없거나 이 좌표를 커버하지 않으면 빈값(편집 불가) 취급
        return false;
    }

    /**
     * ★★★ 입력 주기 단순화 (2026-08): daily(매일) / event(발생 시) 두 가지만
     * 유효한 주기이며, 이 둘은 원래부터 "항상 활성화"로 동일하게 동작했다.
     * 매월 1일/매년 1월 1일에만 활성화되던 monthly/yearly 개념은 완전히
     * 폐기한다 — 컬럼관리 대시보드(cell-auth-admin.html)에서도 이제 주기
     * 선택 옵션은 daily/event 두 개뿐이다.
     *
     * ★ 이미 DB에 남아있을 수 있는 레거시 monthly/yearly 값(마이그레이션 SQL로
     *   daily로 일괄 정리하는 것이 원칙이지만, 혹시 정리되지 않은 값이 있더라도)도
     *   방어적으로 "항상 활성화"로 동일하게 취급한다 — freqCode가 null이 아니면
     *   그 값이 무엇이든 더 이상 날짜(일/월)로 제약하지 않는다.
     */
    private boolean canEditByFrequency(String freqCode, LocalDate targetDate) {
        return freqCode != null && targetDate != null;
    }

    /**
     * ★ 매월 말일 판정 (2026-08 추가) — 과거 일보라도 그 REPORT_DATE가 해당 월의
     * 마지막 날(28~31일, 월별로 다름. 2월은 평년 28일/윤년 29일)이면 월 마감
     * 결산 목적상 예외적으로 편집을 계속 허용한다.
     * ※ {@link DailyReportService#isLastDayOfMonth}와 동일한 기준이며,
     *   두 클래스가 서로 참조하지 않도록 각자 독립적으로 계산한다(로직은
     *   {@code date.equals(date.with(TemporalAdjusters.lastDayOfMonth()))}와
     *   동치).
     */
    private boolean isLastDayOfMonth(LocalDate date) {
        return date != null && date.getDayOfMonth() == date.lengthOfMonth();
    }
}
