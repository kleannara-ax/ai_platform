package com.company.module.code.repository;

import com.company.module.code.entity.CodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeDetailRepository extends JpaRepository<CodeDetail, Long> {

    List<CodeDetail> findByGroup_GroupIdOrderBySortOrderAsc(Long groupId);

    List<CodeDetail> findByGroup_GroupIdAndIsActiveTrueOrderBySortOrderAsc(Long groupId);

    List<CodeDetail> findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc(String groupCode);

    boolean existsByGroup_GroupIdAndCode(Long groupId, String code);

    boolean existsByGroup_GroupIdAndCodeAndCodeIdNot(Long groupId, String code, Long codeId);
}
