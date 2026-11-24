package com.company.module.cckb.dto.response;

import com.company.module.cckb.entity.JudgmentCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JudgmentCodeResponse {
    private String judgmentCode;
    private String judgmentNameKr;
    private String colorHex;
    private Integer sortOrder;
    private String description;

    public static JudgmentCodeResponse from(JudgmentCode e) {
        return JudgmentCodeResponse.builder()
                .judgmentCode(e.getJudgmentCode())
                .judgmentNameKr(e.getJudgmentNameKr())
                .colorHex(e.getColorHex())
                .sortOrder(e.getSortOrder())
                .description(e.getDescription())
                .build();
    }
}
