package com.company.module.personalinfo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 계약 상태 (DB 컬럼이 아니라 종료일/종료처리 여부로 계산한다).
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {
    ACTIVE("계약중"),
    EXPIRING("만료임박"),
    EXPIRED("만료"),
    TERMINATED("종료"),
    NO_END_DATE("기간미정");

    private final String label;

    public static ContractStatus of(PiContract contract, LocalDate today) {
        if ("Y".equals(contract.getTerminatedYn())) {
            return TERMINATED;
        }
        LocalDate end = contract.getContractEndDate();
        if (end == null) {
            return NO_END_DATE;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, end);
        if (daysLeft < 0) {
            return EXPIRED;
        }
        int alertDays = contract.getAlertDaysBefore() == null ? PiContract.DEFAULT_ALERT_DAYS : contract.getAlertDaysBefore();
        return daysLeft <= alertDays ? EXPIRING : ACTIVE;
    }
}
