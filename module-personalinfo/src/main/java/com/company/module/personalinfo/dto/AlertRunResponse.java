package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** 만료 알림 일괄 실행 결과 */
@Getter
@Builder
public class AlertRunResponse {

    private LocalDate baseDate;
    private int candidateCount;
    private int sentCount;
    private int failedCount;
    private int skippedCount;
    private int alreadySentCount;
}
