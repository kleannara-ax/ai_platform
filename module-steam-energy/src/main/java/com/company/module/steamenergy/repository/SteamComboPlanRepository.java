package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamComboPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 복합보일러 연간 계획 Repository
 *
 * <p>Table: steam_combo_plan
 */
public interface SteamComboPlanRepository extends JpaRepository<SteamComboPlan, Long> {

    @Query("select e from SteamComboPlan e where e.yearNo = :yearNo and e.targetYm = :targetYm")
    Optional<SteamComboPlan> findActive(@Param("yearNo") Integer yearNo, @Param("targetYm") String targetYm);

    @Query("select e from SteamComboPlan e where e.yearNo = :yearNo order by e.yearNo asc, e.targetYm asc")
    List<SteamComboPlan> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_combo_plan WHERE YEAR_NO = :yearNo AND TARGET_YM = :targetYm", nativeQuery = true)
    Optional<SteamComboPlan> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("targetYm") String targetYm);
}
