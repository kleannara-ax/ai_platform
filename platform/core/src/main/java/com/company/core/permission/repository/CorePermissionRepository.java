package com.company.core.permission.repository;

import com.company.core.permission.entity.CorePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CorePermissionRepository extends JpaRepository<CorePermission, Long> {
    List<CorePermission> findByIsActiveTrueOrderByPermCode();
    Optional<CorePermission> findByPermCode(String permCode);
    boolean existsByPermCode(String permCode);
}
