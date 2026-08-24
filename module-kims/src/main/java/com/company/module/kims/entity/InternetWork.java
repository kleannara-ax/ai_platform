package com.company.module.kims.entity;

import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
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

import java.time.LocalDate;

/**
 * 인터넷 공사/설치 내역.
 * <p>신규 설치, 자리이동 연결, LAN 포트 활성화 등 공사 건을 관리한다.
 * 외부업체/공사비 발생 여부, 진행 상태(완료일)를 기록하며, 업무 요청과 선택적으로 연결된다.
 */
@Entity
@Table(name = "internet_work")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InternetWork extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "WORK_ID")
    private Long workId;

    /** 연결된 업무 요청 (선택) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID")
    private ServiceRequest serviceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "WORK_TYPE", nullable = false, length = 20)
    private InternetWorkType workType;

    @Column(name = "REQUESTER_NAME", length = 50)
    private String requesterName;

    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    /** 공사 요청 위치 */
    @Column(name = "LOCATION", length = 100)
    private String location;

    /** 공사 내용 */
    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    /** 외부업체 사용 여부 */
    @Column(name = "EXTERNAL_VENDOR", nullable = false)
    private boolean externalVendor;

    /** 외부업체명 (외부업체 사용 시) */
    @Column(name = "VENDOR_NAME", length = 100)
    private String vendorName;

    /** 공사비 발생 여부 */
    @Column(name = "HAS_COST", nullable = false)
    private boolean hasCost;

    /** 공사비 금액 (원, 선택) */
    @Column(name = "COST")
    private Long cost;

    /** 처리 담당자 */
    @Column(name = "ASSIGNEE", length = 50)
    private String assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private InternetWorkStatus status;

    /** 완료일 (상태가 완료로 바뀔 때 자동 입력) */
    @Column(name = "COMPLETED_AT")
    private LocalDate completedAt;

    @Column(name = "REMARK", length = 255)
    private String remark;

    @Builder
    private InternetWork(ServiceRequest serviceRequest, InternetWorkType workType, String requesterName,
                         String department, String location, String content, boolean externalVendor,
                         String vendorName, boolean hasCost, Long cost, String assignee,
                         InternetWorkStatus status, LocalDate completedAt, String remark) {
        this.serviceRequest = serviceRequest;
        this.workType = workType;
        this.requesterName = requesterName;
        this.department = department;
        this.location = location;
        this.content = content;
        this.externalVendor = externalVendor;
        this.vendorName = vendorName;
        this.hasCost = hasCost;
        this.cost = cost;
        this.assignee = assignee;
        this.status = (status != null) ? status : InternetWorkStatus.REQUESTED;
        this.completedAt = completedAt;
        this.remark = remark;
        // 생성 시 이미 완료 상태이고 완료일이 없으면 오늘로 설정
        if (this.status == InternetWorkStatus.COMPLETED && this.completedAt == null) {
            this.completedAt = LocalDate.now();
        }
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    /** 공사 내역 수정 (상태/완료일은 changeStatus 로 별도 관리) */
    public void update(InternetWorkType workType, String requesterName, String department, String location,
                       String content, boolean externalVendor, String vendorName,
                       boolean hasCost, Long cost, String assignee, String remark) {
        this.workType = workType;
        this.requesterName = requesterName;
        this.department = department;
        this.location = location;
        this.content = content;
        this.externalVendor = externalVendor;
        this.vendorName = vendorName;
        this.hasCost = hasCost;
        this.cost = cost;
        this.assignee = assignee;
        this.remark = remark;
    }

    /**
     * 상태 변경. 완료로 바뀌면 완료일을 입력(미지정 시 오늘), 완료가 아니면 완료일을 비운다.
     */
    public void changeStatus(InternetWorkStatus newStatus, LocalDate completedAt) {
        this.status = newStatus;
        if (newStatus == InternetWorkStatus.COMPLETED) {
            this.completedAt = (completedAt != null) ? completedAt : LocalDate.now();
        } else {
            this.completedAt = null;
        }
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }
}
