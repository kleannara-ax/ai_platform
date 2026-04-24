package com.company.module.psinsp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * PS 지분검사 API 통신 로그 엔티티
 *
 * <p>Table: ps_insp_api_log
 * <p>모든 PS-INSP API 호출(Inbound) 및 MES 전송(Outbound) 이력을 기록한다.
 *
 * <p>API_TYPE 코드:
 * <ul>
 *   <li>INSPECTION_SAVE - 검사 결과 저장 (POST /inspections)</li>
 *   <li>INSPECTION_GET - 검사 단건 조회 (GET /inspections/{id})</li>
 *   <li>INSPECTION_LIST - 검사 목록 조회 (GET /inspections)</li>
 *   <li>INSPECTION_SEARCH - 검사 검색 (GET /inspections/search)</li>
 *   <li>INSPECTION_CHECK - 중복 체크 (GET /inspections/check-exists)</li>
 *   <li>INSPECTION_DELETE - 검사 삭제 (DELETE /inspections/{id})</li>
 *   <li>INSPECTION_DELETE_ALL - 전체 삭제 (DELETE /inspections)</li>
 *   <li>MES_SEND - MES 결과 전송 (POST /mes/send-result → 외부 MES)</li>
 *   <li>CONFIG_PPM_GET - PPM 기준값 조회 (GET /config/ppm-limit)</li>
 *   <li>CONFIG_PPM_SAVE - PPM 기준값 저장 (POST /config/ppm-limit)</li>
 *   <li>CONFIG_ADMIN_GET - 권한자 조회 (GET /config/ppm-admins)</li>
 *   <li>CONFIG_ADMIN_SAVE - 권한자 수정 (POST /config/ppm-admins)</li>
 *   <li>CONFIG_ALL - 전체 설정 조회 (GET /config)</li>
 * </ul>
 */
@Entity
@Table(name = "ps_insp_api_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    /** 통신 방향: IN(수신), OUT(외부 발신) */
    @Column(name = "DIRECTION", nullable = false, length = 10)
    private String direction;

    /** API 유형 코드 (INSPECTION_SAVE, MES_SEND 등) */
    @Column(name = "API_TYPE", nullable = false, length = 50)
    private String apiType;

    @Column(name = "HTTP_METHOD", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "REQUEST_URI", nullable = false, length = 1000)
    private String requestUri;

    @Column(name = "QUERY_STRING", length = 2000)
    private String queryString;

    /** 외부 전송 대상 URL (OUT일 때만) */
    @Column(name = "TARGET_URL", length = 2000)
    private String targetUrl;

    @Column(name = "REQUEST_BODY", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "HTTP_STATUS")
    private Integer httpStatus;

    @Column(name = "SUCCESS", nullable = false)
    private Boolean success;

    @Column(name = "RESPONSE_BODY", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    /** 개별바코드 (검사 식별용, 빠른 필터링) */
    @Column(name = "IND_BCD", length = 200)
    private String indBcd;

    @Column(name = "MATNR", length = 100)
    private String matnr;

    @Column(name = "LOTNR", length = 200)
    private String lotnr;

    /** 요청 사용자 ID */
    @Column(name = "USER_ID", length = 200)
    private String userId;

    @Column(name = "CLIENT_IP", length = 100)
    private String clientIp;

    /** 처리 시간 (밀리초) */
    @Column(name = "ELAPSED_MS")
    private Long elapsedMs;

    @Column(name = "REQUESTED_AT", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now(KST);
        }
        this.createdAt = LocalDateTime.now(KST);
    }
}
