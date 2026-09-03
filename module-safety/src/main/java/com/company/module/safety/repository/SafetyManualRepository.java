package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualRepository extends JpaRepository<SafetyManual, Long> {

    @Query("SELECT m FROM SafetyManual m WHERE m.manualId = :id AND m.deletedYn = 'N'")
    Optional<SafetyManual> findActiveById(@Param("id") Long id);

    /** 분류별 매뉴얼 목록 (해당 분류에 직접 속한 매뉴얼만) */
    @Query("SELECT m FROM SafetyManual m WHERE m.category.categoryId = :categoryId AND m.deletedYn = 'N' ORDER BY m.sortOrder ASC, m.manualId ASC")
    List<SafetyManual> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 분류 하위(자기 자신 + 중분류)에 속한 매뉴얼 전체.
     * <p>분류 트리는 대/중 2단계로 고정이므로 부모를 한 단계만 거슬러 비교하면 충분하다.
     * {@code categoryId} 가 null 이면 전체 매뉴얼을 반환한다.
     * 목록 화면에서 분류 경로(대 &gt; 중 &gt; 소)를 함께 보여주므로 상위 분류까지 fetch join 한다.
     */
    @Query("""
            SELECT m FROM SafetyManual m
            JOIN FETCH m.category c
            LEFT JOIN FETCH c.parent p
            WHERE m.deletedYn = 'N' AND c.deletedYn = 'N'
              AND (:categoryId IS NULL
                   OR c.categoryId = :categoryId
                   OR p.categoryId = :categoryId)
            ORDER BY c.sortOrder ASC, c.categoryId ASC, m.sortOrder ASC, m.manualId ASC
            """)
    List<SafetyManual> findInCategorySubtree(@Param("categoryId") Long categoryId);

    /**
     * 분류 하위에서 <b>단계 본문</b>(공정 설명/위험요인/안전보호구/비고)에 키워드가 들어간 매뉴얼.
     * <p>제목 검색과 달리 매뉴얼 안쪽 내용을 뒤진다. {@code categoryId} 가 null 이면 전체가 대상이다.
     */
    @Query("""
            SELECT DISTINCT m FROM SafetyManual m
            JOIN FETCH m.category c
            LEFT JOIN FETCH c.parent p
            WHERE m.deletedYn = 'N' AND c.deletedYn = 'N'
              AND (:categoryId IS NULL
                   OR c.categoryId = :categoryId
                   OR p.categoryId = :categoryId)
              AND EXISTS (
                  SELECT 1 FROM SafetyManualStep s
                  WHERE s.manual = m AND s.deletedYn = 'N'
                    AND (LOWER(s.description)     LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(s.hazard)          LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(s.safetyEquipment) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(s.remark)          LIKE LOWER(CONCAT('%', :keyword, '%')))
              )
            ORDER BY c.sortOrder ASC, c.categoryId ASC, m.sortOrder ASC, m.manualId ASC
            """)
    List<SafetyManual> searchByStepContent(@Param("categoryId") Long categoryId,
                                            @Param("keyword") String keyword);

    /** 분류별 매뉴얼 건수 (좌측 분류 트리의 건수 배지 계산용) */
    @Query("""
            SELECT m.category.categoryId AS categoryId, COUNT(m) AS manualCount
            FROM SafetyManual m
            WHERE m.deletedYn = 'N'
            GROUP BY m.category.categoryId
            """)
    List<CategoryManualCount> countActiveGroupByCategory();

    /** {@link #countActiveGroupByCategory()} 결과 projection */
    interface CategoryManualCount {
        Long getCategoryId();

        long getManualCount();
    }

    /**
     * 매뉴얼 검색 (제목 부분일치 / 분류, 둘 다 선택적) — 관리자 목록/검색용.
     */
    @Query("""
            SELECT m FROM SafetyManual m
            WHERE m.deletedYn = 'N'
              AND (:keyword IS NULL OR m.title LIKE CONCAT('%', :keyword, '%'))
              AND (:categoryId IS NULL OR m.category.categoryId = :categoryId)
            ORDER BY m.category.categoryId ASC, m.sortOrder ASC
            """)
    Page<SafetyManual> search(@Param("keyword") String keyword,
                               @Param("categoryId") Long categoryId,
                               Pageable pageable);

    boolean existsByTitleAndCategory_CategoryId(String title, Long categoryId);
}
