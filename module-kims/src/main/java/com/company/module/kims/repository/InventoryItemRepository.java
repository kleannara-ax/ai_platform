package com.company.module.kims.repository;

import com.company.module.kims.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /** 품목명 중복 확인 */
    boolean existsByItemName(String itemName);

    /**
     * 품목 목록 검색 (품목명 부분일치 / 카테고리, 둘 다 선택적).
     */
    @Query("""
            SELECT i FROM InventoryItem i
            WHERE (:keyword  IS NULL OR i.itemName LIKE CONCAT('%', :keyword, '%'))
              AND (:category IS NULL OR i.category = :category)
            ORDER BY i.itemName ASC
            """)
    Page<InventoryItem> search(@Param("keyword") String keyword,
                               @Param("category") String category,
                               Pageable pageable);

    /**
     * 재고부족(품절) 품목 조회 — 업무 정책상 <b>재고가 0</b>인 품목만 대상.
     */
    @Query("SELECT i FROM InventoryItem i WHERE i.currentStock <= 0 ORDER BY i.itemName ASC")
    List<InventoryItem> findLowStockItems();
}
