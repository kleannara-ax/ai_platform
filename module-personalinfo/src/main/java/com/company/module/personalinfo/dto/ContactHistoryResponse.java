package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiContactHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 담당자 변경 이력 1건 (변경 후 값 스냅샷 + 바뀐 항목) */
@Getter
@Builder
public class ContactHistoryResponse {

    private Long historyId;
    private Long contractId;
    private String changeType;
    private String changeTypeLabel;
    private String vendorName;
    private String serviceName;
    private String deptName;
    private String managerName;
    private String managerEmail;
    /** 이력 기록 시점의 계약기간 */
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String contractPeriodNote;
    /** 바뀐 위탁사 담당자 항목 코드 (DEPT_NAME, MANAGER_NAME, MANAGER_EMAIL) — 등록/이관 건은 빈 목록 */
    private List<String> changedFields;
    private String changedByName;
    private Long changedBy;
    private LocalDateTime changedAt;
    /** 조회 중인 계약의 이력인지 (업체 전체 조회 시 구분용) */
    private boolean currentContract;

    public static ContactHistoryResponse of(PiContactHistory h, Long currentContractId) {
        List<String> fields = changedTrackedFields(h);
        return ContactHistoryResponse.builder()
                .historyId(h.getHistoryId())
                .contractId(h.getContractId())
                .changeType(h.getChangeType().name())
                .changeTypeLabel(h.getChangeType().getLabel())
                .vendorName(h.getVendorName())
                .serviceName(h.getServiceName())
                .deptName(h.getDeptName())
                .managerName(h.getManagerName())
                .managerEmail(h.getManagerEmail())
                .contractStartDate(h.getContractStartDate())
                .contractEndDate(h.getContractEndDate())
                .contractPeriodNote(h.getContractPeriodNote())
                .changedFields(fields)
                .changedByName(h.getChangedByName())
                .changedBy(h.getCreatedBy())
                .changedAt(h.getCreatedAt())
                .currentContract(h.getContractId().equals(currentContractId))
                .build();
    }

    /** 기록된 변경 항목 중 이력 대상(위탁사 담당자) 항목만 */
    public static List<String> changedTrackedFields(PiContactHistory h) {
        String f = h.getChangedFields();
        if (f == null || f.isBlank()) {
            return List.of();
        }
        return Arrays.stream(f.split(",")).map(String::trim)
                .filter(PiContactHistory.TRACKED_FIELDS::contains).toList();
    }
}
