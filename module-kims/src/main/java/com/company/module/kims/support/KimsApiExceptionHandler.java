package com.company.module.kims.support;

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
 * <p>core 의 {@code GlobalExceptionHandler} 는 {@code HttpMessageNotReadableException}
 * (깨진 JSON, 존재하지 않는 Enum 값 등)에 대응하는 핸들러가 없어 이를 500 으로 처리해버린다.
 * 이는 실제로는 "잘못된 요청"이므로 400 으로 응답하는 것이 올바르며, 이 핸들러가 보완한다.
 *
 * <p>업무 규칙 위반(재고 부족 등)은 core 표준에 따라 모두
 * {@code com.company.core.common.exception.BusinessException} 으로 던지도록 전환되었으므로,
 * core 의 {@code GlobalExceptionHandler} 가 처리한다. 이 모듈에서 별도 핸들러가 필요하지 않다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 로 core 의 핸들러보다 먼저 평가되도록 한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class KimsApiExceptionHandler {

    /** 잘못된 요청 본문 (깨진 JSON, 잘못된 Enum 값 등) → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 본문을 해석할 수 없습니다. JSON 형식과 Enum 값(요청유형/상태 등)을 확인하세요."));
    }
}
