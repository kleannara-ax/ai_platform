package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyRatioRef2019;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 연료비 배분비율(2019 참고) Repository
 *
 * <p>Table: steam_energy_ratio_ref2019
 */
public interface SteamEnergyRatioRef2019Repository extends JpaRepository<SteamEnergyRatioRef2019, Long> {

    Optional<SteamEnergyRatioRef2019> findByYearNoAndMonthNo(Integer yearNo, Integer monthNo);

    List<SteamEnergyRatioRef2019> findByYearNoOrderByMonthNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_ratio_ref2019 WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamEnergyRatioRef2019> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
