package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamComboDailyDowntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 복합보일러 운휴·디스퍼져 Repository
 *
 * <p>Table: steam_combo_daily_downtime
 */
public interface SteamComboDailyDowntimeRepository extends JpaRepository<SteamComboDailyDowntime, Long> {

    @Query("select e from SteamComboDailyDowntime e where e.usageDate = :usageDate")
    Optional<SteamComboDailyDowntime> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamComboDailyDowntime e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamComboDailyDowntime> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_combo_daily_downtime WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamComboDailyDowntime> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
