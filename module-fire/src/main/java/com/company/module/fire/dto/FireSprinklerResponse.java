package com.company.module.fire.dto;

import com.company.module.fire.entity.FireSprinkler;
import com.company.module.fire.entity.FireSprinklerInspection;
import com.company.module.fire.service.SprinklerChecklist;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 스프링클러 목록/상세 응답 DTO
 */
@Getter
@Builder
public class FireSprinklerResponse {

    private final Long sprinklerId;
    private final String serialNumber;
    private final Long buildingId;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final BigDecimal x;
    private final BigDecimal y;
    private final String note;
    private final String qrKey;
    private final String imagePath;
    private final boolean isActive;
    private final LocalDateTime createdAt;

    /** 지금 점검할 때 적용되는 점검표 유형 (STANDARD / PARKING_TOWER) */
    private final String checklistType;

    /** 지금 점검할 때 적용되는 점검표 항목 — 상세 조회에서만 채운다 */
    @Builder.Default
    private List<SprinklerChecklist.Group> checklistGroups = List.of();

    private LocalDate lastInspectionDate;
    private LocalTime lastInspectionTime;
    private String lastInspectorName;
    private String lastInspectionStatus;
    private Boolean lastIsFaulty;
    private String lastFaultReason;
    private String lastInspectionNote;
    private Boolean inspectionRequired;

    @Builder.Default
    private List<InspectionRow> inspections = List.of();

    public static FireSprinklerResponse from(FireSprinkler sprinkler) {
        return FireSprinklerResponse.builder()
                .sprinklerId(sprinkler.getSprinklerId())
                .serialNumber(sprinkler.getSerialNumber())
                .buildingId(sprinkler.getBuilding() != null ? sprinkler.getBuilding().getBuildingId() : null)
                .buildingName(sprinkler.getBuilding() != null ? sprinkler.getBuilding().getBuildingName() : null)
                .floorId(sprinkler.getFloor() != null ? sprinkler.getFloor().getFloorId() : null)
                .floorName(sprinkler.getFloor() != null ? sprinkler.getFloor().getFloorName() : null)
                .x(sprinkler.getX())
                .y(sprinkler.getY())
                .note(sprinkler.getNote())
                .qrKey(sprinkler.getQrKey())
                .imagePath(sprinkler.getImagePath())
                .isActive(sprinkler.isActive())
                .createdAt(sprinkler.getCreatedAt())
                .checklistType(SprinklerChecklist.currentType(sprinkler))
                .build();
    }

    public void setLastInspection(FireSprinklerInspection inspection, String faultReason) {
        if (inspection == null) {
            this.lastInspectionDate = null;
            this.lastInspectionStatus = null;
            this.lastIsFaulty = null;
            this.lastFaultReason = null;
            return;
        }
        this.lastInspectionDate = inspection.getInspectionDate();
        this.lastInspectionTime = inspection.getInspectionTime();
        this.lastInspectorName = inspection.getInspectedByName();
        this.lastInspectionStatus = inspection.getInspectionStatus();
        this.lastIsFaulty = "FAULTY".equalsIgnoreCase(inspection.getInspectionStatus());
        this.lastFaultReason = this.lastIsFaulty ? faultReason : null;
        this.lastInspectionNote = inspection.getNote();
    }

    public void includeChecklistGroups() {
        this.checklistGroups = SprinklerChecklist.groups(this.checklistType);
    }

    public void setInspectionRequired(boolean inspectionRequired) {
        this.inspectionRequired = inspectionRequired;
    }

    public void setInspectionHistory(List<FireSprinklerInspection> history, List<List<InspectionChecklistItem>> checklistItems) {
        this.inspections = java.util.stream.IntStream.range(0, history.size())
                .mapToObj(index -> {
                    FireSprinklerInspection inspection = history.get(index);
                    List<InspectionChecklistItem> items = index < checklistItems.size() ? checklistItems.get(index) : List.of();
                    String faultReason = items.stream()
                            .filter(item -> "FAULTY".equalsIgnoreCase(item.getResult()))
                            .map(InspectionChecklistItem::getItemLabel)
                            .collect(Collectors.joining(", "));
                    return new InspectionRow(
                            inspection.getInspectionId(),
                            inspection.getInspectionDate(),
                            inspection.getInspectionTime(),
                            inspection.getInspectedByName(),
                            inspection.getInspectionStatus(),
                            "FAULTY".equalsIgnoreCase(inspection.getInspectionStatus()),
                            faultReason.isBlank() ? null : faultReason,
                            inspection.getNote(),
                            SprinklerChecklist.effectiveType(inspection),
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
        private final boolean isFaulty;
        private final String faultReason;
        private final String note;
        /** 이력의 점검표 유형 — STATUS_ONLY 는 항목 없이 정상/비정상만 표시 */
        private final String checklistType;
        private final List<InspectionChecklistItem> checklistItems;

        public InspectionRow(Long inspectionId, LocalDate inspectionDate, LocalTime inspectionTime, String inspectorName,
                             String inspectionStatus, boolean isFaulty, String faultReason, String note,
                             String checklistType, List<InspectionChecklistItem> checklistItems) {
            this.inspectionId = inspectionId;
            this.inspectionDate = inspectionDate;
            this.inspectionTime = inspectionTime;
            this.inspectorName = inspectorName;
            this.inspectionStatus = inspectionStatus;
            this.isFaulty = isFaulty;
            this.faultReason = faultReason;
            this.note = note;
            this.checklistType = checklistType;
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
