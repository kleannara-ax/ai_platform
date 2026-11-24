package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyRatioSteamUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 스팀사용량 원단위(기타 포함) Repository
 *
 * <p>Table: steam_energy_ratio_steam_unit
 */
public interface SteamEnergyRatioSteamUnitRepository extends JpaRepository<SteamEnergyRatioSteamUnit, Long> {

    Optional<SteamEnergyRatioSteamUnit> findByYearNoAndMonthNo(Integer yearNo, Integer monthNo);

    List<SteamEnergyRatioSteamUnit> findByYearNoOrderByMonthNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_ratio_steam_unit WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamEnergyRatioSteamUnit> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
