package com.company.module.kims.repository;

import com.company.module.kims.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /**
     * 특정 요청의 처리 로그를 시간순(오래된 것 → 최신)으로 조회한다.
     * <p>메서드 이름 규칙: serviceRequest 의 requestId 로 조회 → {@code ServiceRequest_RequestId}
     */
    List<RequestLog> findByServiceRequest_RequestIdOrderByCreatedAtAsc(Long requestId);
}
