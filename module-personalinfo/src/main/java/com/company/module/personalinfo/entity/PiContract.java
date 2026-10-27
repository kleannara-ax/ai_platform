package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 개인정보 위수탁 계약 (외부위탁 현황 리스트 1행 = 1건).
 *
 * <p>상태 변경은 {@link #update}, {@link #terminate}, {@link #reopen}, {@link #delete},
 * {@link #restore}, {@link #applyInspectionResult} 비즈니스 메서드로만 한다.
 */
@Entity
@Table(name = "pi_contract")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiContract extends PiBaseEntity {

    /** 알림 기준일 기본값 (만료 N일 전) */
    public static final int DEFAULT_ALERT_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTRACT_ID")
    private Long contractId;

    @Column(name = "ISMS_SCOPE_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String ismsScopeYn;

    @Column(name = "VENDOR_NAME", nullable = false, length = 200)
    private String vendorName;

    @Column(name = "SERVICE_NAME", length = 200)
    private String serviceName;

    @Column(name = "ENTRUSTED_TASK", nullable = false, length = 1000)
    private String entrustedTask;

    @Column(name = "DEPT_NAME", length = 100)
    private String deptName;

    @Column(name = "MANAGER_NAME", length = 100)
    private String managerName;

    @Column(name = "MANAGER_EMAIL", length = 200)
    private String managerEmail;

    @Column(name = "VENDOR_CONTACT_NAME", length = 100)
    private String vendorContactName;

    @Column(name = "VENDOR_CONTACT_EMAIL", length = 200)
    private String vendorContactEmail;

    @Column(name = "VENDOR_CONTACT_PHONE", length = 50)
    private String vendorContactPhone;

    @Column(name = "CONTRACT_START_DATE")
    private LocalDate contractStartDate;

    @Column(name = "CONTRACT_END_DATE")
    private LocalDate contractEndDate;

    @Column(name = "CONTRACT_PERIOD_NOTE", length = 500)
    private String contractPeriodNote;

    @Column(name = "AUTO_RENEW_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String autoRenewYn;

    @Column(name = "AUTO_RENEW_NOTE", length = 200)
    private String autoRenewNote;

    @Column(name = "CONTRACT_DOC_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String contractDocYn;

    @Column(name = "CONTRACT_SIGNED_DATE")
    private LocalDate contractSignedDate;

    @Column(name = "SUBCONTRACT_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String subcontractYn;

    @Column(name = "SUBCONTRACTOR_NAME", length = 500)
    private String subcontractorName;

    @Column(name = "SUBCONTRACT_TASK", length = 1000)
    private String subcontractTask;

    @Column(name = "SUBCONTRACT_CONSENT_YN", length = 1, columnDefinition = "CHAR(1)")
    private String subcontractConsentYn;

    @Column(name = "ALERT_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String alertYn;

    @Column(name = "ALERT_DAYS_BEFORE", nullable = false)
    private Integer alertDaysBefore;

    @Column(name = "ALERT_EXTRA_EMAILS", length = 1000)
    private String alertExtraEmails;

    @Column(name = "TERMINATED_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String terminatedYn;

    @Column(name = "TERMINATED_DATE")
    private LocalDate terminatedDate;

    @Column(name = "TERMINATION_REASON", length = 500)
    private String terminationReason;

    @Column(name = "LAST_INSPECTION_ID")
    private Long lastInspectionId;

    @Column(name = "LAST_INSPECTION_DATE")
    private LocalDate lastInspectionDate;

    @Column(name = "LAST_INSPECTION_RESULT", length = 20)
    private String lastInspectionResult;

    @Column(name = "REMARK", length = 2000)
    private String remark;

    @Builder
    private PiContract(String ismsScopeYn, String vendorName, String serviceName, String entrustedTask,
                       String deptName, String managerName, String managerEmail,
                       String vendorContactName, String vendorContactEmail, String vendorContactPhone,
                       LocalDate contractStartDate, LocalDate contractEndDate, String contractPeriodNote,
                       String autoRenewYn, String autoRenewNote, String contractDocYn, LocalDate contractSignedDate,
                       String subcontractYn, String subcontractorName, String subcontractTask, String subcontractConsentYn,
                       String alertYn, Integer alertDaysBefore, String alertExtraEmails, String remark,
                       Long createdBy) {
        this.ismsScopeYn = yn(ismsScopeYn, "Y");
        this.vendorName = vendorName;
        this.serviceName = serviceName;
        this.entrustedTask = entrustedTask;
        this.deptName = deptName;
        this.managerName = managerName;
        this.managerEmail = managerEmail;
        this.vendorContactName = vendorContactName;
        this.vendorContactEmail = vendorContactEmail;
        this.vendorContactPhone = vendorContactPhone;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.contractPeriodNote = contractPeriodNote;
        this.autoRenewYn = yn(autoRenewYn, "N");
        this.autoRenewNote = autoRenewNote;
        this.contractDocYn = yn(contractDocYn, "N");
        this.contractSignedDate = contractSignedDate;
        this.subcontractYn = yn(subcontractYn, "N");
        this.subcontractorName = subcontractorName;
        this.subcontractTask = subcontractTask;
        this.subcontractConsentYn = subcontractConsentYn;
        this.alertYn = yn(alertYn, "Y");
        this.alertDaysBefore = alertDaysBefore == null ? DEFAULT_ALERT_DAYS : alertDaysBefore;
        this.alertExtraEmails = alertExtraEmails;
        this.remark = remark;
        this.terminatedYn = "N";
        markCreatedBy(createdBy);
    }

    /** 계약 정보 수정 */
    public void update(String ismsScopeYn, String vendorName, String serviceName, String entrustedTask,
                       String deptName, String managerName, String managerEmail,
                       String vendorContactName, String vendorContactEmail, String vendorContactPhone,
                       LocalDate contractStartDate, LocalDate contractEndDate, String contractPeriodNote,
                       String autoRenewYn, String autoRenewNote, String contractDocYn, LocalDate contractSignedDate,
                       String subcontractYn, String subcontractorName, String subcontractTask, String subcontractConsentYn,
                       String alertYn, Integer alertDaysBefore, String alertExtraEmails, String remark,
                       Long updatedBy) {
        this.ismsScopeYn = yn(ismsScopeYn, this.ismsScopeYn);
        this.vendorName = vendorName;
        this.serviceName = serviceName;
        this.entrustedTask = entrustedTask;
        this.deptName = deptName;
        this.managerName = managerName;
        this.managerEmail = managerEmail;
        this.vendorContactName = vendorContactName;
        this.vendorContactEmail = vendorContactEmail;
        this.vendorContactPhone = vendorContactPhone;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.contractPeriodNote = contractPeriodNote;
        this.autoRenewYn = yn(autoRenewYn, this.autoRenewYn);
        this.autoRenewNote = autoRenewNote;
        this.contractDocYn = yn(contractDocYn, this.contractDocYn);
        this.contractSignedDate = contractSignedDate;
        this.subcontractYn = yn(subcontractYn, this.subcontractYn);
        this.subcontractorName = subcontractorName;
        this.subcontractTask = subcontractTask;
        this.subcontractConsentYn = subcontractConsentYn;
        this.alertYn = yn(alertYn, this.alertYn);
        this.alertDaysBefore = alertDaysBefore == null ? this.alertDaysBefore : alertDaysBefore;
        this.alertExtraEmails = alertExtraEmails;
        this.remark = remark;
        markUpdatedBy(updatedBy);
    }

    /** ISMS 인증 범위 여부 변경 (목록 화면 Y/N 바로 변경) */
    public void changeIsmsScope(String ismsScopeYn, Long updatedBy) {
        this.ismsScopeYn = yn(ismsScopeYn, this.ismsScopeYn);
        markUpdatedBy(updatedBy);
    }

    /**
     * 재위탁 여부 변경 (목록 화면 Y/N 바로 변경).
     * 재위탁 업체명·업무·동의 여부는 지우지 않는다(N → Y 로 되돌릴 때 다시 쓰고, 이력도 남긴다).
     */
    public void changeSubcontract(String subcontractYn, Long updatedBy) {
        this.subcontractYn = yn(subcontractYn, this.subcontractYn);
        markUpdatedBy(updatedBy);
    }

    /** 계약 종료 처리 (위탁업무 종료) */
    public void terminate(LocalDate terminatedDate, String reason, Long updatedBy) {
        this.terminatedYn = "Y";
        this.terminatedDate = terminatedDate == null ? LocalDate.now() : terminatedDate;
        this.terminationReason = reason;
        markUpdatedBy(updatedBy);
    }

    /** 종료 처리 취소 */
    public void reopen(Long updatedBy) {
        this.terminatedYn = "N";
        this.terminatedDate = null;
        this.terminationReason = null;
        markUpdatedBy(updatedBy);
    }

    /** 최근 점검 결과 반영 (목록 화면 표시용 비정규화 컬럼) */
    public void applyInspectionResult(Long inspectionId, LocalDate inspectionDate, InspectionResult result, Long updatedBy) {
        this.lastInspectionId = inspectionId;
        this.lastInspectionDate = inspectionDate;
        this.lastInspectionResult = result == null ? null : result.name();
        markUpdatedBy(updatedBy);
    }

    /** 최근 점검 정보 초기화 (최근 점검이 삭제된 경우) */
    public void clearInspectionResult(Long updatedBy) {
        applyInspectionResult(null, null, null, updatedBy);
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }

    public void restore(Long updatedBy) {
        restoreDeleted(updatedBy);
    }

    private static String yn(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "Y".equalsIgnoreCase(value.trim()) ? "Y" : "N";
    }
}
