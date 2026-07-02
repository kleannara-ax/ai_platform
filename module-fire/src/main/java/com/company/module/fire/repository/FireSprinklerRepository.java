package com.company.module.fire.repository;

import com.company.module.fire.entity.FireSprinkler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FireSprinklerRepository extends JpaRepository<FireSprinkler, Long> {

    @Query(value = """
            SELECT s FROM FireSprinkler s
            JOIN FETCH s.building b
            JOIN FETCH s.floor f
            WHERE s.active = true
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
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
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            AND (:floorId IS NULL OR f.floorId = :floorId)
            AND (:keyword IS NULL OR
                 LOWER(s.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(s.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<FireSprinkler> search(
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT s.serialNumber FROM FireSprinkler s WHERE s.serialNumber LIKE 'SPK-%'")
    List<String> findAllSerialNumbers();
}
