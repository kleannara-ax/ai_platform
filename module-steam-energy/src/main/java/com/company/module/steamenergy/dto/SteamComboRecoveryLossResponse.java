package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamComboRecoveryLoss;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 복합보일러 스팀 회수 감소 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboRecoveryLossResponse {

    private Long comboRecoveryId;
    private Integer yearNo;
    private Integer monthNo;
    private Integer lineNo;
    private String lossDate;
    private String lossTime;
    private String lossNote;
    private BigDecimal lossSteamRate;
    private BigDecimal lossBaseRate;
    private BigDecimal lossMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamComboRecoveryLossResponse from(SteamComboRecoveryLoss e) {
        return from(e, null);
    }

    public static SteamComboRecoveryLossResponse from(SteamComboRecoveryLoss e, Boolean isUpdate) {
        return SteamComboRecoveryLossResponse.builder()
                .comboRecoveryId(e.getComboRecoveryId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .lineNo(e.getLineNo())
                .lossDate(e.getLossDate())
                .lossTime(e.getLossTime())
                .lossNote(e.getLossNote())
                .lossSteamRate(e.getLossSteamRate())
                .lossBaseRate(e.getLossBaseRate())
                .lossMinutes(e.getLossMinutes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
