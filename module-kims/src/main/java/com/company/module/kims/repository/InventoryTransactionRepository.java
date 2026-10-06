package com.company.module.kims.repository;

import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 특정 품목의 재고 변동 이력을 최신순으로 조회한다. */
    List<InventoryTransaction> findByInventoryItem_ItemIdOrderByCreatedAtDesc(Long itemId);

    /** 전체 입출고 이력 검색 (기간/품목/구분/담당자 + 페이징, 최신순) */
    @Query(value = """
            SELECT t FROM InventoryTransaction t JOIN FETCH t.inventoryItem i
            WHERE (:from IS NULL OR t.createdAt >= :from)
              AND (:to   IS NULL OR t.createdAt <= :to)
              AND (:itemId IS NULL OR i.itemId = :itemId)
              AND (:type IS NULL OR t.transactionType = :type)
              AND (:createdBy IS NULL OR t.createdBy LIKE CONCAT('%', :createdBy, '%'))
            ORDER BY t.createdAt DESC, t.transactionId DESC
            """,
            countQuery = """
            SELECT COUNT(t) FROM InventoryTransaction t
            WHERE (:from IS NULL OR t.createdAt >= :from)
              AND (:to   IS NULL OR t.createdAt <= :to)
              AND (:itemId IS NULL OR t.inventoryItem.itemId = :itemId)
              AND (:type IS NULL OR t.transactionType = :type)
              AND (:createdBy IS NULL OR t.createdBy LIKE CONCAT('%', :createdBy, '%'))
            """)
    Page<InventoryTransaction> search(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("itemId") Long itemId,
                                      @Param("type") TransactionType type,
                                      @Param("createdBy") String createdBy,
                                      Pageable pageable);
}
