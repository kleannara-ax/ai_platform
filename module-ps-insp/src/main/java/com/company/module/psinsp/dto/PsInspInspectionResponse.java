package com.company.module.psinsp.dto;

import com.company.module.psinsp.entity.PsInspInspection;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PS 지분 검사 결과 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspInspectionResponse {

    private Long inspectionId;
    private Integer seq;
    private String inspItemGrpCd;
    private String matnr;
    private String matnrNm;
    private String werks;
    private LocalDateTime msrmDate;
    private Integer prcSeqno;
    private String lotnr;
    private String indBcd;
    private String indBcdSeq;
    private LocalDateTime inspectedAt;
    private Integer thresholdMax;
    private Integer totalCount;
    private BigDecimal coverageRatio;
    private Integer densityCount;
    private BigDecimal densityRatio;
    private BigDecimal sizeUniformityScore;
    private BigDecimal distributionUniformityScore;
    private BigDecimal meanSize;
    private BigDecimal stdSize;
    private Integer autoCount;
    private Integer manualCount;
    private Integer removedAutoCount;
    private Integer bucketUpTo3;
    private Integer bucketUpTo5;
    private Integer bucketUpTo7;
    private Integer bucketOver7;
    private Integer quadrantTopLeft;
    private Integer quadrantTopRight;
    private Integer quadrantBottomLeft;
    private Integer quadrantBottomRight;
    private Long objectPixelCount;
    private Long totalPixels;
    private Integer manualAddedCount;
    private Integer manualRemovedCount;
    private String originalImagePath;
    private String originalImageName;
    private String originalImageDir;
    private String resultImagePath;
    private String resultImageName;
    private String resultImageDir;
    private String operatorId;
    private String operatorNm;
    private String deviceId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** INSERT / UPDATE 구분 */
    private Boolean isUpdate;

    /**
     * Entity -> Response DTO 변환 (static 팩토리 메서드)
     */
    public static PsInspInspectionResponse from(PsInspInspection entity) {
        return from(entity, null);
    }

    /**
     * Entity -> Response DTO 변환 (isUpdate 포함)
     */
    public static PsInspInspectionResponse from(PsInspInspection entity, Boolean isUpdate) {
        return PsInspInspectionResponse.builder()
                .inspectionId(entity.getInspectionId())
                .seq(entity.getSeq())
                .inspItemGrpCd(entity.getInspItemGrpCd())
                .matnr(entity.getMatnr())
                .matnrNm(entity.getMatnrNm())
                .werks(entity.getWerks())
                .msrmDate(entity.getMsrmDate())
                .prcSeqno(entity.getPrcSeqno())
                .lotnr(entity.getLotnr())
                .indBcd(entity.getIndBcd())
                .indBcdSeq(entity.getIndBcdSeq())
                .inspectedAt(entity.getInspectedAt())
                .thresholdMax(entity.getThresholdMax())
                .totalCount(entity.getTotalCount())
                .coverageRatio(entity.getCoverageRatio())
                .densityCount(entity.getDensityCount())
                .densityRatio(entity.getDensityRatio())
                .sizeUniformityScore(entity.getSizeUniformityScore())
                .distributionUniformityScore(entity.getDistributionUniformityScore())
                .meanSize(entity.getMeanSize())
                .stdSize(entity.getStdSize())
                .autoCount(entity.getAutoCount())
                .manualCount(entity.getManualCount())
                .removedAutoCount(entity.getRemovedAutoCount())
                .bucketUpTo3(entity.getBucketUpTo3())
                .bucketUpTo5(entity.getBucketUpTo5())
                .bucketUpTo7(entity.getBucketUpTo7())
                .bucketOver7(entity.getBucketOver7())
                .quadrantTopLeft(entity.getQuadrantTopLeft())
                .quadrantTopRight(entity.getQuadrantTopRight())
                .quadrantBottomLeft(entity.getQuadrantBottomLeft())
                .quadrantBottomRight(entity.getQuadrantBottomRight())
                .objectPixelCount(entity.getObjectPixelCount())
                .totalPixels(entity.getTotalPixels())
                .manualAddedCount(entity.getManualAddedCount())
                .manualRemovedCount(entity.getManualRemovedCount())
                .originalImagePath(entity.getOriginalImagePath())
                .originalImageName(entity.getOriginalImageName())
                .originalImageDir(entity.getOriginalImageDir())
                .resultImagePath(entity.getResultImagePath())
                .resultImageName(entity.getResultImageName())
                .resultImageDir(entity.getResultImageDir())
                .operatorId(entity.getOperatorId())
                .operatorNm(entity.getOperatorNm())
                .deviceId(entity.getDeviceId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
