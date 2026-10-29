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
               AND (:ruleCode IS NULL OR d.ruleCode = :ruleCode)
            """)
    Page<SecLogDetection> search(@Param("uploadId") Long uploadId,
                                 @Param("severity") Severity severity,
                                 @Param("reviewStatus") ReviewStatus reviewStatus,
                                 @Param("ruleCode") String ruleCode,
                                 Pageable pageable);

    /** 업로드 내 특정 룰의 탐지 건 (룰 단위 일괄 검토용) */
    @Query("""
            SELECT d FROM SecLogDetection d
             WHERE d.deletedYn = 'N'
               AND d.uploadId = :uploadId
               AND d.ruleCode = :ruleCode
               AND (:reviewStatus IS NULL OR d.reviewStatus = :reviewStatus)
            """)
    List<SecLogDetection> findByRule(@Param("uploadId") Long uploadId,
                                     @Param("ruleCode") String ruleCode,
                                     @Param("reviewStatus") ReviewStatus reviewStatus);

    /** 업로드별 룰 x 검토상태 건수 [ruleCode, reviewStatus, count] */
    @Query("""
            SELECT d.ruleCode, d.reviewStatus, COUNT(d) FROM SecLogDetection d
             WHERE d.deletedYn = 'N' AND d.uploadId = :uploadId
             GROUP BY d.ruleCode, d.reviewStatus
            """)
    List<Object[]> countByRuleAndReview(@Param("uploadId") Long uploadId);

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

    /** 월별 룰 탐지 TOP 집계 - 지정 시스템만 (대시보드: 사용 중 시스템) */
    @Query("""
            SELECT d.ruleCode, d.ruleName, d.severity, COUNT(d)
              FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
             GROUP BY d.ruleCode, d.ruleName, d.severity
             ORDER BY COUNT(d) DESC
            """)
    List<Object[]> countByRuleForMonthAndSystems(@Param("targetYm") String targetYm,
                                                 @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 월별 미검토 탐지 건수 - 지정 시스템만 */
    @Query("""
            SELECT COUNT(d) FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
               AND d.reviewStatus = :reviewStatus
            """)
    long countForMonthByReviewStatusAndSystems(@Param("targetYm") String targetYm,
                                               @Param("reviewStatus") ReviewStatus reviewStatus,
                                               @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 시스템 x 검토 상태별 탐지 건수 [systemId, reviewStatus, count] (대시보드 검토 현황) */
    @Query("""
            SELECT u.systemId, d.reviewStatus, COUNT(d) FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
             GROUP BY u.systemId, d.reviewStatus
            """)
    List<Object[]> countBySystemAndReview(@Param("targetYm") String targetYm,
                                          @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 업로드의 탐지 줄 번호 목록 (일별 집계 재생성용) */
    @Query("SELECT d.lineNumber FROM SecLogDetection d WHERE d.uploadId = :uploadId AND d.deletedYn = 'N'")
    List<Integer> findLineNumbersByUploadId(@Param("uploadId") Long uploadId);

    /** 월 탐지 목록 - 지정 시스템 (월 점검 결과 내보내기) */
    @Query("""
            SELECT d, u.systemName FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
             ORDER BY u.systemName, d.severity DESC, d.detectionId
            """)
    List<Object[]> findForMonthAndSystems(@Param("targetYm") String targetYm,
                                          @Param("systemIds") java.util.Collection<Long> systemIds,
                                          org.springframework.data.domain.Pageable pageable);

    /**
     * 미검토 탐지 경과일 분포 - 지정 시스템 [createdAt(date), count]
     */
    @Query("""
            SELECT d.createdAt FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
               AND d.reviewStatus = com.company.module.security_log.entity.ReviewStatus.PENDING
            """)
    List<java.time.LocalDateTime> findPendingCreatedAt(@Param("targetYm") String targetYm,
                                                        @Param("systemIds") java.util.Collection<Long> systemIds);

    /** 미검토가 가장 오래된 업로드 (시스템별) [uploadId, systemId, systemName, min(createdAt), count] */
    @Query("""
            SELECT u.uploadId, u.systemId, u.systemName, MIN(d.createdAt), COUNT(d)
              FROM SecLogDetection d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
               AND d.reviewStatus = com.company.module.security_log.entity.ReviewStatus.PENDING
             GROUP BY u.uploadId, u.systemId, u.systemName
             ORDER BY MIN(d.createdAt)
            """)
    List<Object[]> findPendingByUpload(@Param("targetYm") String targetYm,
                                       @Param("systemIds") java.util.Collection<Long> systemIds);

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
