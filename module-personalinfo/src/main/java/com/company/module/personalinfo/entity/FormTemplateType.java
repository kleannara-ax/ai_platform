package com.company.module.personalinfo.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;

/**
 * 보안팀이 관리하는 양식 종류 (양식 관리 화면).
 *
 * <p>등록한 양식이 없으면 모듈 jar 에 들어 있는 기본 양식({@link #defaultResource})을 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum FormTemplateType {

    CHECKLIST("checklist", "위수탁 점검 체크리스트",
            "정기 점검(1. 관리체계 / 2. 개인정보처리시스템)과 계약만료시 점검(3. 계약변경 만료시 체크)에 함께 쓰는 엑셀 양식입니다. "
                    + "협력사가 작성해 올린 파일을 시스템이 읽어 자동 판정하므로, 시트 이름과 '평가 항목' 머리글을 지켜야 합니다.",
            List.of("xlsx"), "personalinfo/checklist-template.xlsx", "개인정보 위수탁 협력사 점검 리스트", "체크리스트"),
    SECURITY_PLEDGE("security-pledge", "정보보호 및 비밀유지 확약서",
            "계약 만료 시 협력사에게서 받는 확약서 양식입니다. docx 로 올리면 {{수탁업체명}} 같은 자리표시자에 계약 정보가 자동으로 채워집니다.",
            List.of("docx", "doc", "hwp", "hwpx", "pdf"), "personalinfo/security-pledge-template.docx", "정보보호 및 비밀유지 확약서", "확약서"),
    DESTRUCTION_CONFIRM("destruction-confirm", "개인정보 파기 확인서",
            "계약 만료 시 협력사에게서 받는 파기 확인서 양식입니다. docx 로 올리면 {{수탁업체명}} 같은 자리표시자에 계약 정보가 자동으로 채워집니다.",
            List.of("docx", "doc", "hwp", "hwpx", "pdf"), "personalinfo/destruction-confirm-template.docx", "개인정보 파기 확인서", "파기 확인서");

    /** URL 경로 값 */
    private final String path;
    private final String label;
    private final String description;
    /** 업로드 허용 확장자 (첫 번째가 자동 입력이 되는 권장 형식) */
    private final List<String> extensions;
    /** 모듈 내장 기본 양식 (classpath) */
    private final String defaultResource;
    /** 기본 양식 다운로드 파일명 (확장자 제외) */
    private final String defaultFileBase;
    /** 짧은 이름 (메일 발송 이력의 양식 버전 표시용) */
    private final String shortLabel;

    public static FormTemplateType of(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (FormTemplateType t : values()) {
            if (t.path.equals(v) || t.name().equalsIgnoreCase(v)) {
                return t;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "양식 종류가 올바르지 않습니다: " + value + " (checklist / security-pledge / destruction-confirm)");
    }
}
