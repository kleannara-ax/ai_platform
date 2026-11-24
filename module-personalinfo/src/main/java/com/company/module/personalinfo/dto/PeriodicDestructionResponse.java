package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** 정기 파기확인 현황 (계약 상세 · 기준 연도별 기간 목록) */
@Getter
@Builder
public class PeriodicDestructionResponse {

    private Long contractId;
    private String periodicDestructionYn;
    private String cycle;
    private String cycleLabel;
    private String mailYn;
    /** 관리 시작일 (이 날 이후에 끝나는 기간부터 미수취 판정) */
    private LocalDate managedFrom;
    /** 수취 기한 일수 (기간 종료 + N일) */
    private int replyDays;
    private int year;
    /** 선택 가능한 연도 (계약 시작 연도 ~ 올해, 최근 연도 먼저) */
    private List<Integer> availableYears;
    /** 이 연도의 기간 (최근 기간 먼저) */
    private List<PeriodItem> periods;
    /** 업로드할 때 기본으로 고를 기간 (직전에 끝난 기간, 없으면 진행 중 기간) */
    private String defaultPeriod;
    /** 업로드할 때 고를 수 있는 기간 (계약기간 안, 시작한 기간, 최근 기간 먼저, 최대 36개) */
    private List<PeriodOption> selectablePeriods;
    /** 미수취 기간 수 (전체 기간 기준) */
    private int overdueCount;

    @Getter
    @Builder
    public static class PeriodItem {
        private String key;
        private String label;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate dueDate;
        /** RECEIVED 받음 / OVERDUE 미수취 / WAITING 기한 전 / IN_PROGRESS 진행 중 / NOT_MANAGED 관리 전 */
        private String status;
        private String statusLabel;
        private boolean managed;
        private List<ContractFileResponse> files;
    }

    @Getter
    @Builder
    public static class PeriodOption {
        private String key;
        private String label;
        private String status;
        private String statusLabel;
    }
}
