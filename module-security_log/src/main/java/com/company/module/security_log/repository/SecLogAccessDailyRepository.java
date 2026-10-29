package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogAccessDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SecLogAccessDailyRepository extends JpaRepository<SecLogAccessDaily, Long> {

    long countByUploadIdAndDeletedYn(Long uploadId, String deletedYn);

    /**
     * 시스템 x 일자 합계 (대상 월 업로드 기준, 로그 일자 범위 제한)
     * [systemId, logDate, lineCount, success, fail, offHour, priv, detection]
     */
    @Query("""
            SELECT u.systemId, d.logDate, SUM(d.lineCount), SUM(d.loginSuccessCount), SUM(d.loginFailCount),
                   SUM(d.offHourCount), SUM(d.privLoginCount), SUM(d.detectionCount)
              FROM SecLogAccessDaily d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
               AND d.logDate >= :fromDate AND d.logDate < :toDate
             GROUP BY u.systemId, d.logDate
            """)
    List<Object[]> sumBySystemAndDate(@Param("targetYm") String targetYm,
                                      @Param("systemIds") Collection<Long> systemIds,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);

    /** 업로드별 로그 일자 범위 [systemId, minDate, maxDate] - 파일이 다룬 기간 (활동 없는 날 판단) */
    @Query("""
            SELECT u.systemId, MIN(d.logDate), MAX(d.logDate)
              FROM SecLogAccessDaily d, SecLogUpload u
             WHERE d.uploadId = u.uploadId
               AND d.deletedYn = 'N' AND u.deletedYn = 'N'
               AND u.targetYm = :targetYm AND u.systemId IN :systemIds
             GROUP BY u.uploadId, u.systemId
            """)
    List<Object[]> rangeByUpload(@Param("targetYm") String targetYm,
                                 @Param("systemIds") Collection<Long> systemIds);

    /** 재분석/삭제 시 소프트 삭제 (WHERE UPLOAD_ID 필수) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SecLogAccessDaily d
               SET d.deletedYn = 'Y', d.deletedAt = :deletedAt, d.deletedBy = :deletedBy
             WHERE d.uploadId = :uploadId AND d.deletedYn = 'N'
            """)
    int softDeleteByUploadId(@Param("uploadId") Long uploadId,
                             @Param("deletedBy") Long deletedBy,
                             @Param("deletedAt") LocalDateTime deletedAt);
}
