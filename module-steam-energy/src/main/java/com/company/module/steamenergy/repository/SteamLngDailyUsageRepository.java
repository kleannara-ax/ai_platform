package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamLngDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일별 LNG 연료 사용량 Repository
 *
 * <p>Table: steam_lng_daily_usage
 */
public interface SteamLngDailyUsageRepository extends JpaRepository<SteamLngDailyUsage, Long> {

    Optional<SteamLngDailyUsage> findByUsageDate(LocalDate usageDate);

    List<SteamLngDailyUsage> findByUsageDateBetweenOrderByUsageDateAsc(LocalDate from, LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_lng_daily_usage WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamLngDailyUsage> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
