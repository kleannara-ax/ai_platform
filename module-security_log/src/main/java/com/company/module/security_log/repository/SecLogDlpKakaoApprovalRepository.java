package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpKakaoApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogDlpKakaoApprovalRepository extends JpaRepository<SecLogDlpKakaoApproval, Long> {

    List<SecLogDlpKakaoApproval> findByDeletedYnOrderByEndDateDescKakaoApprovalIdDesc(String deletedYn);

    Optional<SecLogDlpKakaoApproval> findByKakaoApprovalIdAndDeletedYn(Long kakaoApprovalId, String deletedYn);
}
