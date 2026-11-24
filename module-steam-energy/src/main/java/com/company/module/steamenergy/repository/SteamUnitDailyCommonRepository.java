package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamUnitDailyCommon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 원단위 일지(일자별 공통 계측값) Repository
 *
 * <p>Table: steam_unit_daily_common
 */
public interface SteamUnitDailyCommonRepository extends JpaRepository<SteamUnitDailyCommon, Long> {

    Optional<SteamUnitDailyCommon> findByUsageDate(LocalDate usageDate);

    List<SteamUnitDailyCommon> findByUsageDateBetweenOrderByUsageDateAsc(LocalDate from, LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_unit_daily_common WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamUnitDailyCommon> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
