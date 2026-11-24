package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamIncineratorContractDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 소각로 도급내역 Repository
 *
 * <p>Table: steam_incinerator_contract_daily
 */
public interface SteamIncineratorContractDailyRepository extends JpaRepository<SteamIncineratorContractDaily, Long> {

    @Query("select e from SteamIncineratorContractDaily e where e.usageDate = :usageDate")
    Optional<SteamIncineratorContractDaily> findActive(@Param("usageDate") LocalDate usageDate);

    @Query("select e from SteamIncineratorContractDaily e where e.usageDate between :from and :to order by e.usageDate asc")
    List<SteamIncineratorContractDaily> findScope(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_incinerator_contract_daily WHERE USAGE_DATE = :usageDate", nativeQuery = true)
    Optional<SteamIncineratorContractDaily> findAnyByKey(@Param("usageDate") LocalDate usageDate);
}
