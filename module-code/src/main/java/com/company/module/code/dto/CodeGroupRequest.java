package com.company.module.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CodeGroupRequest {

    @NotBlank(message = "그룹 코드는 필수입니다.")
    @Size(max = 50, message = "그룹 코드는 50자 이하여야 합니다.")
    private String groupCode;

    @NotBlank(message = "그룹명은 필수입니다.")
    @Size(max = 100, message = "그룹명은 100자 이하여야 합니다.")
    private String groupName;

    @Size(max = 200)
    private String description;

    private Boolean isActive;
    private Integer sortOrder;
}
