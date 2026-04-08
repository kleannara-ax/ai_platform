package com.company.module.fire.repository;

import com.company.module.fire.entity.FirePump;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FirePumpRepository extends JpaRepository<FirePump, Long> {

    @Query(value = """
            SELECT p FROM FirePump p
            LEFT JOIN FETCH p.floor f
            WHERE p.active = true
            AND (:keyword IS NULL OR
                 LOWER(p.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(p.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(p.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(p) FROM FirePump p
            LEFT JOIN p.floor f
            WHERE p.active = true
            AND (:keyword IS NULL OR
                 LOWER(p.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(p.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(p.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<FirePump> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p.serialNumber FROM FirePump p WHERE p.serialNumber LIKE 'PMP-%'")
    List<String> findAllSerialNumbers();

    Optional<FirePump> findByQrKey(String qrKey);
}
