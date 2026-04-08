package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * QR 코드 페이지 소화기 항목 DTO
 */
@Getter
@AllArgsConstructor
public class QrExtItem {
    private final Long extinguisherId;
    private final String serialNumber;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
    private final String extinguisherType;
    private final String manufactureDate;
}
