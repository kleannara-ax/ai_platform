package com.company.module.fire.facility;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class FacilityEquipmentResponse {
    private final Long equipmentId;
    private final String category;
    private final String serialNumber;
    private final String qrKey;
    private final Long buildingId;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final String equipmentType;
    private final String manufacturer;
    private final String locationDescription;
    private final int outdoorUnitCount;
    private final LocalDate manufactureDate;
    private final int replacementCycleYears;
    private final LocalDate replacementDueDate;
    private final String note;
    private final String imagePath;
    private final BigDecimal x;
    private final BigDecimal y;
    private final LocalDateTime createdAt;

    private LocalDate lastInspectionDate;
    private String lastInspectorName;
    private Boolean lastIsFaulty;
    private String lastFaultReason;
    private List<InspectionRow> inspections;

    public static FacilityEquipmentResponse from(FacilityEquipment e) {
        return FacilityEquipmentResponse.builder()
                .equipmentId(e.getEquipmentId())
                .category(e.getCategory())
                .serialNumber(e.getSerialNumber())
                .qrKey(e.getQrKey())
                .buildingId(e.getBuilding() != null ? e.getBuilding().getBuildingId() : null)
                .buildingName(e.getBuilding() != null ? e.getBuilding().getBuildingName() : null)
                .floorId(e.getFloor() != null ? e.getFloor().getFloorId() : null)
                .floorName(e.getFloor() != null ? e.getFloor().getFloorName() : null)
                .equipmentType(e.getEquipmentType())
                .manufacturer(e.getManufacturer())
                .locationDescription(e.getLocationDescription())
                .outdoorUnitCount(e.getOutdoorUnitCount())
                .manufactureDate(e.getManufactureDate())
                .replacementCycleYears(e.getReplacementCycleYears())
                .replacementDueDate(e.getReplacementDueDate())
                .note(e.getNote())
                .imagePath(e.getImagePath())
                .x(e.getX())
                .y(e.getY())
                .createdAt(e.getCreatedAt())
                .build();
    }

    public void setLastInspection(FacilityEquipmentInspection inspection) {
        if (inspection != null) {
            this.lastInspectionDate = inspection.getInspectionDate();
            this.lastInspectorName = inspection.getInspectedByName();
            this.lastIsFaulty = inspection.isFaulty();
            this.lastFaultReason = inspection.getFaultReason();
        }
    }

    public void setInspectionHistory(List<FacilityEquipmentInspection> list) {
        this.inspections = list.stream()
                .map(i -> new InspectionRow(i.getInspectionId(), i.getInspectionDate(), i.getInspectedByName(), i.isFaulty(), i.getFaultReason()))
                .collect(Collectors.toList());
    }

    @Getter
    public static class InspectionRow {
        private final Long inspectionId;
        private final LocalDate inspectionDate;
        private final String inspectorName;
        private final boolean isFaulty;
        private final String faultReason;

        public InspectionRow(Long inspectionId, LocalDate inspectionDate, String inspectorName, boolean isFaulty, String faultReason) {
            this.inspectionId = inspectionId;
            this.inspectionDate = inspectionDate;
            this.inspectorName = inspectorName;
            this.isFaulty = isFaulty;
            this.faultReason = faultReason;
        }
    }
}
