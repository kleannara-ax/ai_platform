package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 양식 검사 결과 (저장 전 미리 확인용) */
@Getter
@Builder
public class FormTemplateCheckResponse {

    /** 계약 정보 자동 입력 가능 여부 */
    private boolean autofill;
    /** 검사 결과 요약 (항목 수, 자동 입력 항목 등) */
    @Builder.Default
    private List<String> info = List.of();
    /** 등록은 되지만 확인이 필요한 점 */
    @Builder.Default
    private List<String> warnings = List.of();
}
