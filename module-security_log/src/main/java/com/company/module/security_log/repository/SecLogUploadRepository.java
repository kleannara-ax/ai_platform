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

    /**
     * 월별 분석 대상 업로드 - 시스템마다 가장 최근에 올린 파일 1개 (UPLOAD_ID 가 가장 큰 것)
     * 같은 달·같은 시스템에 여러 번 올리면 이전 파일은 분석·집계에서 빠짐. 최신 파일을 삭제하면 그 전 파일이 다시 대상이 됨.
     * 시스템 미지정(직접 입력) 업로드는 제외.
     */
    @Query("""
            SELECT u FROM SecLogUpload u
             WHERE u.deletedYn = 'N' AND u.targetYm = :targetYm AND u.systemId IS NOT NULL
               AND u.uploadId = (SELECT MAX(u2.uploadId) FROM SecLogUpload u2
                                  WHERE u2.deletedYn = 'N' AND u2.targetYm = :targetYm AND u2.systemId = u.systemId)
            """)
    List<SecLogUpload> findLatestByTargetYm(@Param("targetYm") String targetYm);

    /** 월별 업로드 전체 (이전 파일 포함 - 일자 점검 재실행 등 관리용) */
    List<SecLogUpload> findByTargetYmAndDeletedYn(String targetYm, String deletedYn);
}
