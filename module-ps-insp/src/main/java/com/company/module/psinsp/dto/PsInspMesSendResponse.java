package com.company.module.psinsp.dto;

import lombok.*;

/**
 * MES 결과 전송 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspMesSendResponse {

    private boolean success;
    private String message;
    private String transmissionId;
    private String timestamp;

    public static PsInspMesSendResponse success(String message, String transmissionId, String timestamp) {
        return PsInspMesSendResponse.builder()
                .success(true)
                .message(message)
                .transmissionId(transmissionId)
                .timestamp(timestamp)
                .build();
    }

    public static PsInspMesSendResponse fail(String message, String transmissionId, String timestamp) {
        return PsInspMesSendResponse.builder()
                .success(false)
                .message(message)
                .transmissionId(transmissionId)
                .timestamp(timestamp)
                .build();
    }
}
