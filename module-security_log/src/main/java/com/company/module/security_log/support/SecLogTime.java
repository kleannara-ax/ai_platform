package com.company.module.security_log.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 모듈 공통 현재 시각 (한국시간 고정)
 * - 서버(JVM) 시간대가 UTC 여도 화면·저장 시각·자동 수집 시각이 한국시간으로 맞도록 모든 now() 를 이 클래스로 얻는다.
 */
public final class SecLogTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private SecLogTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static YearMonth yearMonth() {
        return YearMonth.now(ZONE);
    }
}
