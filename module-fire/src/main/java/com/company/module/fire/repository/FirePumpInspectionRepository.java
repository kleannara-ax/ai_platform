package com.company.module.fire.repository;

import com.company.module.fire.entity.FirePumpInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FirePumpInspectionRepository extends JpaRepository<FirePumpInspection, Long> {

    boolean existsByPump_PumpIdAndInspectionDate(Long pumpId, LocalDate inspectionDate);
    boolean existsByPump_PumpIdAndInspectionDateAndInspectionIdNot(Long pumpId, LocalDate inspectionDate, Long inspectionId);

    Optional<FirePumpInspection> findTopByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(Long pumpId);

    List<FirePumpInspection> findByPump_PumpIdOrderByInspectionDateDescInspectionIdDesc(Long pumpId, Pageable pageable);

    List<FirePumpInspection> findByPump_PumpIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
            Long pumpId, LocalDate fromDate, LocalDate toDate);

    List<FirePumpInspection> findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query(value = "DELETE FROM fire_pump_inspection " +
            "WHERE pump_id = :pumpId " +
            "AND inspection_id NOT IN (" +
            "  SELECT inspection_id FROM (" +
            "    SELECT inspection_id FROM fire_pump_inspection " +
            "    WHERE pump_id = :pumpId " +
            "    ORDER BY inspection_date DESC, inspection_id DESC " +
            "    LIMIT 12" +
            "  ) AS t" +
            ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("pumpId") Long pumpId);
}
