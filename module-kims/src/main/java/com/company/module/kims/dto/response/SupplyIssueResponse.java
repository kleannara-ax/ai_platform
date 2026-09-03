package com.company.module.kims.dto.response;

import com.company.module.kims.entity.SupplyIssue;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소모품 지급 내역 응답 DTO.
 */
@Getter
@Builder
public class SupplyIssueResponse {

    private final Long issueId;

    private final Long requestId;
    private final String requestNo;   // 연결된 요청번호

    private final Long itemId;
    private final String itemName;    // 지급 품목명

    private final int quantity;
    private final String receiverName;
    private final String department;
    private final String issuedBy;
    private final LocalDate issuedAt;
    private final String subType;   // 세부 구분 (신형/구형/레노버/갤럭시/대여 등, 없으면 null)
    private final LocalDateTime createdAt;

    /**
     * Entity → Response 변환.
     * <p>연관 엔티티(serviceRequest, inventoryItem)에 접근하므로
     * 반드시 트랜잭션(영속성 컨텍스트)이 살아있는 Service 안에서 호출해야 한다.
     */
    public static SupplyIssueResponse from(SupplyIssue entity) {
        return SupplyIssueResponse.builder()
                .issueId(entity.getIssueId())
                .requestId(entity.getServiceRequest().getRequestId())
                .requestNo(entity.getServiceRequest().getRequestNo())
                .itemId(entity.getInventoryItem().getItemId())
                .itemName(entity.getInventoryItem().getItemName())
                .quantity(entity.getQuantity())
                .receiverName(entity.getReceiverName())
                .department(entity.getDepartment())
                .issuedBy(entity.getIssuedBy())
                .issuedAt(entity.getIssuedAt())
                .subType(entity.getSubType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
