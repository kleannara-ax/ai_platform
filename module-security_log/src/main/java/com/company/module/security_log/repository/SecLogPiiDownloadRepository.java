package com.company.module.security_log.repository;

import com.company.module.security_log.entity.PiiReviewStatus;
import com.company.module.security_log.entity.SecLogPiiDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SecLogPiiDownloadRepository extends JpaRepository<SecLogPiiDownload, Long> {

    List<SecLogPiiDownload> findByUploadIdAndDeletedYnOrderByRowNoAsc(Long uploadId, String deletedYn);

    long countByUploadIdAndReviewStatusAndDeletedYn(Long uploadId, PiiReviewStatus status, String deletedYn);

    /** 업로드별 [uploadId, 전체 건수, 미검토 건수] */
    @Query("""
            SELECT p.uploadId, COUNT(p),
                   SUM(CASE WHEN p.reviewStatus = com.company.module.security_log.entity.PiiReviewStatus.PENDING THEN 1 ELSE 0 END)
              FROM SecLogPiiDownload p
             WHERE p.uploadId IN :uploadIds AND p.deletedYn = 'N'
             GROUP BY p.uploadId
            """)
    List<Object[]> countByUploads(@Param("uploadIds") Collection<Long> uploadIds);

    /** 재분석/업로드 삭제 시 소프트 삭제 (WHERE UPLOAD_ID 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogPiiDownload p
               SET p.deletedYn = 'Y', p.deletedAt = :deletedAt, p.deletedBy = :deletedBy
             WHERE p.uploadId = :uploadId AND p.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
