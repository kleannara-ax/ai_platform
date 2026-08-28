package com.company.module.kims.repository;

import com.company.module.kims.entity.IpHistory;
import com.company.module.kims.entity.enums.IpSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IpHistoryRepository extends JpaRepository<IpHistory, Long> {

    /** 특정 IP 의 변경 이력 (최신순) */
    List<IpHistory> findByIpAddress_IpIdOrderByCreatedAtDesc(Long ipId);

    /** 특정 IP 의 변경 이력 (오래된순 — 사용자 변천 추적용) */
    List<IpHistory> findByIpAddress_IpIdOrderByCreatedAtAsc(Long ipId);

    /** 특정 사용자가 변경 전/후에 관여한 장비(IP_ID) 목록 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT h.ipAddress.ipId FROM IpHistory h "
            + "WHERE h.ipAddress.site = :site "
            + "AND (h.beforeUser LIKE CONCAT('%', :kw, '%') OR h.afterUser LIKE CONCAT('%', :kw, '%'))")
    List<Long> findDistinctDeviceIdsByUser(@org.springframework.data.repository.query.Param("kw") String kw,
                                            @org.springframework.data.repository.query.Param("site") IpSite site);

    /** 특정 업무요청에 연결된 IP 변경 이력 (요청 상세용) */
    List<IpHistory> findByServiceRequest_RequestIdOrderByCreatedAtDesc(Long requestId);

    /** 미품의(approved=false) 변경 이력 (최신순) — "미품의 IP 변경 내역" (전체 사업장, 대시보드/월말결산용 — 기존 기능 유지) */
    List<IpHistory> findByApprovedFalseOrderByCreatedAtDesc();

    /** 미품의(approved=false) 변경 이력 (최신순, 사업장별) — PC 관리 화면(청주/서울 탭 분리)용 */
    List<IpHistory> findByApprovedFalseAndIpAddress_SiteOrderByCreatedAtDesc(IpSite site);

    /** 미품의 변경 건수 (대시보드 알림용) */
    long countByApprovedFalse();

    // 월말 결산용 (기간, 전체 사업장 — 기존 기능 유지)
    /** 기간 내 IP 변경 이력 */
    List<IpHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** 기간 내 미품의 IP 변경 이력 */
    List<IpHistory> findByApprovedFalseAndCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** 기간 내 IP 변경 이력 (사업장별) — PC 관리 화면(당월/월별 변경 내역)용 */
    List<IpHistory> findByCreatedAtBetweenAndIpAddress_SiteOrderByCreatedAtDesc(
            java.time.LocalDateTime from, java.time.LocalDateTime to, IpSite site);

    /** 변경 이력이 존재하는 (연,월) 목록: [Year, Month] 내림차순 (전체 사업장 — 기존 기능 유지) */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT YEAR(h.createdAt), MONTH(h.createdAt) FROM IpHistory h "
            + "ORDER BY YEAR(h.createdAt) DESC, MONTH(h.createdAt) DESC")
    List<Object[]> findDistinctYearMonths();

    /** 변경 이력이 존재하는 (연,월) 목록: [Year, Month] 내림차순, 사업장별 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT YEAR(h.createdAt), MONTH(h.createdAt) FROM IpHistory h "
            + "WHERE h.ipAddress.site = :site "
            + "ORDER BY YEAR(h.createdAt) DESC, MONTH(h.createdAt) DESC")
    List<Object[]> findDistinctYearMonthsBySite(@org.springframework.data.repository.query.Param("site") IpSite site);

    /**
     * 이력 검색 — 기준(field) 별 부분일치, 사업장별.
     * <ul>
     *   <li>user: 변경 전/후 사용자 (본인이 실제 관여한 이력만 — 현재 소유 장비의 이전 소유자 이력은 제외)</li>
     *   <li>serial: 장비 제조번호</li>
     *   <li>ip: IP 주소</li>
     * </ul>
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT h FROM IpHistory h
            WHERE h.ipAddress.site = :site
              AND (
                (:field = 'user' AND (h.beforeUser LIKE CONCAT('%', :keyword, '%')
                                     OR h.afterUser LIKE CONCAT('%', :keyword, '%')))
               OR (:field = 'serial' AND h.ipAddress.serialNo LIKE CONCAT('%', :keyword, '%'))
               OR (:field = 'ip' AND (h.snapshotIp LIKE CONCAT('%', :keyword, '%')
                                   OR h.ipAddress.ipAddress LIKE CONCAT('%', :keyword, '%')))
              )
            ORDER BY h.createdAt DESC
            """)
    List<IpHistory> searchHistory(@org.springframework.data.repository.query.Param("field") String field,
                                  @org.springframework.data.repository.query.Param("keyword") String keyword,
                                  @org.springframework.data.repository.query.Param("site") IpSite site,
                                  org.springframework.data.domain.Pageable pageable);
}
