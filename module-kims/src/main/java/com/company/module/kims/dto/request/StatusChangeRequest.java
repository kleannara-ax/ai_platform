package com.company.module.kims.dto.request;

import com.company.module.kims.entity.enums.RequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 처리상태 변경 요청 DTO.
 */
@Getter
@Setter
public class StatusChangeRequest {

    @NotNull(message = "변경할 상태는 필수입니다.")
    private RequestStatus status;

    @NotBlank(message = "변경자는 필수입니다.")
    private String changedBy;

    /** 변경 사유 (선택) */
    private String reason;

    /** 담당자 (선택) - 입력 시 요청의 담당자도 함께 갱신 */
    private String assignee;

    // ---- 완료 처리 시 자동 반영용 (IP 변경/생성 요청) ----
    /** 대상 PC의 IP_ID (IP변경/PC변경 시 관리자가 선택한 현재 PC) */
    private Long targetIpId;

    /** 새 IP 주소 (IP변경/IP신규생성) */
    private String newIp;

    /** PC변경 시 수정할 항목 값 (필드명 → 새 값). 선택된 항목만 포함 */
    private java.util.Map<String, String> pcFields;
}
