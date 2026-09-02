package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryCreateRequest {

    @NotBlank(message = "분류명은 필수입니다.")
    private String name;

    /** 최상위 분류로 만들 때는 null */
    private Long parentId;

    private int sortOrder;
}
