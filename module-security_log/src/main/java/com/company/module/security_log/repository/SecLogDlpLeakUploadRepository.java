package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpLeakUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SecLogDlpLeakUploadRepository extends JpaRepository<SecLogDlpLeakUpload, Long> {

    Optional<SecLogDlpLeakUpload> findByLeakUploadIdAndDeletedYn(Long leakUploadId, String deletedYn);

    /** 그 달 업로드 (최근 순) */
    List<SecLogDlpLeakUpload> findByTargetYmAndDeletedYnOrderByLeakUploadIdDesc(String targetYm, String deletedYn);

    boolean existsByTargetYmAndFileHashAndDeletedYn(String targetYm, String fileHash, String deletedYn);

    /** 월별 최신 업로드 (최근 12개월 등 목록용) [targetYm, 최신 업로드 ID, 파일 수] */
    @Query("""
            SELECT u.targetYm, MAX(u.leakUploadId), COUNT(u) FROM SecLogDlpLeakUpload u
             WHERE u.deletedYn = 'N' AND u.targetYm BETWEEN :fromYm AND :toYm
             GROUP BY u.targetYm
            """)
    List<Object[]> latestByMonth(@Param("fromYm") String fromYm, @Param("toYm") String toYm);
}
