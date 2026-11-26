package com.company.module.security_log.dto;

import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 룰셋별 분석 결과 (업로드 1건)
 * - status: DONE(분석 완료) / FAILED(분석 실패) / NOT_RUN(미분석)
 * - 분석 완료 룰만 검토 대상·검토 완료 건수가 의미 있음 (미분석·분석 실패는 0건으로 보지 않음)
 */
@Getter
@Builder
public class RuleResultsResponse {

    private Long uploadId;
    private LocalDateTime analyzedAt;
    /** 룰별 분석 기록 보유 여부 (false = 기록 기능 이전 분석 → 탐지된 룰만 분석 완료로 판정, 나머지는 미분석) */
    private boolean recorded;
    private List<Row> rows;

    @Getter
    @Builder
    public static class Row {
        private String ruleCode;
        private String ruleName;
        private Severity severity;
        private String status;
        private String statusLabel;
        private String failReason;
        /** 검토 대상 = 저장된 탐지 건수 */
        private long reviewTarget;
        /** 검토 완료 = 검토 기록이 있는 탐지 건수 */
        private long reviewDone;
        /** 개인정보 다운로드 이력 연동 룰 (자사몰·임직원몰) - 검토 대상·완료에 다운로드 이력 포함 */
        private boolean piiLinked;
        /** 연동된 개인정보 다운로드 이력 건수 */
        private long piiCount;
    }
}
