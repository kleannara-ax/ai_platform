package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 소모품 입고 요청 DTO.
 */
@Getter
@Setter
public class InboundRequest {

    @NotNull(message = "입고 수량은 필수입니다.")
    @Positive(message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @NotBlank(message = "입고 담당자는 필수입니다.")
    private String createdBy;

    /** 입고 비고 (선택) */
    private String note;

    /** 입고일 (미입력 시 서버에서 오늘 날짜로 설정) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate inboundAt;

    /**
     * 세부 구분(신형/구형, 제조사 등) — 해당 구분이 있는 품목(마우스/키보드/노트북/모니터/
     * 데스크탑 본체 → "신형"/"구형", 태블릿 → "레노버"/"갤럭시"/"대여")을 입고할 때만 선택되어
     * 전달된다. 구분이 없는 품목은 null.
     */
    private String subType;
}
