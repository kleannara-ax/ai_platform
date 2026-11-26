package com.company.module.security_log.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DLP 모니터링 보고서 입력값 저장 (표지 정보 + 항목별 입력값)
 * - items 에 없는 항목은 그대로 둠, 값을 비우면 저장된 값도 비움
 */
@Getter
@Setter
public class DlpReportSaveRequest {

    @Valid
    private Cover cover;

    @Valid
    @Size(max = 2000, message = "한 번에 2,000개까지 저장할 수 있습니다.")
    private List<Item> items;

    @Getter
    @Setter
    public static class Cover {
        @Size(max = 100, message = "보고 구분은 100자까지 입력할 수 있습니다.")
        private String reportType;
        @Size(max = 200, message = "대상 시스템은 200자까지 입력할 수 있습니다.")
        private String targetSystem;
        @Size(max = 200, message = "작성 부서는 200자까지 입력할 수 있습니다.")
        private String writerDept;
        @Size(max = 500, message = "회사 식별 단어는 500자까지 입력할 수 있습니다.")
        private String companyKeywords;
    }

    @Getter
    @Setter
    public static class Item {
        /** USB_UNREG / KAKAO_USER / DEPT_HEADCOUNT / PI_REINSTALL / PI_OFFLINE / AI_USER */
        @NotBlank(message = "보고서 항목은 필수입니다.")
        private String section;
        @NotBlank(message = "대상은 필수입니다.")
        @Size(max = 300, message = "대상은 300자까지입니다.")
        private String itemKey;
        @Size(max = 100, message = "선택 값은 100자까지입니다.")
        private String value1;
        @Size(max = 1000, message = "내용은 1000자까지 입력할 수 있습니다.")
        private String value2;
    }
}
