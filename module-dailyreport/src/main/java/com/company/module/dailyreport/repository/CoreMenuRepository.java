package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.CoreMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI 플랫폼 메뉴 마스터 리포지토리 (★ Phase 4 — 읽기 전용 참조)
 */
public interface CoreMenuRepository extends JpaRepository<CoreMenu, Long> {

    /** 메뉴 코드로 조회 */
    Optional<CoreMenu> findByMenuCode(String menuCode);

    /** 상위 메뉴 ID로 하위 메뉴 조회 */
    List<CoreMenu> findByParentMenuIdAndIsActiveTrueOrderBySortOrder(Long parentMenuId);

    /** 활성 메뉴 전체 조회 */
    List<CoreMenu> findByIsActiveTrueOrderBySortOrder();
}
