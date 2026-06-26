package com.company.module.fire.dto;

import com.company.module.fire.entity.FireSprinklerPipe;
import com.company.module.fire.entity.FireSprinklerPipeInspection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 스프링쿨러 배관 목록/상세 응답 DTO
 */
@Getter
@Builder
public class FireSprinklerPipeResponse {

    private final Long sprinklerPipeId;
    private final String serialNumber;
    private final String qrKey;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final BigDecimal x;
    private final BigDecimal y;
    private final String locationDescription;
    private final String note;
    private final String imagePath;
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
    public static FireSprinklerPipeResponse from(FireSprinklerPipe sprinklerPipe) {
        return FireSprinklerPipeResponse.builder()
                .sprinklerPipeId(sprinklerPipe.getSprinklerPipeId())
                .serialNumber(sprinklerPipe.getSerialNumber())
                .qrKey(sprinklerPipe.getQrKey())
                .buildingName(sprinklerPipe.getBuildingName())
                .floorId(sprinklerPipe.getFloor() != null ? sprinklerPipe.getFloor().getFloorId() : null)
                .floorName(sprinklerPipe.getFloor() != null ? sprinklerPipe.getFloor().getFloorName() : null)
                .x(sprinklerPipe.getX())
                .y(sprinklerPipe.getY())
                .locationDescription(sprinklerPipe.getLocationDescription())
                .note(sprinklerPipe.getNote())
                .imagePath(sprinklerPipe.getImagePath())
                .isActive(sprinklerPipe.isActive())
                .createdAt(sprinklerPipe.getCreatedAt())
                .build();
    }

    public void setLastInspection(FireSprinklerPipeInspection inspection) {
        if (inspection == null) {
            return;
        }
        this.lastInspectionDate = inspection.getInspectionDate();
        this.lastInspectionTime = inspection.getInspectionTime();
        this.lastInspectorName = inspection.getInspectedByName();
        this.lastInspectionStatus = inspection.getInspectionStatus();
        this.lastInspectionNote = inspection.getNote();
    }

    public void setInspectionHistory(List<FireSprinklerPipeInspection> history, List<List<InspectionChecklistItem>> checklistItems) {
        this.inspections = java.util.stream.IntStream.range(0, history.size())
                .mapToObj(index -> {
                    FireSprinklerPipeInspection inspection = history.get(index);
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
