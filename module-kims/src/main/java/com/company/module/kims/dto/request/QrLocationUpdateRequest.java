package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * QR 구역 수정 요청 DTO.
 */
@Getter
@Setter
public class QrLocationUpdateRequest {

    @Size(max = 100)
    private String name;

    @NotBlank(message = "위치는 필수입니다.")
    @Size(max = 100)
    private String location;

    @Size(max = 50)
    private String department;

    private boolean active;

    @Size(max = 255)
    private String remark;
}
