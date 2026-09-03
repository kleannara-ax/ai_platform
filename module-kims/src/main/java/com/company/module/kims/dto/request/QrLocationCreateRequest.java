package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * QR 구역 생성 요청 DTO. (위치·부서를 입력해 QR 생성)
 */
@Getter
@Setter
public class QrLocationCreateRequest {

    @Size(max = 100)
    private String name;

    @NotBlank(message = "위치는 필수입니다.")
    @Size(max = 100)
    private String location;

    @Size(max = 50)
    private String department;

    /** 사용 여부 (미입력 시 true) */
    private Boolean active;

    @Size(max = 255)
    private String remark;
}
