package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamSrfInvoiceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SRF 인보이스 Adjustment Factor Repository
 *
 * <p>Table: steam_srf_invoice_adjustment
 */
public interface SteamSrfInvoiceAdjustmentRepository extends JpaRepository<SteamSrfInvoiceAdjustment, Long> {

    @Query("select e from SteamSrfInvoiceAdjustment e where e.yearNo = :yearNo and e.section = :section and e.itemCode = :itemCode")
    Optional<SteamSrfInvoiceAdjustment> findActive(@Param("yearNo") Integer yearNo, @Param("section") String section, @Param("itemCode") String itemCode);

    @Query("select e from SteamSrfInvoiceAdjustment e where e.yearNo = :yearNo order by e.yearNo asc, e.section asc, e.itemCode asc")
    List<SteamSrfInvoiceAdjustment> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_srf_invoice_adjustment WHERE YEAR_NO = :yearNo AND SECTION = :section AND ITEM_CODE = :itemCode", nativeQuery = true)
    Optional<SteamSrfInvoiceAdjustment> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("section") String section, @Param("itemCode") String itemCode);
}
