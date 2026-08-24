package com.company.module.kims.support;

import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * KIMS 모듈 전용 예외 보정 핸들러.
 *
 * <p>core 의 {@code GlobalExceptionHandler} 는 처리되지 않은 모든 예외를
 * {@code Exception} 으로 받아 500 으로 응답한다. 그러나 다음 두 예외는
 * "잘못된 요청"이므로 400 으로 응답하는 것이 올바르다.
 * <ul>
 *   <li>{@link IllegalArgumentException} - 재고 부족, 잘못된 수량 등 업무 규칙 위반</li>
 *   <li>{@link HttpMessageNotReadableException} - 잘못된 JSON, 존재하지 않는 Enum 값 등</li>
 * </ul>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 로 core 의 핸들러보다 먼저 평가되도록 하여,
 * 위 두 예외에 한해 이 핸들러가 우선 적용되도록 한다. (그 외 예외는 그대로 core 가 처리)
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class KimsApiExceptionHandler {

    /** 업무 규칙 위반 (예: 재고 부족) → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgumentException] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(e.getMessage()));
    }

    /** 잘못된 요청 본문 (깨진 JSON, 잘못된 Enum 값 등) → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 본문을 해석할 수 없습니다. JSON 형식과 Enum 값(요청유형/상태 등)을 확인하세요."));
    }
}
