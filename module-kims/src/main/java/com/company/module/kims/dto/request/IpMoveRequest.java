package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * IP 변경(이동) 요청 DTO. 같은 PC를 다른 IP 로 옮긴다.
 */
@Getter
@Setter
public class IpMoveRequest {

    @NotBlank(message = "이동할 새 IP 주소는 필수입니다.")
    private String newIpAddress;

    @NotBlank(message = "변경자는 필수입니다.")
    private String changedBy;

    private String reason;

    private Long requestId;
}
