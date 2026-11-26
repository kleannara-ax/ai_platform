package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DLP 유출 로그 업로드 요청 (multipart/form-data: file + targetYm + remark)
 */
@Getter
@Setter
public class DlpLeakUploadRequest {

    @NotBlank(message = "대상 연월은 필수입니다.")
    @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])$", message = "대상 연월은 YYYYMM 형식입니다.")
    private String targetYm;

    @Size(max = 1000, message = "비고는 1000자까지 입력할 수 있습니다.")
    private String remark;
}
