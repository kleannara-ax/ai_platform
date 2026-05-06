package com.company.core.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 암호화 데이터 처리 응답 DTO
 * POST /api/login/sendEncData
 */
@Getter
@Builder
public class EncDataResponse {

    /** 수신된 encData 원본 */
    private final String receivedEncData;

    /** 처리 결과 메시지 */
    private final String resultMessage;

    public static EncDataResponse of(String encData, String message) {
        return EncDataResponse.builder()
                .receivedEncData(encData)
                .resultMessage(message)
                .build();
    }
}
