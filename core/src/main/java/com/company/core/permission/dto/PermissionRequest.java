package com.company.core.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionRequest {
    @NotBlank(message = "권한코드는 필수입니다.") @Size(max = 50) private String permCode;
    @NotBlank(message = "권한명은 필수입니다.") @Size(max = 100) private String permName;
    private String description;
    private Boolean isActive;
}
