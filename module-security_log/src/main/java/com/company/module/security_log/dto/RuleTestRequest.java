package com.company.module.security_log.dto;

import com.company.module.security_log.entity.MatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 룰셋 편집 화면 - 패턴 사전 테스트 요청 (저장하지 않음) */
@Getter
@Setter
public class RuleTestRequest {

    @NotNull
    private MatchType matchType;

    @NotBlank
    @Size(max = 1000)
    private String pattern;

    /** 테스트용 샘플 로그 (여러 줄 가능) */
    @NotBlank
    @Size(max = 20000)
    private String sampleLog;
}
