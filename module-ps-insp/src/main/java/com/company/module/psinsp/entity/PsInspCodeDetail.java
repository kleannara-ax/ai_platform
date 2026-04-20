package com.company.module.psinsp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 공통코드 상세 테이블 (code_detail) 접근용 경량 Entity
 *
 * <p>module-ps-insp는 module-common에 직접 의존하지 않으므로,
 * code_detail 테이블을 읽고 쓰기 위한 독립적인 Entity를 정의합니다.
 *
 * <p>PS_INSP_DEFAULT 그룹 하위 코드:
 * <ul>
 *   <li>PPM_LIMIT    - extraValue1: PPM 기준값 (0 = 비활성)</li>
 * </ul>
 * <p>PS_INSP_ADMIN 그룹 하위 코드:
 * <ul>
 *   <li>PPM_ADMIN    - extraValue1: 수정 권한자 ID 목록 (콤마 구분)</li>
 * </ul>
 */
@Entity
@Table(name = "code_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PsInspCodeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CODE_ID")
    private Long codeId;

    @Column(name = "GROUP_ID", nullable = false)
    private Long groupId;

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Column(name = "CODE_NAME", nullable = false, length = 100)
    private String codeName;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "EXTRA_VALUE1", length = 200)
    private String extraValue1;

    @Column(name = "EXTRA_VALUE2", length = 200)
    private String extraValue2;

    @Column(name = "IS_ACTIVE")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "SORT_ORDER")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * extraValue1 업데이트
     */
    public void updateExtraValue1(String value) {
        this.extraValue1 = value;
    }
}
