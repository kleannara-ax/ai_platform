package com.company.module.cckb.repository;

import com.company.module.cckb.entity.StandardAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StandardAuditLogRepository extends JpaRepository<StandardAuditLog, Long> {

    List<StandardAuditLog> findByStandard_StandardIdOrderByChangedAtDesc(Long standardId);
}
