package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 행(단계) 수정 요청 — 등록과 항목이 같다. */
@Getter
@Setter
@NoArgsConstructor
public class StepUpdateRequest {

    private int stepNo;

    private int sortOrder;

    private List<StepCreateRequest.CellValue> values = new ArrayList<>();
}
