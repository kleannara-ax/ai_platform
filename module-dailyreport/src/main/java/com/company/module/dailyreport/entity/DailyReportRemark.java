package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일보 특이사항 메모 엔티티
 * - 각 표별 또는 전체 일보에 대한 특이사항 기록
 * - 카테고리별 분류 가능
 */
@Entity
@Table(name = "daily_report_remark")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReportRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REMARK_ID")
    private Long remarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_REMARK_REPORT"))
    private DailyReport dailyReport;

    /** 관련 표 코드 (null이면 전체 일보 공통, 값이 있으면 해당 표의 특이사항) */
    @Column(name = "TABLE_CODE", length = 50)
    private String tableCode;

    /** 카테고리: GENERAL / SAFETY / QUALITY / MAINTENANCE / ETC */
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private String category;

    /** 특이사항 내용 */
    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 정렬 순서 */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    /** 작성자 ID (core_user 참조) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public DailyReportRemark(String tableCode, String category, String content,
                             Integer sortOrder, Long createdBy) {
        this.tableCode = tableCode;
        this.category = category;
        this.content = content;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
    }

    /** 부모 일보 연결 (양방향 매핑용) */
    void assignReport(DailyReport dailyReport) {
        this.dailyReport = dailyReport;
    }

    /** 내용 수정 */
    public void updateContent(String content, String category) {
        this.content = content;
        this.category = category;
    }
}
