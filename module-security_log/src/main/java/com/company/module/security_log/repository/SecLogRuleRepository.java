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
    @Query("""
            SELECT r FROM SecLogRule r
             WHERE r.deletedYn = 'N'
               AND (:q IS NULL OR r.ruleName LIKE CONCAT('%', :q, '%') OR r.ruleCode LIKE CONCAT('%', :q, '%'))
               AND (:logType IS NULL OR r.logType = :logType)
               AND (:useYn IS NULL OR r.useYn = :useYn)
            """)
    Page<SecLogRule> search(@Param("q") String q,
                            @Param("logType") LogType logType,
                            @Param("useYn") String useYn,
                            Pageable pageable);

    /** 분석에 적용할 활성 룰 조회 (로그 유형 + ALL 공통 룰) */
    @Query("""
            SELECT r FROM SecLogRule r
             WHERE r.deletedYn = 'N'
               AND r.useYn = 'Y'
               AND r.logType IN :logTypes
             ORDER BY r.sortOrder ASC, r.ruleId ASC
            """)
    List<SecLogRule> findActiveRules(@Param("logTypes") Collection<LogType> logTypes);
}
