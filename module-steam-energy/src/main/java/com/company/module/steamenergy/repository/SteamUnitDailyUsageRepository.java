package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamUnitDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 원단위 일지(호기별 일일 실적) Repository
 *
 * <p>Table: steam_unit_daily_usage
 */
public interface SteamUnitDailyUsageRepository extends JpaRepository<SteamUnitDailyUsage, Long> {

    /** 업서트 키: 일자 + 호기 (삭제된 행 제외) */
    Optional<SteamUnitDailyUsage> findByUsageDateAndMachineCode(LocalDate usageDate, String machineCode);

    /** 기간 조회 — 일자·호기 순 */
    List<SteamUnitDailyUsage> findByUsageDateBetweenOrderByUsageDateAscMachineCodeAsc(LocalDate from, LocalDate to);

    /** 기간 + 호기 조회 */
    List<SteamUnitDailyUsage> findByUsageDateBetweenAndMachineCodeOrderByUsageDateAsc(LocalDate from, LocalDate to, String machineCode);

    /**
     * 삭제된 행까지 포함해 키로 찾는다 — 같은 일자·호기를 다시 저장할 때 소프트 삭제 행을 되살리기 위해.
     * (@SQLRestriction 은 JPQL/파생 쿼리에만 걸리므로 native 로 우회한다.)
     */
    @Query(value = "SELECT * FROM steam_unit_daily_usage WHERE USAGE_DATE = :usageDate AND MACHINE_CODE = :machineCode",
            nativeQuery = true)
    Optional<SteamUnitDailyUsage> findAnyByKey(@Param("usageDate") LocalDate usageDate, @Param("machineCode") String machineCode);
}
