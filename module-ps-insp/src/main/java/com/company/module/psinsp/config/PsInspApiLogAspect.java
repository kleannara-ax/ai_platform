package com.company.module.psinsp.config;

import com.company.module.psinsp.entity.PsInspApiLog;
import com.company.module.psinsp.service.PsInspApiLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * PS 지분검사 API 통신 로그 AOP Aspect
 *
 * <p>PS-INSP 컨트롤러의 모든 API 메서드를 자동 감싸서 로그를 기록한다.
 * <p>Health, Page 컨트롤러는 제외 (비즈니스 로그 대상 아님).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PsInspApiLogAspect {

    private final PsInspApiLogService apiLogService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * PsInspectionApiController + PsInspConfigApiController + PsInspMesController 대상
     */
    @Around("execution(* com.company.module.psinsp.controller.PsInspectionApiController.*(..)) || " +
            "execution(* com.company.module.psinsp.controller.PsInspConfigApiController.*(..)) || " +
            "execution(* com.company.module.psinsp.controller.PsInspMesController.*(..))")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        LocalDateTime requestedAt = LocalDateTime.now(KST);
        long startMs = System.currentTimeMillis();

        // 요청 정보 추출
        HttpServletRequest request = getHttpRequest();
        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";
        String requestUri = request != null ? request.getRequestURI() : "UNKNOWN";
        String queryString = request != null ? request.getQueryString() : null;
        String clientIp = extractClientIp(request);
        String userId = extractUserId(request);
        String apiType = resolveApiType(joinPoint.getSignature().getName());

        Object result = null;
        Integer httpStatus = 200;
        boolean success = true;
        String errorMessage = null;
        String responseBody = null;

        try {
            result = joinPoint.proceed();

            // ResponseEntity에서 상태 코드 추출
            if (result instanceof ResponseEntity<?> re) {
                httpStatus = re.getStatusCode().value();
                success = re.getStatusCode().is2xxSuccessful();
                try {
                    Object body = re.getBody();
                    if (body != null) {
                        responseBody = PsInspApiLogService.truncate(body.toString(), 4000);
                    }
                } catch (Exception ignored) {}
            }

            return result;

        } catch (Throwable ex) {
            success = false;
            httpStatus = 500;
            errorMessage = PsInspApiLogService.truncate(ex.getMessage(), 2000);
            throw ex;

        } finally {
            long elapsedMs = System.currentTimeMillis() - startMs;

            // 검사 연관 정보 추출 (파라미터에서)
            String indBcd = extractParam(request, "indBcd", "IND_BCD");
            String matnr = extractParam(request, "matnr", "MATNR");
            String lotnr = extractParam(request, "lotnr", "LOTNR");

            PsInspApiLog logEntity = PsInspApiLog.builder()
                    .direction("IN")
                    .apiType(apiType)
                    .httpMethod(httpMethod)
                    .requestUri(requestUri)
                    .queryString(PsInspApiLogService.truncate(queryString, 2000))
                    .httpStatus(httpStatus)
                    .success(success)
                    .responseBody(responseBody)
                    .errorMessage(errorMessage)
                    .indBcd(indBcd)
                    .matnr(matnr)
                    .lotnr(lotnr)
                    .userId(userId)
                    .clientIp(clientIp)
                    .elapsedMs(elapsedMs)
                    .requestedAt(requestedAt)
                    .build();

            apiLogService.saveLog(logEntity);
        }
    }

    /**
     * 컨트롤러 메서드명 → API_TYPE 매핑
     */
    private String resolveApiType(String methodName) {
        return switch (methodName) {
            // PsInspectionApiController
            case "saveInspection", "saveInspectionMultipart" -> "INSPECTION_SAVE";
            case "getInspection"        -> "INSPECTION_GET";
            case "listInspections"      -> "INSPECTION_LIST";
            case "searchInspections"    -> "INSPECTION_SEARCH";
            case "checkExists"          -> "INSPECTION_CHECK";
            case "deleteInspection"     -> "INSPECTION_DELETE";
            case "deleteAllInspections" -> "INSPECTION_DELETE_ALL";
            // PsInspMesController
            case "sendResult"           -> "MES_SEND";
            // PsInspConfigApiController
            case "getPpmLimit"          -> "CONFIG_PPM_GET";
            case "savePpmLimit"         -> "CONFIG_PPM_SAVE";
            case "getPpmAdmins"         -> "CONFIG_ADMIN_GET";
            case "updatePpmAdmins"      -> "CONFIG_ADMIN_SAVE";
            case "getAllConfigs"         -> "CONFIG_ALL";
            default -> "UNKNOWN_" + methodName;
        };
    }

    private HttpServletRequest getHttpRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserId(HttpServletRequest request) {
        // 1) USERID 파라미터 우선
        if (request != null) {
            String userIdParam = request.getParameter("USERID");
            if (userIdParam != null && !userIdParam.isBlank()) {
                return userIdParam.trim();
            }
        }
        // 2) Spring Security 인증 정보
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return request.getRemoteAddr();
    }

    /**
     * 요청 파라미터에서 값 추출 (여러 파라미터명 우선순위)
     */
    private String extractParam(HttpServletRequest request, String... paramNames) {
        if (request == null) return null;
        for (String name : paramNames) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
