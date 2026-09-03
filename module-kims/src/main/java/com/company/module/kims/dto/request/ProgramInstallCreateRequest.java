package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 프로그램 설치 내역 등록 요청 DTO.
 */
@Getter
@Setter
public class ProgramInstallCreateRequest {

    @NotBlank(message = "설치 프로그램명은 필수입니다.")
    private String programName;

    private String requesterName;
    private String department;
    private String targetPc;

    @NotBlank(message = "설치 담당자는 필수입니다.")
    private String installedBy;

    /** 설치일 (미입력 시 오늘) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installedAt;

    private String remark;

    /** 연결할 업무 요청 ID (선택) */
    private Long requestId;
}
