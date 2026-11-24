package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedDetailDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 유동상 세부 운영내역 (일별) Repository
 *
 * <p>Table: steam_fluidized_detail_daily
 */
public interface SteamFluidizedDetailDailyRepository extends JpaRepository<SteamFluidizedDetailDaily, Long> {

    @Query("select e from SteamFluidizedDetailDaily e where e.usageDate = :usageDate")
    Optional<SteamFluidizedDetailDaily> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamFluidizedDetailDaily e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamFluidizedDetailDaily> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_detail_daily WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamFluidizedDetailDaily> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
