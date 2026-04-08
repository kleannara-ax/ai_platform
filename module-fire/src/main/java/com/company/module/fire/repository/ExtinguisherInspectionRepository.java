package com.company.module.fire.repository;

import com.company.module.fire.entity.ExtinguisherInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 소화기 점검 이력 Repository
 */
public interface ExtinguisherInspectionRepository extends JpaRepository<ExtinguisherInspection, Long> {

    boolean existsByExtinguisher_ExtinguisherIdAndInspectionDate(Long extinguisherId, LocalDate date);
    boolean existsByExtinguisher_ExtinguisherIdAndInspectionDateAndInspectionIdNot(
            Long extinguisherId, LocalDate date, Long inspectionId);

    List<ExtinguisherInspection> findByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(Long extinguisherId, Pageable pageable);

    @Query("SELECT i FROM ExtinguisherInspection i " +
           "WHERE i.extinguisher.extinguisherId = :extinguisherId " +
           "ORDER BY i.inspectionDate DESC, i.inspectionId DESC")
    List<ExtinguisherInspection> findTop12ByExtinguisherId(
            @Param("extinguisherId") Long extinguisherId, Pageable pageable);

    /**
     * 최근 12건을 초과하는 점검 이력 삭제
     */
    @Modifying
    @Query(value = "DELETE FROM extinguisher_inspection " +
                   "WHERE extinguisher_id = :extinguisherId " +
                   "AND inspection_id NOT IN (" +
                   "  SELECT inspection_id FROM (" +
                   "    SELECT inspection_id FROM extinguisher_inspection " +
                   "    WHERE extinguisher_id = :extinguisherId " +
                   "    ORDER BY inspection_date DESC, inspection_id DESC " +
                   "    LIMIT 12" +
                   "  ) AS t" +
                   ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("extinguisherId") Long extinguisherId);

    Optional<ExtinguisherInspection> findTopByExtinguisher_ExtinguisherIdOrderByInspectionDateDescInspectionIdDesc(Long extinguisherId);
}
