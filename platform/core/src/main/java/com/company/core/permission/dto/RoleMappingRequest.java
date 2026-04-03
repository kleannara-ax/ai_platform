package com.company.core.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoleMappingRequest {
    @NotBlank(message = "역할은 필수입니다.") private String role;
    private List<Long> menuIds;
    private List<Long> permissionIds;
}
