package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamWasteIncinDailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 폐합성소각로 운전일지 Repository
 *
 * <p>Table: steam_waste_incin_daily_log
 */
public interface SteamWasteIncinDailyLogRepository extends JpaRepository<SteamWasteIncinDailyLog, Long> {

    @Query("select e from SteamWasteIncinDailyLog e where e.usageDate = :usageDate")
    Optional<SteamWasteIncinDailyLog> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamWasteIncinDailyLog e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamWasteIncinDailyLog> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_waste_incin_daily_log WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamWasteIncinDailyLog> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
