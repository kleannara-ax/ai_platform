package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매뉴얼 기본정보(제목/분류) 수정 — 단계는 별도 API로 관리 */
@Getter
@NoArgsConstructor
public class ManualUpdateRequest {

    @NotNull(message = "분류는 필수입니다.")
    private Long categoryId;

    @NotBlank(message = "매뉴얼 제목은 필수입니다.")
    private String title;

    private int sortOrder;
}
