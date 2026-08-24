package com.company.module.kims.repository;

import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    /**
     * 요청번호 자동 생성을 위해, 특정 접두어(예: "KIMS-20260602-")로 시작하는
     * 요청 건수를 센다.
     */
    long countByRequestNoStartingWith(String prefix);

    /**
     * 다중 조건 검색.
     * <p>각 조건은 "값이 null 이면 무시"되도록 작성하여, 선택적으로 필터링한다.
     * (기간 / 요청유형 / 처리상태 / 부서 / 요청자)
     * <p>Pageable 에 {@code Pageable.unpaged()} 를 넘기면 전체 결과(엑셀용)를 얻을 수 있다.
     */
    @Query("""
            SELECT r FROM ServiceRequest r
            WHERE (:from IS NULL OR r.createdAt >= :from)
              AND (:to   IS NULL OR r.createdAt <= :to)
              AND (:requestType IS NULL OR r.requestType = :requestType)
              AND (:status IS NULL OR r.status = :status)
              AND (:department IS NULL OR r.department = :department)
              AND (:requester  IS NULL OR r.requesterName LIKE CONCAT('%', :requester, '%'))
              AND (:assignee   IS NULL OR r.assignee = :assignee)
              AND (:location   IS NULL OR r.location LIKE CONCAT('%', :location, '%'))
            ORDER BY r.createdAt DESC
            """)
    Page<ServiceRequest> search(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("requestType") RequestType requestType,
                                @Param("status") RequestStatus status,
                                @Param("department") String department,
                                @Param("requester") String requester,
                                @Param("assignee") String assignee,
                                @Param("location") String location,
                                Pageable pageable);

    // ================================================================
    // 대시보드 집계용
    // ================================================================

    /** 특정 시각 이후 접수된 요청 수 (예: 오늘 0시 이후 = 오늘 접수 수) */
    long countByCreatedAtGreaterThanEqual(LocalDateTime start);

    /** 최근 요청 N건 (대시보드 "최근 요청 목록") */
    List<ServiceRequest> findTop10ByOrderByCreatedAtDesc();

    /** 처리상태별 건수: [RequestStatus, Long] */
    @Query("SELECT r.status, COUNT(r) FROM ServiceRequest r GROUP BY r.status")
    List<Object[]> countGroupByStatus();

    /** 요청유형별 건수: [RequestType, Long] */
    @Query("SELECT r.requestType, COUNT(r) FROM ServiceRequest r GROUP BY r.requestType")
    List<Object[]> countGroupByType();

    /** 특정 상태의 요청유형별 건수 (신규=접수 카드 배지용): [RequestType, Long] */
    @Query("SELECT r.requestType, COUNT(r) FROM ServiceRequest r WHERE r.status = :status GROUP BY r.requestType")
    List<Object[]> countTypeByStatus(@Param("status") RequestStatus status);

    /** 요청이 존재하는 (연,월) 목록: [Year, Month] 내림차순 (월별 목록 셀렉트용) */
    @Query("SELECT DISTINCT YEAR(r.createdAt), MONTH(r.createdAt) FROM ServiceRequest r "
            + "ORDER BY YEAR(r.createdAt) DESC, MONTH(r.createdAt) DESC")
    List<Object[]> findDistinctYearMonths();

    /** 월별 접수 건수: [Year(Integer), Month(Integer), Long] (from 이후) */
    @Query("""
            SELECT YEAR(r.createdAt), MONTH(r.createdAt), COUNT(r)
            FROM ServiceRequest r
            WHERE r.createdAt >= :from
            GROUP BY YEAR(r.createdAt), MONTH(r.createdAt)
            ORDER BY YEAR(r.createdAt), MONTH(r.createdAt)
            """)
    List<Object[]> countMonthly(@Param("from") LocalDateTime from);

    /** 특정 연도의 월별 접수 건수: [Month(Integer), Long] */
    @Query("""
            SELECT MONTH(r.createdAt), COUNT(r)
            FROM ServiceRequest r
            WHERE YEAR(r.createdAt) = :year
            GROUP BY MONTH(r.createdAt)
            ORDER BY MONTH(r.createdAt)
            """)
    List<Object[]> countMonthlyOfYear(@Param("year") int year);

    /** 요청 데이터가 존재하는 연도 목록 (오름차순) */
    @Query("SELECT DISTINCT YEAR(r.createdAt) FROM ServiceRequest r ORDER BY YEAR(r.createdAt)")
    List<Integer> distinctYears();

    // ================================================================
    // 월말 결산용 (기간 집계)
    // ================================================================

    /**
     * 기간 내 요청유형별 처리 건수: [RequestType, Long]
     * <p>취소(CANCELED)된 요청은 실제 처리가 이뤄지지 않았으므로 처리 건수에서 제외한다.
     * (접수/처리중/보류/완료/반려는 모두 처리 건수로 집계)
     */
    @Query("SELECT r.requestType, COUNT(r) FROM ServiceRequest r "
            + "WHERE r.createdAt BETWEEN :from AND :to "
            + "AND r.status <> com.company.module.kims.entity.enums.RequestStatus.CANCELED "
            + "GROUP BY r.requestType")
    List<Object[]> countTypeInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 기간 내 세부 불편유형별 처리 건수: [IssueType, Long]
     * <p>PC관련 불편사항 조치(PROGRAM) 요청에서 선택한 불편유형별 집계.
     * 취소(CANCELED) 및 불편유형 미지정(null)은 제외한다.
     */
    @Query("SELECT r.issueType, COUNT(r) FROM ServiceRequest r "
            + "WHERE r.createdAt BETWEEN :from AND :to "
            + "AND r.issueType IS NOT NULL "
            + "AND r.status <> com.company.module.kims.entity.enums.RequestStatus.CANCELED "
            + "GROUP BY r.issueType")
    List<Object[]> countIssueTypeInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 기간 내 담당자별 처리 건수: [assignee, Long]
     * <p>취소(CANCELED)된 요청은 실제 처리가 이뤄지지 않았으므로 처리 건수에서 제외한다.
     */
    @Query("""
            SELECT r.assignee, COUNT(r) FROM ServiceRequest r
            WHERE r.createdAt BETWEEN :from AND :to AND r.assignee IS NOT NULL AND r.assignee <> ''
            AND r.status <> com.company.module.kims.entity.enums.RequestStatus.CANCELED
            GROUP BY r.assignee ORDER BY COUNT(r) DESC
            """)
    List<Object[]> countAssigneeInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 기간 내 부서별 요청 건수: [department, Long] */
    @Query("""
            SELECT r.department, COUNT(r) FROM ServiceRequest r
            WHERE r.createdAt BETWEEN :from AND :to AND r.department IS NOT NULL AND r.department <> ''
            GROUP BY r.department ORDER BY COUNT(r) DESC
            """)
    List<Object[]> countDepartmentInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 미완료 요청 목록 (상태가 주어진 집합에 속하는 요청, 최신순) */
    List<ServiceRequest> findByStatusInOrderByCreatedAtDesc(java.util.Collection<RequestStatus> statuses);
}
