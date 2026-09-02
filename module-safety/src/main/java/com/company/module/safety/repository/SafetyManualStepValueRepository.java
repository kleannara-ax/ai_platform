package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualStepValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualStepValueRepository extends JpaRepository<SafetyManualStepValue, Long> {

    /** 한 매뉴얼의 모든 셀 값 (행 x 열) */
    @Query("""
            SELECT v FROM SafetyManualStepValue v
            WHERE v.step.manual.manualId = :manualId AND v.deletedYn = 'N' AND v.step.deletedYn = 'N'
            """)
    List<SafetyManualStepValue> findByManualId(@Param("manualId") Long manualId);

    @Query("""
            SELECT v FROM SafetyManualStepValue v
            WHERE v.step.stepId = :stepId AND v.deletedYn = 'N'
            """)
    List<SafetyManualStepValue> findByStepId(@Param("stepId") Long stepId);

    @Query("""
            SELECT v FROM SafetyManualStepValue v
            WHERE v.step.stepId = :stepId AND v.column.columnId = :columnId AND v.deletedYn = 'N'
            """)
    Optional<SafetyManualStepValue> findByStepAndColumn(@Param("stepId") Long stepId,
                                                         @Param("columnId") Long columnId);

    /** 열을 지울 때 그 열의 값도 함께 정리하기 위해 조회한다 */
    @Query("SELECT v FROM SafetyManualStepValue v WHERE v.column.columnId = :columnId AND v.deletedYn = 'N'")
    List<SafetyManualStepValue> findByColumnId(@Param("columnId") Long columnId);

    /**
     * 내용 검색 — 텍스트 열의 값에서 키워드를 찾는다.
     * <p>분류 하위(대/중/소 무관) 범위로 좁힐 수 있고, {@code categoryId} 가 null 이면 전체가 대상이다.
     */
    @Query("""
            SELECT v FROM SafetyManualStepValue v
            JOIN v.step s
            JOIN s.manual m
            JOIN m.category c
            LEFT JOIN c.parent p
            LEFT JOIN p.parent gp
            WHERE v.deletedYn = 'N' AND s.deletedYn = 'N' AND m.deletedYn = 'N' AND c.deletedYn = 'N'
              AND (:categoryId IS NULL
                   OR c.categoryId = :categoryId
                   OR p.categoryId = :categoryId
                   OR gp.categoryId = :categoryId)
              AND LOWER(v.textValue) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY m.manualId ASC, s.sortOrder ASC
            """)
    List<SafetyManualStepValue> searchByText(@Param("categoryId") Long categoryId,
                                              @Param("keyword") String keyword);
}
