package com.company.app.config;

import com.company.core.permission.support.RoleProvider;
import com.company.module.code.entity.CodeDetail;
import com.company.module.code.repository.CodeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공통코드(ROLE 그룹) 기반 RoleProvider 구현체.
 * 공통코드 관리 화면에서 역할을 추가/수정하면 자동 반영된다.
 */
@Component
@RequiredArgsConstructor
public class CommonCodeRoleProvider implements RoleProvider {

    private final CodeDetailRepository codeDetailRepo;

    @Override
    public List<String> getActiveRoles() {
        return codeDetailRepo
                .findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc("ROLE")
                .stream()
                .map(CodeDetail::getCode)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> getRoleNameMap() {
        return codeDetailRepo
                .findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc("ROLE")
                .stream()
                .collect(Collectors.toMap(
                        CodeDetail::getCode,
                        CodeDetail::getCodeName,
                        (a, b) -> a));
    }
}
