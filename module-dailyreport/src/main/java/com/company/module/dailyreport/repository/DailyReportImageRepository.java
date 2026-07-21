package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyReportImageRepository extends JpaRepository<DailyReportImage, Long> {

    /** 일보 ID로 이미지 목록 조회 (정렬 순서) */
    List<DailyReportImage> findByDailyReport_ReportIdOrderBySortOrderAsc(Long reportId);

    /** 일보 ID + 표 코드로 이미지 조회 */
    List<DailyReportImage> findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(
            Long reportId, String tableCode);

    /** 일보에 속한 이미지 개수 */
    long countByDailyReport_ReportId(Long reportId);
}
