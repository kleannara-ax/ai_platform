package com.company.module.fire.dto;

import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.ExtinguisherInspection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 소화기 목록/상세 응답 DTO
 */
@Getter
@Builder
public class ExtinguisherResponse {

    private final Long extinguisherId;
    private final String serialNumber;
    private final String qrKey;
    private final Long buildingId;
    private final String buildingName;
    private final Long floorId;
    private final String floorName;
    private final String extinguisherType;
    private final LocalDate manufactureDate;
    private final int replacementCycleYears;
    private final LocalDate replacementDueDate;
    private final int quantity;
    private final String note;
    private final String imagePath;
    private final BigDecimal x;
    private final BigDecimal y;
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
    public static ExtinguisherResponse from(Extinguisher e) {
        return ExtinguisherResponse.builder()
                .extinguisherId(e.getExtinguisherId())
                .serialNumber(e.getSerialNumber())
                .qrKey(e.getNoteKey())
                .buildingId(e.getBuilding() != null ? e.getBuilding().getBuildingId() : null)
                .buildingName(e.getBuilding() != null ? e.getBuilding().getBuildingName() : null)
                .floorId(e.getFloor() != null ? e.getFloor().getFloorId() : null)
                .floorName(e.getFloor() != null ? e.getFloor().getFloorName() : null)
                .extinguisherType(e.getExtinguisherType())
                .manufactureDate(e.getManufactureDate())
                .replacementCycleYears(e.getReplacementCycleYears())
                .replacementDueDate(e.getReplacementDueDate())
                .quantity(e.getQuantity())
                .note(e.getNote())
                .imagePath(e.getImagePath())
                .x(e.getX())
                .y(e.getY())
                .createdAt(e.getCreatedAt())
                .build();
    }

    public void setLastInspection(ExtinguisherInspection inspection) {
        if (inspection != null) {
            this.lastInspectionDate = inspection.getInspectionDate();
            this.lastInspectorName = inspection.getInspectedByName();
            this.lastIsFaulty = inspection.isFaulty();
            this.lastFaultReason = inspection.getFaultReason();
        }
    }

    public void setInspectionHistory(List<ExtinguisherInspection> list) {
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
