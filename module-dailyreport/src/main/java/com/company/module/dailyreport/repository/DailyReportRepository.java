package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    /** 날짜로 일보 조회 (유니크) */
    Optional<DailyReport> findByReportDate(LocalDate reportDate);

    /** 날짜 존재 여부 확인 */
    boolean existsByReportDate(LocalDate reportDate);

    /**
     * ★ 값 이어받기(carry-over) 전용 — 주어진 날짜 이전(과거)의 가장 최근 일보 1건 조회
     * - 신규 일보/표 생성 시, 이 일보의 DATA 셀 값을 새 표의 초기값으로 채워 넣는다
     */
    Optional<DailyReport> findTopByReportDateLessThanOrderByReportDateDesc(LocalDate reportDate);

    /** 기간별 일보 목록 페이징 조회 */
    @Query("SELECT r FROM DailyReport r " +
           "WHERE (:startDate IS NULL OR r.reportDate >= :startDate) " +
           "AND (:endDate IS NULL OR r.reportDate <= :endDate) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.reportDate DESC")
    Page<DailyReport> findByConditions(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") String status,
            Pageable pageable);

    /** 최근 일보 N건 조회 */
    @Query(value = "SELECT * FROM daily_report ORDER BY REPORT_DATE DESC LIMIT :limit",
           nativeQuery = true)
    java.util.List<DailyReport> findRecentReports(@Param("limit") int limit);

    /**
     * ★ 롤링 헤더 일괄 재계산(refreshRollingHeaders) 전용 — 날짜 범위로 일보 조회.
     * startDate/endDate가 null이면 해당 방향 제한 없음(전체 과거/미래 포함).
     */
    @Query("SELECT r FROM DailyReport r " +
           "WHERE (:startDate IS NULL OR r.reportDate >= :startDate) " +
           "AND (:endDate IS NULL OR r.reportDate <= :endDate) " +
           "ORDER BY r.reportDate ASC")
    java.util.List<DailyReport> findByReportDateRangeOrAll(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
