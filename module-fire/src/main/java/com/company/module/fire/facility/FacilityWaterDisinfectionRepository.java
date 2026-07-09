package com.company.module.fire.facility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityWaterDisinfectionRepository extends JpaRepository<FacilityWaterDisinfection, Long> {
    List<FacilityWaterDisinfection> findTop5ByEquipment_EquipmentIdOrderByDisinfectionDateDescDisinfectionIdDesc(Long equipmentId);
}
