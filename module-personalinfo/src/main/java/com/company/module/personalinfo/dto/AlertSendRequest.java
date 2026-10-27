package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 만료 알림 메일 수동 발송 요청 */
@Getter
@Setter
public class AlertSendRequest {

    /** 기본 수신자 외 추가 수신자 (쉼표/세미콜론 구분) */
    @Size(max = 1000)
    @Pattern(regexp = "^$|^[^@\\s,;]+@[^@\\s,;]+(\\s*[,;]\\s*[^@\\s,;]+@[^@\\s,;]+)*$",
             message = "추가 수신자 이메일 형식이 올바르지 않습니다.")
    private String extraRecipients;

    /** 메일 본문에 덧붙일 메시지 */
    @Size(max = 1000)
    private String message;
}
