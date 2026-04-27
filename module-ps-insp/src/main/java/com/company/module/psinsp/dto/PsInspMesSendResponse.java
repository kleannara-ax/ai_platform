package com.company.module.psinsp.dto;

import lombok.*;

/**
 * MES 결과 전송 응답 DTO
 *
 * <p>MES 서버 응답 규격:
 * <pre>
 *   성공: { "RS_CODE": "S", "RS_MSG": "저장 성공" }
 *   실패: { "RS_CODE": "E", "RS_MSG": "저장 실패" }
 *   세션만료: HTTP 200 + HTML 리다이렉트 (/index.jsp)
 * </pre>
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspMesSendResponse {

    private boolean success;
    private String message;
    private String transmissionId;
    private String timestamp;

    /** MES 서버 원본 응답 코드 (S/E) */
    private String rsCode;

    /** MES 서버 원본 응답 메시지 */
    private String rsMsg;

    public static PsInspMesSendResponse success(String message, String transmissionId, String timestamp,
                                                  String rsCode, String rsMsg) {
        return PsInspMesSendResponse.builder()
                .success(true)
                .message(message)
                .transmissionId(transmissionId)
                .timestamp(timestamp)
                .rsCode(rsCode)
                .rsMsg(rsMsg)
                .build();
    }

    public static PsInspMesSendResponse fail(String message, String transmissionId, String timestamp,
                                               String rsCode, String rsMsg) {
        return PsInspMesSendResponse.builder()
                .success(false)
                .message(message)
                .transmissionId(transmissionId)
                .timestamp(timestamp)
                .rsCode(rsCode)
                .rsMsg(rsMsg)
                .build();
    }

    /** MES 응답 없이 내부 에러인 경우 (타임아웃/연결 실패 등) */
    public static PsInspMesSendResponse fail(String message, String transmissionId, String timestamp) {
        return PsInspMesSendResponse.builder()
                .success(false)
                .message(message)
                .transmissionId(transmissionId)
                .timestamp(timestamp)
                .build();
    }
}
