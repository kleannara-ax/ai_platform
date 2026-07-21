package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 특이사항 저장/수정 요청 DTO
 */
@Getter
@Setter
public class RemarkRequest {

    /** 관련 표 코드 (null이면 전체 일보 공통) */
    private String tableCode;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    @NotBlank(message = "특이사항 내용은 필수입니다.")
    private String content;

    private Integer sortOrder;
}
