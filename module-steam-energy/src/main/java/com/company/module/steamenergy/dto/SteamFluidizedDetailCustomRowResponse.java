package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedDetailCustomRow;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 세부 운영내역 (추가 행) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailCustomRowResponse {

    private Long detailCustomRowId;
    private Integer yearNo;
    private Integer monthNo;
    private String section;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedDetailCustomRowResponse from(SteamFluidizedDetailCustomRow e) {
        return from(e, null);
    }

    public static SteamFluidizedDetailCustomRowResponse from(SteamFluidizedDetailCustomRow e, Boolean isUpdate) {
        return SteamFluidizedDetailCustomRowResponse.builder()
                .detailCustomRowId(e.getDetailCustomRowId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .section(e.getSection())
                .rowKey(e.getRowKey())
                .label(e.getLabel())
                .insertAfter(e.getInsertAfter())
                .day01(e.getDay01())
                .day02(e.getDay02())
                .day03(e.getDay03())
                .day04(e.getDay04())
                .day05(e.getDay05())
                .day06(e.getDay06())
                .day07(e.getDay07())
                .day08(e.getDay08())
                .day09(e.getDay09())
                .day10(e.getDay10())
                .day11(e.getDay11())
                .day12(e.getDay12())
                .day13(e.getDay13())
                .day14(e.getDay14())
                .day15(e.getDay15())
                .day16(e.getDay16())
                .day17(e.getDay17())
                .day18(e.getDay18())
                .day19(e.getDay19())
                .day20(e.getDay20())
                .day21(e.getDay21())
                .day22(e.getDay22())
                .day23(e.getDay23())
                .day24(e.getDay24())
                .day25(e.getDay25())
                .day26(e.getDay26())
                .day27(e.getDay27())
                .day28(e.getDay28())
                .day29(e.getDay29())
                .day30(e.getDay30())
                .day31(e.getDay31())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
