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
 *
 * ★★ 2026-07 개편: TABLE_CODE='TBL_SPECIAL_NOTE'(가상 표코드)로 리포트당
 * 사업부별 최대 5행(CATEGORY=PAPER/TISSUE/PAD/SAFETY/ETC)을 갖도록 재구성.
 * 셀 시스템과 동일하게 daily_report_cell_auth(TABLE_CODE='TBL_SPECIAL_NOTE',
 * CELL_COORDS=[사업부코드])로 담당자를 배정하며, "누가 언제 저장했는지"를
 * 화면에 표시하기 위해 UPDATED_BY(최종 수정자)를 추가한다.
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

    /** 최초 작성자 ID (core_user 참조) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** 최종 수정자 ID (core_user 참조) — "누가 언제 저장했는지" 추적용 */
    @Column(name = "UPDATED_BY")
    private Long updatedBy;

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

    /** 내용 수정 (★ updatedBy를 함께 기록하여 "누가 언제 저장했는지" 추적) */
    public void updateContent(String content, String category, Long updatedBy) {
        this.content = content;
        this.category = category;
        this.updatedBy = updatedBy;
    }
}
