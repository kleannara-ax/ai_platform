package com.company.module.cckb.repository;

import com.company.module.cckb.entity.InspectionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, String> {

    Optional<InspectionRecord> findByInspectionIdAndDeletedYn(String inspectionId, String deletedYn);

    /**
     * 이력 탭(검색/기간 필터 + 페이지네이션) 조회.
     * 원본 V4 HTML 의 getFilteredHistory()(키워드=검사ID 부분일치, 기간=ts범위) + HISTORY_PAGE_SIZE=10 페이지네이션 이관.
     */
    @Query("""
        SELECT r FROM InspectionRecord r
        WHERE r.deletedYn = 'N'
          AND (:keyword IS NULL OR :keyword = '' OR r.inspectionId LIKE CONCAT('%', :keyword, '%'))
          AND (:from IS NULL OR r.inspectionDatetime >= :from)
          AND (:to IS NULL OR r.inspectionDatetime <= :to)
        ORDER BY r.inspectionDatetime DESC
        """)
    Page<InspectionRecord> search(@Param("keyword") String keyword,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    /** 오늘 생성된 검사 건수 — 일련번호(YYMMDD + 4자리) 채번용 */
    @Query("SELECT COUNT(r) FROM InspectionRecord r WHERE r.inspectionId LIKE CONCAT(:datePrefix, '%')")
    long countByDatePrefix(@Param("datePrefix") String datePrefix);
}
