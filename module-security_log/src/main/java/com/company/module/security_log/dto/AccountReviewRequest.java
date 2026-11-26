package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 접속 계정 검토 저장 요청 (선택한 계정 일괄)
 * - reviewStatus: NORMAL(정상 확인) / CONFIRMED(이상 확인, 조치 필요) / RESOLVED(조치완료) / PENDING(미검토로 되돌리기)
 * - CONFIRMED·RESOLVED 는 reviewComment(소명·조치 내용) 필수
 */
@Getter
@Setter
public class AccountReviewRequest {

    @NotEmpty(message = "검토할 계정을 선택하세요.")
    @Size(max = 1000, message = "한 번에 최대 1000개 계정까지 처리할 수 있습니다.")
    private List<String> accountIds;

    @NotBlank(message = "검토 상태는 필수입니다.")
    private String reviewStatus;

    @Size(max = 2000, message = "소명·조치 내용은 2000자까지 입력할 수 있습니다.")
    private String reviewComment;
}
