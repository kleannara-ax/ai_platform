package com.company.module.epr.repository;

import com.company.module.epr.entity.EprAnnualSales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 기본 조회는 삭제되지 않은 행만 본다 (엔티티의 @SQLRestriction). */
public interface EprAnnualSalesRepository extends JpaRepository<EprAnnualSales, Long> {

    Optional<EprAnnualSales> findByReportYear(Integer reportYear);

    boolean existsByReportYear(Integer reportYear);

    List<EprAnnualSales> findAllByReportYearBetweenOrderByReportYearAsc(Integer fromYear, Integer toYear);

    List<EprAnnualSales> findAllByOrderByReportYearAsc();

    /** 삭제된 행까지 포함해 찾는다 — 같은 연도를 다시 저장할 때 UNIQUE 충돌 없이 되살리기 위해. */
    @Query(value = "SELECT * FROM epr_annual_sales WHERE REPORT_YEAR = :reportYear", nativeQuery = true)
    Optional<EprAnnualSales> findAnyByReportYear(@Param("reportYear") Integer reportYear);
}
