package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogAccountReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SecLogAccountReviewRepository extends JpaRepository<SecLogAccountReview, Long> {

    List<SecLogAccountReview> findByUploadIdAndDeletedYn(Long uploadId, String deletedYn);

    List<SecLogAccountReview> findByUploadIdInAndDeletedYn(Collection<Long> uploadIds, String deletedYn);

    /** 재분석/업로드 삭제 시 소프트 삭제 (WHERE UPLOAD_ID 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogAccountReview r
               SET r.deletedYn = 'Y', r.deletedAt = :deletedAt, r.deletedBy = :deletedBy
             WHERE r.uploadId = :uploadId AND r.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
