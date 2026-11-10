package com.company.module.cckb.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CCKB 후면 색상검사 판정 기준값.
 * <p>원본 V4 HTML 의 "⚙ 기준값 설정" 탭에서 다루던 기준 L* / a* / b* + 정상/불량 판정 임계값을
 * DB 로 이관한 엔티티. 과거에는 localStorage({@code cckb_ref_lab_v1}, {@code cckb_thresholds_v1})
 * 에 단일 값으로만 저장했으나, 플랫폼 이관에서는 버전/적용기간을 가진 이력 테이블로 승격한다.
 *
 * <p>현재 유효한 기준값은 {@code isActive='Y'} 인 레코드 1건으로 조회한다
 * ({@link com.company.module.cckb.repository.InspectionStandardRepository#findByIsActiveAndDeletedYn}).
 */
@Entity
@Table(name = "inspection_standard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionStandard extends CckbBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STANDARD_ID")
    private Long standardId;

    @Column(name = "STANDARD_VERSION", nullable = false, length = 10)
    private String standardVersion;

    @Column(name = "REF_L", nullable = false, precision = 5, scale = 2)
    private BigDecimal refL;

    @Column(name = "REF_A", nullable = false, precision = 5, scale = 2)
    private BigDecimal refA;

    @Column(name = "REF_B", nullable = false, precision = 5, scale = 2)
    private BigDecimal refB;

    @Column(name = "NORMAL_DE00_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal normalDe00Max;

    @Column(name = "NORMAL_DL_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal normalDlMax;

    @Column(name = "NORMAL_DA_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal normalDaMax;

    @Column(name = "NORMAL_DB_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal normalDbMax;

    @Column(name = "DEFECT_DE00_MIN", nullable = false, precision = 4, scale = 2)
    private BigDecimal defectDe00Min;

    @Column(name = "DEFECT_DL_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal defectDlMax;

    @Column(name = "DEFECT_DA_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal defectDaMax;

    @Column(name = "DEFECT_DB_MAX", nullable = false, precision = 4, scale = 2)
    private BigDecimal defectDbMax;

    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL 이면 현재까지 유효 */
    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;

    /** 현재 활성 기준인지 여부 (Y/N) */
    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    private String isActive;

    @Column(name = "REMARKS", length = 200)
    private String remarks;

    @Builder
    private InspectionStandard(String standardVersion, BigDecimal refL, BigDecimal refA, BigDecimal refB,
                                BigDecimal normalDe00Max, BigDecimal normalDlMax, BigDecimal normalDaMax, BigDecimal normalDbMax,
                                BigDecimal defectDe00Min, BigDecimal defectDlMax, BigDecimal defectDaMax, BigDecimal defectDbMax,
                                LocalDate effectiveFrom, String remarks, Long createdBy) {
        validateLab(refL, refA, refB);
        this.standardVersion = (standardVersion == null || standardVersion.isBlank()) ? "v1.0" : standardVersion;
        this.refL = refL;
        this.refA = refA;
        this.refB = refB;
        this.normalDe00Max = normalDe00Max;
        this.normalDlMax = normalDlMax;
        this.normalDaMax = normalDaMax;
        this.normalDbMax = normalDbMax;
        this.defectDe00Min = defectDe00Min;
        this.defectDlMax = defectDlMax;
        this.defectDaMax = defectDaMax;
        this.defectDbMax = defectDbMax;
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : LocalDate.now();
        this.isActive = "Y";
        this.remarks = remarks;
        markCreatedBy(createdBy);
    }

    private static void validateLab(BigDecimal l, BigDecimal a, BigDecimal b) {
        if (l == null || a == null || b == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "기준 L* / a* / b* 값은 필수입니다.");
        }
    }

    /**
     * 기준값 갱신 (새 버전으로 값만 변경).
     * 변경 전/후 값 비교는 호출부(Service)에서 {@code StandardAuditLog} 로 별도 기록한다.
     */
    public void update(BigDecimal refL, BigDecimal refA, BigDecimal refB,
                        BigDecimal normalDe00Max, BigDecimal normalDlMax, BigDecimal normalDaMax, BigDecimal normalDbMax,
                        BigDecimal defectDe00Min, BigDecimal defectDlMax, BigDecimal defectDaMax, BigDecimal defectDbMax,
                        String remarks, Long updatedBy) {
        validateLab(refL, refA, refB);
        this.refL = refL;
        this.refA = refA;
        this.refB = refB;
        this.normalDe00Max = normalDe00Max;
        this.normalDlMax = normalDlMax;
        this.normalDaMax = normalDaMax;
        this.normalDbMax = normalDbMax;
        this.defectDe00Min = defectDe00Min;
        this.defectDlMax = defectDlMax;
        this.defectDaMax = defectDaMax;
        this.defectDbMax = defectDbMax;
        this.remarks = remarks;
        markUpdatedBy(updatedBy);
    }

    /** 이 기준을 비활성화하고(=더 이상 현재 기준 아님) 종료일을 오늘로 기록한다 */
    public void deactivate(Long updatedBy) {
        this.isActive = "N";
        this.effectiveTo = LocalDate.now();
        markUpdatedBy(updatedBy);
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }
}
