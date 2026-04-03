package com.company.core.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 공통 로깅 AOP
 * 모든 모듈의 Controller, Service 레이어에 자동 적용된다.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * com.company 하위의 모든 Controller 클래스
     */
    @Pointcut("within(com.company..*.controller..*)")
    public void controllerPointcut() {}

    /**
     * com.company 하위의 모든 Service 클래스
     */
    @Pointcut("within(com.company..*.service..*)")
    public void servicePointcut() {}

    /**
     * Controller 요청/응답 로깅
     */
    @Around("controllerPointcut()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        String uri = request != null ? request.getRequestURI() : "N/A";
        String httpMethod = request != null ? request.getMethod() : "N/A";

        log.info("[REQ] {} {} → {}.{}() args={}",
                httpMethod, uri, className, methodName,
                Arrays.toString(joinPoint.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;

            log.info("[RES] {} {} → {}.{}() {}ms",
                    httpMethod, uri, className, methodName, elapsed);

            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ERR] {} {} → {}.{}() {}ms exception={}",
                    httpMethod, uri, className, methodName, elapsed, e.getMessage());
            throw e;
        }
    }

    /**
     * Service 실행 로깅 (DEBUG 레벨)
     */
    @Around("servicePointcut()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.debug("[SVC] {}.{}() START", className, methodName);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;

            log.debug("[SVC] {}.{}() END ({}ms)", className, methodName, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[SVC] {}.{}() FAIL ({}ms) exception={}",
                    className, methodName, elapsed, e.getMessage());
            throw e;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
