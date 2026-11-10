package com.company.module.cckb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 검사 1건 레코드. PK 는 자동증가가 아니라 {@code INSPECTION_ID CHAR(10)} 자연키이며,
 * 날짜(YYMMDD) + 당일 4자리 일련번호 형식(예: {@code 2611060001})을 서비스 계층에서 생성한다.
 * (원본 V4 HTML 의 {@code generateSeqNo()} 로직을 그대로 이관 — {@code CckbSequenceService} 참고)
 *
 * <p>재검사(🔁) 시 {@link #originalInspectionId} 로 최초 검사를 자기참조한다.
 */
@Entity
@Table(name = "inspection_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionRecord extends CckbBaseEntity {

    @Id
    @Column(name = "INSPECTION_ID", length = 10)
    private String inspectionId;

    @Column(name = "INSPECTION_DATETIME", nullable = false)
    private LocalDateTime inspectionDatetime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STANDARD_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_INSPECTION_STANDARD"))
    private InspectionStandard standard;

    @Column(name = "MEASURED_L", nullable = false, precision = 6, scale = 2)
    private BigDecimal measuredL;

    @Column(name = "MEASURED_A", nullable = false, precision = 6, scale = 2)
    private BigDecimal measuredA;

    @Column(name = "MEASURED_B", nullable = false, precision = 6, scale = 2)
    private BigDecimal measuredB;

    @Column(name = "DELTA_L", nullable = false, precision = 6, scale = 2)
    private BigDecimal deltaL;

    @Column(name = "DELTA_A", nullable = false, precision = 6, scale = 2)
    private BigDecimal deltaA;

    @Column(name = "DELTA_B", nullable = false, precision = 6, scale = 2)
    private BigDecimal deltaB;

    /** CIEDE2000 색차 — 주 판정값 */
    @Column(name = "DELTA_E00", nullable = false, precision = 6, scale = 2)
    private BigDecimal deltaE00;

    @Column(name = "JUDGMENT_CODE", nullable = false, length = 10)
    private String judgmentCode;

    @Column(name = "JUDGMENT_REASON", length = 200)
    private String judgmentReason;

    /** 재검사 여부 (Y/N) */
    @Column(name = "IS_REINSPECTED", nullable = false, length = 1)
    private String isReinspected;

    /** 재검사 시 최초 검사 참조 (자기참조, FK 없이 ID 문자열만 보관 — PK 가 자연키라 self-FK 생략) */
    @Column(name = "ORIGINAL_INSPECTION_ID", length = 10)
    private String originalInspectionId;

    @Builder
    private InspectionRecord(String inspectionId, InspectionStandard standard,
                              BigDecimal measuredL, BigDecimal measuredA, BigDecimal measuredB,
                              BigDecimal deltaL, BigDecimal deltaA, BigDecimal deltaB, BigDecimal deltaE00,
                              String judgmentCode, String judgmentReason,
                              String originalInspectionId, Long createdBy) {
        this.inspectionId = inspectionId;
        this.inspectionDatetime = LocalDateTime.now();
        this.standard = standard;
        this.measuredL = measuredL;
        this.measuredA = measuredA;
        this.measuredB = measuredB;
        this.deltaL = deltaL;
        this.deltaA = deltaA;
        this.deltaB = deltaB;
        this.deltaE00 = deltaE00;
        this.judgmentCode = judgmentCode;
        this.judgmentReason = judgmentReason;
        this.originalInspectionId = originalInspectionId;
        this.isReinspected = (originalInspectionId != null && !originalInspectionId.isBlank()) ? "Y" : "N";
        markCreatedBy(createdBy);
    }

    public boolean isReinspectedFlag() {
        return "Y".equals(this.isReinspected);
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }
}
