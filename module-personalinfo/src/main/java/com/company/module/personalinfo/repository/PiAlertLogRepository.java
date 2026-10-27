package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiAlertLogRepository extends JpaRepository<PiAlertLog, Long> {

    Optional<PiAlertLog> findByDedupKey(String dedupKey);

    List<PiAlertLog> findTop20ByContractIdAndDeletedYnOrderByAlertIdDesc(Long contractId, String deletedYn);
}
