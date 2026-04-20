package com.company.module.psinsp.repository;

import com.company.module.psinsp.entity.PsInspCodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 공통코드 그룹(code_group) 접근 Repository (PS-INSP 전용)
 */
public interface PsInspCodeGroupRepository extends JpaRepository<PsInspCodeGroup, Long> {

    Optional<PsInspCodeGroup> findByGroupCode(String groupCode);
}
