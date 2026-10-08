package com.company.module.security_log.dto;

import com.company.module.security_log.entity.ReviewStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 탐지 결과 검토/조치 등록 요청 (단건/다건 일괄) */
@Getter
@Setter
public class DetectionReviewRequest {

    @NotEmpty(message = "검토 대상 탐지 ID는 필수입니다.")
    @Size(max = 1000, message = "한 번에 최대 1000건까지 처리할 수 있습니다.")
    private List<Long> detectionIds;

    @NotNull(message = "검토 상태는 필수입니다.")
    private ReviewStatus reviewStatus;

    @Size(max = 2000)
    private String actionContent;
}
