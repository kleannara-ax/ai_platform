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

    /** 자동연장 기간 기본값 (개월, 12 = 1년 단위) */
    public static final int DEFAULT_AUTO_RENEW_MONTHS = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTRACT_ID")
    private Long contractId;

    @Column(name = "ISMS_SCOPE_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String ismsScopeYn;

    @Column(name = "VENDOR_NAME", nullable = false, length = 200)
    private String vendorName;

    /** 계약명 (한 업체의 여러 계약 구분. 예: 본사 물류 위탁계약, 부산지사 물류 위탁계약) */
    @Column(name = "CONTRACT_NAME", length = 200)
    private String contractName;

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

    /** 자동연장 기간 (개월). 원계약 종료일 이후 이 기간 단위로 계약기간이 자동 연장된다 (기본 12 = 1년) */
    @Column(name = "AUTO_RENEW_PERIOD_MONTHS", nullable = false)
    private Integer autoRenewPeriodMonths;

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

    /** 만료 서류(만료 점검 체크리스트·확약서·파기 확인서)를 다 받지 못한 채 종료 처리한 사유 */
    @Column(name = "EXPIRY_DOCS_NOTE", length = 500)
    private String expiryDocsNote;

    /** 미수취 만료 서류 독촉 메일 계속 발송 여부 (Y/N, 기본 Y) */
    @Column(name = "EXPIRY_DOCS_REMIND_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String expiryDocsRemindYn = "Y";

    @Column(name = "LAST_INSPECTION_ID")
    private Long lastInspectionId;

    @Column(name = "LAST_INSPECTION_DATE")
    private LocalDate lastInspectionDate;

    @Column(name = "LAST_INSPECTION_RESULT", length = 20)
    private String lastInspectionResult;

    @Column(name = "REMARK", length = 2000)
    private String remark;

    @Builder
    private PiContract(String ismsScopeYn, String vendorName, String contractName, String serviceName, String entrustedTask,
                       String deptName, String managerName, String managerEmail,
                       String vendorContactName, String vendorContactEmail, String vendorContactPhone,
                       LocalDate contractStartDate, LocalDate contractEndDate, String contractPeriodNote,
                       String autoRenewYn, String autoRenewNote, Integer autoRenewPeriodMonths,
                       String contractDocYn, LocalDate contractSignedDate,
                       String subcontractYn, String subcontractorName, String subcontractTask, String subcontractConsentYn,
                       String alertYn, Integer alertDaysBefore, String alertExtraEmails, String remark,
                       Long createdBy) {
        this.ismsScopeYn = yn(ismsScopeYn, "Y");
        this.vendorName = vendorName;
        this.contractName = contractName;
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
        this.autoRenewPeriodMonths = autoRenewPeriodMonths == null ? DEFAULT_AUTO_RENEW_MONTHS : autoRenewPeriodMonths;
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
        this.expiryDocsRemindYn = "Y";
        markCreatedBy(createdBy);
    }

    /** 계약 정보 수정 */
    public void update(String ismsScopeYn, String vendorName, String contractName, String serviceName, String entrustedTask,
                       String deptName, String managerName, String managerEmail,
                       String vendorContactName, String vendorContactEmail, String vendorContactPhone,
                       LocalDate contractStartDate, LocalDate contractEndDate, String contractPeriodNote,
                       String autoRenewYn, String autoRenewNote, Integer autoRenewPeriodMonths,
                       String contractDocYn, LocalDate contractSignedDate,
                       String subcontractYn, String subcontractorName, String subcontractTask, String subcontractConsentYn,
                       String alertYn, Integer alertDaysBefore, String alertExtraEmails, String remark,
                       Long updatedBy) {
        this.ismsScopeYn = yn(ismsScopeYn, this.ismsScopeYn);
        this.vendorName = vendorName;
        this.contractName = contractName;
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
        this.autoRenewPeriodMonths = autoRenewPeriodMonths == null ? this.autoRenewPeriodMonths : autoRenewPeriodMonths;
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

    /**
     * 개인정보처리 위탁계약 체결일을 최신 위수탁 계약서의 체결일로 맞춘다.
     * 계약서가 있다는 뜻이므로 개인정보처리 위탁계약서 작성 여부도 Y 로 바꾼다.
     */
    public void applyLatestContractDocDate(LocalDate latestSignedDate, Long updatedBy) {
        if (latestSignedDate == null) {
            return;
        }
        this.contractSignedDate = latestSignedDate;
        this.contractDocYn = "Y";
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
        terminate(terminatedDate, reason, null, null, updatedBy);
    }

    /**
     * 계약 종료 처리. 만료 서류를 다 받지 못했으면 그 사유(expiryDocsNote)를 함께 남긴다.
     * remindYn 이 N 이면 미수취 서류 독촉 메일을 보내지 않는다(null 이면 그대로).
     */
    public void terminate(LocalDate terminatedDate, String reason, String expiryDocsNote, String remindYn, Long updatedBy) {
        this.terminatedYn = "Y";
        this.terminatedDate = terminatedDate == null ? LocalDate.now() : terminatedDate;
        this.terminationReason = reason;
        this.expiryDocsNote = expiryDocsNote;
        if (remindYn != null) {
            this.expiryDocsRemindYn = yn(remindYn, this.expiryDocsRemindYn);
        }
        markUpdatedBy(updatedBy);
    }

    /** 미수취 만료 서류 독촉 메일 발송 여부 변경 */
    public void changeExpiryDocsRemind(String remindYn, Long updatedBy) {
        this.expiryDocsRemindYn = yn(remindYn, this.expiryDocsRemindYn);
        markUpdatedBy(updatedBy);
    }

    /** 종료 처리 취소 */
    /**
     * 자동연장 중인 계약인지: 자동연장 Y + 종료 처리 안 함 + 원계약 종료일 있음.
     * 원계약 종료일이 지나도 추가 계약 없이 자동연장 기간 단위로 계속 이어지므로 만료 대상이 아니다(종료 처리해야 만료 대상).
     */
    public boolean isAutoRenewing() {
        return "Y".equals(autoRenewYn) && !"Y".equals(terminatedYn) && contractEndDate != null;
    }

    /** 자동연장 기간 표시 (12의 배수면 "N년", 아니면 "N개월") */
    public static String renewPeriodLabel(int months) {
        return months % 12 == 0 ? (months / 12) + "년" : months + "개월";
    }

    /** 자동연장 기간 (개월, 1 미만이면 기본 12) */
    public int autoRenewMonths() {
        return autoRenewPeriodMonths == null || autoRenewPeriodMonths < 1 ? DEFAULT_AUTO_RENEW_MONTHS : autoRenewPeriodMonths;
    }

    /** 기준일까지 자동연장된 횟수 (자동연장 중이 아니거나 원계약 기간 안이면 0) */
    public int autoRenewCount(LocalDate today) {
        if (!isAutoRenewing() || !contractEndDate.isBefore(today)) {
            return 0;
        }
        int months = autoRenewMonths();
        int k = 1;
        while (contractEndDate.plusMonths((long) k * months).isBefore(today) && k < 1200) {
            k++;
        }
        return k;
    }

    /**
     * 기준일에 적용되는 현재 계약 종료일.
     * 자동연장 중이면 원계약 종료일 + (연장 횟수 × 자동연장 기간), 아니면 원계약 종료일.
     * 월말 날짜가 밀리지 않도록 매번 원계약 종료일에서 다시 계산한다(예: 12/31 → 이듬해 12/31).
     */
    public LocalDate currentEndDate(LocalDate today) {
        int k = autoRenewCount(today);
        return k == 0 ? contractEndDate : contractEndDate.plusMonths((long) k * autoRenewMonths());
    }

    public void reopen(Long updatedBy) {
        this.terminatedYn = "N";
        this.terminatedDate = null;
        this.terminationReason = null;
        this.expiryDocsNote = null;
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
