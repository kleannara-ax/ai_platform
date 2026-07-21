package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 셀 편집 권한 엔티티
 * - 사용자별로 편집 가능한 셀(표+행+열 범위)을 지정
 * - 입력 주기(DAILY/WEEKLY/MONTHLY)에 따라 잠금/활성화 제어
 */
@Entity
@Table(name = "daily_report_cell_permission", uniqueConstraints = {
        @UniqueConstraint(name = "UK_CELL_PERM_USER_RANGE",
                columnNames = {"USER_ID", "TABLE_CODE", "ROW_START", "ROW_END", "COL_START", "COL_END"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PERMISSION_ID")
    private Long permissionId;

    /** 권한 대상 사용자 ID (core_user 참조) */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 대상 표 코드 (예: TBL_PRODUCTION) */
    @Column(name = "TABLE_CODE", nullable = false, length = 50)
    private String tableCode;

    /** 편집 가능 행 범위 시작 (0-based, inclusive) */
    @Column(name = "ROW_START", nullable = false)
    private Integer rowStart;

    /** 편집 가능 행 범위 끝 (0-based, inclusive) */
    @Column(name = "ROW_END", nullable = false)
    private Integer rowEnd;

    /** 편집 가능 열 범위 시작 (0-based, inclusive) */
    @Column(name = "COL_START", nullable = false)
    private Integer colStart;

    /** 편집 가능 열 범위 끝 (0-based, inclusive) */
    @Column(name = "COL_END", nullable = false)
    private Integer colEnd;

    /** 입력 주기: DAILY / WEEKLY / MONTHLY */
    @Column(name = "INPUT_CYCLE", nullable = false, length = 20)
    private String inputCycle;

    /** 활성 여부 */
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public CellPermission(Long userId, String tableCode, Integer rowStart, Integer rowEnd,
                           Integer colStart, Integer colEnd, String inputCycle, Boolean isActive) {
        this.userId = userId;
        this.tableCode = tableCode;
        this.rowStart = rowStart;
        this.rowEnd = rowEnd;
        this.colStart = colStart;
        this.colEnd = colEnd;
        this.inputCycle = inputCycle;
        this.isActive = isActive != null ? isActive : true;
    }

    /** 권한 범위 수정 */
    public void updateRange(Integer rowStart, Integer rowEnd, Integer colStart, Integer colEnd) {
        this.rowStart = rowStart;
        this.rowEnd = rowEnd;
        this.colStart = colStart;
        this.colEnd = colEnd;
    }

    /** 입력 주기 변경 */
    public void updateInputCycle(String inputCycle) {
        this.inputCycle = inputCycle;
    }

    /** 활성/비활성 전환 */
    public void updateActive(boolean active) {
        this.isActive = active;
    }

    /** 특정 셀이 이 권한 범위에 포함되는지 확인 */
    public boolean coversCell(int rowIndex, int colIndex) {
        return rowIndex >= this.rowStart && rowIndex <= this.rowEnd
                && colIndex >= this.colStart && colIndex <= this.colEnd;
    }
}
