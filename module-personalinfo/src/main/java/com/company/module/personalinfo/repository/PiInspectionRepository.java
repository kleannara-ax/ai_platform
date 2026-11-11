package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiInspectionRepository extends JpaRepository<PiInspection, Long> {

    List<PiInspection> findByContractIdAndDeletedYnOrderByInspectionDateDescInspectionIdDesc(Long contractId, String deletedYn);

    Optional<PiInspection> findFirstByContractIdAndDeletedYnOrderByInspectionDateDescInspectionIdDesc(Long contractId, String deletedYn);

    Optional<PiInspection> findByInspectionIdAndDeletedYn(Long inspectionId, String deletedYn);

    List<PiInspection> findByInspectionYearAndDeletedYn(Integer inspectionYear, String deletedYn);

    List<PiInspection> findByInspectionTypeAndDeletedYn(InspectionType inspectionType, String deletedYn);

    List<PiInspection> findByContractIdInAndInspectionTypeAndDeletedYn(java.util.Collection<Long> contractIds,
                                                                      InspectionType inspectionType, String deletedYn);
}
