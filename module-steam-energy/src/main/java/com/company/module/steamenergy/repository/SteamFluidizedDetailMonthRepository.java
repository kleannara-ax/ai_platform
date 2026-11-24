package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedDetailMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) Repository
 *
 * <p>Table: steam_fluidized_detail_month
 */
public interface SteamFluidizedDetailMonthRepository extends JpaRepository<SteamFluidizedDetailMonth, Long> {

    @Query("select e from SteamFluidizedDetailMonth e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamFluidizedDetailMonth> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamFluidizedDetailMonth e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamFluidizedDetailMonth> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_detail_month WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamFluidizedDetailMonth> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
