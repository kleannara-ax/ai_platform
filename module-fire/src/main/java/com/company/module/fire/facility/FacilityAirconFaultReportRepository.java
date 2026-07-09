package com.company.module.fire.facility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityAirconFaultReportRepository extends JpaRepository<FacilityAirconFaultReport, Long> {
    List<FacilityAirconFaultReport> findTop5ByEquipment_EquipmentIdOrderByCreatedAtDescReportIdDesc(Long equipmentId);
}
