package com.company.module.code.repository;

import com.company.module.code.entity.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, Long> {

    List<CodeGroup> findAllByOrderBySortOrderAscGroupCodeAsc();

    List<CodeGroup> findByIsActiveTrueOrderBySortOrderAscGroupCodeAsc();

    Optional<CodeGroup> findByGroupCode(String groupCode);

    boolean existsByGroupCode(String groupCode);
}
