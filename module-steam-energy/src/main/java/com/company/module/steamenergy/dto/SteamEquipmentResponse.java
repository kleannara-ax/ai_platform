package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEquipment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 스팀 설비 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEquipmentResponse {

    private Long equipmentId;
    private String equipmentCode;
    private String name;
    private String category;
    private String unit;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SteamEquipmentResponse from(SteamEquipment e) {
        return SteamEquipmentResponse.builder()
                .equipmentId(e.getEquipmentId())
                .equipmentCode(e.getEquipmentCode())
                .name(e.getName())
                .category(e.getCategory())
                .unit(e.getUnit())
                .sortOrder(e.getSortOrder())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
