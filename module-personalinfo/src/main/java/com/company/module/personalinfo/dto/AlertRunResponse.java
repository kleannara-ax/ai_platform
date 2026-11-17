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
    /** 만료 서류 미수취 독촉 메일 발송 건수 */
    private int docsReminderCount;
    /** 점검 조치 기한 지남 메일 발송 건수 */
    private int actionOverdueCount;
    /** 정기 점검 요청 메일(담당자) 발송 건수 */
    private int regularInspectionCount;
    /** 정기 점검 대상 요약 메일(관리자) 발송 건수 */
    private int regularSummaryCount;
    /** 자동연장 예정 확인 메일 발송 건수 */
    private int autoRenewNoticeCount;
}
