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
        private long detectionCount;
    }
}
