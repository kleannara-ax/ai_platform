package com.company.module.fire.repository;

import com.company.module.fire.entity.FireSprinkler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FireSprinklerRepository extends JpaRepository<FireSprinkler, Long> {

    @Query(value = """
            SELECT s FROM FireSprinkler s
            JOIN FETCH s.building b
            JOIN FETCH s.floor f
            WHERE s.active = true
            AND (:buildingIds IS NULL OR b.buildingId IN :buildingIds)
            AND (:floorId IS NULL OR f.floorId = :floorId)
            AND (:keyword IS NULL OR
                 LOWER(s.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(s.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(s) FROM FireSprinkler s
            JOIN s.building b
            JOIN s.floor f
            WHERE s.active = true
            AND (:buildingIds IS NULL OR b.buildingId IN :buildingIds)
            AND (:floorId IS NULL OR f.floorId = :floorId)
            AND (:keyword IS NULL OR
                 LOWER(s.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(s.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<FireSprinkler> search(
            @Param("buildingIds") List<Long> buildingIds,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT s.serialNumber FROM FireSprinkler s WHERE s.serialNumber LIKE 'SPK-%'")
    List<String> findAllSerialNumbers();

    Optional<FireSprinkler> findByQrKey(String qrKey);

    boolean existsByQrKey(String qrKey);

    List<FireSprinkler> findByActiveTrue();

    List<FireSprinkler> findByBuilding_BuildingIdAndActiveTrue(Long buildingId);

    List<FireSprinkler> findByFloor_FloorIdAndActiveTrue(Long floorId);

    List<FireSprinkler> findByBuilding_BuildingIdAndFloor_FloorIdAndActiveTrue(Long buildingId, Long floorId);
}
