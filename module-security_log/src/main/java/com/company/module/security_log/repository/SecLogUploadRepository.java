package com.company.module.security_log.repository;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SecLogUploadRepository extends JpaRepository<SecLogUpload, Long> {

    Optional<SecLogUpload> findByUploadIdAndDeletedYn(Long uploadId, String deletedYn);

    boolean existsByTargetYmAndSystemNameAndFileHashAndDeletedYn(String targetYm, String systemName,
                                                                  String fileHash, String deletedYn);

    /** 월별 업로드/분석 목록 조회 */
    @Query("""
            SELECT u FROM SecLogUpload u
             WHERE u.deletedYn = 'N'
               AND u.targetYm = :targetYm
               AND (:systemName IS NULL OR u.systemName LIKE CONCAT('%', :systemName, '%'))
               AND (:logType IS NULL OR u.logType = :logType)
               AND (:status IS NULL OR u.status = :status)
               AND (:maxSeverity IS NULL OR u.maxSeverity = :maxSeverity)
            """)
    Page<SecLogUpload> search(@Param("targetYm") String targetYm,
                              @Param("systemName") String systemName,
                              @Param("logType") LogType logType,
                              @Param("status") UploadStatus status,
                              @Param("maxSeverity") Severity maxSeverity,
                              Pageable pageable);

    /** 월별 모니터링 대상 전체 (대시보드 집계용) */
    List<SecLogUpload> findByTargetYmAndDeletedYn(String targetYm, String deletedYn);
}
