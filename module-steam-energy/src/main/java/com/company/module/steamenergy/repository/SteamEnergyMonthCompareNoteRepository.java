package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEnergyMonthCompareNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 회계비용 마감비교 비고 Repository
 *
 * <p>Table: steam_energy_month_compare_note
 */
public interface SteamEnergyMonthCompareNoteRepository extends JpaRepository<SteamEnergyMonthCompareNote, Long> {

    Optional<SteamEnergyMonthCompareNote> findByYearNo(Integer yearNo);

    List<SteamEnergyMonthCompareNote> findByYearNoOrderByYearNoAsc(Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_energy_month_compare_note WHERE YEAR_NO = :yearNo", nativeQuery = true)
    Optional<SteamEnergyMonthCompareNote> findAnyByKey(@Param("yearNo") Integer yearNo);
}
