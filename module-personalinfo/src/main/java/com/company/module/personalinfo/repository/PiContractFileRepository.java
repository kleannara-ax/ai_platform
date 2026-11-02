package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiContractFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiContractFileRepository extends JpaRepository<PiContractFile, Long> {

    List<PiContractFile> findByContractIdAndDeletedYnOrderByFileIdDesc(Long contractId, String deletedYn);

    /** 삭제되지 않은 위수탁 계약서 중 체결일이 가장 최근인 파일 (같은 날짜면 나중에 올린 파일) */
    Optional<PiContractFile> findFirstByContractIdAndFileTypeAndDeletedYnAndSignedDateIsNotNullOrderBySignedDateDescFileIdDesc(
            Long contractId, com.company.module.personalinfo.entity.ContractFileType fileType, String deletedYn);

    Optional<PiContractFile> findByFileIdAndDeletedYn(Long fileId, String deletedYn);

    long countByContractIdAndDeletedYn(Long contractId, String deletedYn);
}
