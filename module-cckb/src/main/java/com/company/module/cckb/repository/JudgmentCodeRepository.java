package com.company.module.cckb.repository;

import com.company.module.cckb.entity.JudgmentCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JudgmentCodeRepository extends JpaRepository<JudgmentCode, String> {

    List<JudgmentCode> findByDeletedYnOrderBySortOrderAsc(String deletedYn);
}
