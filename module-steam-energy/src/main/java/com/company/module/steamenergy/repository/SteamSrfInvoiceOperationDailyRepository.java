package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamSrfInvoiceOperationDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SRF 인보이스 운영내역 Repository
 *
 * <p>Table: steam_srf_invoice_operation_daily
 */
public interface SteamSrfInvoiceOperationDailyRepository extends JpaRepository<SteamSrfInvoiceOperationDaily, Long> {

    @Query("select e from SteamSrfInvoiceOperationDaily e where e.usageDate = :usageDate")
    Optional<SteamSrfInvoiceOperationDaily> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamSrfInvoiceOperationDaily e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamSrfInvoiceOperationDaily> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_srf_invoice_operation_daily WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamSrfInvoiceOperationDaily> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
