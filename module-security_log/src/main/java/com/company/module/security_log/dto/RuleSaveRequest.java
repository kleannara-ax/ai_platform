package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 탐지 룰 등록/수정 요청
 * - ruleCode 는 등록 시에만 사용되며 수정 시 무시된다(변경 불가).
 */
@Getter
@Setter
public class RuleSaveRequest {

    /** 등록 시 필수(Service 에서 검증), 수정 시 무시 */
    @Pattern(regexp = "^[A-Z0-9_]{3,50}$", message = "룰 코드는 영문 대문자/숫자/언더스코어 3~50자입니다.")
    private String ruleCode;

    @NotBlank(message = "룰명은 필수입니다.")
    @Size(max = 200)
    private String ruleName;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "적용 로그 유형은 필수입니다.")
    private LogType logType;

    @NotNull(message = "매칭 방식은 필수입니다.")
    private MatchType matchType;

    @NotBlank(message = "탐지 패턴은 필수입니다.")
    @Size(max = 1000)
    private String pattern;

    @NotNull(message = "위험도는 필수입니다.")
    private Severity severity;

    @Min(value = 1, message = "임계 건수는 1 이상입니다.")
    @Max(value = 100000)
    private Integer thresholdCount = 1;

    @Pattern(regexp = "^[YN]$", message = "사용여부는 Y 또는 N 입니다.")
    private String useYn = "Y";

    @Min(0)
    @Max(99999)
    private Integer sortOrder = 0;
}
