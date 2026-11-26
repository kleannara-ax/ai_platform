package com.company.module.security_log.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 메일 알림 규칙 자동 실행
 * - 1분마다 규칙을 확인해, 사용 중이고 오늘 보내는 시각이 지났는데 그 시각 이후 실행 기록이 없는 규칙을 실행
 *   → 화면에서 보내는 시각을 바꾸면 서버 재시작 없이 바로 적용, 서버가 그 시각에 꺼져 있었으면 켜진 뒤 실행
 * - 플랫폼 전체 스케줄링(@EnableScheduling)을 켜지 않도록 모듈 안에서 전용 스케줄러 1개만 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecLogDlpMailRuleScheduler {

    private final SecLogDlpMailRuleService ruleService;
    private ThreadPoolTaskScheduler scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("seclog-mail-rule-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::tick, Instant.now().plusSeconds(30), Duration.ofMinutes(1));
        log.info("[security_log] 메일 알림 규칙 자동 실행 등록 (1분마다 확인), 메일 서버 {}", ruleService.smtpReady() ? "설정됨" : "미설정(대기만 함)");
    }

    private void tick() {
        try {
            for (Long id : ruleService.dueRuleIds()) {
                try {
                    ruleService.run(id, false, null);
                } catch (Exception e) {
                    log.warn("[security_log] 메일 알림 규칙 {} 자동 실행 실패: {}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[security_log] 메일 알림 규칙 확인 실패: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
