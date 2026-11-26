package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpLeakFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecLogDlpLeakFileRepository extends JpaRepository<SecLogDlpLeakFile, Long> {

    Optional<SecLogDlpLeakFile> findByLeakUploadIdAndDeletedYn(Long leakUploadId, String deletedYn);
}
