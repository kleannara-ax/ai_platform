package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 유동상 세부 운영내역 (추가 행) 저장 요청 — (YEAR_NO, MONTH_NO, SECTION, ROW_KEY) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedDetailCustomRowSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotNull(message = "구역 (kne · chemical · waste · power · water · etc)(section)은(는) 필수입니다.")
    private String section;

    @NotNull(message = "행 식별자 (화면이 만든 값, 예: r1779432186923216)(rowKey)은(는) 필수입니다.")
    private String rowKey;

    private String label;
    private Integer insertAfter;
    private BigDecimal day01;
    private BigDecimal day02;
    private BigDecimal day03;
    private BigDecimal day04;
    private BigDecimal day05;
    private BigDecimal day06;
    private BigDecimal day07;
    private BigDecimal day08;
    private BigDecimal day09;
    private BigDecimal day10;
    private BigDecimal day11;
    private BigDecimal day12;
    private BigDecimal day13;
    private BigDecimal day14;
    private BigDecimal day15;
    private BigDecimal day16;
    private BigDecimal day17;
    private BigDecimal day18;
    private BigDecimal day19;
    private BigDecimal day20;
    private BigDecimal day21;
    private BigDecimal day22;
    private BigDecimal day23;
    private BigDecimal day24;
    private BigDecimal day25;
    private BigDecimal day26;
    private BigDecimal day27;
    private BigDecimal day28;
    private BigDecimal day29;
    private BigDecimal day30;
    private BigDecimal day31;
}
