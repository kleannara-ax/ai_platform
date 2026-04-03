package com.company.core.permission.dto;

import lombok.*;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoleMappingResponse {
    private String role;
    private String roleDescription;
    private List<Long> menuIds;
    private List<Long> permissionIds;
}
