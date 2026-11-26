package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

/** 접속 계정 검토 저장 결과 */
@Getter
@Builder
public class AccountReviewResponse {

    private Long uploadId;
    /** 저장(또는 되돌린) 계정 수 */
    private int updatedCount;
    /** 저장 후 검토가 남은 위험 표시 계정 수 */
    private long riskPendingCount;
}
