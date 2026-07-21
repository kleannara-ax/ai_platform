package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportRemark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyReportRemarkRepository extends JpaRepository<DailyReportRemark, Long> {

    /** 일보 ID로 특이사항 목록 조회 (정렬 순서) */
    List<DailyReportRemark> findByDailyReport_ReportIdOrderBySortOrderAsc(Long reportId);

    /** 일보 ID + 표 코드로 특이사항 조회 */
    List<DailyReportRemark> findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(
            Long reportId, String tableCode);

    /** 일보 ID + 카테고리로 특이사항 조회 */
    List<DailyReportRemark> findByDailyReport_ReportIdAndCategoryOrderBySortOrderAsc(
            Long reportId, String category);
}
