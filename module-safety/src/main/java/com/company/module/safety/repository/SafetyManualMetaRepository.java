package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualMetaRepository extends JpaRepository<SafetyManualMeta, Long> {

    /** 한 매뉴얼의 머리말 항목 (표시 순서대로) */
    @Query("""
            SELECT m FROM SafetyManualMeta m
            WHERE m.manual.manualId = :manualId AND m.deletedYn = 'N'
            ORDER BY m.sortOrder ASC, m.metaId ASC
            """)
    List<SafetyManualMeta> findByManualId(@Param("manualId") Long manualId);

    @Query("SELECT m FROM SafetyManualMeta m WHERE m.metaId = :id AND m.deletedYn = 'N'")
    Optional<SafetyManualMeta> findActiveById(@Param("id") Long id);
}
