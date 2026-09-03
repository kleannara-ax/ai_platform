package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryUpdateRequest {

    @NotBlank(message = "분류명은 필수입니다.")
    private String name;

    private int sortOrder;
}
