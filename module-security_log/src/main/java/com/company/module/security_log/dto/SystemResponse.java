package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogSystem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SystemResponse {

    private Long systemId;
    private String systemCode;
    private String systemName;
    private String category;
    private LogType defaultLogType;
    private String defaultLogTypeLabel;
    private String managerName;
    private String description;
    private String useYn;
    private Integer sortOrder;

    public static SystemResponse from(SecLogSystem s) {
        return SystemResponse.builder()
                .systemId(s.getSystemId())
                .systemCode(s.getSystemCode())
                .systemName(s.getSystemName())
                .category(s.getCategory())
                .defaultLogType(s.getDefaultLogType())
                .defaultLogTypeLabel(s.getDefaultLogType().getLabel())
                .managerName(s.getManagerName())
                .description(s.getDescription())
                .useYn(s.getUseYn())
                .sortOrder(s.getSortOrder())
                .build();
    }
}
