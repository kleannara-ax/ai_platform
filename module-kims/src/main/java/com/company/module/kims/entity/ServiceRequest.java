package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.IssueType;
import com.company.module.kims.entity.enums.ReceivedChannel;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 전산업무 요청 (마스터).
 * <p>요청자가 등록한 한 건의 업무 요청을 표현한다.
 * 처리 로그(RequestLog), 소모품 지급(SupplyIssue)이 이 요청을 참조한다.
 */
@Entity
@Table(name = "service_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 용 기본 생성자 (외부 직접 생성 방지)
public class ServiceRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    /** 자동 생성되는 요청번호 (예: KIMS-20260602-0001) */
    @Column(name = "REQUEST_NO", nullable = false, length = 30, unique = true)
    private String requestNo;

    @Column(name = "REQUESTER_NAME", nullable = false, length = 50)
    private String requesterName;

    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    @Column(name = "CONTACT", length = 30)
    private String contact;

    @Column(name = "LOCATION", length = 100)
    private String location;

    /** 요청유형 (Enum) - DB 에는 이름 문자열로 저장 */
    @Enumerated(EnumType.STRING)
    @Column(name = "REQUEST_TYPE", nullable = false, length = 20)
    private RequestType requestType;

    /**
     * 세부 불편유형 (요청유형이 PC관련 불편사항 조치(PROGRAM)일 때 선택).
     * <p>그 외 유형에서는 null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ISSUE_TYPE", length = 30)
    private IssueType issueType;

    /** 요청목록 (요청유형 IP 일 때: IP변경/IP신규생성/PC변경/기타변경) */
    @Enumerated(EnumType.STRING)
    @Column(name = "IP_KIND", length = 20)
    private com.company.module.kims.entity.enums.IpRequestKind ipKind;

    /** 변경자/생성자 (자동 반영 대상 사용자명) */
    @Column(name = "CHANGER_NAME", length = 100)
    private String changerName;

    /** 완료 시 반영한 변경 전 스냅샷(JSON) — 취소 시 원복용 */
    @Column(name = "APPLIED_SNAPSHOT", columnDefinition = "MEDIUMTEXT")
    private String appliedSnapshot;

    /** 상세 요청내용 (긴 텍스트) */
    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 긴급 여부 */
    @Column(name = "URGENT", nullable = false)
    private boolean urgent;

    /** 처리상태 (Enum) - 기본값 접수(RECEIVED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RequestStatus status;

    /** 처리 담당자 */
    @Column(name = "ASSIGNEE", length = 50)
    private String assignee;

    /** 접수 채널 (전화/직접/QR) - 향후 QR 확장 대비 */
    @Enumerated(EnumType.STRING)
    @Column(name = "RECEIVED_CHANNEL", nullable = false, length = 20)
    private ReceivedChannel receivedChannel;

    /** 완료 시각 (상태가 완료로 바뀔 때 자동 입력) */
    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    /**
     * 빌더를 통한 생성. 생성 시 상태는 항상 접수(RECEIVED)로 초기화된다.
     */
    @Builder
    private ServiceRequest(String requestNo, String requesterName, String department,
                           String contact, String location, RequestType requestType,
                           IssueType issueType, com.company.module.kims.entity.enums.IpRequestKind ipKind,
                           String changerName, String content, boolean urgent,
                           ReceivedChannel receivedChannel) {
        this.requestNo = requestNo;
        this.requesterName = requesterName;
        this.department = department;
        this.contact = contact;
        this.location = location;
        this.requestType = requestType;
        this.issueType = issueType;
        this.ipKind = ipKind;
        this.changerName = changerName;
        this.content = content;
        this.urgent = urgent;
        this.receivedChannel = (receivedChannel != null) ? receivedChannel : ReceivedChannel.PHONE; // 기본값: 전화
        this.status = RequestStatus.RECEIVED; // 기본값: 접수
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드 (setter 대신 사용)
    // ----------------------------------------------------------------

    /**
     * 처리상태를 변경한다.
     * <ul>
     *   <li>담당자가 함께 전달되면 담당자도 갱신한다.</li>
     *   <li>완료(COMPLETED)로 변경되면 완료 시각을 자동 입력한다.</li>
     * </ul>
     *
     * @param newStatus 변경할 상태
     * @param assignee  담당자 (null/공백이면 기존 값 유지)
     */
    public void changeStatus(RequestStatus newStatus, String assignee) {
        this.status = newStatus;

        if (assignee != null && !assignee.isBlank()) {
            this.assignee = assignee;
        }
        if (newStatus == RequestStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    /** 완료 시 자동 반영한 변경 전 스냅샷(JSON) 저장 */
    public void setAppliedSnapshot(String snapshotJson) {
        this.appliedSnapshot = snapshotJson;
    }

    /** 원복 완료 후 스냅샷 제거 */
    public void clearAppliedSnapshot() {
        this.appliedSnapshot = null;
    }
}
