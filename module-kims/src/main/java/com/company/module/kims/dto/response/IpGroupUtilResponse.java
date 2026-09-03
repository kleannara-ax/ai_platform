package com.company.module.kims.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * IP 대역(그룹) 사용 현황 응답 DTO.
 * <p>예: 192.1.0 대역의 전체 254개 중 사용중/미사용 수.
 */
@Getter
@Builder
public class IpGroupUtilResponse {

    private final String group;     // 대역 (예: 192.1.0)
    private final String type;      // 대역 구분: USER(사용자) / FACILITY(설비)
    private final int total;        // 사용 가능 IP 수 (.1~.254 = 254)
    private final int used;         // 사용중(IN_USE)
    private final int available;    // 미사용
    private final int registered;   // 관리대장에 등록된 수(미사용 예약 포함)
}
