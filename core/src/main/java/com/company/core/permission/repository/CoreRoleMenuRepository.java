package com.company.core.permission.repository;

import com.company.core.permission.entity.CoreRoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CoreRoleMenuRepository extends JpaRepository<CoreRoleMenu, Long> {
    List<CoreRoleMenu> findByRole(String role);

    @Modifying
    @Query("DELETE FROM CoreRoleMenu r WHERE r.role = :role")
    void deleteByRole(String role);

    @Modifying
    @Query("DELETE FROM CoreRoleMenu r WHERE r.role = :role AND r.menuId = :menuId")
    void deleteByRoleAndMenuId(String role, Long menuId);

    @Modifying
    @Query("DELETE FROM CoreRoleMenu r WHERE r.menuId = :menuId")
    void deleteByMenuId(Long menuId);
}
