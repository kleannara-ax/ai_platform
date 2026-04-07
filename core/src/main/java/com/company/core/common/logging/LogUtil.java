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
     * remoteAddr을 우선 사용한다 (방화벽이 프록시 헤더를 덮어쓰는 환경 대응).
     * remoteAddr이 루프백(127.0.0.1, 0:0:0:0:0:0:0:1)인 경우에만 프록시 헤더를 확인한다.
     *
     * @param request HttpServletRequest
     * @return 클라이언트 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // remoteAddr이 실제 클라이언트 IP이면 바로 사용
        if (remoteAddr != null && !isLoopback(remoteAddr)) {
            return remoteAddr;
        }
        // 루프백인 경우(리버스 프록시 경유) → 프록시 헤더에서 추출
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    /** 루프백 IP 여부 확인 */
    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
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
