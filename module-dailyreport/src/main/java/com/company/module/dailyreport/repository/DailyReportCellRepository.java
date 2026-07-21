package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
