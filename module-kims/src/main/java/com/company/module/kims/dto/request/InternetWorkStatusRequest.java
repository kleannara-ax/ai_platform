package com.company.module.kims.dto.request;

import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 인터넷 공사 상태 변경 요청 DTO.
 */
@Getter
@Setter
public class InternetWorkStatusRequest {

    @NotNull(message = "변경할 상태는 필수입니다.")
    private InternetWorkStatus status;

    /** 완료일 (완료로 변경 시, 미입력이면 오늘) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate completedAt;
}
