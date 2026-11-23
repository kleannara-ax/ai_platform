package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamIncineratorOperationMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 소각로 계획대비 가동실적 Repository
 *
 * <p>Table: steam_incinerator_operation_monthly
 */
public interface SteamIncineratorOperationMonthlyRepository extends JpaRepository<SteamIncineratorOperationMonthly, Long> {

    @Query("select e from SteamIncineratorOperationMonthly e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamIncineratorOperationMonthly> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamIncineratorOperationMonthly e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamIncineratorOperationMonthly> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_incinerator_operation_monthly WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamIncineratorOperationMonthly> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
