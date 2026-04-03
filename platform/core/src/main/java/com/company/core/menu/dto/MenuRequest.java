package com.company.core.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuRequest {
    @NotBlank(message = "메뉴명은 필수입니다.") @Size(max = 100) private String menuName;
    @NotBlank(message = "메뉴코드는 필수입니다.") @Size(max = 50) private String menuCode;
    private Long parentId;
    private String menuUrl;
    private String icon;
    private Integer sortOrder;
    private String menuType;
    private Boolean isVisible;
    private Boolean isActive;
    private String description;
}
