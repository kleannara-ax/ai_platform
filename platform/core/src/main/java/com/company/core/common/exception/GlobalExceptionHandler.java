package com.company.core.common.exception;

import com.company.core.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 글로벌 예외 처리 핸들러
 * 모든 모듈의 예외를 공통으로 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation 예외 (@Valid, @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        log.warn("Validation failed: URI={}, errors={}", request.getRequestURI(), e.getBindingResult());

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_INPUT_VALUE.getMessage(),
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * @ModelAttribute Binding 예외
     */
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(
            BindException e, HttpServletRequest request) {

        log.warn("Bind failed: URI={}", request.getRequestURI());

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_INPUT_VALUE.getMessage(),
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 타입 불일치 예외
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        log.warn("Type mismatch: URI={}, param={}", request.getRequestURI(), e.getName());

        return ResponseEntity.badRequest().body(
                ApiResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        ErrorCode.INVALID_TYPE_VALUE.getMessage()
                )
        );
    }

    /**
     * HTTP Method 불일치
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        log.warn("Method not allowed: URI={}, method={}", request.getRequestURI(), e.getMethod());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ApiResponse.error(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        ErrorCode.METHOD_NOT_ALLOWED.getMessage()
                )
        );
    }

    /**
     * Spring Security 인증 예외
     */
    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException e, HttpServletRequest request) {

        log.warn("Authentication failed: URI={}, message={}", request.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(
                        HttpStatus.UNAUTHORIZED.value(),
                        ErrorCode.AUTHENTICATION_FAILED.getMessage()
                )
        );
    }

    /**
     * Spring Security 권한 예외
     */
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException e, HttpServletRequest request) {

        log.warn("Access denied: URI={}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiResponse.error(
                        HttpStatus.FORBIDDEN.value(),
                        ErrorCode.ACCESS_DENIED.getMessage()
                )
        );
    }

    /**
     * 비즈니스 예외 (커스텀)
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e, HttpServletRequest request) {

        ErrorCode errorCode = e.getErrorCode();
        log.warn("Business exception: URI={}, code={}, message={}",
                request.getRequestURI(), errorCode.getCode(), e.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(
                ApiResponse.error(errorCode.getHttpStatus().value(), e.getMessage())
        );
    }

    /**
     * 정적 리소스 Not Found (favicon.ico 등)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {

        log.debug("Resource not found: URI={}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error(
                        HttpStatus.NOT_FOUND.value(),
                        "요청한 리소스를 찾을 수 없습니다."
                )
        );
    }

    /**
     * 그 외 모든 예외 (최후 방어선)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {

        log.error("Unhandled exception: URI={}", request.getRequestURI(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                )
        );
    }
}
