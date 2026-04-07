package com.company.core.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * IP 해석 공통 유틸리티
 * 프록시/방화벽/로드밸런서 환경에서 실제 클라이언트 IP를 추출한다.
 */
@Slf4j
public class LogUtil {

    /** IP 관련 헤더 목록 (우선순위 순) */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private LogUtil() {}

    /**
     * HttpServletRequest에서 실제 클라이언트 IP를 추출한다.
     * X-Forwarded-For 헤더가 있으면 첫 번째 IP(=원본 클라이언트 IP)를 반환한다.
     *
     * @param request HttpServletRequest
     * @return 클라이언트 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For: clientIP, proxy1IP, proxy2IP → 첫 번째가 실제 클라이언트
                String clientIp = value.split(",")[0].trim();
                return clientIp;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 디버깅용: 모든 IP 관련 헤더를 로그로 출력한다.
     * 방화벽/프록시 환경에서 실제 IP가 어디에 있는지 추적할 때 사용.
     *
     * @param request HttpServletRequest
     * @param tag     로그 구분 태그
     */
    public static void logAllIpHeaders(HttpServletRequest request, String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(tag).append("] IP헤더 전체 덤프 | ");
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null) {
                sb.append(header).append("=").append(value).append(" | ");
            }
        }
        sb.append("remoteAddr=").append(request.getRemoteAddr());
        sb.append(" | remoteHost=").append(request.getRemoteHost());
        sb.append(" | localAddr=").append(request.getLocalAddr());
        log.info(sb.toString());
    }
}
