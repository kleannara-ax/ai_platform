package com.company.module.psinsp.service;

import com.company.module.psinsp.entity.PsInspApiLog;
import com.company.module.psinsp.repository.PsInspApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * PS 지분검사 API 통신 로그 저장 서비스
 *
 * <p>비동기(@Async)로 저장하여 API 응답 속도에 영향을 주지 않는다.
 * <p>로그 저장 실패 시에도 원래 API 동작에는 영향 없음 (별도 트랜잭션).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PsInspApiLogService {

    private final PsInspApiLogRepository apiLogRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * API 통신 로그 비동기 저장
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(PsInspApiLog logEntity) {
        try {
            apiLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("[PS-INSP-LOG] API 로그 저장 실패 (무시) - api: {}, error: {}",
                    logEntity.getApi(), e.getMessage());
        }
    }

    /**
     * 성공 로그 빌더
     */
    public PsInspApiLog.PsInspApiLogBuilder success(String api) {
        return PsInspApiLog.builder()
                .api(api)
                .comstat("S")
                .credat(LocalDate.now(KST))
                .cretim(LocalTime.now(KST));
    }

    /**
     * 에러 로그 빌더
     */
    public PsInspApiLog.PsInspApiLogBuilder error(String api, String inerrat) {
        return PsInspApiLog.builder()
                .api(api)
                .comstat("E")
                .inerrat(inerrat)
                .credat(LocalDate.now(KST))
                .cretim(LocalTime.now(KST));
    }

    /**
     * 처리 중 로그 빌더
     */
    public PsInspApiLog.PsInspApiLogBuilder processing(String api) {
        return PsInspApiLog.builder()
                .api(api)
                .comstat("P")
                .credat(LocalDate.now(KST))
                .cretim(LocalTime.now(KST));
    }

    /**
     * 텍스트를 최대 길이로 잘라서 반환
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
