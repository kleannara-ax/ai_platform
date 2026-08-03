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

    /**
     * 특이사항 내용
     * ★★ 2026-08: @NotBlank 제거 — 이제 일보 생성 시 5개 사업부 행이 항상
     * 미리 만들어지고(값 이어받기), 전일 값이 없으면 빈 값 그대로 이어받을 수
     * 있다. 프론트가 "저장" 클릭 시 편집 가능한 행을 모두 다시 전송하므로,
     * 사용자가 건드리지 않은 빈 이어받기 행도 요청에 포함될 수 있어 빈 값을
     * 허용해야 한다(서버 DailyReportService.updateRemark가 "이전 값과 동일하면
     * 아무 것도 하지 않음"으로 안전하게 처리한다).
     */
    private String content;

    private Integer sortOrder;
}
