package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedSrfRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 SRF 처리단가 Repository
 *
 * <p>Table: steam_fluidized_srf_rate
 */
public interface SteamFluidizedSrfRateRepository extends JpaRepository<SteamFluidizedSrfRate, Long> {

    @Query("select e from SteamFluidizedSrfRate e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamFluidizedSrfRate> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamFluidizedSrfRate e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamFluidizedSrfRate> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_srf_rate WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamFluidizedSrfRate> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
