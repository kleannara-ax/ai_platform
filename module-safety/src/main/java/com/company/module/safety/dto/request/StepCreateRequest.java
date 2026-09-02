package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 행(단계) 등록 요청.
 * <p>칸 내용은 열 구성이 매뉴얼마다 달라 고정 필드가 아니라 열 ID 기준 목록으로 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class StepCreateRequest {

    private int stepNo;

    private int sortOrder;

    private List<CellValue> values = new ArrayList<>();

    /** 칸 하나의 값 — 열 유형에 따라 text 또는 checked 를 쓴다 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CellValue {
        private Long columnId;
        private String text;
        private boolean checked;
    }
}
