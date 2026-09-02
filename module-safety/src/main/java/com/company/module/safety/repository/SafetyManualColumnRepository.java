package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualColumnRepository extends JpaRepository<SafetyManualColumn, Long> {

    /** 한 매뉴얼의 표 열 정의 (왼쪽부터) */
    @Query("""
            SELECT c FROM SafetyManualColumn c
            WHERE c.manual.manualId = :manualId AND c.deletedYn = 'N'
            ORDER BY c.sortOrder ASC, c.columnId ASC
            """)
    List<SafetyManualColumn> findByManualId(@Param("manualId") Long manualId);

    @Query("SELECT c FROM SafetyManualColumn c WHERE c.columnId = :id AND c.deletedYn = 'N'")
    Optional<SafetyManualColumn> findActiveById(@Param("id") Long id);

    /** 새 열을 맨 뒤에 붙일 때 쓸 다음 순서값 */
    @Query("""
            SELECT COALESCE(MAX(c.sortOrder), 0) FROM SafetyManualColumn c
            WHERE c.manual.manualId = :manualId AND c.deletedYn = 'N'
            """)
    int findMaxSortOrder(@Param("manualId") Long manualId);
}
