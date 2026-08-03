package com.company.module.fire.facility;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FacilityWaterConsumptionRequest {
    private LocalDate consumptionDate;
    private Integer bottleCount;
}
