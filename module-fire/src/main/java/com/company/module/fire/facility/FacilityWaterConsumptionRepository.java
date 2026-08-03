package com.company.module.fire.facility;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FacilityWaterConsumptionRepository extends JpaRepository<FacilityWaterConsumption, Long> {

    List<FacilityWaterConsumption> findTop10ByEquipment_EquipmentIdOrderByConsumptionDateDescConsumptionIdDesc(Long equipmentId);

    List<FacilityWaterConsumption> findByEquipment_EquipmentIdAndConsumptionDateBetweenOrderByConsumptionDateDescConsumptionIdDesc(
            Long equipmentId, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(c.bottleCount), 0) from FacilityWaterConsumption c " +
            "where c.equipment.equipmentId = :equipmentId and c.consumptionDate between :from and :to")
    Integer sumBottleCountByEquipmentAndDateBetween(@Param("equipmentId") Long equipmentId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);
}
