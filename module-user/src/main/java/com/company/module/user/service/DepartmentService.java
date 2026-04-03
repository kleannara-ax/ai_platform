package com.company.module.user.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.user.dto.DepartmentCreateRequest;
import com.company.module.user.dto.DepartmentResponse;
import com.company.module.user.entity.Department;
import com.company.module.user.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 부서 관리 서비스
 * Core의 @Transactional 정책을 동일하게 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * 부서 생성
     */
    @Transactional
    public DepartmentResponse createDepartment(DepartmentCreateRequest request) {
        if (departmentRepository.existsByDeptCode(request.getDeptCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이미 존재하는 부서 코드입니다: " + request.getDeptCode());
        }

        Department department = Department.builder()
                .deptName(request.getDeptName())
                .deptCode(request.getDeptCode())
                .parentDeptId(request.getParentDeptId())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .enabled(true)
                .build();

        Department saved = departmentRepository.save(department);
        log.info("부서 생성 완료: deptCode={}", saved.getDeptCode());

        return DepartmentResponse.from(saved);
    }

    /**
     * 부서 단건 조회
     */
    public DepartmentResponse getDepartment(Long deptId) {
        Department department = findDepartmentById(deptId);
        return DepartmentResponse.from(department);
    }

    /**
     * 활성화된 전체 부서 목록 조회
     */
    public List<DepartmentResponse> getAllActiveDepartments() {
        return departmentRepository.findByEnabledTrueOrderBySortOrder()
                .stream()
                .map(DepartmentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 하위 부서 목록 조회
     */
    public List<DepartmentResponse> getChildDepartments(Long parentDeptId) {
        return departmentRepository.findByParentDeptIdOrderBySortOrder(parentDeptId)
                .stream()
                .map(DepartmentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 부서 비활성화
     */
    @Transactional
    public void disableDepartment(Long deptId) {
        Department department = findDepartmentById(deptId);
        department.disable();
        log.info("부서 비활성화: deptId={}", deptId);
    }

    // ── 내부 헬퍼 ──

    private Department findDepartmentById(Long deptId) {
        return departmentRepository.findById(deptId)
                .orElseThrow(() -> new EntityNotFoundException("Department", deptId));
    }
}
