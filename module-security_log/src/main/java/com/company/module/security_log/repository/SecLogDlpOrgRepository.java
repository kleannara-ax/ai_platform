package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpOrg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogDlpOrgRepository extends JpaRepository<SecLogDlpOrg, Long> {

    List<SecLogDlpOrg> findByDeletedYnOrderByOrgNameAsc(String deletedYn);

    Optional<SecLogDlpOrg> findByOrgIdAndDeletedYn(Long orgId, String deletedYn);

    Optional<SecLogDlpOrg> findByParentOrgIdAndOrgName(Long parentOrgId, String orgName);
}
