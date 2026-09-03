package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.RequestLogType;
import com.company.module.kims.entity.enums.RequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 처리 로그.
 * <p>요청 등록, 상태 변경, 처리내용 입력 등의 이력을 한 줄씩 기록한다.
 * 요청 상세 화면에서 시간순으로 조회된다.
 */
@Entity
@Table(name = "request_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    /**
     * 로그가 속한 업무 요청.
     * <p>지연 로딩(LAZY): 실제로 접근할 때만 요청 정보를 조회한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID", nullable = false)
    private ServiceRequest serviceRequest;

    /** 로그 종류 (등록/상태변경/처리내용) */
    @Enumerated(EnumType.STRING)
    @Column(name = "LOG_TYPE", nullable = false, length = 20)
    private RequestLogType logType;

    /** 변경 전 상태 (상태변경 로그에서만 사용) */
    @Enumerated(EnumType.STRING)
    @Column(name = "BEFORE_STATUS", length = 20)
    private RequestStatus beforeStatus;

    /** 변경 후 상태 (상태변경 로그에서만 사용) */
    @Enumerated(EnumType.STRING)
    @Column(name = "AFTER_STATUS", length = 20)
    private RequestStatus afterStatus;

    /** 변경자/작성자 */
    @Column(name = "CHANGED_BY", nullable = false, length = 50)
    private String changedBy;

    /** 변경 사유 */
    @Column(name = "REASON", length = 255)
    private String reason;

    /** 처리내용/메모 (긴 텍스트) */
    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    @Builder
    private RequestLog(ServiceRequest serviceRequest, RequestLogType logType,
                       RequestStatus beforeStatus, RequestStatus afterStatus,
                       String changedBy, String reason, String content) {
        this.serviceRequest = serviceRequest;
        this.logType = logType;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.content = content;
    }

    // ----------------------------------------------------------------
    // 정적 팩토리 메서드 - 상황별 로그를 쉽게 생성
    // ----------------------------------------------------------------

    /** 요청 등록 로그 */
    public static RequestLog forCreated(ServiceRequest request, String changedBy) {
        return RequestLog.builder()
                .serviceRequest(request)
                .logType(RequestLogType.CREATED)
                .changedBy(changedBy)
                .content("업무 요청이 등록되었습니다.")
                .build();
    }

    /** 상태 변경 로그 */
    public static RequestLog forStatusChange(ServiceRequest request, RequestStatus before,
                                             RequestStatus after, String changedBy, String reason) {
        return RequestLog.builder()
                .serviceRequest(request)
                .logType(RequestLogType.STATUS_CHANGED)
                .beforeStatus(before)
                .afterStatus(after)
                .changedBy(changedBy)
                .reason(reason)
                .build();
    }

    /** 처리내용/메모 로그 */
    public static RequestLog forNote(ServiceRequest request, String changedBy, String content) {
        return RequestLog.builder()
                .serviceRequest(request)
                .logType(RequestLogType.PROCESS_NOTE)
                .changedBy(changedBy)
                .content(content)
                .build();
    }
}
