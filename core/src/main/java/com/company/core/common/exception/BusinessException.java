package com.company.core.common.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외의 최상위 클래스
 * 모든 업무 모듈의 예외는 이 클래스를 상속한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(String message) {
        super(message);
        this.errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
