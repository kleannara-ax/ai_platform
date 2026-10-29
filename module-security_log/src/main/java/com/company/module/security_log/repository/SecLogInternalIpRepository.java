package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogInternalIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogInternalIpRepository extends JpaRepository<SecLogInternalIp, Long> {

    Optional<SecLogInternalIp> findByInternalIpIdAndDeletedYn(Long internalIpId, String deletedYn);

    /** UK(IP_RANGE)는 삭제된 행도 포함 → 재등록 시 복구용 */
    Optional<SecLogInternalIp> findByIpRange(String ipRange);

    List<SecLogInternalIp> findByDeletedYnOrderBySortOrderAscStartIpNumAsc(String deletedYn);

    List<SecLogInternalIp> findByDeletedYnAndUseYnOrderBySortOrderAscStartIpNumAsc(String deletedYn, String useYn);
}
