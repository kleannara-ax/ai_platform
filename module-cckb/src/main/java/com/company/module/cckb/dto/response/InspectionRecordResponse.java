package com.company.module.cckb.dto.response;

import com.company.module.cckb.entity.InspectionRecord;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검사 이력 카드 1건 응답 DTO.
 * 원본 V4 HTML 의 renderHistoryCard(r) 가 쓰던 필드를 그대로 반영
 * (refLab/cmpLab/deltaL,a,b/deltaE00/judgment label/reason/thresholds/images).
 */
@Getter
@Builder
public class InspectionRecordResponse {

    private String inspectionId;
    private LocalDateTime inspectionDatetime;

    private BigDecimal refL;
    private BigDecimal refA;
    private BigDecimal refB;

    /**
     * 이 검사 당시 적용되었던 기준(standard)의 판정 임계값 전체 — 원본 V4 HTML 의 r.thresholds
     * (renderHistoryCard 의 "판정 기준: 정상 &lt; X · 불량 &gt; Y" 문구 + updateDeltaBlock 의
     * delta-alert 강조 기준(|Δ*| &gt; normal*Max)에 그대로 사용).
     */
    private BigDecimal normalDe00Max;
    private BigDecimal normalDlMax;
    private BigDecimal normalDaMax;
    private BigDecimal normalDbMax;
    private BigDecimal defectDe00Min;

    private BigDecimal measuredL;
    private BigDecimal measuredA;
    private BigDecimal measuredB;

    private BigDecimal deltaL;
    private BigDecimal deltaA;
    private BigDecimal deltaB;
    private BigDecimal deltaE00;

    private String judgmentCode;
    private String judgmentNameKr;
    private String colorHex;
    private String judgmentReason;

    private boolean reinspected;
    private String originalInspectionId;

    /** 비교사진(CMP) 목록 — 프론트엔드가 fetch()+blob 으로 조회 */
    private List<CckbImageResponse> images;

    public static InspectionRecordResponse from(InspectionRecord e, String judgmentNameKr, String colorHex,
                                                 List<CckbImageResponse> images) {
        return InspectionRecordResponse.builder()
                .inspectionId(e.getInspectionId())
                .inspectionDatetime(e.getInspectionDatetime())
                .refL(e.getStandard() != null ? e.getStandard().getRefL() : null)
                .refA(e.getStandard() != null ? e.getStandard().getRefA() : null)
                .refB(e.getStandard() != null ? e.getStandard().getRefB() : null)
                .normalDe00Max(e.getStandard() != null ? e.getStandard().getNormalDe00Max() : null)
                .normalDlMax(e.getStandard() != null ? e.getStandard().getNormalDlMax() : null)
                .normalDaMax(e.getStandard() != null ? e.getStandard().getNormalDaMax() : null)
                .normalDbMax(e.getStandard() != null ? e.getStandard().getNormalDbMax() : null)
                .defectDe00Min(e.getStandard() != null ? e.getStandard().getDefectDe00Min() : null)
                .measuredL(e.getMeasuredL())
                .measuredA(e.getMeasuredA())
                .measuredB(e.getMeasuredB())
                .deltaL(e.getDeltaL())
                .deltaA(e.getDeltaA())
                .deltaB(e.getDeltaB())
                .deltaE00(e.getDeltaE00())
                .judgmentCode(e.getJudgmentCode())
                .judgmentNameKr(judgmentNameKr)
                .colorHex(colorHex)
                .judgmentReason(e.getJudgmentReason())
                .reinspected(e.isReinspectedFlag())
                .originalInspectionId(e.getOriginalInspectionId())
                .images(images)
                .build();
    }
}
