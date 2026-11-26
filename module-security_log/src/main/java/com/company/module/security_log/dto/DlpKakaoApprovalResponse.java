package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 카카오톡 사용 승인 대장 목록
 * - state (오늘 기준 계산): ACTIVE 사용 중 / EXPIRING 7일 내 만료 / EXPIRED 만료 / BEFORE 시작 전
 * - latestYn: 같은 사람(부서+이름) 신청 중 종료일이 가장 늦은 건 = Y
 */
@Getter
@Builder
public class DlpKakaoApprovalResponse {

    private LocalDate today;
    private long activeCount;
    private long expiringCount;
    private long expiredCount;
    private long beforeCount;
    private List<Row> rows;

    @Getter
    @Builder
    public static class Row {
        private Long kakaoApprovalId;
        private String divisionName;
        private String deptName;
        private String userName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String applyStatus;
        private String approvalDocNo;
        private String reason;
        private String state;
        private String stateLabel;
        /** 종료일까지 남은 날 (만료면 음수) */
        private long daysLeft;
        private String latestYn;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
