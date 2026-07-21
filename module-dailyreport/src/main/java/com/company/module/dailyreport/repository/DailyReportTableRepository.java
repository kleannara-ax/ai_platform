package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DailyReportTableRepository extends JpaRepository<DailyReportTable, Long> {

    /** 일보 ID로 모든 표 조회 (정렬 순서) */
    List<DailyReportTable> findByDailyReport_ReportIdOrderBySortOrderAsc(Long reportId);

    /** 일보 ID + 표 코드로 단건 조회 */
    Optional<DailyReportTable> findByDailyReport_ReportIdAndTableCode(Long reportId, String tableCode);

    /** 일보 ID에 속한 표 개수 */
    long countByDailyReport_ReportId(Long reportId);

    /** 일보 ID + 표 코드 존재 여부 */
    boolean existsByDailyReport_ReportIdAndTableCode(Long reportId, String tableCode);
}
