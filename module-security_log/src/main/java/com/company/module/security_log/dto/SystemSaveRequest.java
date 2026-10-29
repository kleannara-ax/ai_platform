package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 점검 대상 시스템 등록/수정 요청 (systemCode 는 등록 시에만 사용) */
@Getter
@Setter
public class SystemSaveRequest {

    @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "시스템 코드는 영문 대문자/숫자/_ 2~50자입니다.")
    private String systemCode;

    @NotBlank(message = "시스템명은 필수입니다.")
    @Size(max = 100)
    private String systemName;

    @NotBlank(message = "시스템 구분은 필수입니다.")
    @Size(max = 50)
    private String category;

    @NotNull(message = "기본 로그 유형은 필수입니다.")
    private LogType defaultLogType;

    @Size(max = 100)
    private String managerName;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "^[YN]$")
    private String useYn = "Y";

    @Min(0)
    @Max(99999)
    private Integer sortOrder = 0;
}
