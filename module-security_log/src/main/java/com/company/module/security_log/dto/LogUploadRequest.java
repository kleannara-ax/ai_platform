package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 로그 업로드(등록) 요청 - multipart/form-data 의 메타데이터 부분
 * 파일은 Controller 에서 MultipartFile(file) 로 별도 수신한다.
 */
@Getter
@Setter
public class LogUploadRequest {

    @NotBlank(message = "대상 연월은 필수입니다.")
    @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])$", message = "대상 연월은 YYYYMM 형식입니다.")
    private String targetYm;

    @NotBlank(message = "시스템명은 필수입니다.")
    @Size(max = 100)
    private String systemName;

    @NotNull(message = "로그 유형은 필수입니다.")
    private LogType logType;

    /** 파일 인코딩 (UTF-8 / EUC-KR / MS949). 미입력 시 UTF-8 */
    @Pattern(regexp = "^(UTF-8|EUC-KR|MS949)$", message = "지원 인코딩: UTF-8, EUC-KR, MS949")
    private String fileCharset = "UTF-8";

    @Size(max = 1000)
    private String remark;
}
