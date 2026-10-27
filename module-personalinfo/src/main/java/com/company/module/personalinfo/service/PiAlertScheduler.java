package com.company.module.personalinfo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 계약 만료 알림 일일 실행.
 *
 * <p>플랫폼 app 에 {@code @EnableScheduling} 이 있어야 동작한다. (README "core/app 에 추가 필요" 참고)
 * 없으면 이 메서드는 호출되지 않으며, 관리자가 {@code POST /personalinfo-api/alerts/run} 으로 수동 실행할 수 있다.
 *
 * <ul>
 *   <li>{@code personalinfo.alert.enabled} (기본 true) : false 면 자동 실행 안 함</li>
 *   <li>{@code personalinfo.alert.cron} (기본 매일 08:30) : 실행 시각</li>
 * </ul>
 * 같은 단계 알림은 DEDUP_KEY UNIQUE 로 한 번만 발송되므로 WAS 가 여러 대여도 중복 발송되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiAlertScheduler {

    private final PiAlertMailService alertMailService;

    @Value("${personalinfo.alert.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${personalinfo.alert.cron:0 30 8 * * *}", zone = "${personalinfo.alert.zone:Asia/Seoul}")
    public void runDaily() {
        if (!enabled) {
            return;
        }
        try {
            alertMailService.runDailyAlerts(LocalDate.now());
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않게 로그만 남긴다
            log.error("[personalinfo] 만료 알림 일일 실행 실패", e);
        }
    }
}
