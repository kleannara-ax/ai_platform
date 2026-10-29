package com.company.module.security_log.dto;

import com.company.module.security_log.entity.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 룰 단위 일괄 검토 요청
 * - 같은 룰로 탐지된 건을 한 번에 검토/조치 등록 (예: 로그인 실패 - 단순 비밀번호 오입력)
 */
@Getter
@Setter
public class RuleReviewRequest {

    @NotBlank(message = "룰 코드는 필수입니다.")
    @Size(max = 50)
    private String ruleCode;

    @NotNull(message = "검토 상태는 필수입니다.")
    private ReviewStatus reviewStatus;

    @Size(max = 2000)
    private String actionContent;

    /** true(기본): 미검토 건만 처리 / false: 이미 검토한 건도 덮어씀 */
    private Boolean onlyPending = Boolean.TRUE;
}
