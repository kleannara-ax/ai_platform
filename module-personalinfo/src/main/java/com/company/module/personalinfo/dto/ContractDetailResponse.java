package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.ContractStatus;
import com.company.module.personalinfo.entity.PiContract;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 계약 상세 (첨부파일, 점검 이력, 알림 이력 포함) */
@Getter
@Builder
public class ContractDetailResponse {

    private Long contractId;
    private String ismsScopeYn;
    private String vendorName;
    private String serviceName;
    private String entrustedTask;
    private String deptName;
    private String managerName;
    private String managerEmail;
    private String vendorContactName;
    private String vendorContactEmail;
    private String vendorContactPhone;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String contractPeriodNote;
    private String autoRenewYn;
    private String autoRenewNote;
    private String contractDocYn;
    private LocalDate contractSignedDate;
    private String subcontractYn;
    private String subcontractorName;
    private String subcontractTask;
    private String subcontractConsentYn;
    private String alertYn;
    private Integer alertDaysBefore;
    private String alertExtraEmails;
    private String terminatedYn;
    private LocalDate terminatedDate;
    private String terminationReason;
    private Long lastInspectionId;
    private LocalDate lastInspectionDate;
    private String lastInspectionResult;
    private String lastInspectionResultLabel;
    private String remark;

    private String status;
    private String statusLabel;
    private Long daysLeft;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    private List<ContractFileResponse> files;
    private List<InspectionResponse> inspections;
    private List<AlertLogResponse> alerts;

    public static ContractDetailResponse of(PiContract c, LocalDate today,
                                            List<ContractFileResponse> files,
                                            List<InspectionResponse> inspections,
                                            List<AlertLogResponse> alerts) {
        ContractStatus status = ContractStatus.of(c, today);
        return ContractDetailResponse.builder()
                .contractId(c.getContractId())
                .ismsScopeYn(c.getIsmsScopeYn())
                .vendorName(c.getVendorName())
                .serviceName(c.getServiceName())
                .entrustedTask(c.getEntrustedTask())
                .deptName(c.getDeptName())
                .managerName(c.getManagerName())
                .managerEmail(c.getManagerEmail())
                .vendorContactName(c.getVendorContactName())
                .vendorContactEmail(c.getVendorContactEmail())
                .vendorContactPhone(c.getVendorContactPhone())
                .contractStartDate(c.getContractStartDate())
                .contractEndDate(c.getContractEndDate())
                .contractPeriodNote(c.getContractPeriodNote())
                .autoRenewYn(c.getAutoRenewYn())
                .autoRenewNote(c.getAutoRenewNote())
                .contractDocYn(c.getContractDocYn())
                .contractSignedDate(c.getContractSignedDate())
                .subcontractYn(c.getSubcontractYn())
                .subcontractorName(c.getSubcontractorName())
                .subcontractTask(c.getSubcontractTask())
                .subcontractConsentYn(c.getSubcontractConsentYn())
                .alertYn(c.getAlertYn())
                .alertDaysBefore(c.getAlertDaysBefore())
                .alertExtraEmails(c.getAlertExtraEmails())
                .terminatedYn(c.getTerminatedYn())
                .terminatedDate(c.getTerminatedDate())
                .terminationReason(c.getTerminationReason())
                .lastInspectionId(c.getLastInspectionId())
                .lastInspectionDate(c.getLastInspectionDate())
                .lastInspectionResult(c.getLastInspectionResult())
                .lastInspectionResultLabel(ContractSummaryResponse.resultLabel(c.getLastInspectionResult()))
                .remark(c.getRemark())
                .status(status.name())
                .statusLabel(status.getLabel())
                .daysLeft(ContractSummaryResponse.daysLeft(c, today))
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .files(files)
                .inspections(inspections)
                .alerts(alerts)
                .build();
    }
}
