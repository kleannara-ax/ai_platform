package com.company.module.psinsp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PS 지분 검사 결과 엔티티 (점보롤 지분 검사)
 *
 * <p>Table: ps_insp_inspection (소문자 snake_case)
 * <p>Column: UPPER_SNAKE_CASE
 * <p>PK: INSPECTION_ID (BIGINT, AUTO_INCREMENT)
 */
@Entity
@Table(name = "ps_insp_inspection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSPECTION_ID")
    private Long inspectionId;

    @Column(name = "SEQ", nullable = false)
    private Integer seq;

    @Column(name = "INSP_ITEM_GRP_CD", length = 100)
    private String inspItemGrpCd;

    @Column(name = "MATNR", length = 100)
    private String matnr;

    @Column(name = "MATNR_NM", length = 500)
    private String matnrNm;

    @Column(name = "WERKS", length = 100)
    private String werks;

    @Column(name = "MSRM_DATE")
    private LocalDateTime msrmDate;

    @Column(name = "PRC_SEQNO")
    private Integer prcSeqno;

    @Column(name = "LOTNR", length = 200)
    private String lotnr;

    @Column(name = "IND_BCD", length = 200)
    private String indBcd;

    @Column(name = "IND_BCD_SEQ", length = 100)
    private String indBcdSeq;

    @Column(name = "INSPECTED_AT")
    private LocalDateTime inspectedAt;

    @Column(name = "THRESHOLD_MAX")
    private Integer thresholdMax;

    @Column(name = "TOTAL_COUNT")
    private Integer totalCount;

    @Column(name = "COVERAGE_RATIO", precision = 12, scale = 6)
    private BigDecimal coverageRatio;

    @Column(name = "DENSITY_COUNT")
    private Integer densityCount;

    @Column(name = "DENSITY_RATIO", precision = 8, scale = 4)
    private BigDecimal densityRatio;

    @Column(name = "SIZE_UNIFORMITY_SCORE", precision = 8, scale = 4)
    private BigDecimal sizeUniformityScore;

    @Column(name = "DISTRIBUTION_UNIFORMITY_SCORE", precision = 8, scale = 4)
    private BigDecimal distributionUniformityScore;

    @Column(name = "MEAN_SIZE", precision = 12, scale = 4)
    private BigDecimal meanSize;

    @Column(name = "STD_SIZE", precision = 12, scale = 4)
    private BigDecimal stdSize;

    @Column(name = "AUTO_COUNT")
    private Integer autoCount;

    @Column(name = "MANUAL_COUNT")
    private Integer manualCount;

    @Column(name = "REMOVED_AUTO_COUNT")
    private Integer removedAutoCount;

    @Column(name = "BUCKET_UP_TO_3")
    private Integer bucketUpTo3;

    @Column(name = "BUCKET_UP_TO_5")
    private Integer bucketUpTo5;

    @Column(name = "BUCKET_UP_TO_7")
    private Integer bucketUpTo7;

    @Column(name = "BUCKET_OVER_7")
    private Integer bucketOver7;

    @Column(name = "QUADRANT_TOP_LEFT")
    private Integer quadrantTopLeft;

    @Column(name = "QUADRANT_TOP_RIGHT")
    private Integer quadrantTopRight;

    @Column(name = "QUADRANT_BOTTOM_LEFT")
    private Integer quadrantBottomLeft;

    @Column(name = "QUADRANT_BOTTOM_RIGHT")
    private Integer quadrantBottomRight;

    @Column(name = "OBJECT_PIXEL_COUNT")
    private Long objectPixelCount;

    @Column(name = "TOTAL_PIXELS")
    private Long totalPixels;

    @Column(name = "MANUAL_ADDED_COUNT")
    private Integer manualAddedCount;

    @Column(name = "MANUAL_REMOVED_COUNT")
    private Integer manualRemovedCount;

    @Column(name = "ORIGINAL_IMAGE_PATH", length = 1000)
    private String originalImagePath;

    @Column(name = "ORIGINAL_IMAGE_NAME", length = 500)
    private String originalImageName;

    @Column(name = "ORIGINAL_IMAGE_DIR", length = 1000)
    private String originalImageDir;

    @Column(name = "RESULT_IMAGE_PATH", length = 1000)
    private String resultImagePath;

    @Column(name = "RESULT_IMAGE_NAME", length = 500)
    private String resultImageName;

    @Column(name = "RESULT_IMAGE_DIR", length = 1000)
    private String resultImageDir;

    @Column(name = "OPERATOR_ID", length = 200)
    private String operatorId;

    @Column(name = "OPERATOR_NM", length = 200)
    private String operatorNm;

    @Column(name = "DEVICE_ID", length = 200)
    private String deviceId;

    @Column(name = "STATUS", length = 100)
    private String status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.inspectedAt == null) {
            this.inspectedAt = LocalDateTime.now();
        }
        if (this.msrmDate == null) {
            this.msrmDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재검사 시 검사 데이터 업데이트 (setter 사용 금지 - 비즈니스 메서드로 갱신)
     */
    public void updateInspectionData(
            LocalDateTime inspectedAt, LocalDateTime msrmDate,
            Integer thresholdMax, Integer totalCount,
            BigDecimal coverageRatio, Integer densityCount, BigDecimal densityRatio,
            BigDecimal sizeUniformityScore, BigDecimal distributionUniformityScore,
            BigDecimal meanSize, BigDecimal stdSize,
            Integer autoCount, Integer manualCount, Integer removedAutoCount,
            Integer bucketUpTo3, Integer bucketUpTo5, Integer bucketUpTo7, Integer bucketOver7,
            Integer quadrantTopLeft, Integer quadrantTopRight,
            Integer quadrantBottomLeft, Integer quadrantBottomRight,
            Long objectPixelCount, Long totalPixels,
            Integer manualAddedCount, Integer manualRemovedCount,
            String operatorId, String operatorNm, String deviceId, String status) {

        this.inspectedAt = inspectedAt != null ? inspectedAt : LocalDateTime.now();
        this.msrmDate = msrmDate != null ? msrmDate : LocalDateTime.now();
        this.thresholdMax = thresholdMax;
        this.totalCount = totalCount != null ? totalCount : 0;
        this.coverageRatio = coverageRatio;
        this.densityCount = densityCount;
        this.densityRatio = densityRatio;
        this.sizeUniformityScore = sizeUniformityScore;
        this.distributionUniformityScore = distributionUniformityScore;
        this.meanSize = meanSize;
        this.stdSize = stdSize;
        this.autoCount = autoCount != null ? autoCount : 0;
        this.manualCount = manualCount != null ? manualCount : 0;
        this.removedAutoCount = removedAutoCount != null ? removedAutoCount : 0;
        this.bucketUpTo3 = bucketUpTo3;
        this.bucketUpTo5 = bucketUpTo5;
        this.bucketUpTo7 = bucketUpTo7;
        this.bucketOver7 = bucketOver7;
        this.quadrantTopLeft = quadrantTopLeft;
        this.quadrantTopRight = quadrantTopRight;
        this.quadrantBottomLeft = quadrantBottomLeft;
        this.quadrantBottomRight = quadrantBottomRight;
        this.objectPixelCount = objectPixelCount;
        this.totalPixels = totalPixels;
        this.manualAddedCount = manualAddedCount != null ? manualAddedCount : 0;
        this.manualRemovedCount = manualRemovedCount != null ? manualRemovedCount : 0;
        this.operatorId = operatorId;
        this.operatorNm = operatorNm;
        this.deviceId = deviceId;
        this.status = status;
    }

    /**
     * 원본 이미지 경로 업데이트
     */
    public void updateOriginalImage(String path, String name, String dir) {
        if (path != null) {
            this.originalImagePath = path;
            this.originalImageName = name;
            this.originalImageDir = dir;
        }
    }

    /**
     * 결과 이미지 경로 업데이트
     */
    public void updateResultImage(String path, String name, String dir) {
        if (path != null) {
            this.resultImagePath = path;
            this.resultImageName = name;
            this.resultImageDir = dir;
        }
    }

    /**
     * 자재명 업데이트
     */
    public void updateMatnrNm(String matnrNm) {
        if (matnrNm != null) this.matnrNm = matnrNm;
    }

    /**
     * 차수(indBcdSeq) 증가 - 재검사 시 호출
     */
    public void incrementIndBcdSeq() {
        int current = 1;
        try { current = Integer.parseInt(this.indBcdSeq); } catch (Exception ignored) {}
        this.indBcdSeq = String.valueOf(current + 1);
    }
}
