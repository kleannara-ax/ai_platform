package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** 수탁업체 단위 요약 (업체별 체크리스트 업로드 화면) */
@Getter
@Builder
public class VendorSummaryResponse {

    /** 업체 그룹 키 (정규화한 업체명: ㈜/(주)/공백 제거) */
    private String vendorKey;
    /** 대표 업체명 (가장 먼저 등록된 계약의 업체명) */
    private String vendorName;
    private int contractCount;
    private int activeContractCount;
    /** 업체 계약 중 가장 최근 점검일 / 결과 */
    private LocalDate lastInspectionDate;
    private String lastInspectionResult;
    private String lastInspectionResultLabel;
    /** 올해 점검이 없는 미종료 계약 수 */
    private int notInspectedThisYearCount;
    private List<VendorContractItem> contracts;

    @Getter
    @Builder
    public static class VendorContractItem {
        private Long contractId;
        private String vendorName;
        private String serviceName;
        private String entrustedTask;
        private String deptName;
        private String managerName;
        private LocalDate contractEndDate;
        private String status;
        private String statusLabel;
        private LocalDate lastInspectionDate;
        private String lastInspectionResult;
        private String lastInspectionResultLabel;
    }
}
