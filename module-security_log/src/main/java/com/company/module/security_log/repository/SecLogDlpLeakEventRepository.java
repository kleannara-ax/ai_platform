package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpLeakEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecLogDlpLeakEventRepository extends JpaRepository<SecLogDlpLeakEvent, Long> {

    /** 집계용 전체 이벤트 (분석용 줄 포함) */
    List<SecLogDlpLeakEvent> findByLeakUploadIdAndDeletedYnOrderByRowNoAsc(Long leakUploadId, String deletedYn);

    /** 업로드 삭제 시 소프트 삭제 (WHERE LEAK_UPLOAD_ID 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogDlpLeakEvent e
               SET e.deletedYn = 'Y', e.deletedAt = :deletedAt, e.deletedBy = :deletedBy
             WHERE e.leakUploadId = :uploadId AND e.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
