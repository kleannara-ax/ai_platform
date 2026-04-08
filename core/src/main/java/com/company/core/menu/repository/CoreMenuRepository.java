package com.company.core.menu.repository;

import com.company.core.menu.entity.CoreMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CoreMenuRepository extends JpaRepository<CoreMenu, Long> {
    List<CoreMenu> findByIsActiveTrueOrderBySortOrder();
    List<CoreMenu> findByParentIdAndIsActiveTrueOrderBySortOrder(Long parentId);
    List<CoreMenu> findByMenuIdInAndIsActiveTrueOrderBySortOrder(List<Long> menuIds);
    Optional<CoreMenu> findByMenuCode(String menuCode);
    boolean existsByMenuCode(String menuCode);
    List<CoreMenu> findByParentIdOrderBySortOrder(Long parentId);
    List<CoreMenu> findAllByOrderBySortOrder();
    long countByParentId(Long parentId);
}
