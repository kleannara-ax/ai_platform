package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamSrfInvoiceBsc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SRF 인보이스 BSC 단가 Repository
 *
 * <p>Table: steam_srf_invoice_bsc
 */
public interface SteamSrfInvoiceBscRepository extends JpaRepository<SteamSrfInvoiceBsc, Long> {

    @Query("select e from SteamSrfInvoiceBsc e where e.yearNo = :yearNo")
    Optional<SteamSrfInvoiceBsc> findActive(@Param("yearNo") Integer yearNo);

    @Query("select e from SteamSrfInvoiceBsc e where e.yearNo = :yearNo order by e.yearNo asc")
    List<SteamSrfInvoiceBsc> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_srf_invoice_bsc WHERE YEAR_NO = :yearNo", nativeQuery = true)
    Optional<SteamSrfInvoiceBsc> findAnyByKey(@Param("yearNo") Integer yearNo);
}
