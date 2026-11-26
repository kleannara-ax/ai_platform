package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogUploadFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecLogUploadFileRepository extends JpaRepository<SecLogUploadFile, Long> {

    Optional<SecLogUploadFile> findByUploadIdAndDeletedYn(Long uploadId, String deletedYn);
}
