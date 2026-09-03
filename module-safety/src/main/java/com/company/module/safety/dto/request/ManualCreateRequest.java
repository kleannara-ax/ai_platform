package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 관리자가 화면에서 매뉴얼을 직접 추가할 때 쓰는 요청 (단계까지 한번에 등록) */
@Getter
@NoArgsConstructor
public class ManualCreateRequest {

    @NotNull(message = "분류는 필수입니다.")
    private Long categoryId;

    @NotBlank(message = "매뉴얼 제목은 필수입니다.")
    private String title;

    private int sortOrder;

    /** 서식 코드 (WORK_METHOD / RISK_ASSESSMENT). 비우면 안전작업 매뉴얼로 만든다. */
    private String formType;

    private List<StepCreateRequest> steps = new ArrayList<>();
}
