package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


/**
 * 사고·정비 이력 저장 요청 — (YEAR_NO, RECORD_KEY) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamIncidentSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)(recordKey)은(는) 필수입니다.")
    private String recordKey;

    private String facility;
    private String kind;
    private String occurDate;
    private String reason;
    private String actionTaken;
    private String downtimeHours;
    private String lossAmount;
    private String note;
    private Integer sortOrder;
}
