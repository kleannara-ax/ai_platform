package com.company.module.security_log.repository;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SecLogRuleRepository extends JpaRepository<SecLogRule, Long> {

    Optional<SecLogRule> findByRuleIdAndDeletedYn(Long ruleId, String deletedYn);

    boolean existsByRuleCode(String ruleCode);

    /** 룰셋 편집 화면 목록 조회 (삭제 제외) */
    /**
     * 룰셋 편집 화면 목록 조회 (삭제 제외)
     * - scope: COMMON(공통 룰만) / SYSTEM(시스템 전용만) / null(전체)
     * - systemId: 지정 시 해당 시스템 전용 룰만
     */
    @Query("""
            SELECT r FROM SecLogRule r
             WHERE r.deletedYn = 'N'
               AND (:q IS NULL OR r.ruleName LIKE CONCAT('%', :q, '%') OR r.ruleCode LIKE CONCAT('%', :q, '%'))
               AND (:logType IS NULL OR r.logType = :logType)
               AND (:useYn IS NULL OR r.useYn = :useYn)
               AND (:scope IS NULL OR (:scope = 'COMMON' AND r.systemId IS NULL) OR (:scope = 'SYSTEM' AND r.systemId IS NOT NULL))
               AND (:systemId IS NULL OR r.systemId = :systemId)
            """)
    Page<SecLogRule> search(@Param("q") String q,
                            @Param("logType") LogType logType,
                            @Param("useYn") String useYn,
                            @Param("scope") String scope,
                            @Param("systemId") Long systemId,
                            Pageable pageable);

    /** 분석에 적용할 활성 '시스템 전용' 룰 */
    @Query("""
            SELECT r FROM SecLogRule r
             WHERE r.deletedYn = 'N'
               AND r.useYn = 'Y'
               AND r.systemId = :systemId
             ORDER BY r.sortOrder ASC, r.ruleId ASC
            """)
    List<SecLogRule> findActiveSystemRules(@Param("systemId") Long systemId);

    /** 시스템별 전용 룰 수 [systemId, 전체, 사용] */
    @Query("""
            SELECT r.systemId, COUNT(r), SUM(CASE WHEN r.useYn = 'Y' THEN 1 ELSE 0 END) FROM SecLogRule r
             WHERE r.deletedYn = 'N' AND r.systemId IS NOT NULL
             GROUP BY r.systemId
            """)
    List<Object[]> countBySystem();

    /** 공통 룰 수 [로그 유형, 전체, 사용] */
    @Query("""
            SELECT r.logType, COUNT(r), SUM(CASE WHEN r.useYn = 'Y' THEN 1 ELSE 0 END) FROM SecLogRule r
             WHERE r.deletedYn = 'N' AND r.systemId IS NULL
             GROUP BY r.logType
            """)
    List<Object[]> countCommonByLogType();

    /** 분석에 적용할 활성 '공통' 룰 조회 (로그 유형 + ALL, 시스템 전용 룰 제외) */
    @Query("""
            SELECT r FROM SecLogRule r
             WHERE r.deletedYn = 'N'
               AND r.useYn = 'Y'
               AND r.systemId IS NULL
               AND r.logType IN :logTypes
             ORDER BY r.sortOrder ASC, r.ruleId ASC
            """)
    List<SecLogRule> findActiveRules(@Param("logTypes") Collection<LogType> logTypes);
}
