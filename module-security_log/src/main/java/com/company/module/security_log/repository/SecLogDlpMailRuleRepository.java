package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpMailRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogDlpMailRuleRepository extends JpaRepository<SecLogDlpMailRule, Long> {

    List<SecLogDlpMailRule> findByDeletedYnOrderByMailRuleIdAsc(String deletedYn);

    Optional<SecLogDlpMailRule> findByMailRuleIdAndDeletedYn(Long mailRuleId, String deletedYn);
}
