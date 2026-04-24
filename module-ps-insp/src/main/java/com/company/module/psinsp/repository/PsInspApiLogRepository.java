package com.company.module.psinsp.repository;

import com.company.module.psinsp.entity.PsInspApiLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PS 지분검사 API 통신 로그 Repository
 */
public interface PsInspApiLogRepository extends JpaRepository<PsInspApiLog, Long> {
}
