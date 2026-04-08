package com.company.module.fire.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 소화기 점검 등록 요청 DTO
 */
@Getter
@Setter
public class ExtinguisherInspectRequest {

    @NotNull(message = "소화기 ID가 필요합니다.")
    private Long extinguisherId;

    @JsonProperty("isFaulty")
    @JsonAlias({"faulty"})
    private boolean isFaulty;

    private String faultReason;

    private LocalDate inspectionDate;
}
