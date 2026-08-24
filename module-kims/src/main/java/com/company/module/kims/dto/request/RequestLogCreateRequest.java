package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 처리내용(메모) 로그 추가 요청 DTO.
 */
@Getter
@Setter
public class RequestLogCreateRequest {

    @NotBlank(message = "작성자는 필수입니다.")
    private String changedBy;

    @NotBlank(message = "처리내용은 필수입니다.")
    private String content;
}
