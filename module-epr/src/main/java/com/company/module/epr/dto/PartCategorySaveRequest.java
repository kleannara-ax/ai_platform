package com.company.module.epr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 파트 코드 추가·수정 요청 (같은 파트코드가 있으면 파트명만 바꾼다) */
@Getter
@Setter
public class PartCategorySaveRequest {

    @NotBlank(message = "파트코드를 입력하세요.")
    @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "파트코드는 영문/숫자 2~10자여야 합니다.")
    private String partPrefix;

    @NotBlank(message = "파트명을 입력하세요.")
    @Size(max = 255, message = "파트명은 255자 이내여야 합니다.")
    private String partName;
}
