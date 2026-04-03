package com.company.core.menu.dto;

import com.company.core.menu.entity.CoreMenu;
import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuResponse {
    private Long menuId;
    private String menuName;
    private String menuCode;
    private Long parentId;
    private String menuUrl;
    private String icon;
    private Integer sortOrder;
    private String menuType;
    private Boolean isVisible;
    private Boolean isActive;
    private String description;
    @Builder.Default private List<MenuResponse> children = new ArrayList<>();

    public static MenuResponse from(CoreMenu e) {
        return MenuResponse.builder()
            .menuId(e.getMenuId()).menuName(e.getMenuName()).menuCode(e.getMenuCode())
            .parentId(e.getParentId()).menuUrl(e.getMenuUrl()).icon(e.getIcon())
            .sortOrder(e.getSortOrder()).menuType(e.getMenuType())
            .isVisible(e.getIsVisible()).isActive(e.getIsActive())
            .description(e.getDescription()).build();
    }
}
