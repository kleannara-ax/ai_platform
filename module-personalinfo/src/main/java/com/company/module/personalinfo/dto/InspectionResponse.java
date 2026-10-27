package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiInspection;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 점검 이력 (상세 조회 시 items 포함) */
@Getter
@Builder
public class InspectionResponse {

    private Long inspectionId;
    private Long contractId;
    private String inspectionType;
    private String inspectionTypeLabel;
    private Integer inspectionYear;
    private LocalDate inspectionDate;
    private String inspectorName;
    private String sheetCompanyName;
    private String sheetTaskName;
    private String systemOwnedYn;
    private String systemName;
    private Integer totalCount;
    /** Lombok getter(getYCount)를 Jackson 이 "ycount" 로 바꾸지 않도록 이름을 고정 */
    @JsonProperty("yCount")
    private Integer yCount;
    @JsonProperty("nCount")
    private Integer nCount;
    private Integer naCount;
    private Integer blankCount;
    private String result;
    private String resultLabel;
    private LocalDate actionDueDate;
    private String actionCompletedYn;
    private LocalDate actionCompletedDate;
    private String originalName;
    private Long fileSize;
    private String remark;
    /** 업체별 일괄 업로드 묶음 ID (개별 업로드는 null) */
    private String uploadGroupId;
    /** 계약 수탁업체명 / 관련 서비스 (업체별 일괄 업로드 결과 표시용) */
    private String vendorName;
    private String serviceName;
    private Long createdBy;
    private LocalDateTime createdAt;
    /** 업로드 시 파서가 남긴 안내 (예: 업체명 불일치) */
    private List<String> warnings;
    private List<InspectionItemResponse> items;

    /** 일괄 업로드 결과: 계약 정보(업체명/서비스)를 함께 담는다 */
    public static InspectionResponse withContract(PiInspection i, String vendorName, String serviceName) {
        InspectionResponse r = of(i, null, null);
        r.vendorName = vendorName;
        r.serviceName = serviceName;
        return r;
    }

    public static InspectionResponse from(PiInspection i) {
        return of(i, null, null);
    }

    public static InspectionResponse of(PiInspection i, List<InspectionItemResponse> items, List<String> warnings) {
        return InspectionResponse.builder()
                .inspectionId(i.getInspectionId())
                .contractId(i.getContractId())
                .inspectionType(i.getInspectionType().name())
                .inspectionTypeLabel(i.getInspectionType().getLabel())
                .inspectionYear(i.getInspectionYear())
                .inspectionDate(i.getInspectionDate())
                .inspectorName(i.getInspectorName())
                .sheetCompanyName(i.getSheetCompanyName())
                .sheetTaskName(i.getSheetTaskName())
                .systemOwnedYn(i.getSystemOwnedYn())
                .systemName(i.getSystemName())
                .totalCount(i.getTotalCount())
                .yCount(i.getYCount())
                .nCount(i.getNCount())
                .naCount(i.getNaCount())
                .blankCount(i.getBlankCount())
                .result(i.getResult().name())
                .resultLabel(i.getResult().getLabel())
                .actionDueDate(i.getActionDueDate())
                .actionCompletedYn(i.getActionCompletedYn())
                .actionCompletedDate(i.getActionCompletedDate())
                .originalName(i.getOriginalName())
                .fileSize(i.getFileSize())
                .remark(i.getRemark())
                .uploadGroupId(i.getUploadGroupId())
                .createdBy(i.getCreatedBy())
                .createdAt(i.getCreatedAt())
                .warnings(warnings)
                .items(items)
                .build();
    }
}
