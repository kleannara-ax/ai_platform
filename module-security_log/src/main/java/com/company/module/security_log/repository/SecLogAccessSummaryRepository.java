package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogAccessSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecLogAccessSummaryRepository extends JpaRepository<SecLogAccessSummary, Long> {

    List<SecLogAccessSummary> findByUploadIdAndDeletedYnOrderByFailCountDescSuccessCountDesc(Long uploadId, String deletedYn);

    /** 월별 시스템별 접속 집계 [systemId, 계정 수(중복 제외), 성공 합, 실패 합, 위험계정 수(중복 제외)] - 매일 수집돼도 같은 계정은 1번만 셈 */
    @Query("""
            SELECT u.systemId, COUNT(DISTINCT s.accountId), SUM(s.successCount), SUM(s.failCount),
                   COUNT(DISTINCT CASE WHEN s.riskFlags IS NOT NULL AND s.riskFlags <> '' THEN s.accountId END)
              FROM SecLogAccessSummary s, SecLogUpload u
             WHERE s.uploadId = u.uploadId
               AND s.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IS NOT NULL
             GROUP BY u.systemId
            """)
    List<Object[]> aggregateBySystem(@Param("targetYm") String targetYm);

    /** 월별 위험 표시 계정 [summary, systemId, systemName, uploadId] (대시보드) */
    @Query("""
            SELECT s, u.systemId, u.systemName, u.uploadId
              FROM SecLogAccessSummary s, SecLogUpload u
             WHERE s.uploadId = u.uploadId
               AND s.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
               AND s.riskFlags IS NOT NULL AND s.riskFlags <> ''
            """)
    List<Object[]> findRiskForMonth(@Param("targetYm") String targetYm,
                                    @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 월 접속 계정 전체 [summary, systemId, systemName, uploadId] (특권 계정 활동, 내보내기) */
    @Query("""
            SELECT s, u.systemId, u.systemName, u.uploadId
              FROM SecLogAccessSummary s, SecLogUpload u
             WHERE s.uploadId = u.uploadId
               AND s.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
            """)
    List<Object[]> findAllForMonth(@Param("targetYm") String targetYm,
                                   @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 재분석/삭제 시 소프트 삭제 (WHERE UPLOAD_ID 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogAccessSummary s
               SET s.deletedYn = 'Y', s.deletedAt = :deletedAt, s.deletedBy = :deletedBy
             WHERE s.uploadId = :uploadId AND s.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
