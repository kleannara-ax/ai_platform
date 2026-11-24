package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamDailyUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 일일 스팀 사용 실적 Repository
 *
 * <p>Table: steam_daily_usage
 */
public interface SteamDailyUsageRepository extends JpaRepository<SteamDailyUsage, Long> {

    /** 업서트 키: 일자 + 설비 */
    Optional<SteamDailyUsage> findByUsageDateAndEquipmentId(LocalDate usageDate, Long equipmentId);

    Page<SteamDailyUsage> findAllByOrderByUsageDateDescEquipmentIdAsc(Pageable pageable);

    @Query("SELECT u FROM SteamDailyUsage u " +
           "WHERE u.usageDate BETWEEN :from AND :to " +
           "ORDER BY u.usageDate DESC, u.equipmentId ASC")
    Page<SteamDailyUsage> findByDateRange(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          Pageable pageable);

    @Query("SELECT u FROM SteamDailyUsage u " +
           "WHERE u.equipmentId = :equipmentId AND u.usageDate BETWEEN :from AND :to " +
           "ORDER BY u.usageDate DESC")
    Page<SteamDailyUsage> findByEquipmentAndDateRange(@Param("equipmentId") Long equipmentId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to,
                                                      Pageable pageable);
}
