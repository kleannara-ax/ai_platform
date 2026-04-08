package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QrPumpItem {
    private final Long pumpId;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
}
