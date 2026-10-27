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
    private String serviceName;
    private String entrustedTask;
    private String deptName;
    private String managerName;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String contractPeriodNote;
    private String autoRenewYn;
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

    public static ContractSummaryResponse from(PiContract c, LocalDate today) {
        ContractStatus status = ContractStatus.of(c, today);
        return ContractSummaryResponse.builder()
                .contractId(c.getContractId())
                .ismsScopeYn(c.getIsmsScopeYn())
                .vendorName(c.getVendorName())
                .serviceName(c.getServiceName())
                .entrustedTask(c.getEntrustedTask())
                .deptName(c.getDeptName())
                .managerName(c.getManagerName())
                .contractStartDate(c.getContractStartDate())
                .contractEndDate(c.getContractEndDate())
                .contractPeriodNote(c.getContractPeriodNote())
                .autoRenewYn(c.getAutoRenewYn())
                .contractDocYn(c.getContractDocYn())
                .subcontractYn(c.getSubcontractYn())
                .subcontractorName(c.getSubcontractorName())
                .status(status.name())
                .statusLabel(status.getLabel())
                .daysLeft(daysLeft(c, today))
                .lastInspectionDate(c.getLastInspectionDate())
                .lastInspectionResult(c.getLastInspectionResult())
                .lastInspectionResultLabel(resultLabel(c.getLastInspectionResult()))
                .build();
    }

    static Long daysLeft(PiContract c, LocalDate today) {
        return c.getContractEndDate() == null ? null : ChronoUnit.DAYS.between(today, c.getContractEndDate());
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
