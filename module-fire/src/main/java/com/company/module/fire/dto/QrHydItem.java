package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * QR 코드 페이지 소화전 항목 DTO
 */
@Getter
@AllArgsConstructor
public class QrHydItem {
    private final Long hydrantId;
    private final String serialNumber;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
    private final String hydrantType;
    private final String operationType;
    private final String locationDescription;
}
