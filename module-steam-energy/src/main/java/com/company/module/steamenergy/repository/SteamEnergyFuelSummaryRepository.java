package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyFuelSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 연료비 요약 Repository
 *
 * <p>Table: steam_energy_fuel_summary
 */
public interface SteamEnergyFuelSummaryRepository extends JpaRepository<SteamEnergyFuelSummary, Long> {

    Optional<SteamEnergyFuelSummary> findByYearNoAndMonthNo(Integer yearNo, Integer monthNo);

    List<SteamEnergyFuelSummary> findByYearNoOrderByMonthNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_fuel_summary WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamEnergyFuelSummary> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
