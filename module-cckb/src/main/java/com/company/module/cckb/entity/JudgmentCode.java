package com.company.module.cckb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판정 코드 공통코드 테이블. PK 는 자연키(VARCHAR) 로 NORMAL/BOUNDARY/DEFECT 3건 고정.
 * <p>원본 V4 HTML 의 {@code classifyResult()} 가 반환하던 label(정상/경계/불량) +
 * className(status-normal/status-boundary/status-defect) 을 DB 로 분리한 것.
 */
@Entity
@Table(name = "judgment_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgmentCode extends CckbBaseEntity {

    public static final String NORMAL = "NORMAL";
    public static final String BOUNDARY = "BOUNDARY";
    public static final String DEFECT = "DEFECT";

    @Id
    @Column(name = "JUDGMENT_CODE", length = 10)
    private String judgmentCode;

    @Column(name = "JUDGMENT_NAME_KR", nullable = false, length = 10)
    private String judgmentNameKr;

    @Column(name = "COLOR_HEX", length = 10)
    private String colorHex;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "DESCRIPTION", length = 150)
    private String description;

    @Builder
    private JudgmentCode(String judgmentCode, String judgmentNameKr, String colorHex,
                          Integer sortOrder, String description, Long createdBy) {
        this.judgmentCode = judgmentCode;
        this.judgmentNameKr = judgmentNameKr;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
        this.description = description;
        markCreatedBy(createdBy);
    }
}
