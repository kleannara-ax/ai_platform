package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpMailSendLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecLogDlpMailSendLogRepository extends JpaRepository<SecLogDlpMailSendLog, Long> {

    List<SecLogDlpMailSendLog> findByMailRuleIdAndDeletedYn(Long mailRuleId, String deletedYn);

    List<SecLogDlpMailSendLog> findTop300ByDeletedYnOrderByLastTryAtDescMailSendLogIdDesc(String deletedYn);

    List<SecLogDlpMailSendLog> findTop300ByMailRuleIdAndDeletedYnOrderByLastTryAtDescMailSendLogIdDesc(Long mailRuleId, String deletedYn);
}
