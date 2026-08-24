package com.company.module.kims.dto.request;

import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 인터넷 공사 등록 요청 DTO.
 */
@Getter
@Setter
public class InternetWorkCreateRequest {

    @NotNull(message = "공사유형은 필수입니다.")
    private InternetWorkType workType;

    private String requesterName;
    private String department;
    private String location;
    private String content;

    /** 외부업체 사용 여부 */
    private boolean externalVendor;
    private String vendorName;

    /** 공사비 발생 여부 */
    private boolean hasCost;
    private Long cost;

    private String assignee;

    /** 상태 (미입력 시 접수) */
    private InternetWorkStatus status;

    /** 완료일 (완료 상태로 등록 시) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate completedAt;

    private String remark;

    /** 연결할 업무 요청 ID (선택) */
    private Long requestId;
}
