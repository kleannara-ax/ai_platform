package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 세부공장일보 마스터 엔티티
 * - 날짜별 1건의 일보를 관리
 * - 4개 표 + 특이사항 + 이미지 첨부를 하위로 보유
 * - 표: 1.주요 생산 지표 현황, 2.제지 재공품 및 야적현황,
 *       3.에너지 원단위, 4.보일러 운영 현황
 */
@Entity
@Table(name = "daily_report", uniqueConstraints = {
        @UniqueConstraint(name = "UK_DAILY_REPORT_DATE", columnNames = {"REPORT_DATE"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REPORT_ID")
    private Long reportId;

    /** 일보 작성 대상 날짜 (유니크) */
    @Column(name = "REPORT_DATE", nullable = false)
    private LocalDate reportDate;

    /** 일보 제목 (예: "2024-07-20 세부공장일보") */
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** 일보 상태: DRAFT / SUBMITTED / CONFIRMED */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    /** 최종 작성자 ID (core_user 참조) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** 최종 수정자 ID */
    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "dailyReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DailyReportTable> tables = new ArrayList<>();

    @OneToMany(mappedBy = "dailyReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DailyReportRemark> remarks = new ArrayList<>();

    @OneToMany(mappedBy = "dailyReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DailyReportImage> images = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "DRAFT";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public DailyReport(LocalDate reportDate, String title, String status, Long createdBy) {
        this.reportDate = reportDate;
        this.title = title;
        this.status = status != null ? status : "DRAFT";
        this.createdBy = createdBy;
    }

    /** 일보 상태 변경 */
    public void updateStatus(String status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }

    /** 일보 제목 변경 */
    public void updateTitle(String title, Long updatedBy) {
        this.title = title;
        this.updatedBy = updatedBy;
    }

    /** 테이블 추가 */
    public void addTable(DailyReportTable table) {
        this.tables.add(table);
        table.assignReport(this);
    }

    /** 특이사항 추가 */
    public void addRemark(DailyReportRemark remark) {
        this.remarks.add(remark);
        remark.assignReport(this);
    }

    /** 이미지 추가 */
    public void addImage(DailyReportImage image) {
        this.images.add(image);
        image.assignReport(this);
    }
}
