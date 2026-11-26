package com.company.module.security_log.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 메일 알림 규칙 등록·수정 */
@Getter
@Setter
public class DlpMailRuleSaveRequest {

    @NotBlank(message = "규칙 이름을 입력해 주세요.")
    @Size(max = 100, message = "규칙 이름은 100자까지 입력할 수 있습니다.")
    private String ruleName;

    /** 보내는 날 = 신청 종료일 + N일 (음수 = 종료일 전) */
    @NotNull(message = "보내는 날(신청 종료일 기준 일수)을 입력해 주세요.")
    @Min(value = -90, message = "보내는 날은 신청 종료일 90일 전부터 90일 후까지 정할 수 있습니다.")
    @Max(value = 90, message = "보내는 날은 신청 종료일 90일 전부터 90일 후까지 정할 수 있습니다.")
    private Integer offsetDays;

    @NotBlank(message = "보내는 시각을 입력해 주세요.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "보내는 시각은 HH:MM (예: 11:00) 형식입니다.")
    private String sendTime;

    /** 받는 사람 (비어 있으면 규칙을 켤 수 없음 - useYn = N 으로만 저장) */
    @Size(max = 1000, message = "받는 사람은 1000자까지 입력할 수 있습니다.")
    private String toAddrs;

    @Size(max = 1000, message = "참조는 1000자까지 입력할 수 있습니다.")
    private String ccAddrs;

    @NotBlank(message = "메일 제목을 입력해 주세요.")
    @Size(max = 300, message = "메일 제목은 300자까지 입력할 수 있습니다.")
    private String subjectTpl;

    @NotBlank(message = "메일 본문을 입력해 주세요.")
    @Size(max = 2000, message = "메일 본문은 2000자까지 입력할 수 있습니다.")
    private String bodyTpl;

    @Pattern(regexp = "^[YN]$", message = "사용 여부는 Y 또는 N 입니다.")
    private String useYn;
}
