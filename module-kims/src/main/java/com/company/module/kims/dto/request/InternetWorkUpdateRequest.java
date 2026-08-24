package com.company.module.kims.dto.request;

import com.company.module.kims.entity.enums.InternetWorkType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 인터넷 공사 정보 수정 요청 DTO. (상태/완료일은 별도 상태변경 API 사용)
 */
@Getter
@Setter
public class InternetWorkUpdateRequest {

    @NotNull(message = "공사유형은 필수입니다.")
    private InternetWorkType workType;

    private String requesterName;
    private String department;
    private String location;
    private String content;

    private boolean externalVendor;
    private String vendorName;

    private boolean hasCost;
    private Long cost;

    private String assignee;
    private String remark;
}
