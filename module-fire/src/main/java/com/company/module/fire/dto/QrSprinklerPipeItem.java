package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QrSprinklerPipeItem {
    private final Long sprinklerPipeId;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
    private final String locationDescription;
}
