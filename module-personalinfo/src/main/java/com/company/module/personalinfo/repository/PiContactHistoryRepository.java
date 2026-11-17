package com.company.module.personalinfo.repository;

import com.company.module.personalinfo.entity.PiContactHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PiContactHistoryRepository extends JpaRepository<PiContactHistory, Long> {

    List<PiContactHistory> findByContractIdInAndDeletedYnOrderByCreatedAtDescHistoryIdDesc(
            Collection<Long> contractIds, String deletedYn);
}
