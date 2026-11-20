package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamComboRecoveryLoss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 복합보일러 스팀 회수 감소 Repository
 *
 * <p>Table: steam_combo_recovery_loss
 */
public interface SteamComboRecoveryLossRepository extends JpaRepository<SteamComboRecoveryLoss, Long> {

    @Query("select e from SteamComboRecoveryLoss e where e.yearNo = :yearNo and e.monthNo = :monthNo and e.lineNo = :lineNo")
    Optional<SteamComboRecoveryLoss> findActive(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("lineNo") Integer lineNo);

    @Query("select e from SteamComboRecoveryLoss e where e.yearNo = :yearNo order by e.yearNo asc, e.monthNo asc, e.lineNo asc")
    List<SteamComboRecoveryLoss> findScope(@Param("yearNo") Integer yearNo);

    /** 삭제된 행까지 포함해 키로 찾는다 — 되살리기용 (native 로 @SQLRestriction 우회). */
    @Query(value = "SELECT * FROM steam_combo_recovery_loss WHERE YEAR_NO = :yearNo AND MONTH_NO = :monthNo AND LINE_NO = :lineNo", nativeQuery = true)
    Optional<SteamComboRecoveryLoss> findAnyByKey(@Param("yearNo") Integer yearNo, @Param("monthNo") Integer monthNo, @Param("lineNo") Integer lineNo);
}
