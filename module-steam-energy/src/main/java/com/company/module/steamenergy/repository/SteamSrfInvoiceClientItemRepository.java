package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamSrfInvoiceClientItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SRF 인보이스 Client Invoice Repository
 *
 * <p>Table: steam_srf_invoice_client_item
 */
public interface SteamSrfInvoiceClientItemRepository extends JpaRepository<SteamSrfInvoiceClientItem, Long> {

    @Query("select e from SteamSrfInvoiceClientItem e where e.yearNo = :yearNo and e.monthNo = :monthNo and e.section = :section and e.lineNo = :lineNo")
    Optional<SteamSrfInvoiceClientItem> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("section") String section, @Param("lineNo") Integer lineNo);

    @Query("select e from SteamSrfInvoiceClientItem e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc, e.section asc, e.lineNo asc")
    List<SteamSrfInvoiceClientItem> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_srf_invoice_client_item WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo AND SECTION = :section AND LINE_NO = :lineNo", nativeQuery = true)
    Optional<SteamSrfInvoiceClientItem> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("section") String section, @Param("lineNo") Integer lineNo);
}
