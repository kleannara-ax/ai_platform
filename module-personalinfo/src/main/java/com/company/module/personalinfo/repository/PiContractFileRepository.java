package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiContractFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PiContractFileRepository extends JpaRepository<PiContractFile, Long> {

    List<PiContractFile> findByContractIdAndDeletedYnOrderByFileIdDesc(Long contractId, String deletedYn);

    Optional<PiContractFile> findByFileIdAndDeletedYn(Long fileId, String deletedYn);

    long countByContractIdAndDeletedYn(Long contractId, String deletedYn);
}
