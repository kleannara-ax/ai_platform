package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * QR 코드 페이지 기타설비 항목 DTO
 */
@Getter
@AllArgsConstructor
public class QrFacilityItem {
    private final Long equipmentId;
    private final String serialNumber;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
    private final String equipmentType;
    private final String manufactureDate;
    private final String locationDescription;
    private final String note;
}
