package com.company.module.kims.repository;

import com.company.module.kims.entity.InternetWork;
import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InternetWorkRepository extends JpaRepository<InternetWork, Long> {

    /** 특정 요청에 연결된 공사 내역 (요청 상세용, 삭제되지 않은 것만) */
    List<InternetWork> findByServiceRequest_RequestIdAndDeletedYnOrderByCreatedAtAsc(Long requestId, String deletedYn);

    /** 기간 내 공사 내역 (월말 결산용, 접수일 기준, 삭제되지 않은 것만) */
    List<InternetWork> findByCreatedAtBetweenAndDeletedYnOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, String deletedYn);

    /**
     * 공사 내역 검색 (요청자/위치/내용 부분일치, 공사유형, 상태, 부서, 접수기간 — 모두 선택적).
     * <p>{@code Pageable.unpaged()} 로 전체(엑셀용) 조회 가능. 소프트 삭제된 건은 제외한다.
     */
    @Query("""
            SELECT w FROM InternetWork w
            WHERE w.deletedYn = 'N'
              AND (:keyword IS NULL OR w.requesterName LIKE CONCAT('%', :keyword, '%')
                                    OR w.location LIKE CONCAT('%', :keyword, '%')
                                    OR w.content LIKE CONCAT('%', :keyword, '%'))
              AND (:workType IS NULL OR w.workType = :workType)
              AND (:status IS NULL OR w.status = :status)
              AND (:department IS NULL OR w.department = :department)
              AND (:from IS NULL OR w.createdAt >= :from)
              AND (:to   IS NULL OR w.createdAt <= :to)
            ORDER BY w.createdAt DESC
            """)
    Page<InternetWork> search(@Param("keyword") String keyword,
                              @Param("workType") InternetWorkType workType,
                              @Param("status") InternetWorkStatus status,
                              @Param("department") String department,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);
}
