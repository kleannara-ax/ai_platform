package com.company.module.fire.facility;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FacilityEquipmentInspectionRepository extends JpaRepository<FacilityEquipmentInspection, Long> {
    boolean existsByEquipment_EquipmentIdAndInspectionDate(Long equipmentId, LocalDate date);
    Optional<FacilityEquipmentInspection> findByEquipment_EquipmentIdAndInspectionDate(Long equipmentId, LocalDate date);
    boolean existsByEquipment_EquipmentIdAndInspectionDateAndInspectionIdNot(Long equipmentId, LocalDate date, Long inspectionId);
    Optional<FacilityEquipmentInspection> findTopByEquipment_EquipmentIdOrderByInspectionDateDescInspectionIdDesc(Long equipmentId);
    List<FacilityEquipmentInspection> findByEquipment_EquipmentIdOrderByInspectionDateDescInspectionIdDesc(Long equipmentId, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM facility_equipment_inspection " +
            "WHERE EQUIPMENT_ID = :equipmentId " +
            "AND INSPECTION_ID NOT IN (" +
            "  SELECT INSPECTION_ID FROM (" +
            "    SELECT INSPECTION_ID FROM facility_equipment_inspection " +
            "    WHERE EQUIPMENT_ID = :equipmentId " +
            "    ORDER BY INSPECTION_DATE DESC, INSPECTION_ID DESC " +
            "    LIMIT 12" +
            "  ) AS t" +
            ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("equipmentId") Long equipmentId);
}
