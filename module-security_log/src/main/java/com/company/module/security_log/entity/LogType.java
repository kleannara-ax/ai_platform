package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그 유형
 * - 룰의 LOG_TYPE 이 ALL 이면 모든 로그 유형에 적용된다.
 * - 업로드 로그에는 ALL 을 사용할 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum LogType {
    ALL("전체(룰 전용)"),
    LINUX("Linux/Unix 시스템 로그"),
    WINDOWS("Windows 이벤트 로그"),
    WEB("웹서버 접근/에러 로그"),
    WAS("WAS 애플리케이션 로그"),
    DB("DBMS 감사/접근 로그"),
    NETWORK("방화벽/네트워크 장비 로그"),
    APP("업무 애플리케이션 로그"),
    ETC("기타");

    private final String label;
}
