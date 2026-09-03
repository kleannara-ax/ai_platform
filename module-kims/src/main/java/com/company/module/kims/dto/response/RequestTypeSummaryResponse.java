package com.company.module.kims.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 업무요청 홈 카드용 유형별 요약.
 */
@Getter
@Builder
public class RequestTypeSummaryResponse {

    private final String type;       // 코드 (예: SUPPLY)
    private final String label;      // 한글명
    private final long newCount;     // 신규(접수 상태) 건수
    private final long totalCount;   // 전체 건수
}
