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
}
