package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.AccessActionType;
import com.company.module.personalinfo.entity.PiAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PiAccessLogRepository extends JpaRepository<PiAccessLog, Long> {

    /** 접근 기록 검색 (문자열 조건은 빈 문자열이면 적용 안 함) */
    @Query("""
            select a from PiAccessLog a
             where a.deletedYn = 'N'
               and a.createdAt >= :from and a.createdAt < :to
               and (:actionOn = 0 or a.actionType = :actionType)
               and (:contractId is null or a.contractId = :contractId)
               and (:user = '' or a.userName like concat('%', :user, '%') or a.userLoginId like concat('%', :user, '%'))
               and (:vendor = '' or a.vendorName like concat('%', :vendor, '%') or a.contractName like concat('%', :vendor, '%')
                                 or a.targetName like concat('%', :vendor, '%'))
             order by a.accessId desc
            """)
    Page<PiAccessLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                             @Param("actionOn") int actionOn, @Param("actionType") AccessActionType actionType,
                             @Param("contractId") Long contractId, @Param("user") String user,
                             @Param("vendor") String vendor, Pageable pageable);
}
