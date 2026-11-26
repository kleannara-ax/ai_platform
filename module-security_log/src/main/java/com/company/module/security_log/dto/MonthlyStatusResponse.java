package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 월별 시스템별 점검 현황 (접속·행위 로그) */
@Getter
@Builder
public class MonthlyStatusResponse {

    private String targetYm;
    private int systemCount;
    private int accessDoneCount;
    private List<Row> systems;

    @Getter
    @Builder
    public static class Row {
        private Long systemId;
        private String category;
        private String systemName;
        private String managerName;

        /** 접속 행위로그 */
        private int accessUploadCount;
        private Long latestUploadId;
        private String accessStatus;      // NONE / ANALYZED / REVIEWED
        private long accessAccountCount;
        private long accessSuccess;
        private long accessFail;
        private long accessRiskAccounts;
        /** 위험 표시 계정 중 검토가 남은 계정 수 (최신 파일 기준) */
        private long accessRiskPending;
        private long detectionCount;

        /** 개인정보 다운로드 검토 대상 시스템 (자사몰·임직원몰) */
        private boolean piiTarget;
        /** 개인정보 다운로드 내역 탭 읽기 결과 (FOUND / NOT_FOUND / null, 최신 파일 기준) */
        private String piiSheetStatus;
        /** 개인정보 다운로드 건수 (최신 파일 기준) */
        private long piiDownloadCount;
        /** 사유 검토가 남은 개인정보 다운로드 건수 (최신 파일 기준) */
        private long piiPending;
    }
}
