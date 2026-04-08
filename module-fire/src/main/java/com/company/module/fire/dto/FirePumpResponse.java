package com.company.module.fire.dto;

import com.company.module.fire.entity.FirePump;
import com.company.module.fire.entity.FirePumpInspection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 소방펌프 목록/상세 응답 DTO
 */
@Getter
@Builder
public class FirePumpResponse {

    private final Long pumpId;
    private final String serialNumber;
    private final String qrKey;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final BigDecimal x;
    private final BigDecimal y;
    private final String locationDescription;
    private final String note;
    private final boolean isActive;
    private final LocalDateTime createdAt;

    private LocalDate lastInspectionDate;
    private LocalTime lastInspectionTime;
    private String lastInspectorName;
    private String lastInspectionStatus;
    private String lastInspectionNote;
    @Builder.Default
    private List<InspectionRow> inspections = List.of();

    /**
     * Entity → Response 변환 팩토리
     */
    public static FirePumpResponse from(FirePump pump) {
        return FirePumpResponse.builder()
                .pumpId(pump.getPumpId())
                .serialNumber(pump.getSerialNumber())
                .qrKey(pump.getQrKey())
                .buildingName(pump.getBuildingName())
                .floorId(pump.getFloor() != null ? pump.getFloor().getFloorId() : null)
                .floorName(pump.getFloor() != null ? pump.getFloor().getFloorName() : null)
                .x(pump.getX())
                .y(pump.getY())
                .locationDescription(pump.getLocationDescription())
                .note(pump.getNote())
                .isActive(pump.isActive())
                .createdAt(pump.getCreatedAt())
                .build();
    }

    public void setLastInspection(FirePumpInspection inspection) {
        if (inspection == null) {
            return;
        }
        this.lastInspectionDate = inspection.getInspectionDate();
        this.lastInspectionTime = inspection.getInspectionTime();
        this.lastInspectorName = inspection.getInspectedByName();
        this.lastInspectionStatus = inspection.getInspectionStatus();
        this.lastInspectionNote = inspection.getNote();
    }

    public void setInspectionHistory(List<FirePumpInspection> history, List<List<InspectionChecklistItem>> checklistItems) {
        this.inspections = java.util.stream.IntStream.range(0, history.size())
                .mapToObj(index -> {
                    FirePumpInspection inspection = history.get(index);
                    List<InspectionChecklistItem> items = index < checklistItems.size() ? checklistItems.get(index) : List.of();
                    return new InspectionRow(
                            inspection.getInspectionId(),
                            inspection.getInspectionDate(),
                            inspection.getInspectionTime(),
                            inspection.getInspectedByName(),
                            inspection.getInspectionStatus(),
                            inspection.getNote(),
                            inspection.getImagePath(),
                            items
                    );
                })
                .collect(Collectors.toList());
    }

    @Getter
    public static class InspectionRow {
        private final Long inspectionId;
        private final LocalDate inspectionDate;
        private final LocalTime inspectionTime;
        private final String inspectorName;
        private final String inspectionStatus;
        private final String note;
        private final String imagePath;
        private final List<InspectionChecklistItem> checklistItems;

        public InspectionRow(Long inspectionId, LocalDate inspectionDate, LocalTime inspectionTime, String inspectorName,
                             String inspectionStatus, String note, String imagePath,
                             List<InspectionChecklistItem> checklistItems) {
            this.inspectionId = inspectionId;
            this.inspectionDate = inspectionDate;
            this.inspectionTime = inspectionTime;
            this.inspectorName = inspectorName;
            this.inspectionStatus = inspectionStatus;
            this.note = note;
            this.imagePath = imagePath;
            this.checklistItems = checklistItems;
        }
    }

    @Getter
    public static class InspectionChecklistItem {
        private final String itemKey;
        private final String itemLabel;
        private final String result;

        public InspectionChecklistItem(String itemKey, String itemLabel, String result) {
            this.itemKey = itemKey;
            this.itemLabel = itemLabel;
            this.result = result;
        }
    }
}
