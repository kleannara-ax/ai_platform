package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 일보 생성/수정 요청 DTO
 */
@Getter
@Setter
public class DailyReportRequest {

    @NotNull(message = "일보 날짜는 필수입니다.")
    private LocalDate reportDate;

    private String title;

    private String status;
}
