package com.company.module.fire.repository;

import com.company.module.fire.entity.FireReceiverInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FireReceiverInspectionRepository extends JpaRepository<FireReceiverInspection, Long> {

    boolean existsByReceiver_ReceiverIdAndInspectionDate(Long receiverId, LocalDate inspectionDate);
    boolean existsByReceiver_ReceiverIdAndInspectionDateAndInspectionIdNot(Long receiverId, LocalDate inspectionDate, Long inspectionId);

    Optional<FireReceiverInspection> findTopByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(Long receiverId);

    List<FireReceiverInspection> findByReceiver_ReceiverIdOrderByInspectionDateDescInspectionIdDesc(Long receiverId, Pageable pageable);

    List<FireReceiverInspection> findByReceiver_ReceiverIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
            Long receiverId, LocalDate fromDate, LocalDate toDate);

    List<FireReceiverInspection> findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query(value = "DELETE FROM fire_receiver_inspection " +
            "WHERE receiver_id = :receiverId " +
            "AND inspection_id NOT IN (" +
            "  SELECT inspection_id FROM (" +
            "    SELECT inspection_id FROM fire_receiver_inspection " +
            "    WHERE receiver_id = :receiverId " +
            "    ORDER BY inspection_date DESC, inspection_id DESC " +
            "    LIMIT 12" +
            "  ) AS t" +
            ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("receiverId") Long receiverId);
}
