package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** 양식 종류별 현황 (양식 관리 화면) */
@Getter
@Builder
public class FormTemplateGroupResponse {

    /** URL 경로 값 (checklist / security-pledge / destruction-confirm) */
    private String formType;
    private String code;
    private String label;
    private String description;
    private List<String> extensions;
    /** docx 자리표시자 → 설명 */
    private Map<String, String> placeholders;
    /** 체크리스트 양식 구조 규칙 */
    private List<String> rules;
    /** 사용 중 버전 (v2 / 기본) */
    private String currentVersionLabel;
    private String currentOriginalName;
    private boolean currentAutofill;
    private boolean usingDefault;
    private String defaultFileName;
    /** 등록 버전 (최신순, 삭제 제외) */
    private List<FormTemplateResponse> versions;
}
