package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyAccountingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 회계비용 계획 Repository
 *
 * <p>Table: steam_energy_accounting_plan
 */
public interface SteamEnergyAccountingPlanRepository extends JpaRepository<SteamEnergyAccountingPlan, Long> {

    Optional<SteamEnergyAccountingPlan> findByYearNoAndMonthNo(Integer yearNo, Integer monthNo);

    List<SteamEnergyAccountingPlan> findByYearNoOrderByMonthNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_accounting_plan WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamEnergyAccountingPlan> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
