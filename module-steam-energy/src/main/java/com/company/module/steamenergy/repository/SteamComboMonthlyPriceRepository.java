package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamComboMonthlyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 복합보일러 월 연료단가 Repository
 *
 * <p>Table: steam_combo_monthly_price
 */
public interface SteamComboMonthlyPriceRepository extends JpaRepository<SteamComboMonthlyPrice, Long> {

    @Query("select e from SteamComboMonthlyPrice e where e.yearNo = :yearNo and e.monthNo = :monthNo")
    Optional<SteamComboMonthlyPrice> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);

    @Query("select e from SteamComboMonthlyPrice e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc")
    List<SteamComboMonthlyPrice> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_combo_monthly_price WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo", nativeQuery = true)
    Optional<SteamComboMonthlyPrice> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo);
}
