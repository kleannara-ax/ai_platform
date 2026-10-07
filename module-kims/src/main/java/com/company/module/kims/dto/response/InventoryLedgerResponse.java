package com.company.module.kims.dto.response;

import com.company.module.kims.entity.InventoryTransaction;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.SupplyIssue;
import com.company.module.kims.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소모품 입출고 이력(전체 품목) 응답 DTO.
 * <p>출고 이력은 연결된 지급 내역에서 요청자·지급대상자·부서·지급 담당자를 가져온다.
 * 지급 내역과 연결되지 않은 예전 출고 이력은 비고에 남은 업무요청 번호/요청자명으로 보완한다.
 */
@Getter
@Builder
public class InventoryLedgerResponse {

    private final Long transactionId;
    /** 입출고일 (입고일/출고일) */
    private final LocalDate transactionDate;
    private final LocalDateTime createdAt;
    private final TransactionType transactionType;
    private final String transactionTypeLabel;

    private final Long itemId;
    private final String itemName;
    private final String category;

    private final int quantity;
    private final int beforeStock;
    private final int afterStock;
    /** 세부 구분 (신형/구형/레노버/갤럭시, 없으면 null) */
    private final String subType;
    /** 담당자 (입고 담당자 / 지급 담당자) */
    private final String createdBy;
    /** 비고 (메모) */
    private final String note;

    /** 연결된 지급 내역 ID (출고만, 예전 이력은 null) */
    private final Long issueId;
    private final Long requestId;
    private final String requestNo;
    private final String requesterName;
    /** 지급 대상자 (출고만) */
    private final String receiverName;
    private final String department;

    /**
     * @param legacyRequest   지급 내역과 연결되지 않은 예전 출고의 비고 요청번호로 찾은 업무요청 (없으면 null)
     * @param legacyRequester 지급 내역과 연결되지 않은 예전 직접 지급 출고의 비고 요청자명 (없으면 null)
     */
    public static InventoryLedgerResponse of(InventoryTransaction t, ServiceRequest legacyRequest, String legacyRequester) {
        SupplyIssue issue = t.getSupplyIssue();
        ServiceRequest request = (issue != null) ? issue.getServiceRequest() : legacyRequest;
        String requester = (issue != null) ? issue.getRequesterName()
                : (request != null ? request.getRequesterName() : legacyRequester);
        String department = (issue != null) ? issue.getDepartment()
                : (request != null ? request.getDepartment() : null);
        return InventoryLedgerResponse.builder()
                .transactionId(t.getTransactionId())
                .transactionDate(t.getTransactionDate() != null ? t.getTransactionDate()
                        : (t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null))
                .createdAt(t.getCreatedAt())
                .transactionType(t.getTransactionType())
                .transactionTypeLabel(t.getTransactionType().getLabel())
                .itemId(t.getInventoryItem().getItemId())
                .itemName(t.getInventoryItem().getItemName())
                .category(t.getInventoryItem().getCategory())
                .quantity(t.getQuantity())
                .beforeStock(t.getBeforeStock())
                .afterStock(t.getAfterStock())
                .subType(t.getSubType() != null ? t.getSubType() : (issue != null ? issue.getSubType() : null))
                .createdBy(issue != null ? issue.getIssuedBy() : t.getCreatedBy())
                .note(t.getNote())
                .issueId(issue != null ? issue.getIssueId() : null)
                .requestId(request != null ? request.getRequestId() : null)
                .requestNo(request != null ? request.getRequestNo() : null)
                .requesterName(requester)
                .receiverName(issue != null ? issue.getReceiverName() : null)
                .department(department)
                .build();
    }
}
