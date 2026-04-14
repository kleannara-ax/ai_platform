package com.company.module.psinsp.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PS 지분 검사 결과 저장 요청 DTO
 */
@Getter
@Setter
public class PsInspectionSaveRequest {

    @Size(max = 100)
    private String inspItemGrpCd;

    @Size(max = 100)
    private String matnr;

    @Size(max = 500)
    private String matnrNm;

    @Size(max = 100)
    private String werks;

    private LocalDateTime msrmDate;

    private Integer prcSeqno;

    @Size(max = 200)
    private String lotnr;

    @Size(max = 200)
    private String indBcd;

    @Size(max = 100)
    private String indBcdSeq;

    private LocalDateTime inspectedAt;

    @Min(0) @Max(255)
    private Integer thresholdMax;

    @Min(0)
    private Integer totalCount;

    private BigDecimal coverageRatio;
    private Integer densityCount;
    private BigDecimal densityRatio;
    private BigDecimal sizeUniformityScore;
    private BigDecimal distributionUniformityScore;
    private BigDecimal meanSize;
    private BigDecimal stdSize;

    @Min(0) private Integer autoCount;
    @Min(0) private Integer manualCount;
    @Min(0) private Integer removedAutoCount;

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

    @Min(0) private Integer manualAddedCount;
    @Min(0) private Integer manualRemovedCount;

    @Size(max = 1000) private String originalImagePath;
    @Size(max = 1000) private String resultImagePath;

    @Size(max = 200) private String operatorId;
    @Size(max = 200) private String operatorNm;
    @Size(max = 200) private String deviceId;
    @Size(max = 100) private String status;
}
