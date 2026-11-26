package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DLP 모니터링 - 그 달 올린 유출 로그 파일
 * - upload = 보고서 기준 파일 (가장 최근에 올린 파일, 없으면 null)
 * - agent = 그 달 올린 Privacy-I Agent 현황 파일 (없으면 null)
 * - 보고서 내용은 /report-data 에서 조회
 */
@Getter
@Builder
public class DlpLeakMonthlyResponse {

    private String targetYm;
    private DlpLeakUploadResponse upload;
    /** 그 달 올린 파일 (최근 순, 첫 번째 = 보고서 기준) */
    private List<DlpLeakUploadResponse> files;
    /** 그 달 Privacy-I Agent 현황 파일 (없으면 null) */
    private Agent agent;

    @Getter
    @Builder
    public static class Agent {
        private String fileName;
        private LocalDateTime uploadedAt;
        /** 파일에서 읽은 인원 */
        private Integer totalCount;
        /** 재설치 인원 (설치 일시가 그 달) */
        private long reinstallCount;
        /** 장기 미접속 인원 (미접속일 7일 이상) */
        private long offlineCount;
    }
}
