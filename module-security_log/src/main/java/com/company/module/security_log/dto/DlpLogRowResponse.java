package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

/** DLP 점검 보고서의 근거 로그 한 줄 (원본 파일 행 번호 기준) */
@Getter
@Builder
public class DlpLogRowResponse {
    private int row;
    private String time;
    private String account;
    private String name;
    private String ip;
    private String type;
    private String content;
    private String detail;
}
