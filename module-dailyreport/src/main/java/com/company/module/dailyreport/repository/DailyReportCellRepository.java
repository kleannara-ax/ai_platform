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
}
