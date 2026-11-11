package com.company.module.personalinfo.entity;

/** 알림 메일 발송 상태 */
public enum AlertSendStatus {
    PENDING,
    SENT,
    FAILED,
    /** 메일 서버 미설정 또는 수신자 없음으로 발송하지 않음 */
    SKIPPED
}
