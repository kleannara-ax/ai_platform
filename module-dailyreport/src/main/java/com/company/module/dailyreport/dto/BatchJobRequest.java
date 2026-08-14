package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 재업로드 요청 DTO (★ 신규, 2026-08)
 *
 * 오전 8:05 이후 "수정" 버튼을 누르면 사용자가 라디오 버튼으로 아래 3가지
 * 선택지 중 정확히 하나를 골라야 한다:
 *   "1" = 공장일보 재업로드   (특이사항 / 표2 제지 재공품 항목 수정)
 *   "2" = 세부공장일보 재업로드 (표 1, 2, 3, 4 값 수정 / 보고서 이미지 수정)
 *   "3" = 모두 재업로드       (공장일보 + 세부공장일보 둘 다)
 */
@Getter
@Setter
public class BatchJobRequest {

    /** 구분: 1=공장일보, 2=세부공장일보, 3=모두 */
    @NotBlank(message = "게시판 구분은 필수입니다.")
    @Pattern(regexp = "^[123]$", message = "게시판 구분은 1, 2, 3 중 하나여야 합니다.")
    private String batchType;
}
