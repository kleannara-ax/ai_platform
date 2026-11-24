package com.company.module.cckb.dto.response;

import com.company.module.cckb.entity.InspectionStandard;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "⚙ 기준값 설정" 탭 응답 DTO.
 * 원본 V4 HTML 의 DEFAULT_REF_LAB / DEFAULT_THRESHOLDS 구조를 그대로 반영한다.
 */
@Getter
@Builder
public class InspectionStandardResponse {

    private Long standardId;
    private String standardVersion;
    private BigDecimal refL;
    private BigDecimal refA;
    private BigDecimal refB;
    private BigDecimal normalDe00Max;
    private BigDecimal normalDlMax;
    private BigDecimal normalDaMax;
    private BigDecimal normalDbMax;
    private BigDecimal defectDe00Min;
    private BigDecimal defectDlMax;
    private BigDecimal defectDaMax;
    private BigDecimal defectDbMax;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String isActive;
    private String remarks;

    public static InspectionStandardResponse from(InspectionStandard e) {
        return InspectionStandardResponse.builder()
                .standardId(e.getStandardId())
                .standardVersion(e.getStandardVersion())
                .refL(e.getRefL())
                .refA(e.getRefA())
                .refB(e.getRefB())
                .normalDe00Max(e.getNormalDe00Max())
                .normalDlMax(e.getNormalDlMax())
                .normalDaMax(e.getNormalDaMax())
                .normalDbMax(e.getNormalDbMax())
                .defectDe00Min(e.getDefectDe00Min())
                .defectDlMax(e.getDefectDlMax())
                .defectDaMax(e.getDefectDaMax())
                .defectDbMax(e.getDefectDbMax())
                .effectiveFrom(e.getEffectiveFrom())
                .effectiveTo(e.getEffectiveTo())
                .isActive(e.getIsActive())
                .remarks(e.getRemarks())
                .build();
    }
}
