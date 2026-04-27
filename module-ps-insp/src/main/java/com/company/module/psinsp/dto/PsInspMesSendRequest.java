package com.company.module.psinsp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * MES 결과 전송 요청 DTO
 *
 * <p>MES 연동 규격 (saveDustInspectionResult):
 * <pre>
 *   GET https://mesdev.kleannara.com:444/mobile/saveDustInspectionResult.data
 *       ?IND_BCD=25830J0069&RST_VAL=777
 * </pre>
 *
 * <p>프론트엔드에서는 기존과 동일하게 POST JSON으로 전송하되,
 * 백엔드에서 MES 서버로는 GET 쿼리 파라미터로 변환하여 전달합니다.
 */
@Getter
@Setter
public class PsInspMesSendRequest {

    /** 개별바코드 */
    @NotBlank(message = "개별바코드(IND_BCD)는 필수입니다")
    @JsonProperty("IND_BCD")
    private String indBcd;

    /** 검사 결과값 (ppm) - 프론트엔드에서 전달하는 값 */
    @NotNull(message = "결과 데이터(RST_VAL)는 필수입니다")
    @JsonProperty("RST_VAL")
    private Double rstVal;
}
