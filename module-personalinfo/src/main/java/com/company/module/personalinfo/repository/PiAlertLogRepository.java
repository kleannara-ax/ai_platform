package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiAlertLogRepository extends JpaRepository<PiAlertLog, Long> {

    Optional<PiAlertLog> findByDedupKey(String dedupKey);

    List<PiAlertLog> findTop20ByContractIdAndDeletedYnOrderByAlertIdDesc(Long contractId, String deletedYn);

    /** 같은 대상의 독촉 메일 발송 횟수 (DEDUP_KEY 접두어 기준) */
    long countByDedupKeyStartingWithAndSendStatus(String dedupKeyPrefix,
                                                  com.company.module.personalinfo.entity.AlertSendStatus sendStatus);

    List<PiAlertLog> findTop50ByAlertTypeInAndDeletedYnOrderByAlertIdDesc(
            java.util.Collection<com.company.module.personalinfo.entity.AlertType> types, String deletedYn);
}
