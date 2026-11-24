package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyMonthCompareNote;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 회계비용 마감비교 비고 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyMonthCompareNoteResponse {

    private Long monthCompareNoteId;
    private Integer yearNo;
    private String noteFuel;
    private String noteDeduct;
    private String noteActual;
    private String noteSteamRatio;
    private String notePlan;
    private String noteProd;
    private String noteProduct;
    private String noteUnit;
    private String noteProfit;
    private String noteAux;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyMonthCompareNoteResponse from(SteamEnergyMonthCompareNote e) {
        return from(e, null);
    }

    public static SteamEnergyMonthCompareNoteResponse from(SteamEnergyMonthCompareNote e, Boolean isUpdate) {
        return SteamEnergyMonthCompareNoteResponse.builder()
                .monthCompareNoteId(e.getMonthCompareNoteId())
                .yearNo(e.getYearNo())
                .noteFuel(e.getNoteFuel())
                .noteDeduct(e.getNoteDeduct())
                .noteActual(e.getNoteActual())
                .noteSteamRatio(e.getNoteSteamRatio())
                .notePlan(e.getNotePlan())
                .noteProd(e.getNoteProd())
                .noteProduct(e.getNoteProduct())
                .noteUnit(e.getNoteUnit())
                .noteProfit(e.getNoteProfit())
                .noteAux(e.getNoteAux())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
