package com.company.module.kims.dto.response;

import com.company.module.kims.entity.InternetWork;
import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인터넷 공사 응답 DTO.
 */
@Getter
@Builder
public class InternetWorkResponse {

    private final Long workId;
    private final Long requestId;
    private final String requestNo;

    private final InternetWorkType workType;
    private final String workTypeLabel;

    private final String requesterName;
    private final String department;
    private final String location;
    private final String content;

    private final boolean externalVendor;
    private final String vendorName;
    private final boolean hasCost;
    private final Long cost;

    private final String assignee;
    private final InternetWorkStatus status;
    private final String statusLabel;
    private final LocalDate completedAt;
    private final String remark;
    private final LocalDateTime createdAt;

    public static InternetWorkResponse from(InternetWork e) {
        boolean linked = e.getServiceRequest() != null;
        return InternetWorkResponse.builder()
                .workId(e.getWorkId())
                .requestId(linked ? e.getServiceRequest().getRequestId() : null)
                .requestNo(linked ? e.getServiceRequest().getRequestNo() : null)
                .workType(e.getWorkType())
                .workTypeLabel(e.getWorkType().getLabel())
                .requesterName(e.getRequesterName())
                .department(e.getDepartment())
                .location(e.getLocation())
                .content(e.getContent())
                .externalVendor(e.isExternalVendor())
                .vendorName(e.getVendorName())
                .hasCost(e.isHasCost())
                .cost(e.getCost())
                .assignee(e.getAssignee())
                .status(e.getStatus())
                .statusLabel(e.getStatus().getLabel())
                .completedAt(e.getCompletedAt())
                .remark(e.getRemark())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
