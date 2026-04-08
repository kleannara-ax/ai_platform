package com.company.module.fire.repository;

import com.company.module.fire.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 건물 Repository
 * <p>
 * NOTE: Building 엔티티의 필드명이 'active'(DB 컬럼 is_active)이므로
 *       Spring Data JPA 메서드 명명 규칙도 'ActiveTrue' 대신 'Active'를 사용한다.
 */
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /**
     * 활성 건물 목록 조회 (이름순)
     * Building.active = true 조건 (DB 컬럼: is_active)
     */
    List<Building> findByActiveTrueOrderByBuildingName();

    /** QR 페이지: 전체 건물 목록 (이름순) */
    List<Building> findAllByOrderByBuildingNameAsc();
}
