package com.company.module.fire.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QrReceiverItem {
    private final Long receiverId;
    private final String qrKey;
    private final String buildingName;
    private final String floorName;
}
