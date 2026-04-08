package com.company.module.fire.repository;

import com.company.module.fire.entity.FireReceiver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FireReceiverRepository extends JpaRepository<FireReceiver, Long> {

    @Query(value = """
            SELECT r FROM FireReceiver r
            LEFT JOIN FETCH r.floor f
            WHERE r.active = true
            AND (:keyword IS NULL OR
                 LOWER(r.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(r.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(r.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(r) FROM FireReceiver r
            LEFT JOIN r.floor f
            WHERE r.active = true
            AND (:keyword IS NULL OR
                 LOWER(r.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(r.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                 LOWER(COALESCE(r.locationDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<FireReceiver> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT r.serialNumber FROM FireReceiver r WHERE r.serialNumber LIKE 'RCV-%'")
    List<String> findAllSerialNumbers();

    Optional<FireReceiver> findByQrKey(String qrKey);
}
