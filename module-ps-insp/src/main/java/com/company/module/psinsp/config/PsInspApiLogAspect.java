package com.company.module.psinsp.config;

import com.company.module.psinsp.entity.PsInspApiLog;
import com.company.module.psinsp.service.PsInspApiLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.time.LocalTime;
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
    private final ObjectMapper objectMapper;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * PsInspectionApiController + PsInspConfigApiController + PsInspMesController 대상
     */
    @Around("execution(* com.company.module.psinsp.controller.PsInspectionApiController.*(..)) || " +
            "execution(* com.company.module.psinsp.controller.PsInspConfigApiController.*(..)) || " +
            "execution(* com.company.module.psinsp.controller.PsInspMesController.*(..))")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {

        HttpServletRequest request = getHttpRequest();
        String apiType = resolveApiType(joinPoint.getSignature().getName());
        String ip = extractClientIp(request);
        String creusr = extractUserId(request);
        String parameterIn = buildInputParameter(request);

        Object result = null;
        String comstat = "S";
        String errtxt = null;
        String inerrat = null;
        String inerrtxt = null;
        String parameterOut = null;
        String remark = null;

        try {
            result = joinPoint.proceed();

            // ResponseEntity에서 응답 추출
            if (result instanceof ResponseEntity<?> re) {
                int status = re.getStatusCode().value();
                if (!re.getStatusCode().is2xxSuccessful()) {
                    comstat = "E";
                    inerrat = "HTTP_" + status;
                    errtxt = "HTTP " + status + " 응답";
                }
                try {
                    Object body = re.getBody();
                    if (body != null) {
                        parameterOut = PsInspApiLogService.truncate(
                                objectMapper.writeValueAsString(body), 60000);
                    }
                } catch (Exception ignored) {}
            }

            return result;

        } catch (org.springframework.security.access.AccessDeniedException ex) {
            comstat = "E";
            inerrat = "AUTH_FAIL";
            errtxt = "접근 권한 없음";
            inerrtxt = PsInspApiLogService.truncate(ex.getMessage(), 2000);
            throw ex;

        } catch (org.springframework.web.bind.MethodArgumentNotValidException ex) {
            comstat = "E";
            inerrat = "VALIDATION_ERROR";
            errtxt = "입력값 검증 실패";
            inerrtxt = PsInspApiLogService.truncate(ex.getMessage(), 2000);
            throw ex;

        } catch (org.springframework.dao.DataAccessException ex) {
            comstat = "E";
            inerrat = "DB_ERROR";
            errtxt = "데이터베이스 오류";
            inerrtxt = PsInspApiLogService.truncate(ex.getMessage(), 2000);
            throw ex;

        } catch (Throwable ex) {
            comstat = "E";
            inerrat = "UNKNOWN_ERROR";
            errtxt = PsInspApiLogService.truncate(ex.getMessage(), 2000);
            inerrtxt = PsInspApiLogService.truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 2000);
            throw ex;

        } finally {
            remark = buildRemark(apiType, comstat);

            PsInspApiLog logEntity = PsInspApiLog.builder()
                    .api(apiType)
                    .ip(ip)
                    .comstat(comstat)
                    .errtxt(errtxt)
                    .credat(LocalDate.now(KST))
                    .cretim(LocalTime.now(KST))
                    .creusr(creusr)
                    .remark(remark)
                    .inerrat(inerrat)
                    .inerrtxt(inerrtxt)
                    .inParameter(PsInspApiLogService.truncate(parameterIn, 60000))
                    .outParameter(PsInspApiLogService.truncate(parameterOut, 60000))
                    .build();

            apiLogService.saveLog(logEntity);
        }
    }

    /**
     * 컨트롤러 메서드명 → API 코드 매핑
     */
    private String resolveApiType(String methodName) {
        return switch (methodName) {
            case "saveInspection", "saveInspectionMultipart" -> "INSPECTION_SAVE";
            case "getInspection"        -> "INSPECTION_GET";
            case "listInspections"      -> "INSPECTION_LIST";
            case "searchInspections"    -> "INSPECTION_SEARCH";
            case "checkExists"          -> "INSPECTION_CHECK";
            case "deleteInspection"     -> "INSPECTION_DELETE";
            case "deleteAllInspections" -> "INSPECTION_DELETE_ALL";
            case "sendResult"           -> "MES_SEND";
            case "getPpmLimit"          -> "CONFIG_PPM_GET";
            case "savePpmLimit"         -> "CONFIG_PPM_SAVE";
            case "getPpmAdmins"         -> "CONFIG_ADMIN_GET";
            case "updatePpmAdmins"      -> "CONFIG_ADMIN_SAVE";
            case "getAllConfigs"         -> "CONFIG_ALL";
            default -> "UNKNOWN_" + methodName;
        };
    }

    /**
     * INPUT 파라미터 조합 (쿼리 파라미터 + URI)
     */
    private String buildInputParameter(HttpServletRequest request) {
        if (request == null) return null;

        StringBuilder sb = new StringBuilder();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String qs = request.getQueryString();

        sb.append(method).append(" ").append(uri);
        if (qs != null && !qs.isBlank()) {
            // USERID, _t 파라미터는 인증용이므로 제외
            String filtered = filterAuthParams(qs);
            if (!filtered.isBlank()) {
                sb.append("?").append(filtered);
            }
        }

        return sb.toString();
    }

    /**
     * 인증 관련 파라미터 필터링 (로그에 토큰 노출 방지)
     */
    private String filterAuthParams(String queryString) {
        if (queryString == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String param : queryString.split("&")) {
            String key = param.split("=")[0];
            if ("_t".equals(key)) continue;  // JWT 토큰 제외
            if (sb.length() > 0) sb.append("&");
            sb.append(param);
        }
        return sb.toString();
    }

    /**
     * 자동 비고 생성
     */
    private String buildRemark(String apiType, String comstat) {
        String desc = switch (apiType) {
            case "INSPECTION_SAVE"       -> "검사 결과 저장";
            case "INSPECTION_GET"        -> "검사 단건 조회";
            case "INSPECTION_LIST"       -> "검사 목록 조회";
            case "INSPECTION_SEARCH"     -> "검사 검색";
            case "INSPECTION_CHECK"      -> "검사 중복 체크";
            case "INSPECTION_DELETE"     -> "검사 삭제";
            case "INSPECTION_DELETE_ALL" -> "검사 전체 삭제";
            case "MES_SEND"              -> "MES 결과 전송 요청";
            case "CONFIG_PPM_GET"        -> "PPM 기준값 조회";
            case "CONFIG_PPM_SAVE"       -> "PPM 기준값 저장";
            case "CONFIG_ADMIN_GET"      -> "권한자 목록 조회";
            case "CONFIG_ADMIN_SAVE"     -> "권한자 목록 수정";
            case "CONFIG_ALL"            -> "전체 설정 조회";
            default -> apiType;
        };
        String statusText = switch (comstat) {
            case "S" -> "성공";
            case "E" -> "실패";
            case "P" -> "처리 중";
            default -> comstat;
        };
        return desc + " - " + statusText;
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
        if (request != null) {
            String userIdParam = request.getParameter("USERID");
            if (userIdParam != null && !userIdParam.isBlank()) {
                return userIdParam.trim();
            }
        }
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
}
