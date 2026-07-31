package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 특이사항 저장/수정 요청 DTO
 *
 * ★★ 2026-07 개편: 특이사항이 사업부별 5행(제지/화장지/패드/사고·안전사고/기타)
 * 구조로 바뀌면서, tableCode는 항상 'TBL_SPECIAL_NOTE'이고 category가
 * 사업부 코드(PAPER/TISSUE/PAD/SAFETY/ETC)를 나타낸다.
 */
@Getter
@Setter
public class RemarkRequest {

    /** 관련 표 코드 (특이사항은 항상 'TBL_SPECIAL_NOTE') */
    private String tableCode;

    /** 사업부 코드: PAPER(제지) / TISSUE(화장지) / PAD(패드) / SAFETY(사고/안전사고) / ETC(기타) */
    @NotBlank(message = "사업부 구분은 필수입니다.")
    private String category;

    @NotBlank(message = "특이사항 내용은 필수입니다.")
    private String content;

    private Integer sortOrder;
}
