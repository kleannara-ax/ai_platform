package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamEquipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 스팀 설비 마스터 Repository
 *
 * <p>Table: steam_equipment
 */
public interface SteamEquipmentRepository extends JpaRepository<SteamEquipment, Long> {

    Optional<SteamEquipment> findByEquipmentCode(String equipmentCode);

    boolean existsByEquipmentCode(String equipmentCode);

    Page<SteamEquipment> findAllByOrderBySortOrderAscEquipmentIdAsc(Pageable pageable);

    Page<SteamEquipment> findByCategoryOrderBySortOrderAscEquipmentIdAsc(String category, Pageable pageable);

    @Query("SELECT e FROM SteamEquipment e " +
           "WHERE (e.name LIKE %:q% OR e.equipmentCode LIKE %:q%) " +
           "ORDER BY e.sortOrder ASC, e.equipmentId ASC")
    Page<SteamEquipment> search(@Param("q") String q, Pageable pageable);

    @Query("SELECT e FROM SteamEquipment e " +
           "WHERE e.category = :category AND (e.name LIKE %:q% OR e.equipmentCode LIKE %:q%) " +
           "ORDER BY e.sortOrder ASC, e.equipmentId ASC")
    Page<SteamEquipment> searchByCategory(@Param("q") String q,
                                          @Param("category") String category,
                                          Pageable pageable);
}
