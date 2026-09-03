package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualMeta;
import lombok.Builder;
import lombok.Getter;

/** 매뉴얼 머리말 항목(라벨-값) */
@Getter
@Builder
public class MetaResponse {

    private final Long metaId;
    private final String label;
    private final String value;
    private final int sortOrder;

    public static MetaResponse from(SafetyManualMeta entity) {
        return MetaResponse.builder()
                .metaId(entity.getMetaId())
                .label(entity.getLabel())
                .value(entity.getValueText())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
