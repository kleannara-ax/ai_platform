package com.company.module.kims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 소모품 지급 내역.
 * <p>업무 요청(ServiceRequest)과 연결하여, 어떤 품목을 누구에게 몇 개 지급했는지 기록한다.
 * 지급이 등록되면 해당 품목의 재고가 차감된다(차감 로직은 Service 계층에서 처리).
 */
@Entity
@Table(name = "supply_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyIssue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ISSUE_ID")
    private Long issueId;

    /** 연결된 업무 요청 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID", nullable = false)
    private ServiceRequest serviceRequest;

    /** 지급된 소모품 품목 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ITEM_ID", nullable = false)
    private InventoryItem inventoryItem;

    /** 지급 수량 */
    @Column(name = "QUANTITY", nullable = false)
    private int quantity;

    /** 지급 대상자 */
    @Column(name = "RECEIVER_NAME", nullable = false, length = 50)
    private String receiverName;

    /** 대상자 부서 */
    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    /** 지급 담당자 */
    @Column(name = "ISSUED_BY", nullable = false, length = 50)
    private String issuedBy;

    /** 지급일 */
    @Column(name = "ISSUED_AT", nullable = false)
    private LocalDate issuedAt;

    /**
     * 세부 구분(신형/구형, 제조사 등). 구분이 있는 품목을 지급했을 때만 값이 채워진다.
     * <p>지급 취소/원복(reverseByRequest) 시 이 값을 기준으로 품목 비고(remark)의
     * 해당 구분 수치를 정확히 되돌리기 위해 저장해 둔다.
     */
    @Column(name = "SUB_TYPE", length = 20)
    private String subType;

    @Builder
    private SupplyIssue(ServiceRequest serviceRequest, InventoryItem inventoryItem,
                        int quantity, String receiverName, String department,
                        String issuedBy, LocalDate issuedAt, String subType) {
        this.serviceRequest = serviceRequest;
        this.inventoryItem = inventoryItem;
        this.quantity = quantity;
        this.receiverName = receiverName;
        this.department = department;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
        this.subType = subType;
    }
}
