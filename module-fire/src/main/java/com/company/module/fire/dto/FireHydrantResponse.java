package com.company.module.fire.dto;

import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.FireHydrantInspection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 소화전 목록/상세 응답 DTO
 */
@Getter
@Builder
public class FireHydrantResponse {

    private final Long hydrantId;
    private final String serialNumber;
    private final String qrKey;
    private final String hydrantType;
    private final String operationType;
    private final Long buildingId;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final BigDecimal x;
    private final BigDecimal y;
    private final String locationDescription;
    private final String imagePath;
    private final boolean isActive;
    private final LocalDateTime createdAt;

    // 최종 점검 정보
    private LocalDate lastInspectionDate;
    private String lastInspectorName;
    private Boolean lastIsFaulty;
    private String lastFaultReason;

    // 점검 이력 (상세 조회 시)
    private List<InspectionRow> inspections;

    /**
     * Entity → Response 변환 팩토리
     */
    public static FireHydrantResponse from(FireHydrant h) {
        return FireHydrantResponse.builder()
                .hydrantId(h.getHydrantId())
                .serialNumber(h.getSerialNumber())
                .qrKey(h.getQrKey())
                .hydrantType(h.getHydrantType())
                .operationType(h.getOperationType())
                .buildingId(h.getBuilding() != null ? h.getBuilding().getBuildingId() : null)
                .buildingName(h.getBuilding() != null ? h.getBuilding().getBuildingName() : null)
                .floorId(h.getFloor() != null ? h.getFloor().getFloorId() : null)
                .floorName(h.getFloor() != null ? h.getFloor().getFloorName() : null)
                .x(h.getX())
                .y(h.getY())
                .locationDescription(h.getLocationDescription())
                .imagePath(h.getImagePath())
                .isActive(h.isActive())
                .createdAt(h.getCreatedAt())
                .build();
    }

    public void setLastInspection(FireHydrantInspection inspection) {
        if (inspection != null) {
            this.lastInspectionDate = inspection.getInspectionDate();
            this.lastInspectorName = inspection.getInspectedByName();
            this.lastIsFaulty = inspection.isFaulty();
            this.lastFaultReason = inspection.getFaultReason();
        }
    }

    public void setInspectionHistory(List<FireHydrantInspection> list) {
        this.inspections = list.stream()
                .map(i -> new InspectionRow(
                        i.getInspectionId(),
                        i.getInspectionDate(),
                        i.getInspectedByName(),
                        i.isFaulty(),
                        i.getFaultReason()))
                .collect(Collectors.toList());
    }

    @Getter
    public static class InspectionRow {
        private final Long inspectionId;
        private final LocalDate inspectionDate;
        private final String inspectorName;
        private final boolean isFaulty;
        private final String faultReason;

        public InspectionRow(Long inspectionId, LocalDate inspectionDate, String inspectorName,
                             boolean isFaulty, String faultReason) {
            this.inspectionId = inspectionId;
            this.inspectionDate = inspectionDate;
            this.inspectorName = inspectorName;
            this.isFaulty = isFaulty;
            this.faultReason = faultReason;
        }
    }
}
