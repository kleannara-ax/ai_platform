package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiInspectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PiInspectionItemRepository extends JpaRepository<PiInspectionItem, Long> {

    List<PiInspectionItem> findByInspectionIdAndDeletedYnOrderBySortOrderAsc(Long inspectionId, String deletedYn);
}
