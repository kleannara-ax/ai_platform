package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 개인정보 다운로드 사유 검토 저장 요청 (선택한 다운로드 건 일괄)
 * - reviewStatus: NORMAL(정상 업무, 사유 확인) / CONFIRMED(사유 불명확·조치 필요) / RESOLVED(조치완료) / PENDING(미검토로 되돌리기)
 * - PENDING 외에는 reviewComment(다운로드 사유·확인 내용) 필수
 */
@Getter
@Setter
public class PiiDownloadReviewRequest {

    @NotEmpty(message = "검토할 다운로드 건을 선택하세요.")
    @Size(max = 1000, message = "한 번에 최대 1000건까지 처리할 수 있습니다.")
    private List<Long> piiDownloadIds;

    @NotBlank(message = "검토 상태는 필수입니다.")
    private String reviewStatus;

    @Size(max = 2000, message = "다운로드 사유·확인 내용은 2000자까지 입력할 수 있습니다.")
    private String reviewComment;
}
