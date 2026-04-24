package com.company.module.psinsp.service;

import com.company.module.psinsp.entity.PsInspApiLog;
import com.company.module.psinsp.repository.PsInspApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
            log.warn("[PS-INSP-LOG] API 로그 저장 실패 (무시) - apiType: {}, uri: {}, error: {}",
                    logEntity.getApiType(), logEntity.getRequestUri(), e.getMessage());
        }
    }

    /**
     * Inbound API 로그 빌더 (컨트롤러 → 서버)
     */
    public PsInspApiLog.PsInspApiLogBuilder inbound(String apiType, String httpMethod, String requestUri) {
        return PsInspApiLog.builder()
                .direction("IN")
                .apiType(apiType)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .requestedAt(LocalDateTime.now(KST))
                .success(true);
    }

    /**
     * Outbound API 로그 빌더 (서버 → MES 등 외부)
     */
    public PsInspApiLog.PsInspApiLogBuilder outbound(String apiType, String httpMethod, String targetUrl) {
        return PsInspApiLog.builder()
                .direction("OUT")
                .apiType(apiType)
                .httpMethod(httpMethod)
                .requestUri("/ps-insp-api/mes/send-result")
                .targetUrl(targetUrl)
                .requestedAt(LocalDateTime.now(KST))
                .success(true);
    }

    /**
     * 응답 Body를 최대 길이로 잘라서 반환 (TEXT 컬럼 보호)
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "...(truncated)" : text;
    }
}
