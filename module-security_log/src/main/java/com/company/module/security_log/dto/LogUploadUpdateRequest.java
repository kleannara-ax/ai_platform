package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 업로드 기본정보 수정 요청 (파일 교체 불가 - 재업로드 필요) */
@Getter
@Setter
public class LogUploadUpdateRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])$", message = "대상 연월은 YYYYMM 형식입니다.")
    private String targetYm;

    @NotBlank
    @Size(max = 100)
    private String systemName;

    @NotNull
    private LogType logType;

    @Size(max = 1000)
    private String remark;

    /** 로그 유형 변경 시 재분석 수행 여부 */
    private boolean reanalyze;
}
