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
