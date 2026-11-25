package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamMovePlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 이동계획 일자별 운휴 Repository
 *
 * <p>Table: steam_move_plan_day
 */
public interface SteamMovePlanDayRepository extends JpaRepository<SteamMovePlanDay, Long> {

    @Query("select e from SteamMovePlanDay e where e.planDate = :planDate")
    Optional<SteamMovePlanDay> findActive(@Param("planDate") LocalDate planDate);

    @Query("select e from SteamMovePlanDay e where e.planDate between :from and :to order by e.planDate asc")
    List<SteamMovePlanDay> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_move_plan_day WHERE PLAN_DATE = :planDate", nativeQuery = true)
    Optional<SteamMovePlanDay> findAnyByKey(@Param("planDate") LocalDate planDate);
}
