package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 일보 표 메타 엔티티
 * - 하나의 일보에 5개 표가 포함됨
 * - 각 표는 고유한 이름과 정렬 순서를 가짐
 */
@Entity
@Table(name = "daily_report_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReportTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TABLE_ID")
    private Long tableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_RPT_TABLE_REPORT"))
    private DailyReport dailyReport;

    /** 표 코드 (예: TBL_PRODUCTION, TBL_QUALITY 등) */
    @Column(name = "TABLE_CODE", nullable = false, length = 50)
    private String tableCode;

    /** 표 이름 (예: "생산 현황", "품질 관리") */
    @Column(name = "TABLE_NAME", nullable = false, length = 100)
    private String tableName;

    /** 표 정렬 순서 (1~5) */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    /** 표 총 행 수 */
    @Column(name = "ROW_COUNT", nullable = false)
    private Integer rowCount;

    /** 표 총 열 수 */
    @Column(name = "COL_COUNT", nullable = false)
    private Integer colCount;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "reportTable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DailyReportCell> cells = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public DailyReportTable(String tableCode, String tableName, Integer sortOrder,
                            Integer rowCount, Integer colCount) {
        this.tableCode = tableCode;
        this.tableName = tableName;
        this.sortOrder = sortOrder;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }

    /** 부모 일보 연결 (양방향 매핑용) */
    void assignReport(DailyReport dailyReport) {
        this.dailyReport = dailyReport;
    }

    /** 셀 추가 */
    public void addCell(DailyReportCell cell) {
        this.cells.add(cell);
        cell.assignTable(this);
    }

    /** 표 메타 수정 */
    public void updateMeta(String tableName, Integer rowCount, Integer colCount) {
        this.tableName = tableName;
        this.rowCount = rowCount;
        this.colCount = colCount;
    }
}
