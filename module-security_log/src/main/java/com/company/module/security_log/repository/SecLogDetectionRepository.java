package com.company.module.security_log.repository;

import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SecLogDetectionRepository extends JpaRepository<SecLogDetection, Long> {

    Optional<SecLogDetection> findByDetectionIdAndDeletedYn(Long detectionId, String deletedYn);

    @Query("""
            SELECT d FROM SecLogDetection d
             WHERE d.deletedYn = 'N'
               AND d.uploadId = :uploadId
               AND (:severity IS NULL OR d.severity = :severity)
               AND (:reviewStatus IS NULL OR d.reviewStatus = :reviewStatus)
            """)
    Page<SecLogDetection> search(@Param("uploadId") Long uploadId,
                                 @Param("severity") Severity severity,
                                 @Param("reviewStatus") ReviewStatus reviewStatus,
                                 Pageable pageable);

    long countByUploadIdAndReviewStatusAndDeletedYn(Long uploadId, ReviewStatus reviewStatus, String deletedYn);

    /** 업로드별 검토상태 건수 집계 [reviewStatus, count] */
    @Query("""
            SELECT d.reviewStatus, COUNT(d) FROM SecLogDetection d
             WHERE d.deletedYn = 'N' AND d.uploadId = :uploadId
             GROUP BY d.reviewStatus
            """)
    List<Object[]> countByReviewStatus(@Param("uploadId") Long uploadId);

    /** 업로드별 룰 탐지 건수 집계 [ruleCode, ruleName, severity, count] */
    @Query("""
            SELECT d.ruleCode, d.ruleName, d.severity, COUNT(d) FROM SecLogDetection d
             WHERE d.deletedYn = 'N' AND d.uploadId = :uploadId
             GROUP BY d.ruleCode, d.ruleName, d.severity
             ORDER BY COUNT(d) DESC
            """)
    List<Object[]> countByRule(@Param("uploadId") Long uploadId);

    /** 월별 룰 탐지 TOP 집계 [ruleCode, ruleName, severity, count] */
    @Query("""
            SELECT d.ruleCode, d.ruleName, d.severity, COUNT(d)
              FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm
             GROUP BY d.ruleCode, d.ruleName, d.severity
             ORDER BY COUNT(d) DESC
            """)
    List<Object[]> countByRuleForMonth(@Param("targetYm") String targetYm);

    /** 월별 미검토 탐지 건수 */
    @Query("""
            SELECT COUNT(d) FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm
               AND d.reviewStatus = :reviewStatus
            """)
    long countForMonthByReviewStatus(@Param("targetYm") String targetYm,
                                     @Param("reviewStatus") ReviewStatus reviewStatus);

    /**
     * 업로드 단위 탐지결과 일괄 소프트 삭제 (재분석/업로드 삭제 시)
     * - 물리 삭제가 아닌 DELETED_YN = 'Y' 처리
     * - WHERE 조건(UPLOAD_ID) 필수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogDetection d
               SET d.deletedYn = 'Y', d.deletedAt = :deletedAt, d.deletedBy = :deletedBy
             WHERE d.uploadId = :uploadId AND d.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
