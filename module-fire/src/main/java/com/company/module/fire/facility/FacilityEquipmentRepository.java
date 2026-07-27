package com.company.module.fire.facility;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FacilityEquipmentRepository extends JpaRepository<FacilityEquipment, Long> {

    Optional<FacilityEquipment> findBySerialNumber(String serialNumber);
    boolean existsBySerialNumber(String serialNumber);
    Optional<FacilityEquipment> findByQrKey(String qrKey);
    boolean existsByQrKey(String qrKey);

    @Query(value =
            "SELECT e FROM FacilityEquipment e " +
            "JOIN FETCH e.building b " +
            "JOIN FETCH e.floor f " +
            "WHERE e.category = :category " +
            "AND (:buildingIds IS NULL OR b.buildingId IN :buildingIds) " +
            "AND (:floorId IS NULL OR f.floorId = :floorId) " +
            "AND (:keyword IS NULL OR " +
            "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.equipmentType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(COALESCE(e.manufacturer, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(COALESCE(e.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.note) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery =
            "SELECT COUNT(e) FROM FacilityEquipment e " +
            "JOIN e.building b " +
            "JOIN e.floor f " +
            "WHERE e.category = :category " +
            "AND (:buildingIds IS NULL OR b.buildingId IN :buildingIds) " +
            "AND (:floorId IS NULL OR f.floorId = :floorId) " +
            "AND (:keyword IS NULL OR " +
            "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.equipmentType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(COALESCE(e.manufacturer, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(COALESCE(e.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "     LOWER(e.note) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FacilityEquipment> search(
            @Param("category") String category,
            @Param("buildingIds") List<Long> buildingIds,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    List<FacilityEquipment> findByCategory(String category);
}
