package com.company.module.kims.dto.response;

import com.company.module.kims.entity.RequestLog;
import com.company.module.kims.entity.enums.RequestLogType;
import com.company.module.kims.entity.enums.RequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 처리 로그 응답 DTO.
 */
@Getter
@Builder
public class RequestLogResponse {

    private final Long logId;
    private final RequestLogType logType;
    private final String logTypeLabel;

    private final RequestStatus beforeStatus;
    private final RequestStatus afterStatus;

    private final String changedBy;
    private final String reason;
    private final String content;
    private final LocalDateTime createdAt;

    public static RequestLogResponse from(RequestLog entity) {
        return RequestLogResponse.builder()
                .logId(entity.getLogId())
                .logType(entity.getLogType())
                .logTypeLabel(entity.getLogType().getLabel())
                .beforeStatus(entity.getBeforeStatus())
                .afterStatus(entity.getAfterStatus())
                .changedBy(entity.getChangedBy())
                .reason(entity.getReason())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
