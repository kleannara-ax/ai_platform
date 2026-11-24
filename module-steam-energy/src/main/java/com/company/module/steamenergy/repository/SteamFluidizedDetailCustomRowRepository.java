package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamFluidizedDetailCustomRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유동상 세부 운영내역 (추가 행) Repository
 *
 * <p>Table: steam_fluidized_detail_custom_row
 */
public interface SteamFluidizedDetailCustomRowRepository extends JpaRepository<SteamFluidizedDetailCustomRow, Long> {

    @Query("select e from SteamFluidizedDetailCustomRow e where e.yearNo = :yearNo and e.monthNo = :monthNo and e.section = :section and e.rowKey = :rowKey")
    Optional<SteamFluidizedDetailCustomRow> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("section") String section, @Param("rowKey") String rowKey);

    @Query("select e from SteamFluidizedDetailCustomRow e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc, e.section asc, e.rowKey asc")
    List<SteamFluidizedDetailCustomRow> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_fluidized_detail_custom_row WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo AND SECTION = :section AND ROW_KEY = :rowKey", nativeQuery = true)
    Optional<SteamFluidizedDetailCustomRow> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("section") String section, @Param("rowKey") String rowKey);
}
