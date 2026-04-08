package com.company.core.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 공통 API 응답 포맷
 * 모든 REST API 응답은 이 형식을 사용한다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 성공 여부 */
    private final boolean success;

    /** 응답 코드 (HTTP Status 또는 비즈니스 코드) */
    private final int code;

    /** 응답 메시지 */
    private final String message;

    /** 응답 데이터 */
    private final T data;

    /** 응답 시각 */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    // ──────────────────────────────────────────────
    //  Factory Methods
    // ──────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message("SUCCESS")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> success() {
        return ApiResponse.<Void>builder()
                .success(true)
                .code(200)
                .message("SUCCESS")
                .build();
    }

    public static ApiResponse<Void> created() {
        return ApiResponse.<Void>builder()
                .success(true)
                .code(201)
                .message("CREATED")
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(201)
                .message("CREATED")
                .data(data)
                .build();
    }

    public static ApiResponse<Void> error(int code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(400)
                .message(message)
                .build();
    }
}
