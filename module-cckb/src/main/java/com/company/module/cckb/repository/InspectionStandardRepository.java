package com.company.module.cckb.repository;

import com.company.module.cckb.entity.InspectionStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspectionStandardRepository extends JpaRepository<InspectionStandard, Long> {

    /** 현재 활성화된(사용 중인) 기준값 1건 조회 — 화면 진입/판정 시마다 사용 */
    Optional<InspectionStandard> findFirstByIsActiveAndDeletedYnOrderByStandardIdDesc(String isActive, String deletedYn);
}
