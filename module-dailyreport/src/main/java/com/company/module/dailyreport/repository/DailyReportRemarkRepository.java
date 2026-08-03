package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReportRemark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyReportRemarkRepository extends JpaRepository<DailyReportRemark, Long> {

    /** 일보 ID로 특이사항 목록 조회 (정렬 순서) */
    List<DailyReportRemark> findByDailyReport_ReportIdOrderBySortOrderAsc(Long reportId);

    /** 일보 ID + 표 코드로 특이사항 조회 */
    List<DailyReportRemark> findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(
            Long reportId, String tableCode);

    /** 일보 ID + 카테고리로 특이사항 조회 */
    List<DailyReportRemark> findByDailyReport_ReportIdAndCategoryOrderBySortOrderAsc(
            Long reportId, String category);

    /**
     * ★ 값 전파(forward propagation, 2026-08 추가) 전용 — 특정 일보의 특정
     * 사업부(category) 행을 단건 조회한다. 미래 일보들을 날짜순으로 순회하며
     * 해당 사업부 행이 이미 존재하는지, 사람이 손댄 적 있는지(CREATED_BY)를
     * 확인하기 위해 사용한다.
     */
    Optional<DailyReportRemark> findByDailyReport_ReportIdAndCategory(Long reportId, String category);
}
