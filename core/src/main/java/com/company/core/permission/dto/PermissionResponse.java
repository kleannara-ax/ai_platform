package com.company.core.permission.dto;

import com.company.core.permission.entity.CorePermission;
import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionResponse {
    private Long permId;
    private String permCode;
    private String permName;
    private String description;
    private Boolean isActive;

    public static PermissionResponse from(CorePermission e) {
        return PermissionResponse.builder()
            .permId(e.getPermId()).permCode(e.getPermCode()).permName(e.getPermName())
            .description(e.getDescription()).isActive(e.getIsActive()).build();
    }
}
