package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * IP 정보/사용자 변경 요청 DTO.
 */
@Getter
@Setter
public class IpModifyRequest {

    private String userName;
    private String department;
    private String location;
    private String device;

    // PC/장비 상세 스펙
    private String model;
    private String serialNo;
    private String vendor;
    private String osVersion;
    private String osSerial;
    private String officeVersion;
    private String officeSerial;
    private String hangulVersion;
    private String hangulSerial;
    /** 렌탈사명 (롯데/AJ 등). 미입력이면 자산. */
    private String rentalCompany;
    private String pcAssetNo;
    private String monitorAssetNo;

    /** 품의 여부 (이번 변경이 품의된 것인지) */
    private boolean approved;
    private String approvalNo;
    private String remark;

    /** 비고 작성일 */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate noteDate;

    /** 연결할 업무 요청 ID (선택) */
    private Long requestId;

    @NotBlank(message = "변경자는 필수입니다.")
    private String changedBy;

    /** 변경 사유/내용 (선택) */
    private String reason;
}
