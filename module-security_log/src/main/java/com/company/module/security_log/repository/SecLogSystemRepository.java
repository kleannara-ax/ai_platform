package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogSystemRepository extends JpaRepository<SecLogSystem, Long> {

    Optional<SecLogSystem> findBySystemIdAndDeletedYn(Long systemId, String deletedYn);

    boolean existsBySystemCode(String systemCode);

    List<SecLogSystem> findByDeletedYnOrderBySortOrderAscSystemIdAsc(String deletedYn);

    List<SecLogSystem> findByDeletedYnAndUseYnOrderBySortOrderAscSystemIdAsc(String deletedYn, String useYn);
}
