package com.company.module.psinsp.service;

import com.company.module.psinsp.dto.PsInspMesSendRequest;
import com.company.module.psinsp.dto.PsInspMesSendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * MES 결과 전송 서비스
 *
 * <p>mesEndpointUrl이 설정되지 않으면 mock 모드로 동작합니다.
 */
@Slf4j
@Service
public class PsInspMesService {

    @Value("${ps-insp.mes.endpoint-url:}")
    private String mesEndpointUrl;

    private final RestTemplate restTemplate;

    public PsInspMesService(@Qualifier("psInspRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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

        log.info("[PS-INSP][MES][MOCK] 전송 성공 - IND_BCD: {}, ResultData: {} ppm", indBcd, resultData);
        return PsInspMesSendResponse.success(
                "MES 전송 완료 (IND_BCD: " + indBcd + ", ResultData: " + resultData + ") [mock 모드]",
                transmissionId, timestamp);
    }

    private PsInspMesSendResponse sendToMesServer(PsInspMesSendRequest request,
                                                    String transmissionId, String timestamp) {
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

            boolean success = response.getStatusCode().is2xxSuccessful();
            String msg = success
                    ? "MES 전송 완료 (IND_BCD: " + request.getIndBcd() + ")"
                    : "MES 전송 실패 - HTTP " + response.getStatusCode();

            return success
                    ? PsInspMesSendResponse.success(msg, transmissionId, timestamp)
                    : PsInspMesSendResponse.fail(msg, transmissionId, timestamp);

        } catch (Exception e) {
            log.error("[PS-INSP][MES] 전송 오류 - IND_BCD: {}", request.getIndBcd(), e);
            return PsInspMesSendResponse.fail("MES 전송 오류: " + e.getMessage(), transmissionId, timestamp);
        }
    }
}
