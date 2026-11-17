package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 업체별 체크리스트 일괄 업로드 결과 */
@Getter
@Builder
public class VendorChecklistUploadResponse {

    /** 이번 업로드 묶음 ID (생성된 점검 이력에 공통 저장) */
    private String uploadGroupId;
    private String vendorName;
    private String result;
    private String resultLabel;
    private int registeredCount;
    /** 계약별로 생성된 점검 이력 */
    private List<InspectionResponse> inspections;
    private List<String> warnings;
}
