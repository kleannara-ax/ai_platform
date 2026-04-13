package com.company.module.psinsp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * MES 결과 전송 요청 DTO
 *
 * <p>MES 연동 규격:
 * <pre>
 *   { "IND_BCD": "26228J0039", "ResultData": 265.30 }
 * </pre>
 */
@Getter
@Setter
public class PsInspMesSendRequest {

    @NotBlank(message = "개별바코드(IND_BCD)는 필수입니다")
    @JsonProperty("IND_BCD")
    private String indBcd;

    @NotNull(message = "결과 데이터(ResultData)는 필수입니다")
    @JsonProperty("ResultData")
    private Double resultData;
}
