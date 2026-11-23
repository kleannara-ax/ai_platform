package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyFuelPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 연료비 사업계획 Repository
 *
 * <p>Table: steam_energy_fuel_plan
 */
public interface SteamEnergyFuelPlanRepository extends JpaRepository<SteamEnergyFuelPlan, Long> {

    Optional<SteamEnergyFuelPlan> findByYearNoAndMonthNo(Integer yearNo, Integer monthNo);

    List<SteamEnergyFuelPlan> findByYearNoOrderByMonthNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_fuel_plan WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamEnergyFuelPlan> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
