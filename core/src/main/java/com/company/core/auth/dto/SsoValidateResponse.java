package com.company.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * SSO 암호화 데이터 검증 응답 DTO
 * 
 * 응답 예시:
 * {
 *   "head": {
 *     "returnCode": "0",
 *     "returnMessage": "Success"
 *   },
 *   "body": {
 *     "sproId": "사용자아이디",
 *     ...
 *   }
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class SsoValidateResponse {

    private Head head;
    private Body body;

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Head {
        private String returnCode;
        private String returnMessage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private String sproId;
    }

    /**
     * SSO 검증 성공 여부 (returnCode == "0")
     */
    public boolean isSuccess() {
        return head != null && "0".equals(head.getReturnCode());
    }

    /**
     * SSO 인증된 사용자 ID (sproId) 반환
     */
    public String getSproId() {
        return body != null ? body.getSproId() : null;
    }
}
