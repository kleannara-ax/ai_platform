package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyReportCellRepository extends JpaRepository<DailyReportCell, Long> {

    /** 표 ID로 모든 셀 조회 (행→열 순) */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableId = :tableId " +
           "ORDER BY c.rowIndex ASC, c.colIndex ASC")
    List<DailyReportCell> findByTableIdOrdered(@Param("tableId") Long tableId);

    /** 표 ID + 좌표로 단건 조회 */
    Optional<DailyReportCell> findByReportTable_TableIdAndRowIndexAndColIndex(
            Long tableId, Integer rowIndex, Integer colIndex);

    /** 표 ID + 행 범위로 셀 조회 */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableId = :tableId " +
           "AND c.rowIndex BETWEEN :rowStart AND :rowEnd " +
           "ORDER BY c.rowIndex ASC, c.colIndex ASC")
    List<DailyReportCell> findByTableIdAndRowRange(
            @Param("tableId") Long tableId,
            @Param("rowStart") int rowStart,
            @Param("rowEnd") int rowEnd);

    /** 특정 표의 DATA 타입 셀만 조회 */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableId = :tableId " +
           "AND c.cellType = 'DATA' " +
           "ORDER BY c.rowIndex ASC, c.colIndex ASC")
    List<DailyReportCell> findEditableCellsByTableId(@Param("tableId") Long tableId);

    /**
     * ★ 표 코드(TABLE_CODE) 기준 전체 DATA 셀 조회 (모든 일보 통틀어)
     * - CellOwnershipSyncService가 CellAuth 변경 시 OWNER_IDS/OWNER_NAMES 캐시를
     *   재계산하기 위해 사용 — 같은 tableCode를 가진 모든 일보(날짜)의 셀이 대상
     */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableCode = :tableCode " +
           "AND c.cellType = 'DATA'")
    List<DailyReportCell> findDataCellsByTableCode(@Param("tableCode") String tableCode);

    /** 일보의 모든 표의 셀을 잠금 처리 (상태 확정 시) */
    @Modifying
    @Query("UPDATE DailyReportCell c SET c.isLocked = true " +
           "WHERE c.reportTable.tableId IN " +
           "(SELECT t.tableId FROM DailyReportTable t WHERE t.dailyReport.reportId = :reportId)")
    int lockAllCellsByReportId(@Param("reportId") Long reportId);

    /** 특정 입력 주기의 셀 잠금/해제 */
    @Modifying
    @Query("UPDATE DailyReportCell c SET c.isLocked = :locked " +
           "WHERE c.reportTable.tableId = :tableId " +
           "AND c.inputCycle = :inputCycle " +
           "AND c.cellType = 'DATA'")
    int updateLockByCycle(@Param("tableId") Long tableId,
                          @Param("inputCycle") String inputCycle,
                          @Param("locked") boolean locked);

    /**
     * ★ 표 롤링(월 이동) 실측값 조회 전용 — 특정 표코드 + 좌표(rowIndex/colIndex)의
     * "매일(daily)" 입력 컬럼에서, 주어진 기간(start~end, 보통 해당 연월의
     * [조회가능 시작일, 월말]) 내 가장 최근 값이 채워진 셀부터 최신순으로 반환한다.
     *
     * - 호출측(DailyReportService)이 결과 리스트의 첫 번째(가장 최근 날짜) 값을
     *   그 달의 "월말 대표값(누적값)"으로 사용한다.
     * - 이 쿼리 자체는 커트오프(개발 시작일 이전 조회 금지) 정책을 모른다 —
     *   호출측이 start 파라미터를 커트오프 날짜 이후로 제한해서 넘겨야 한다.
     */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableCode = :tableCode " +
           "AND c.rowIndex = :rowIndex AND c.colIndex = :colIndex " +
           "AND c.cellType = 'DATA' " +
           "AND c.cellValue IS NOT NULL AND c.cellValue <> '' " +
           "AND c.reportTable.dailyReport.reportDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.reportTable.dailyReport.reportDate DESC")
    List<DailyReportCell> findMonthlyValueCandidates(
            @Param("tableCode") String tableCode,
            @Param("rowIndex") int rowIndex,
            @Param("colIndex") int colIndex,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * ★ 표7(안전사고 연도별 추이) 롤링(연도 이동) 실측값 조회 전용 — 특정 표코드 +
     * 좌표(rowIndex/colIndex)의 "매일(daily)" 입력 컬럼에서, 주어진 연도(targetYear)
     * 전체 기간(1/1~12/31) 내 가장 최근 값이 채워진 셀부터 최신순으로 반환한다.
     *
     * - {@link #findMonthlyValueCandidates}와 동일한 원리를 "연도" 단위로 적용한 것.
     * - 호출측(DailyReportService)이 결과 리스트의 첫 번째(가장 최근 날짜) 값을
     *   그 연도의 "연말 대표값(연간 누적값)"으로 사용한다.
     * - 표5/6과 마찬가지로 신규 기능이라 커트오프 정책이 없다 — 항상 해당 연도
     *   전체 기간을 조회한다.
     */
    @Query("SELECT c FROM DailyReportCell c " +
           "WHERE c.reportTable.tableCode = :tableCode " +
           "AND c.rowIndex = :rowIndex AND c.colIndex = :colIndex " +
           "AND c.cellType = 'DATA' " +
           "AND c.cellValue IS NOT NULL AND c.cellValue <> '' " +
           "AND YEAR(c.reportTable.dailyReport.reportDate) = :targetYear " +
           "ORDER BY c.reportTable.dailyReport.reportDate DESC")
    List<DailyReportCell> findYearlyValueCandidates(
            @Param("tableCode") String tableCode,
            @Param("rowIndex") int rowIndex,
            @Param("colIndex") int colIndex,
            @Param("targetYear") int targetYear);

    /**
     * ★ 셀 hover 시 "최종 저장자/시각" 표시용 fallback 조회 (2026-08).
     * - 이월(carryOverValue)된 셀은 LAST_EDITOR_ID/LAST_EDITED_AT이 항상 NULL이므로
     *   (전파 제어 플래그와 겸용되기 때문에 의도적으로 세팅하지 않음), 화면 표시만을
     *   위해 "같은 좌표(rowIndex/colIndex)에서 조회 기준일 이전 날짜 중 실제로
     *   사람이 입력한(LAST_EDITOR_ID IS NOT NULL) 가장 최근 셀"을 좌표별로 1건씩
     *   윈도우 함수로 일괄 조회한다.
     * - 표(tableCode) 단위로 1회만 호출 — 셀 개수(최대 ~320개)만큼 N+1 조회하지
     *   않도록 배치 처리한다.
     * - 스키마 변경 없음 (기존 컬럼만 SELECT) — DB 마이그레이션 불필요.
     */
    @Query(value =
            "SELECT row_index, col_index, last_editor_id, last_edited_at " +
            "FROM ( " +
            "  SELECT c.ROW_INDEX AS row_index, c.COL_INDEX AS col_index, " +
            "         c.LAST_EDITOR_ID AS last_editor_id, c.LAST_EDITED_AT AS last_edited_at, " +
            "         ROW_NUMBER() OVER (PARTITION BY c.ROW_INDEX, c.COL_INDEX " +
            "                            ORDER BY r.REPORT_DATE DESC) AS rn " +
            "  FROM daily_report_cell c " +
            "  JOIN daily_report_table t ON c.TABLE_ID = t.TABLE_ID " +
            "  JOIN daily_report r ON t.REPORT_ID = r.REPORT_ID " +
            "  WHERE t.TABLE_CODE = :tableCode " +
            "    AND r.REPORT_DATE <= :reportDate " +
            "    AND c.LAST_EDITOR_ID IS NOT NULL " +
            ") x " +
            "WHERE rn = 1",
            nativeQuery = true)
    List<Object[]> findLastRealEditorByCoordUpToDate(
            @Param("tableCode") String tableCode,
            @Param("reportDate") LocalDate reportDate);
}
