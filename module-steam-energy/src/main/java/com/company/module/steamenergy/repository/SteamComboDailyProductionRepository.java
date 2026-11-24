package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamComboDailyProduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 복합보일러 스팀생산실적 Repository
 *
 * <p>Table: steam_combo_daily_production
 */
public interface SteamComboDailyProductionRepository extends JpaRepository<SteamComboDailyProduction, Long> {

    @Query("select e from SteamComboDailyProduction e where e.usageDate = :usageDate")
    Optional<SteamComboDailyProduction> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamComboDailyProduction e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamComboDailyProduction> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_combo_daily_production WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamComboDailyProduction> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
