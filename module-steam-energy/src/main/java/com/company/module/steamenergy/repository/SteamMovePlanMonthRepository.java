package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamMovePlanMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 이동계획 월 기준값 Repository
 *
 * <p>Table: steam_move_plan_month
 */
public interface SteamMovePlanMonthRepository extends JpaRepository<SteamMovePlanMonth, Long> {

    @Query("select e from SteamMovePlanMonth e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamMovePlanMonth> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    /** 한 해 (monthNo 를 주면 그 달만) */
    @Query("select e from SteamMovePlanMonth e where e.yearNo = :yearNo and (:monthNo is null or e.monthNo = :monthNo) order by e.yearNo asc, e.monthNo asc")
    List<SteamMovePlanMonth> findScope(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_move_plan_month WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamMovePlanMonth> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
