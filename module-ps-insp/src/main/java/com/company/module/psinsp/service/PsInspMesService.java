package com.company.module.psinsp.service;

import com.company.module.psinsp.dto.PsInspMesSendRequest;
import com.company.module.psinsp.dto.PsInspMesSendResponse;
import com.company.module.psinsp.entity.PsInspApiLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * MES 결과 전송 서비스
 *
 * <p>mesEndpointUrl이 설정되지 않으면 mock 모드로 동작합니다.
 * <p>MES 외부 전송(Outbound) 통신 로그를 ps_insp_api_log 테이블에 기록합니다.
 */
@Slf4j
@Service
public class PsInspMesService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${ps-insp.mes.endpoint-url:}")
    private String mesEndpointUrl;

    private final RestTemplate restTemplate;
    private final PsInspApiLogService apiLogService;

    public PsInspMesService(@Qualifier("psInspRestTemplate") RestTemplate restTemplate,
                            PsInspApiLogService apiLogService) {
        this.restTemplate = restTemplate;
        this.apiLogService = apiLogService;
    }

    public PsInspMesSendResponse sendResult(PsInspMesSendRequest request) {
        String indBcd = request.getIndBcd();
        Double resultData = request.getResultData();

        log.info("[PS-INSP][MES] 결과 전송 요청 - IND_BCD: {}, ResultData: {} ppm", indBcd, resultData);

        String transmissionId = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (mesEndpointUrl != null && !mesEndpointUrl.isBlank()) {
            return sendToMesServer(request, transmissionId, timestamp);
        }

        // Mock 모드: Outbound 로그 기록
        saveMesOutboundLog(indBcd, resultData, "MOCK", null, 200, true, null, 0);

        log.info("[PS-INSP][MES][MOCK] 전송 성공 - IND_BCD: {}, ResultData: {} ppm", indBcd, resultData);
        return PsInspMesSendResponse.success(
                "MES 전송 완료 (IND_BCD: " + indBcd + ", ResultData: " + resultData + ") [mock 모드]",
                transmissionId, timestamp);
    }

    private PsInspMesSendResponse sendToMesServer(PsInspMesSendRequest request,
                                                    String transmissionId, String timestamp) {
        long startMs = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                    "IND_BCD", request.getIndBcd(),
                    "ResultData", request.getResultData());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("[PS-INSP][MES] 전송 시작 - URL: {}", mesEndpointUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    mesEndpointUrl, HttpMethod.POST, entity, String.class);

            long elapsedMs = System.currentTimeMillis() - startMs;
            boolean success = response.getStatusCode().is2xxSuccessful();
            String msg = success
                    ? "MES 전송 완료 (IND_BCD: " + request.getIndBcd() + ")"
                    : "MES 전송 실패 - HTTP " + response.getStatusCode();

            // Outbound 로그 기록
            saveMesOutboundLog(request.getIndBcd(), request.getResultData(),
                    mesEndpointUrl, response.getBody(),
                    response.getStatusCode().value(), success, null, elapsedMs);

            return success
                    ? PsInspMesSendResponse.success(msg, transmissionId, timestamp)
                    : PsInspMesSendResponse.fail(msg, transmissionId, timestamp);

        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.error("[PS-INSP][MES] 전송 오류 - IND_BCD: {}", request.getIndBcd(), e);

            // 오류 Outbound 로그 기록
            saveMesOutboundLog(request.getIndBcd(), request.getResultData(),
                    mesEndpointUrl, null, 0, false, e.getMessage(), elapsedMs);

            return PsInspMesSendResponse.fail("MES 전송 오류: " + e.getMessage(), transmissionId, timestamp);
        }
    }

    /**
     * MES Outbound 통신 로그 저장
     */
    private void saveMesOutboundLog(String indBcd, Double resultData,
                                     String targetUrl, String responseBody,
                                     int httpStatus, boolean success,
                                     String errorMessage, long elapsedMs) {
        try {
            String requestBodyJson = "{\"IND_BCD\":\"" + indBcd + "\",\"ResultData\":" + resultData + "}";

            PsInspApiLog logEntity = PsInspApiLog.builder()
                    .direction("OUT")
                    .apiType("MES_SEND_OUT")
                    .httpMethod("POST")
                    .requestUri("/ps-insp-api/mes/send-result")
                    .targetUrl(targetUrl)
                    .requestBody(requestBodyJson)
                    .httpStatus(httpStatus)
                    .success(success)
                    .responseBody(PsInspApiLogService.truncate(responseBody, 4000))
                    .errorMessage(PsInspApiLogService.truncate(errorMessage, 2000))
                    .indBcd(indBcd)
                    .elapsedMs(elapsedMs)
                    .requestedAt(LocalDateTime.now(KST))
                    .build();

            apiLogService.saveLog(logEntity);
        } catch (Exception e) {
            log.warn("[PS-INSP][MES] Outbound 로그 저장 실패 (무시): {}", e.getMessage());
        }
    }
}
