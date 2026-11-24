package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.ContractStatus;
import com.company.module.personalinfo.entity.InspectionResult;
import com.company.module.personalinfo.entity.PiContract;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 계약 목록 화면 행 */
@Getter
@Builder
public class ContractSummaryResponse {

    private Long contractId;
    private String ismsScopeYn;
    private String vendorName;
    private String contractName;
    private String serviceName;
    private String entrustedTask;
    private String deptName;
    private String managerName;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String contractPeriodNote;
    private String autoRenewYn;
    /** 자동연장 기간 (개월) */
    private Integer autoRenewPeriodMonths;
    /** 자동연장 중 (자동연장 Y + 미종료 + 종료일 있음) — 만료 대상 아님 */
    private Boolean autoRenewing;
    /** 기준일까지 자동연장된 횟수 */
    private Integer autoRenewCount;
    /** 현재 계약 종료일 (자동연장 중이면 연장된 종료일, 아니면 원계약 종료일) */
    private LocalDate currentEndDate;
    /** 정기 파기확인 대상 여부 (Y/N) */
    private String periodicDestructionYn;
    /** 정기 파기확인 주기 (MONTH / QUARTER / HALF) */
    private String periodicDestructionCycle;
    /** 정기 파기확인 주기 이름 (월 / 분기 / 반기) */
    private String periodicDestructionCycleLabel;
    /** 정기 파기확인 요청·독촉 메일 발송 여부 */
    private String periodicDestructionMailYn;
    /** 정기 파기확인 관리 시작일 (이 날 이후에 끝나는 기간부터 미수취 판정) */
    private LocalDate periodicDestructionFrom;
    private String contractDocYn;
    private String subcontractYn;
    private String subcontractorName;
    private String status;
    private String statusLabel;
    /** 만료까지 남은 일수 (종료일 없으면 null, 지났으면 음수) */
    private Long daysLeft;
    private LocalDate lastInspectionDate;
    private String lastInspectionResult;
    private String lastInspectionResultLabel;
    /** 계약종결 체크(계약변경·만료 점검) 업로드 여부 Y/N */
    private String terminationCheckYn;
    /** 같은 업체(업체명 정규화 기준)의 계약 수 (이 계약 포함, 삭제 제외). 목록 조회 시에만 채움 */
    private Integer sameVendorContractCount;

    // ── 보안팀 확인 필요 (목록 조회 시 채움) ──
    /** 만료 서류를 받아야 하는 계약 (종료 / 만료임박 / 만료) */
    private Boolean expiryDocsNeeded;
    /** 받은 만료 서류 수 (0~3) */
    private Integer expiryDocsReceived;
    /** 받아야 하는 만료 서류 중 미수취가 있음 */
    private Boolean expiryDocsMissing;
    /** 조치 예정일이 지난 점검 건수 */
    private Integer actionOverdueCount;
    /** 재위탁 동의서 누락 */
    private Boolean consentMissing;
    /** 파기 완료일이 허용 기한보다 늦음 */
    private Boolean destructionLate;
    /** 파기 확인서 회신 기한(종료 + 30일)이 지났는데 파기 확인서 미수취 */
    private Boolean confirmReplyOverdue;
    /** 정기 파기 확인서 미수취 기간 수 (수취 기한 지남) */
    private Integer periodicDestructionOverdueCount;
    /** 가장 최근에 받은 정기 파기 확인서의 기간 이름 (예: 2026년 3분기) */
    private String periodicDestructionLastLabel;

    /** 확인 필요 상태 채우기 (응답 DTO) */
    public ContractSummaryResponse withFollowUp(FollowUpResponse f) {
        if (f != null) {
            this.expiryDocsNeeded = f.isExpiryDocsNeeded();
            this.expiryDocsReceived = f.getExpiryDocsReceived();
            this.expiryDocsMissing = f.isExpiryDocsMissing();
            this.actionOverdueCount = f.getActionOverdueCount();
            this.consentMissing = f.isConsentMissing();
            this.destructionLate = f.isDestructionLate();
            this.confirmReplyOverdue = f.isConfirmReplyOverdue();
            this.periodicDestructionOverdueCount = f.getPeriodicDestructionOverdueCount();
            this.periodicDestructionLastLabel = f.getPeriodicDestructionLastLabel();
        }
        return this;
    }

    public static ContractSummaryResponse from(PiContract c, LocalDate today) {
        return from(c, today, null);
    }

    public static ContractSummaryResponse from(PiContract c, LocalDate today, Boolean terminationCheck) {
        return from(c, today, terminationCheck, null);
    }

    public static ContractSummaryResponse from(PiContract c, LocalDate today, Boolean terminationCheck,
                                               Integer sameVendorContractCount) {
        ContractStatus status = ContractStatus.of(c, today);
        return ContractSummaryResponse.builder()
                .contractId(c.getContractId())
                .ismsScopeYn(c.getIsmsScopeYn())
                .vendorName(c.getVendorName())
                .contractName(c.getContractName())
                .serviceName(c.getServiceName())
                .entrustedTask(c.getEntrustedTask())
                .deptName(c.getDeptName())
                .managerName(c.getManagerName())
                .contractStartDate(c.getContractStartDate())
                .contractEndDate(c.getContractEndDate())
                .contractPeriodNote(c.getContractPeriodNote())
                .autoRenewYn(c.getAutoRenewYn())
                .autoRenewPeriodMonths(c.autoRenewMonths())
                .autoRenewing(c.isAutoRenewing())
                .autoRenewCount(c.autoRenewCount(today))
                .currentEndDate(c.getContractEndDate() == null ? null : c.currentEndDate(today))
                .periodicDestructionYn(c.getPeriodicDestructionYn())
                .periodicDestructionCycle(c.destructionCycle().name())
                .periodicDestructionCycleLabel(c.destructionCycle().getLabel())
                .periodicDestructionMailYn(c.getPeriodicDestructionMailYn())
                .periodicDestructionFrom(c.getPeriodicDestructionFrom())
                .contractDocYn(c.getContractDocYn())
                .subcontractYn(c.getSubcontractYn())
                .subcontractorName(c.getSubcontractorName())
                .status(status.name())
                .statusLabel(status.getLabel())
                .daysLeft(daysLeft(c, today))
                .lastInspectionDate(c.getLastInspectionDate())
                .lastInspectionResult(c.getLastInspectionResult())
                .lastInspectionResultLabel(resultLabel(c.getLastInspectionResult()))
                .terminationCheckYn(terminationCheck == null ? null : (terminationCheck ? "Y" : "N"))
                .sameVendorContractCount(sameVendorContractCount)
                .build();
    }

    static Long daysLeft(PiContract c, LocalDate today) {
        return c.getContractEndDate() == null ? null : ChronoUnit.DAYS.between(today, c.currentEndDate(today));
    }

    static String resultLabel(String result) {
        if (result == null) {
            return "미점검";
        }
        try {
            return InspectionResult.valueOf(result).getLabel();
        } catch (IllegalArgumentException e) {
            return result;
        }
    }
}
