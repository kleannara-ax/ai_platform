package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 월별 계획·비용 Repository
 *
 * <p>Table: steam_fluidized_month
 */
public interface SteamFluidizedMonthRepository extends JpaRepository<SteamFluidizedMonth, Long> {

    @Query("select e from SteamFluidizedMonth e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamFluidizedMonth> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamFluidizedMonth e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamFluidizedMonth> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_month WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamFluidizedMonth> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
