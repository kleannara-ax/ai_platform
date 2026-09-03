package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.IpChangeType;
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
 * IP 변경 이력.
 * <p>생성/정보변경/사용자변경/회수가 발생할 때마다 한 줄씩 기록한다.
 * 품의 여부(approved)를 함께 남겨 "미품의 변경 내역"을 추적할 수 있다.
 * <p>업무 요청과 연계해 처리한 경우 {@link #serviceRequest} 로 연결된다(선택).
 */
@Entity
@Table(name = "ip_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IpHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IP_ID", nullable = false)
    private IpAddress ipAddress;

    /** 연결된 업무 요청 (선택) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID")
    private ServiceRequest serviceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHANGE_TYPE", nullable = false, length = 20)
    private IpChangeType changeType;

    /** 변경 내용 설명 */
    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    /** 이 변경이 품의된 것인지 여부 (false = 미품의 변경) */
    @Column(name = "APPROVED", nullable = false)
    private boolean approved;

    @Column(name = "APPROVAL_NO", length = 50)
    private String approvalNo;

    @Column(name = "CHANGED_BY", nullable = false, length = 50)
    private String changedBy;

    /** 정보변경(IP 이동) 시 변경 전 IP (선택) */
    @Column(name = "BEFORE_IP", length = 45)
    private String beforeIp;

    /** 정보변경(IP 이동) 시 변경 후 IP (선택) */
    @Column(name = "AFTER_IP", length = 45)
    private String afterIp;

    /** 이 이력 시점의 전체 IP (대역+끝자리) — 장비가 이후 이동해도 당시 IP 보존 */
    @Column(name = "SNAPSHOT_IP", length = 45)
    private String snapshotIp;

    /** 변경 전 사용자 (이력 시점 기준) */
    @Column(name = "BEFORE_USER", length = 100)
    private String beforeUser;

    /** 변경 후 사용자 (이력 시점 기준) */
    @Column(name = "AFTER_USER", length = 100)
    private String afterUser;

    @Builder
    private IpHistory(IpAddress ipAddress, ServiceRequest serviceRequest, IpChangeType changeType,
                      String content, boolean approved, String approvalNo, String changedBy,
                      String beforeIp, String afterIp, String beforeUser, String afterUser) {
        this.ipAddress = ipAddress;
        this.serviceRequest = serviceRequest;
        this.changeType = changeType;
        this.content = content;
        this.approved = approved;
        this.approvalNo = approvalNo;
        this.changedBy = changedBy;
        this.beforeIp = beforeIp;
        this.afterIp = afterIp;
        this.beforeUser = beforeUser;
        this.afterUser = afterUser;
    }

    public static IpHistory of(IpAddress ip, ServiceRequest request, IpChangeType type,
                               String content, boolean approved, String approvalNo, String changedBy) {
        return IpHistory.builder()
                .ipAddress(ip)
                .serviceRequest(request)
                .changeType(type)
                .content(content)
                .approved(approved)
                .approvalNo(approvalNo)
                .changedBy(changedBy)
                .build();
    }
}
