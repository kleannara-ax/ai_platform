package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedSrfInboundDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 유동상 SRF 입고내역 Repository
 *
 * <p>Table: steam_fluidized_srf_inbound_daily
 */
public interface SteamFluidizedSrfInboundDailyRepository extends JpaRepository<SteamFluidizedSrfInboundDaily, Long> {

    @Query("select e from SteamFluidizedSrfInboundDaily e where e.usageDate = :usageDate")
    Optional<SteamFluidizedSrfInboundDaily> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamFluidizedSrfInboundDaily e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamFluidizedSrfInboundDaily> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_srf_inbound_daily WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamFluidizedSrfInboundDaily> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
