package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedImprovementPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 설비개선 월별 비용 Repository
 *
 * <p>Table: steam_fluidized_improvement_payment
 */
public interface SteamFluidizedImprovementPaymentRepository extends JpaRepository<SteamFluidizedImprovementPayment, Long> {

    @Query("select e from SteamFluidizedImprovementPayment e where e.yearNo = :yearNo and e.recordKey = :recordKey")
    Optional<SteamFluidizedImprovementPayment> findActive(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);

    @Query("select e from SteamFluidizedImprovementPayment e where e.yearNo = :yearNo order by e.yearNo asc, e.recordKey asc")
    List<SteamFluidizedImprovementPayment> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_improvement_payment WHERE YEAR_NO = :yearNo AND RECORD_KEY = :recordKey", nativeQuery = true)
    Optional<SteamFluidizedImprovementPayment> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);
}
