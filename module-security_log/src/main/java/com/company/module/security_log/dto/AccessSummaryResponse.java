package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogAccessSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/** 계정별 접속 행위 집계 */
@Getter
@Builder(toBuilder = true)
public class AccessSummaryResponse {

    private Long summaryId;
    /** 해당 계정이 포함된 최신 업로드 ID (대시보드 시스템 상세 → 분석 결과 이동) */
    private Long uploadId;
    private String accountId;
    private int successCount;
    private int failCount;
    private int offHourCount;
    private int externalIpCount;
    private int ipCount;
    private String ipList;
    private String firstSeen;
    private String lastSeen;
    private List<String> riskFlags;

    // ---- 계정 검토 (업로드 1건 기준 접속 행위 분석에서만 채움, 미검토면 null)
    /** NORMAL / CONFIRMED / RESOLVED (null = 미검토) */
    private String reviewStatus;
    private String reviewStatusLabel;
    private String reviewComment;
    private java.time.LocalDateTime reviewedAt;
    private Long reviewedBy;

    public static AccessSummaryResponse from(SecLogAccessSummary s) {
        List<String> flags = s.getRiskFlags() == null || s.getRiskFlags().isBlank()
                ? List.of() : Arrays.asList(s.getRiskFlags().split(","));
        return AccessSummaryResponse.builder()
                .summaryId(s.getSummaryId())
                .accountId(s.getAccountId())
                .successCount(s.getSuccessCount())
                .failCount(s.getFailCount())
                .offHourCount(s.getOffHourCount())
                .externalIpCount(s.getExternalIpCount())
                .ipCount(s.getIpCount())
                .ipList(s.getIpList())
                .firstSeen(s.getFirstSeen())
                .lastSeen(s.getLastSeen())
                .riskFlags(flags)
                .build();
    }
}
