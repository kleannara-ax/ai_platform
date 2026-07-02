package com.company.module.fire.repository;

import com.company.module.fire.entity.FireSprinklerInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FireSprinklerInspectionRepository extends JpaRepository<FireSprinklerInspection, Long> {

    boolean existsBySprinkler_SprinklerIdAndInspectionDate(Long sprinklerId, LocalDate inspectionDate);

    boolean existsBySprinkler_SprinklerIdAndInspectionDateAndInspectionIdNot(Long sprinklerId, LocalDate inspectionDate, Long inspectionId);

    Optional<FireSprinklerInspection> findTopBySprinkler_SprinklerIdOrderByInspectionDateDescInspectionIdDesc(Long sprinklerId);

    List<FireSprinklerInspection> findBySprinkler_SprinklerIdOrderByInspectionDateDescInspectionIdDesc(Long sprinklerId, Pageable pageable);

    List<FireSprinklerInspection> findBySprinkler_SprinklerIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
            Long sprinklerId, LocalDate fromDate, LocalDate toDate);

    List<FireSprinklerInspection> findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query(value = "DELETE FROM fire_sprinkler_inspection " +
            "WHERE sprinkler_id = :sprinklerId " +
            "AND inspection_id NOT IN (" +
            "  SELECT inspection_id FROM (" +
            "    SELECT inspection_id FROM fire_sprinkler_inspection " +
            "    WHERE sprinkler_id = :sprinklerId " +
            "    ORDER BY inspection_date DESC, inspection_id DESC " +
            "    LIMIT 12" +
            "  ) AS t" +
            ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("sprinklerId") Long sprinklerId);
}
