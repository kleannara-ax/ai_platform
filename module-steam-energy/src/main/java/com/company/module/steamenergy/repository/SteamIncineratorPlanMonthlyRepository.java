package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamIncineratorPlanMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 소각로 사업계획대비 Repository
 *
 * <p>Table: steam_incinerator_plan_monthly
 */
public interface SteamIncineratorPlanMonthlyRepository extends JpaRepository<SteamIncineratorPlanMonthly, Long> {

    @Query("select e from SteamIncineratorPlanMonthly e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamIncineratorPlanMonthly> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamIncineratorPlanMonthly e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamIncineratorPlanMonthly> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_incinerator_plan_monthly WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamIncineratorPlanMonthly> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
