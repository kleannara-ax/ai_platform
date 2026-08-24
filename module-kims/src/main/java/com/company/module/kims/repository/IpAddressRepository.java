package com.company.module.kims.repository;

import com.company.module.kims.entity.IpAddress;
import com.company.module.kims.entity.enums.IpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IpAddressRepository extends JpaRepository<IpAddress, Long> {

    boolean existsByIpAddress(String ipAddress);

    java.util.Optional<com.company.module.kims.entity.IpAddress> findByIpAddress(String ipAddress);

    /**
     * IP 목록 검색 (IP/사용자 부분일치, 상태, 부서 — 모두 선택적).
     * <p>{@code Pageable.unpaged()} 로 전체(엑셀용) 조회 가능.
     */
    @Query("""
            SELECT i FROM IpAddress i
            WHERE (
                (:searchField IS NULL AND (:keyword IS NULL
                     OR i.ipAddress LIKE CONCAT('%', :keyword, '%')
                     OR i.userName LIKE CONCAT('%', :keyword, '%')
                     OR i.serialNo LIKE CONCAT('%', :keyword, '%')
                     OR i.device LIKE CONCAT('%', :keyword, '%')
                     OR i.remark LIKE CONCAT('%', :keyword, '%')))
             OR (:searchField = 'serial' AND (:keyword IS NULL OR i.serialNo LIKE CONCAT('%', :keyword, '%')))
             OR (:searchField = 'user'   AND (:keyword IS NULL OR i.userName LIKE CONCAT('%', :keyword, '%')))
             OR (:searchField = 'dept'   AND (:keyword IS NULL OR i.department LIKE CONCAT('%', :keyword, '%')))
              )
              AND (:status IS NULL OR i.status = :status)
              AND (:department IS NULL OR i.department = :department)
              AND (:ipGroup IS NULL OR i.ipGroup = :ipGroup)
              AND (:excludeGroup IS NULL OR i.ipGroup IS NULL OR i.ipGroup <> :excludeGroup)
            ORDER BY i.ipGroup, FUNCTION('INET_ATON', i.ipAddress), i.ipId
            """)
    Page<IpAddress> search(@Param("keyword") String keyword,
                           @Param("searchField") String searchField,
                           @Param("status") IpStatus status,
                           @Param("department") String department,
                           @Param("ipGroup") String ipGroup,
                           @Param("excludeGroup") String excludeGroup,
                           Pageable pageable);

    /** 등록된 IP 그룹 목록 (중복 제거, 정렬) */
    @Query("SELECT DISTINCT i.ipGroup FROM IpAddress i WHERE i.ipGroup IS NOT NULL AND i.ipGroup <> '' ORDER BY i.ipGroup")
    java.util.List<String> findDistinctGroups();

    // ================================================================
    // 대역(그룹) 사용 현황 집계
    // ================================================================

    /** 특정 대역 접두어(예: "192.1.0.")의 전체 등록 IP 수 */
    long countByIpAddressStartingWith(String prefix);

    /** 특정 대역 접두어 + 상태별 IP 수 (사용중 집계용) */
    long countByStatusAndIpAddressStartingWith(IpStatus status, String prefix);
}
