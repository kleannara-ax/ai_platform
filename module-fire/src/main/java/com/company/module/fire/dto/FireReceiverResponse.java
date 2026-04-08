package com.company.module.fire.dto;

import com.company.module.fire.entity.FireReceiver;
import com.company.module.fire.entity.FireReceiverInspection;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class FireReceiverResponse {

    private final Long receiverId;
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
    private List<InspectionRow> inspections = List.of();

    public FireReceiverResponse(FireReceiver receiver) {
        this.receiverId = receiver.getReceiverId();
        this.serialNumber = receiver.getSerialNumber();
        this.qrKey = receiver.getQrKey();
        this.buildingName = receiver.getBuildingName();
        this.floorId = receiver.getFloor() != null ? receiver.getFloor().getFloorId() : null;
        this.floorName = receiver.getFloor() != null ? receiver.getFloor().getFloorName() : null;
        this.x = receiver.getX();
        this.y = receiver.getY();
        this.locationDescription = receiver.getLocationDescription();
        this.note = receiver.getNote();
        this.isActive = receiver.isActive();
        this.createdAt = receiver.getCreatedAt();
    }

    public void setLastInspection(FireReceiverInspection inspection) {
        if (inspection == null) {
            return;
        }
        this.lastInspectionDate = inspection.getInspectionDate();
        this.lastInspectionTime = inspection.getInspectionTime();
        this.lastInspectorName = inspection.getInspectedByName();
        this.lastInspectionStatus = inspection.getInspectionStatus();
        this.lastInspectionNote = inspection.getNote();
    }

    public void setInspectionHistory(List<FireReceiverInspection> history, List<List<InspectionChecklistItem>> checklistItems) {
        this.inspections = java.util.stream.IntStream.range(0, history.size())
                .mapToObj(index -> {
                    FireReceiverInspection inspection = history.get(index);
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
