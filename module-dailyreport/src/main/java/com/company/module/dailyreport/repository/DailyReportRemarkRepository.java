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
     *
     * ★★ 2026-09: TBL_SPECIAL_NOTE 외에 사고 통계 특이사항표(TBL_SAFETY_AMOUNT_NOTE,
     * TBL_SAFETY_TREND_NOTE)가 카테고리 코드를 재사용(PAPER/TISSUE/PAD)하게 되면서,
     * tableCode 없이 category만으로 조회하면 서로 다른 표의 행이 뒤섞일 수 있다.
     * 신규 코드는 반드시 아래 tableCode 포함 버전을 사용할 것 — 이 메서드는
     * TBL_SPECIAL_NOTE만 사용하던 기존 호출부 호환을 위해 남겨두되 더 이상
     * 새로 호출하지 않는다.
     */
    Optional<DailyReportRemark> findByDailyReport_ReportIdAndCategory(Long reportId, String category);

    /**
     * ★★ 2026-09 신규 — tableCode까지 포함해 스코프한 단건 조회. 사고 통계
     * 특이사항표처럼 카테고리 코드가 다른 표와 겹치는 경우 반드시 이 메서드를
     * 사용해야 값이 섞이지 않는다.
     */
    Optional<DailyReportRemark> findByDailyReport_ReportIdAndTableCodeAndCategory(
            Long reportId, String tableCode, String category);
}
