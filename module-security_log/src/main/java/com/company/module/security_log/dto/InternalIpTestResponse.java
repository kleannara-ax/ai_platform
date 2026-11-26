package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** IP 확인 결과 */
@Getter
@Builder
public class InternalIpTestResponse {

    private String ip;
    /** IPv4 형식인지 (IPv6·형식 오류는 외부 IP 판정을 하지 않음) */
    private boolean valid;
    /** 사용 중인 내부 IP 대역에 속하는지 */
    private boolean internal;
    /** 속한 대역 (사용 중인 대역만, "이름 (CIDR)") */
    private List<String> matched;
    private String message;
}
