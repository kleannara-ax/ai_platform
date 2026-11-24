package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamSrfInvoiceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SRF 인보이스 Summary Repository
 *
 * <p>Table: steam_srf_invoice_summary
 */
public interface SteamSrfInvoiceSummaryRepository extends JpaRepository<SteamSrfInvoiceSummary, Long> {

    @Query("select e from SteamSrfInvoiceSummary e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamSrfInvoiceSummary> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamSrfInvoiceSummary e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamSrfInvoiceSummary> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_srf_invoice_summary WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamSrfInvoiceSummary> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
