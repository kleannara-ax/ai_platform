package com.company.module.fire.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FireReceiverSaveRequest {

    private Long receiverId;

    @NotBlank(message = "건물 정보를 입력하세요.")
    private String buildingName;

    private BigDecimal x;
    private BigDecimal y;
    private String locationDescription;
    private String note;
}
