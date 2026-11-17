package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiAccessLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 접근 기록 1건 */
@Getter
@Builder
public class AccessLogResponse {

    private Long accessId;
    private LocalDateTime accessedAt;
    private String actionType;
    private String actionTypeLabel;
    private Long contractId;
    private String vendorName;
    private String contractName;
    private Long targetId;
    private String targetName;
    private Integer recordCount;
    private String reason;
    private Long userId;
    private String userLoginId;
    private String userName;
    private String clientIp;
    private String userAgent;

    public static AccessLogResponse from(PiAccessLog a) {
        return AccessLogResponse.builder()
                .accessId(a.getAccessId())
                .accessedAt(a.getCreatedAt())
                .actionType(a.getActionType().name())
                .actionTypeLabel(a.getActionType().getLabel())
                .contractId(a.getContractId())
                .vendorName(a.getVendorName())
                .contractName(a.getContractName())
                .targetId(a.getTargetId())
                .targetName(a.getTargetName())
                .recordCount(a.getRecordCount())
                .reason(a.getReason())
                .userId(a.getUserId())
                .userLoginId(a.getUserLoginId())
                .userName(a.getUserName())
                .clientIp(a.getClientIp())
                .userAgent(a.getUserAgent())
                .build();
    }
}
