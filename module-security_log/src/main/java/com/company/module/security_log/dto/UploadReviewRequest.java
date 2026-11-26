package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 업로드 건 최종 검토완료 요청 (정보보안팀) */
@Getter
@Setter
public class UploadReviewRequest {

    @NotBlank(message = "검토 의견은 필수입니다.")
    @Size(max = 2000)
    private String reviewComment;
}
