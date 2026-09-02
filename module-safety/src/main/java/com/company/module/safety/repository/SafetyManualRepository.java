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
     * 분류 하위(자기 자신 + 중분류 + 소분류)에 속한 매뉴얼 전체.
     * <p>분류 트리는 대/중/소 3단계로 고정이므로 부모를 2단계까지만 거슬러 비교하면 충분하다.
     * {@code categoryId} 가 null 이면 전체 매뉴얼을 반환한다.
     * 목록 화면에서 분류 경로(대 &gt; 중 &gt; 소)를 함께 보여주므로 상위 분류까지 fetch join 한다.
     */
    @Query("""
            SELECT m FROM SafetyManual m
            JOIN FETCH m.category c
            LEFT JOIN FETCH c.parent p
            LEFT JOIN FETCH p.parent gp
            WHERE m.deletedYn = 'N' AND c.deletedYn = 'N'
              AND (:categoryId IS NULL
                   OR c.categoryId = :categoryId
                   OR p.categoryId = :categoryId
                   OR gp.categoryId = :categoryId)
            ORDER BY c.sortOrder ASC, c.categoryId ASC, m.sortOrder ASC, m.manualId ASC
            """)
    List<SafetyManual> findInCategorySubtree(@Param("categoryId") Long categoryId);

    /** 소분류별 매뉴얼 건수 (좌측 분류 트리의 건수 배지 계산용) */
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
