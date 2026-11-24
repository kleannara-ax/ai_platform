package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFuelDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 유동상·소각로·복합·외부 일일 스팀 구매량 Repository
 *
 * <p>Table: steam_fuel_daily_usage
 */
public interface SteamFuelDailyUsageRepository extends JpaRepository<SteamFuelDailyUsage, Long> {

    Optional<SteamFuelDailyUsage> findByUsageDate(LocalDate usageDate);

    List<SteamFuelDailyUsage> findByUsageDateBetweenOrderByUsageDateAsc(LocalDate from, LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fuel_daily_usage WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamFuelDailyUsage> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
