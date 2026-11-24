package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiFormTemplate;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 양식 버전 1건 */
@Getter
@Builder
public class FormTemplateResponse {

    private Long formTemplateId;
    private String formType;
    private Integer versionNo;
    private String versionLabel;
    private String originalName;
    private String fileExt;
    private Long fileSize;
    private String fileHash;
    private String changeNote;
    private String validationNote;
    private boolean active;
    /** 계약 정보 자동 입력 형식 (xlsx / docx) */
    private boolean autofill;
    private LocalDateTime activatedAt;
    private String activatedByName;
    private String createdByName;
    private LocalDateTime createdAt;
    /** 등록 직후 검사 경고 */
    private List<String> warnings;

    public static FormTemplateResponse from(PiFormTemplate f, List<String> warnings) {
        return FormTemplateResponse.builder()
                .formTemplateId(f.getFormTemplateId())
                .formType(f.getFormType().getPath())
                .versionNo(f.getVersionNo())
                .versionLabel(f.getVersionLabel())
                .originalName(f.getOriginalName())
                .fileExt(f.getFileExt())
                .fileSize(f.getFileSize())
                .fileHash(f.getFileHash())
                .changeNote(f.getChangeNote())
                .validationNote(f.getValidationNote())
                .active(f.isActive())
                .autofill("xlsx".equals(f.getFileExt()) || "docx".equals(f.getFileExt()))
                .activatedAt(f.getActivatedAt())
                .activatedByName(f.getActivatedByName())
                .createdByName(f.getCreatedByName())
                .createdAt(f.getCreatedAt())
                .warnings(warnings)
                .build();
    }
}
