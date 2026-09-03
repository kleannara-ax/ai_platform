package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * IP 회수 처리 요청 DTO.
 */
@Getter
@Setter
public class IpReclaimRequest {

    @NotBlank(message = "처리자는 필수입니다.")
    private String changedBy;

    /** 회수 사유 (선택) */
    private String reason;

    /** 연결할 업무 요청 ID (선택) */
    private Long requestId;
}
