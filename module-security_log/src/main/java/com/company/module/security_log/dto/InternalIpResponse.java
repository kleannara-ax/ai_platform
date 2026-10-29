package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogInternalIp;
import com.company.module.security_log.support.IpRange;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InternalIpResponse {

    private Long internalIpId;
    private String ipRange;
    /** 대역 시작·끝 IP (표시용) */
    private String startIp;
    private String endIp;
    private long ipCount;
    private String ipName;
    private String description;
    private String useYn;
    private Integer sortOrder;
    private LocalDateTime updatedAt;

    public static InternalIpResponse from(SecLogInternalIp e) {
        return InternalIpResponse.builder()
                .internalIpId(e.getInternalIpId())
                .ipRange(e.getIpRange())
                .startIp(IpRange.toText(e.getStartIpNum()))
                .endIp(IpRange.toText(e.getEndIpNum()))
                .ipCount(e.getEndIpNum() - e.getStartIpNum() + 1)
                .ipName(e.getIpName())
                .description(e.getDescription())
                .useYn(e.getUseYn())
                .sortOrder(e.getSortOrder())
                .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt())
                .build();
    }
}
