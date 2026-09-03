package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualStepRepository extends JpaRepository<SafetyManualStep, Long> {

    @Query("SELECT s FROM SafetyManualStep s WHERE s.stepId = :id AND s.deletedYn = 'N'")
    Optional<SafetyManualStep> findActiveById(@Param("id") Long id);

    /** 매뉴얼 상세 화면: 단계 목록 (엑셀의 행 순서 그대로) */
    @Query("SELECT s FROM SafetyManualStep s WHERE s.manual.manualId = :manualId AND s.deletedYn = 'N' ORDER BY s.sortOrder ASC, s.stepId ASC")
    List<SafetyManualStep> findByManualIdOrderBySortOrder(@Param("manualId") Long manualId);

    long countByManual_ManualIdAndDeletedYn(Long manualId, String deletedYn);

    /** 내용 검색 결과에서 "어디가 걸렸는지" 보여주기 위해, 키워드가 들어간 단계만 모아 온다. */
    @Query("""
            SELECT s FROM SafetyManualStep s
            WHERE s.deletedYn = 'N' AND s.manual.manualId IN :manualIds
              AND (LOWER(s.description)     LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.hazard)          LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.safetyEquipment) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(s.remark)          LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.manual.manualId ASC, s.sortOrder ASC, s.stepId ASC
            """)
    List<SafetyManualStep> findMatchingSteps(@Param("manualIds") List<Long> manualIds,
                                              @Param("keyword") String keyword);
}
