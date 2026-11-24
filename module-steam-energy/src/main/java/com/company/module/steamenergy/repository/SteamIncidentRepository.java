package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 사고·정비 이력 Repository
 *
 * <p>Table: steam_incident
 */
public interface SteamIncidentRepository extends JpaRepository<SteamIncident, Long> {

    @Query("select e from SteamIncident e where e.yearNo = :yearNo and e.recordKey = :recordKey")
    Optional<SteamIncident> findActive(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);

    @Query("select e from SteamIncident e where e.yearNo = :yearNo order by e.yearNo asc, e.recordKey asc")
    List<SteamIncident> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_incident WHERE YEAR_NO = :yearNo AND RECORD_KEY = :recordKey", nativeQuery = true)
    Optional<SteamIncident> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("recordKey") String recordKey);
}
