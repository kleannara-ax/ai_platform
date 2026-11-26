package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

/** 개인정보 다운로드 사유 검토 저장 결과 */
@Getter
@Builder
public class PiiDownloadReviewResponse {

    private Long uploadId;
    /** 저장(또는 되돌린) 건수 */
    private int updatedCount;
    /** 저장 후 남은 미검토 다운로드 건수 */
    private long pendingCount;
}
