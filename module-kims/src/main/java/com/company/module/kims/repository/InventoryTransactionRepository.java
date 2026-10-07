package com.company.module.kims.repository;

import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 특정 품목의 입출고 이력을 입출고일 최신순으로 조회한다. */
    List<InventoryTransaction> findByInventoryItem_ItemIdOrderByTransactionDateDescTransactionIdDesc(Long itemId);

    /**
     * 전체 입출고 이력 검색 (기간/품목/구분/담당자 + 페이징, 입출고일 최신순).
     * <p>기간은 입출고일(모달에서 입력한 입고일·출고일) 기준. 담당자는 이력 담당자 또는 지급 담당자.
     */
    @Query(value = """
            SELECT t FROM InventoryTransaction t JOIN FETCH t.inventoryItem i
              LEFT JOIN FETCH t.supplyIssue s LEFT JOIN FETCH s.serviceRequest
            WHERE (:from IS NULL OR t.transactionDate >= :from)
              AND (:to   IS NULL OR t.transactionDate <= :to)
              AND (:itemId IS NULL OR i.itemId = :itemId)
              AND (:type IS NULL OR t.transactionType = :type)
              AND (:createdBy IS NULL OR t.createdBy LIKE CONCAT('%', :createdBy, '%')
                   OR s.issuedBy LIKE CONCAT('%', :createdBy, '%'))
            ORDER BY t.transactionDate DESC, t.transactionId DESC
            """,
            countQuery = """
            SELECT COUNT(t) FROM InventoryTransaction t LEFT JOIN t.supplyIssue s
            WHERE (:from IS NULL OR t.transactionDate >= :from)
              AND (:to   IS NULL OR t.transactionDate <= :to)
              AND (:itemId IS NULL OR t.inventoryItem.itemId = :itemId)
              AND (:type IS NULL OR t.transactionType = :type)
              AND (:createdBy IS NULL OR t.createdBy LIKE CONCAT('%', :createdBy, '%')
                   OR s.issuedBy LIKE CONCAT('%', :createdBy, '%'))
            """)
    Page<InventoryTransaction> search(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("itemId") Long itemId,
                                      @Param("type") TransactionType type,
                                      @Param("createdBy") String createdBy,
                                      Pageable pageable);
}
