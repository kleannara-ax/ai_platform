package com.company.module.cckb.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * "⚙ 기준값 설정" 탭 저장 요청 DTO.
 * 원본 V4 HTML 의 설정 저장 핸들러가 검증하던 11개 숫자 필드(refreshSettingsUI 참고)를 그대로 받는다.
 */
@Getter
@NoArgsConstructor
public class InspectionStandardUpdateRequest {

    @NotNull private BigDecimal refL;
    @NotNull private BigDecimal refA;
    @NotNull private BigDecimal refB;

    @NotNull private BigDecimal normalDe00Max;
    @NotNull private BigDecimal normalDlMax;
    @NotNull private BigDecimal normalDaMax;
    @NotNull private BigDecimal normalDbMax;

    @NotNull private BigDecimal defectDe00Min;
    @NotNull private BigDecimal defectDlMax;
    @NotNull private BigDecimal defectDaMax;
    @NotNull private BigDecimal defectDbMax;

    /** 변경 사유 (standard_audit_log.CHANGE_REASON) */
    private String remarks;
}
