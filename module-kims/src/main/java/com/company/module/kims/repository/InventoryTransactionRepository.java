package com.company.module.kims.repository;

import com.company.module.kims.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 특정 품목의 재고 변동 이력을 최신순으로 조회한다. */
    List<InventoryTransaction> findByInventoryItem_ItemIdOrderByCreatedAtDesc(Long itemId);
}
