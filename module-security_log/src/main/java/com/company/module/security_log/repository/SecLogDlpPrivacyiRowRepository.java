package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpPrivacyiRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecLogDlpPrivacyiRowRepository extends JpaRepository<SecLogDlpPrivacyiRow, Long> {

    List<SecLogDlpPrivacyiRow> findByTargetYmAndKindAndDeletedYnOrderByRowNoAsc(String targetYm, String kind, String deletedYn);

    /** 다시 올리기 전에 이전 목록 소프트 삭제 (WHERE 연월 + 종류 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogDlpPrivacyiRow r
               SET r.deletedYn = 'Y', r.deletedAt = :deletedAt, r.deletedBy = :deletedBy
             WHERE r.targetYm = :targetYm AND r.kind = :kind AND r.deletedYn = 'N'
            """)
    int softDelete(@Param("targetYm") String targetYm, @Param("kind") String kind,
                   @Param("deletedBy") Long deletedBy, @Param("deletedAt") LocalDateTime deletedAt);
}
