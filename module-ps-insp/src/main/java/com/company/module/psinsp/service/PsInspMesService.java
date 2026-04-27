package com.company.module.psinsp.service;

import com.company.module.psinsp.dto.PsInspMesSendRequest;
import com.company.module.psinsp.dto.PsInspMesSendResponse;
import com.company.module.psinsp.entity.PsInspApiLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MES 검사 결과 전송 서비스 (saveDustInspectionResult)
 *
 * <p>MES API 규격:
 * <pre>
 *   GET https://mesdev.kleannara.com:444/mobile/saveDustInspectionResult.data
 *       ?IND_BCD=25830J0069&RST_VAL=777
 *
 *   성공: { "RS_CODE": "S", "RS_MSG": "저장 성공" }
 *   실패: { "RS_CODE": "E", "RS_MSG": "저장 실패" }
 *   세션만료: HTTP 200 + HTML (/index.jsp 리다이렉트)
 * </pre>
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
    private final ObjectMapper objectMapper;

    public PsInspMesService(@Qualifier("psInspRestTemplate") RestTemplate restTemplate,
                            PsInspApiLogService apiLogService,
                            @Qualifier("psInspObjectMapper") ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiLogService = apiLogService;
        this.objectMapper = objectMapper;
    }

    public PsInspMesSendResponse sendResult(PsInspMesSendRequest request) {
        String indBcd = request.getIndBcd();
        Double rstVal = request.getRstVal();
        // RST_VAL은 정수 문자열로 전송 (소수점 이하 반올림)
        String rstValStr = String.valueOf(Math.round(rstVal));

        log.info("[PS-INSP][MES] 결과 전송 요청 - IND_BCD: {}, RST_VAL: {}", indBcd, rstValStr);

        String transmissionId = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now(KST).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (mesEndpointUrl != null && !mesEndpointUrl.isBlank()) {
            return sendToMesServer(indBcd, rstValStr, transmissionId, timestamp);
        }

        // ── Mock 모드 ──
        saveMesOutboundLog(indBcd, rstValStr, null,
                "{\"RS_CODE\":\"S\",\"RS_MSG\":\"저장 성공 [mock]\"}",
                "S", null, null, null, "MES mock 모드 전송 성공");

        log.info("[PS-INSP][MES][MOCK] 전송 성공 - IND_BCD: {}, RST_VAL: {}", indBcd, rstValStr);
        return PsInspMesSendResponse.success(
                "MES 전송 완료 [mock 모드] (IND_BCD: " + indBcd + ", RST_VAL: " + rstValStr + ")",
                transmissionId, timestamp, "S", "저장 성공 [mock]");
    }

    /**
     * MES 서버로 GET 요청 전송
     *
     * <pre>
     *   GET {mesEndpointUrl}?IND_BCD=xxx&RST_VAL=yyy
     * </pre>
     */
    private PsInspMesSendResponse sendToMesServer(String indBcd, String rstVal,
                                                    String transmissionId, String timestamp) {
        // GET URL 조립
        String fullUrl = UriComponentsBuilder.fromHttpUrl(mesEndpointUrl)
                .queryParam("IND_BCD", indBcd)
                .queryParam("RST_VAL", rstVal)
                .toUriString();

        log.info("[PS-INSP][MES] 전송 시작 - URL: {}", fullUrl);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(fullUrl, String.class);
            String body = response.getBody();

            log.debug("[PS-INSP][MES] 응답 - status: {}, body: {}", response.getStatusCode(), body);

            // ── 세션 만료 감지: HTTP 200이지만 HTML 응답 (index.jsp 리다이렉트) ──
            if (isSessionExpiredResponse(body)) {
                log.warn("[PS-INSP][MES] 세션 만료 감지 - MES 서버에서 HTML 리다이렉트 반환");
                saveMesOutboundLog(indBcd, rstVal, fullUrl, body,
                        "E", "MES_SESSION_EXPIRED", "MES 서버 세션 만료 (HTML 리다이렉트)",
                        "응답이 JSON이 아닌 HTML (index.jsp)", "MES 세션 만료");
                return PsInspMesSendResponse.fail(
                        "MES 서버 세션이 만료되었습니다. MES 관리자에게 문의하세요.",
                        transmissionId, timestamp, "E", "세션 만료");
            }

            // ── JSON 응답 파싱 ──
            return parseMesResponse(body, indBcd, rstVal, fullUrl, transmissionId, timestamp);

        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                log.error("[PS-INSP][MES] 전송 타임아웃 - IND_BCD: {}", indBcd, e);
                saveMesOutboundLog(indBcd, rstVal, fullUrl, null,
                        "E", "MES_TIMEOUT", "MES 전송 타임아웃",
                        cause.getMessage(), "MES 전송 타임아웃");
                return PsInspMesSendResponse.fail(
                        "MES 전송 타임아웃: " + cause.getMessage(), transmissionId, timestamp);
            } else if (cause instanceof java.net.ConnectException) {
                log.error("[PS-INSP][MES] 연결 실패 - IND_BCD: {}", indBcd, e);
                saveMesOutboundLog(indBcd, rstVal, fullUrl, null,
                        "E", "MES_CONN_FAIL", "MES 서버 연결 실패",
                        cause.getMessage(), "MES 서버 연결 실패");
                return PsInspMesSendResponse.fail(
                        "MES 연결 실패: " + cause.getMessage(), transmissionId, timestamp);
            }
            log.error("[PS-INSP][MES] 통신 오류 - IND_BCD: {}", indBcd, e);
            saveMesOutboundLog(indBcd, rstVal, fullUrl, null,
                    "E", "MES_CONN_FAIL", e.getMessage(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), "MES 통신 오류");
            return PsInspMesSendResponse.fail(
                    "MES 통신 오류: " + e.getMessage(), transmissionId, timestamp);

        } catch (Exception e) {
            log.error("[PS-INSP][MES] 전송 오류 - IND_BCD: {}", indBcd, e);
            saveMesOutboundLog(indBcd, rstVal, fullUrl, null,
                    "E", "UNKNOWN_ERROR", e.getMessage(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), "MES 전송 오류");
            return PsInspMesSendResponse.fail(
                    "MES 전송 오류: " + e.getMessage(), transmissionId, timestamp);
        }
    }

    /**
     * MES 서버 JSON 응답 파싱
     *
     * <pre>
     *   성공: { "RS_CODE": "S", "RS_MSG": "저장 성공" }
     *   실패: { "RS_CODE": "E", "RS_MSG": "저장 실패" }
     * </pre>
     */
    private PsInspMesSendResponse parseMesResponse(String body, String indBcd, String rstVal,
                                                     String fullUrl, String transmissionId, String timestamp) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String rsCode = root.has("RS_CODE") ? root.get("RS_CODE").asText() : null;
            String rsMsg = root.has("RS_MSG") ? root.get("RS_MSG").asText() : null;

            boolean success = "S".equals(rsCode);

            if (success) {
                log.info("[PS-INSP][MES] 전송 성공 - IND_BCD: {}, RS_CODE: {}, RS_MSG: {}",
                        indBcd, rsCode, rsMsg);
                saveMesOutboundLog(indBcd, rstVal, fullUrl, body,
                        "S", null, null, null,
                        "MES 전송 성공 - " + rsMsg);
                return PsInspMesSendResponse.success(
                        "MES 전송 완료 (IND_BCD: " + indBcd + ") - " + rsMsg,
                        transmissionId, timestamp, rsCode, rsMsg);
            } else {
                log.warn("[PS-INSP][MES] 전송 실패 - IND_BCD: {}, RS_CODE: {}, RS_MSG: {}",
                        indBcd, rsCode, rsMsg);
                saveMesOutboundLog(indBcd, rstVal, fullUrl, body,
                        "E", "MES_RESP_ERROR",
                        "MES 응답 실패 - RS_CODE: " + rsCode + ", RS_MSG: " + rsMsg,
                        null, "MES 응답 실패 - " + rsMsg);
                return PsInspMesSendResponse.fail(
                        "MES 전송 실패 - " + (rsMsg != null ? rsMsg : "RS_CODE=" + rsCode),
                        transmissionId, timestamp, rsCode, rsMsg);
            }

        } catch (Exception e) {
            // JSON 파싱 실패 = 예상 못한 응답 형식
            log.warn("[PS-INSP][MES] 응답 파싱 실패 - body: {}", body, e);
            saveMesOutboundLog(indBcd, rstVal, fullUrl, body,
                    "E", "MES_PARSE_ERROR",
                    "MES 응답 파싱 실패",
                    e.getMessage(), "MES 응답 형식 오류");
            return PsInspMesSendResponse.fail(
                    "MES 응답 파싱 실패: " + e.getMessage(), transmissionId, timestamp);
        }
    }

    /**
     * 세션 만료 응답 감지
     *
     * <p>MES 서버가 세션 만료 시 HTTP 200을 반환하면서
     * HTML 페이지(/index.jsp)로 리다이렉트하는 경우를 감지합니다.
     */
    private boolean isSessionExpiredResponse(String body) {
        if (body == null || body.isBlank()) return false;
        String trimmed = body.trim();
        // HTML 태그가 포함되어 있거나 index.jsp 리다이렉트가 있으면 세션 만료
        if (trimmed.startsWith("<") || trimmed.startsWith("<!")) return true;
        if (trimmed.contains("/index.jsp")) return true;
        if (trimmed.contains("<html") || trimmed.contains("<HTML")) return true;
        return false;
    }

    /**
     * MES Outbound 통신 로그 저장
     *
     * @param indBcd     개별바코드
     * @param rstVal     결과값 (문자열)
     * @param targetUrl  호출 URL (GET full URL)
     * @param responseBody MES 응답 원문
     * @param comstat    통신 상태 (S/E/P)
     * @param inerrat    내부 에러 코드
     * @param errtxt     에러 메시지
     * @param inerrtxt   내부 에러 상세
     * @param remark     비고
     */
    private void saveMesOutboundLog(String indBcd, String rstVal, String targetUrl,
                                     String responseBody,
                                     String comstat, String inerrat, String errtxt,
                                     String inerrtxt, String remark) {
        try {
            String paramIn = "{\"IND_BCD\":\"" + indBcd + "\",\"RST_VAL\":\"" + rstVal + "\""
                    + (targetUrl != null ? ",\"URL\":\"" + targetUrl + "\"" : "")
                    + "}";

            PsInspApiLog logEntity = PsInspApiLog.builder()
                    .api("MES_SEND_OUT")
                    .comstat(comstat)
                    .errtxt(PsInspApiLogService.truncate(errtxt, 2000))
                    .credat(LocalDate.now(KST))
                    .cretim(LocalTime.now(KST))
                    .remark(remark)
                    .inerrat(inerrat)
                    .inerrtxt(PsInspApiLogService.truncate(inerrtxt, 2000))
                    .inParameter(paramIn)
                    .outParameter(PsInspApiLogService.truncate(responseBody, 4000))
                    .build();

            apiLogService.saveLog(logEntity);
        } catch (Exception e) {
            log.warn("[PS-INSP][MES] Outbound 로그 저장 실패 (무시): {}", e.getMessage());
        }
    }
}
