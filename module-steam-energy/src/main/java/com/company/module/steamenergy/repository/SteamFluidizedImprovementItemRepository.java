package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedImprovementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 설비개선 항목 Repository
 *
 * <p>Table: steam_fluidized_improvement_item
 */
public interface SteamFluidizedImprovementItemRepository extends JpaRepository<SteamFluidizedImprovementItem, Long> {

    @Query("select e from SteamFluidizedImprovementItem e where e.yearNo = :yearNo and e.recordKey = :recordKey")
    Optional<SteamFluidizedImprovementItem> findActive(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);

    @Query("select e from SteamFluidizedImprovementItem e where e.yearNo = :yearNo order by e.yearNo asc, e.recordKey asc")
    List<SteamFluidizedImprovementItem> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_improvement_item WHERE YEAR_NO = :yearNo AND RECORD_KEY = :recordKey", nativeQuery = true)
    Optional<SteamFluidizedImprovementItem> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);
}
