package com.company.core.common.exception;

/**
 * 엔티티 조회 실패 시 발생하는 예외
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public EntityNotFoundException(String entityName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
                String.format("%s(id=%s)를 찾을 수 없습니다.", entityName, id));
    }
}
