package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PiContractRepository extends JpaRepository<PiContract, Long> {

    Optional<PiContract> findByContractIdAndDeletedYn(Long contractId, String deletedYn);

    List<PiContract> findByDeletedYnOrderByContractIdAsc(String deletedYn);

    /**
     * 계약 목록 검색. 삭제(DELETED_YN='Y') 데이터는 제외한다.
     *
     * <p>문자열 조건은 빈 문자열("")이면 조건을 적용하지 않는다.
     * 계약 상태는 DB 컬럼이 아니라 종료일/종료여부/알림기준일로 계산한다.
     * ({@link com.company.module.personalinfo.entity.ContractStatus} 와 같은 규칙)
     * DATEDIFF 는 MariaDB 함수이다(플랫폼 DB 고정).
     *
     * <p>기준 연도 조건(yearOn=1)은 {@link com.company.module.personalinfo.service.PiYearScope} 와 같은 규칙이다.
     * 대상 = 시작일 ≤ 연말 이고 종료 기준일(종료 처리일 또는 원계약 종료일) ≥ 전년도 12/31,
     * ENDED = 종료 기준일 &lt; 기준일(오늘을 그 연도 안으로 맞춘 날), ACTIVE = 그 밖.</p>
     */
    @Query("""
            select c from PiContract c
             where c.deletedYn = 'N'
               and (:keyword = '' or c.vendorName like concat('%', :keyword, '%')
                                  or c.contractName like concat('%', :keyword, '%')
                                  or c.serviceName like concat('%', :keyword, '%')
                                  or c.entrustedTask like concat('%', :keyword, '%')
                                  or c.subcontractorName like concat('%', :keyword, '%'))
               and (:deptName = '' or c.deptName like concat('%', :deptName, '%'))
               and (:managerName = '' or c.managerName like concat('%', :managerName, '%'))
               and (:ismsScopeYn = '' or c.ismsScopeYn = :ismsScopeYn)
               and (:inspectionResult = ''
                    or (:inspectionResult = 'NONE' and c.lastInspectionResult is null)
                    or c.lastInspectionResult = :inspectionResult)
               and (:endFrom is null or c.contractEndDate >= :endFrom)
               and (:endTo is null or c.contractEndDate <= :endTo)
               and (:status = ''
                    or (:status = 'TERMINATED'  and c.terminatedYn = 'Y')
                    or (:status = 'NO_END_DATE' and c.terminatedYn = 'N' and c.contractEndDate is null)
                    or (:status = 'EXPIRED'     and c.terminatedYn = 'N' and c.contractEndDate < :today)
                    or (:status = 'EXPIRING'    and c.terminatedYn = 'N' and c.contractEndDate >= :today
                                                and function('DATEDIFF', c.contractEndDate, :today) <= c.alertDaysBefore)
                    or (:status = 'ACTIVE'      and c.terminatedYn = 'N' and c.contractEndDate >= :today
                                                and function('DATEDIFF', c.contractEndDate, :today) > c.alertDaysBefore))
               and (:yearOn = 0 or (
                        (c.contractStartDate is null or c.contractStartDate <= :yEnd)
                    and ((c.terminatedYn = 'N' and (c.contractEndDate is null or c.contractEndDate >= :prevEnd))
                         or (c.terminatedYn = 'Y' and ((c.terminatedDate is not null and c.terminatedDate >= :prevEnd)
                              or (c.terminatedDate is null and (c.contractEndDate is null or c.contractEndDate >= :prevEnd)))))))
               and (:yearStatus = ''
                    or (:yearStatus = 'ENDED' and ((c.terminatedYn = 'N' and c.contractEndDate is not null and c.contractEndDate < :refDate)
                              or (c.terminatedYn = 'Y' and ((c.terminatedDate is not null and c.terminatedDate < :refDate)
                                   or (c.terminatedDate is null and (c.contractEndDate is null or c.contractEndDate < :refDate))))))
                    or (:yearStatus = 'ACTIVE' and not ((c.terminatedYn = 'N' and c.contractEndDate is not null and c.contractEndDate < :refDate)
                              or (c.terminatedYn = 'Y' and ((c.terminatedDate is not null and c.terminatedDate < :refDate)
                                   or (c.terminatedDate is null and (c.contractEndDate is null or c.contractEndDate < :refDate)))))))
               and (:yearCheck = ''
                    or (:yearCheck = 'INSPECTED' and exists (select 1 from PiInspection i where i.contractId = c.contractId
                            and i.deletedYn = 'N' and i.inspectionType = :regularType and i.inspectionYear = :baseYear))
                    or (:yearCheck = 'NOT_INSPECTED' and not exists (select 1 from PiInspection i where i.contractId = c.contractId
                            and i.deletedYn = 'N' and i.inspectionType = :regularType and i.inspectionYear = :baseYear))
                    or (:yearCheck = 'TERM_UPLOADED' and exists (select 1 from PiInspection i where i.contractId = c.contractId
                            and i.deletedYn = 'N' and i.inspectionType = :terminationType))
                    or (:yearCheck = 'TERM_MISSING' and not exists (select 1 from PiInspection i where i.contractId = c.contractId
                            and i.deletedYn = 'N' and i.inspectionType = :terminationType)))
            """)
    Page<PiContract> search(@Param("keyword") String keyword,
                            @Param("deptName") String deptName,
                            @Param("managerName") String managerName,
                            @Param("ismsScopeYn") String ismsScopeYn,
                            @Param("inspectionResult") String inspectionResult,
                            @Param("endFrom") LocalDate endFrom,
                            @Param("endTo") LocalDate endTo,
                            @Param("status") String status,
                            @Param("today") LocalDate today,
                            @Param("yearOn") int yearOn,
                            @Param("baseYear") int baseYear,
                            @Param("prevEnd") LocalDate prevEnd,
                            @Param("yEnd") LocalDate yEnd,
                            @Param("refDate") LocalDate refDate,
                            @Param("yearStatus") String yearStatus,
                            @Param("yearCheck") String yearCheck,
                            @Param("regularType") InspectionType regularType,
                            @Param("terminationType") InspectionType terminationType,
                            Pageable pageable);

    /** 알림 대상 후보: 미종료 + 알림 사용 + 종료일이 기간 안에 있는 계약 */
    @Query("""
            select c from PiContract c
             where c.deletedYn = 'N'
               and c.terminatedYn = 'N'
               and c.alertYn = 'Y'
               and c.contractEndDate is not null
               and c.contractEndDate between :from and :to
             order by c.contractEndDate asc
            """)
    List<PiContract> findAlertCandidates(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
