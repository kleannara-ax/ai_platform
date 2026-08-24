package com.company.module.kims.repository;

import com.company.module.kims.entity.SupplyIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SupplyIssueRepository extends JpaRepository<SupplyIssue, Long> {

    /** 특정 요청에 연결된 지급 내역을 시간순으로 조회한다. (상세 화면용) */
    List<SupplyIssue> findByServiceRequest_RequestIdOrderByCreatedAtAsc(Long requestId);

    /**
     * 지급 내역 검색 (지급일 기간 / 품목 / 부서, 모두 선택적).
     * <p>{@code Pageable.unpaged()} 를 넘기면 전체 결과(엑셀용)를 얻을 수 있다.
     */
    @Query("""
            SELECT s FROM SupplyIssue s
            WHERE (:from IS NULL OR s.issuedAt >= :from)
              AND (:to   IS NULL OR s.issuedAt <= :to)
              AND (:itemId IS NULL OR s.inventoryItem.itemId = :itemId)
              AND (:department IS NULL OR s.department = :department)
            ORDER BY s.issuedAt DESC, s.issueId DESC
            """)
    Page<SupplyIssue> search(@Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("itemId") Long itemId,
                             @Param("department") String department,
                             Pageable pageable);

    // ================================================================
    // 월말 결산용 (기간 지급내역 + 수량 합계 집계)
    // ================================================================

    /** 기간 내 지급내역 (지급일 기준) */
    List<SupplyIssue> findByIssuedAtBetweenOrderByIssuedAtDesc(LocalDate from, LocalDate to);

    /** 품목별 지급 수량 합계: [itemName, Long] */
    @Query("""
            SELECT s.inventoryItem.itemName, SUM(s.quantity) FROM SupplyIssue s
            WHERE s.issuedAt BETWEEN :from AND :to
            GROUP BY s.inventoryItem.itemName ORDER BY SUM(s.quantity) DESC
            """)
    List<Object[]> sumByItem(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 부서별 지급 수량 합계: [department, Long] */
    @Query("""
            SELECT s.department, SUM(s.quantity) FROM SupplyIssue s
            WHERE s.issuedAt BETWEEN :from AND :to
            GROUP BY s.department ORDER BY SUM(s.quantity) DESC
            """)
    List<Object[]> sumByDepartment(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 요청자별 지급 수량 합계 (연결된 요청의 요청자 기준): [requesterName, Long] */
    @Query("""
            SELECT s.serviceRequest.requesterName, SUM(s.quantity) FROM SupplyIssue s
            WHERE s.issuedAt BETWEEN :from AND :to
            GROUP BY s.serviceRequest.requesterName ORDER BY SUM(s.quantity) DESC
            """)
    List<Object[]> sumByRequester(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 담당자별 지급 수량 합계: [issuedBy, Long] */
    @Query("""
            SELECT s.issuedBy, SUM(s.quantity) FROM SupplyIssue s
            WHERE s.issuedAt BETWEEN :from AND :to
            GROUP BY s.issuedBy ORDER BY SUM(s.quantity) DESC
            """)
    List<Object[]> sumByIssuedBy(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
