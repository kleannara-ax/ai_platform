package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecLogDlpReportRepository extends JpaRepository<SecLogDlpReport, Long> {

    Optional<SecLogDlpReport> findByTargetYmAndDeletedYn(String targetYm, String deletedYn);

    /** 이전 달 표지 정보 (그 달 값이 없을 때 이어서 사용) */
    Optional<SecLogDlpReport> findFirstByTargetYmLessThanAndDeletedYnOrderByTargetYmDesc(String targetYm, String deletedYn);
}
