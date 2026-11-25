package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamMovePlanMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 이동계획 호기별 생산계획 Repository
 *
 * <p>Table: steam_move_plan_machine
 */
public interface SteamMovePlanMachineRepository extends JpaRepository<SteamMovePlanMachine, Long> {

    @Query("select e from SteamMovePlanMachine e where e.yearNo = :yearNo and e.monthNo = :monthNo and e.machineCode = :machineCode")
    Optional<SteamMovePlanMachine> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("machineCode") String machineCode);

    /** 한 해 (monthNo 를 주면 그 달만) */
    @Query("select e from SteamMovePlanMachine e where e.yearNo = :yearNo and (:monthNo is null or e.monthNo = :monthNo) order by e.yearNo asc, e.monthNo asc, e.machineCode asc")
    List<SteamMovePlanMachine> findScope(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_move_plan_machine WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo AND MACHINE_CODE = :machineCode", nativeQuery = true)
    Optional<SteamMovePlanMachine> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("machineCode") String machineCode);
}
