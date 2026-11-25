package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamMovePlanBoiler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 이동계획 보일러별 기준값 Repository
 *
 * <p>Table: steam_move_plan_boiler
 */
public interface SteamMovePlanBoilerRepository extends JpaRepository<SteamMovePlanBoiler, Long> {

    @Query("select e from SteamMovePlanBoiler e where e.yearNo = :yearNo and e.monthNo = :monthNo and e.boilerCode = :boilerCode")
    Optional<SteamMovePlanBoiler> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("boilerCode") String boilerCode);

    /** 한 해 (monthNo 를 주면 그 달만) */
    @Query("select e from SteamMovePlanBoiler e where e.yearNo = :yearNo and (:monthNo is null or e.monthNo = :monthNo) order by e.yearNo asc, e.monthNo asc, e.boilerCode asc")
    List<SteamMovePlanBoiler> findScope(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_move_plan_boiler WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo AND BOILER_CODE = :boilerCode", nativeQuery = true)
    Optional<SteamMovePlanBoiler> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("boilerCode") String boilerCode);
}
