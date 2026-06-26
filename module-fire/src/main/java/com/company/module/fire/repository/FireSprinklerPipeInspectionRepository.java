package com.company.module.fire.repository;

import com.company.module.fire.entity.FireSprinklerPipeInspection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FireSprinklerPipeInspectionRepository extends JpaRepository<FireSprinklerPipeInspection, Long> {

    boolean existsBySprinklerPipe_SprinklerPipeIdAndInspectionDate(Long sprinklerPipeId, LocalDate inspectionDate);
    boolean existsBySprinklerPipe_SprinklerPipeIdAndInspectionDateAndInspectionIdNot(Long sprinklerPipeId, LocalDate inspectionDate, Long inspectionId);

    Optional<FireSprinklerPipeInspection> findTopBySprinklerPipe_SprinklerPipeIdOrderByInspectionDateDescInspectionIdDesc(Long sprinklerPipeId);

    List<FireSprinklerPipeInspection> findBySprinklerPipe_SprinklerPipeIdOrderByInspectionDateDescInspectionIdDesc(Long sprinklerPipeId, Pageable pageable);

    List<FireSprinklerPipeInspection> findBySprinklerPipe_SprinklerPipeIdAndImagePathIsNotNull(Long sprinklerPipeId);

    List<FireSprinklerPipeInspection> findBySprinklerPipe_SprinklerPipeIdAndInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(
            Long sprinklerPipeId, LocalDate fromDate, LocalDate toDate);

    List<FireSprinklerPipeInspection> findByInspectionDateBetweenOrderByInspectionDateDescInspectionIdDesc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query(value = "DELETE FROM fire_sprinkler_pipe_inspection " +
            "WHERE sprinkler_pipe_id = :sprinklerPipeId " +
            "AND inspection_id NOT IN (" +
            "  SELECT inspection_id FROM (" +
            "    SELECT inspection_id FROM fire_sprinkler_pipe_inspection " +
            "    WHERE sprinkler_pipe_id = :sprinklerPipeId " +
            "    ORDER BY inspection_date DESC, inspection_id DESC " +
            "    LIMIT 12" +
            "  ) AS t" +
            ")", nativeQuery = true)
    void trimInspectionsKeepLatest12(@Param("sprinklerPipeId") Long sprinklerPipeId);
}
