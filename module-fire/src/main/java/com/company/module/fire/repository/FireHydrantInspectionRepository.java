package com.company.module.fire.repository;

import com.company.module.fire.entity.FireHydrantInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 소화전 점검 이력 Repository
 */
public interface FireHydrantInspectionRepository extends JpaRepository<FireHydrantInspection, Long> {

    boolean existsByHydrant_HydrantIdAndInspectionDate(Long hydrantId, LocalDate date);
    boolean existsByHydrant_HydrantIdAndInspectionDateAndInspectionIdNot(Long hydrantId, LocalDate date, Long inspectionId);

    List<FireHydrantInspection> findByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(Long hydrantId, Pageable pageable);

    Optional<FireHydrantInspection> findTopByHydrant_HydrantIdOrderByInspectionDateDescInspectionIdDesc(Long hydrantId);

    /**
     * 최근 12건 초과 점검 이력 삭제
     */
    @Modifying
    @Query(value = "DELETE FROM fire_hydrant_inspection " +
                   "WHERE hydrant_id = :hydrantId " +
                   "AND inspection_id NOT IN (" +
                   "  SELECT inspection_id FROM (" +
                   "    SELECT inspection_id FROM fire_hydrant_inspection " +
                   "    WHERE hydrant_id = :hydrantId " +
                   "    ORDER BY inspection_date DESC, inspection_id DESC " +
                   "    LIMIT 12" +
                   "  ) AS t" +
                   ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("hydrantId") Long hydrantId);
}
