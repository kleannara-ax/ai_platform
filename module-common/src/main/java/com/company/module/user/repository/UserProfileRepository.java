package com.company.module.user.repository;

import com.company.module.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 프로필 Repository
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    List<UserProfile> findByDeptCode(String deptCode);

    boolean existsByUserId(Long userId);

    Optional<UserProfile> findByEmployeeNo(String employeeNo);
}
