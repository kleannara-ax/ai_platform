package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualStepPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualStepPhotoRepository extends JpaRepository<SafetyManualStepPhoto, Long> {

    @Query("SELECT p FROM SafetyManualStepPhoto p WHERE p.photoId = :id AND p.deletedYn = 'N'")
    Optional<SafetyManualStepPhoto> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM SafetyManualStepPhoto p WHERE p.step.stepId = :stepId AND p.deletedYn = 'N' ORDER BY p.sortOrder ASC, p.photoId ASC")
    List<SafetyManualStepPhoto> findByStepIdOrderBySortOrder(@Param("stepId") Long stepId);

    /** 매뉴얼 전체 사진 (단계별로 묶어 상세화면에서 한번에 로드할 때) */
    @Query("""
            SELECT p FROM SafetyManualStepPhoto p
            WHERE p.step.manual.manualId = :manualId AND p.deletedYn = 'N'
            ORDER BY p.step.sortOrder ASC, p.sortOrder ASC
            """)
    List<SafetyManualStepPhoto> findByManualId(@Param("manualId") Long manualId);
}
