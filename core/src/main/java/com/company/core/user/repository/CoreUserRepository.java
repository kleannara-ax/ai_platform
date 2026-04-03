package com.company.core.user.repository;

import com.company.core.user.entity.CoreUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 사용자 Repository (Core)
 */
@Repository
public interface CoreUserRepository extends JpaRepository<CoreUser, Long> {

    Optional<CoreUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    Optional<CoreUser> findByEmail(String email);
}
