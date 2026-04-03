package com.company.module.user.repository;

import com.company.module.user.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 부서 Repository
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDeptCode(String deptCode);

    boolean existsByDeptCode(String deptCode);

    List<Department> findByParentDeptIdOrderBySortOrder(Long parentDeptId);

    List<Department> findByEnabledTrueOrderBySortOrder();
}
