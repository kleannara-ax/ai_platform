package com.company.module.kims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 프로그램 설치 내역.
 * <p>특정 PC에 어떤 프로그램(SAP, PowerPoint, Klogi 등)을 누가 언제 설치했는지 기록한다.
 * 업무 요청과 연계해 처리한 경우 {@link #serviceRequest} 로 연결된다(선택).
 */
@Entity
@Table(name = "program_install")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramInstall extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSTALL_ID")
    private Long installId;

    /** 연결된 업무 요청 (선택) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID")
    private ServiceRequest serviceRequest;

    /** 설치 프로그램명 (예: SAP, PowerPoint, Klogi) */
    @Column(name = "PROGRAM_NAME", nullable = false, length = 100)
    private String programName;

    /** 설치 요청자 */
    @Column(name = "REQUESTER_NAME", length = 50)
    private String requesterName;

    @Column(name = "DEPARTMENT", length = 50)
    private String department;

    /** 설치 대상 PC */
    @Column(name = "TARGET_PC", length = 100)
    private String targetPc;

    /** 설치 담당자 */
    @Column(name = "INSTALLED_BY", nullable = false, length = 50)
    private String installedBy;

    /** 설치일 */
    @Column(name = "INSTALLED_AT", nullable = false)
    private LocalDate installedAt;

    @Column(name = "REMARK", length = 255)
    private String remark;

    @Builder
    private ProgramInstall(ServiceRequest serviceRequest, String programName, String requesterName,
                           String department, String targetPc, String installedBy,
                           LocalDate installedAt, String remark) {
        this.serviceRequest = serviceRequest;
        this.programName = programName;
        this.requesterName = requesterName;
        this.department = department;
        this.targetPc = targetPc;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
        this.remark = remark;
    }

    /** 설치 내역 수정 (요청 연결은 변경하지 않음) */
    public void update(String programName, String requesterName, String department,
                       String targetPc, String installedBy, LocalDate installedAt, String remark) {
        this.programName = programName;
        this.requesterName = requesterName;
        this.department = department;
        this.targetPc = targetPc;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
        this.remark = remark;
    }
}
