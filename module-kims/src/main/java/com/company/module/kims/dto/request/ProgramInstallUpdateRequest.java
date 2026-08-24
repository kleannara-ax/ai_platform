package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 프로그램 설치 내역 수정 요청 DTO.
 */
@Getter
@Setter
public class ProgramInstallUpdateRequest {

    @NotBlank(message = "설치 프로그램명은 필수입니다.")
    private String programName;

    private String requesterName;
    private String department;
    private String targetPc;

    @NotBlank(message = "설치 담당자는 필수입니다.")
    private String installedBy;

    @NotNull(message = "설치일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installedAt;

    private String remark;
}
