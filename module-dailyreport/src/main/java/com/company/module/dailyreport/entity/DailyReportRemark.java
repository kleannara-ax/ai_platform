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
 *
 * ★★ 2026-08 개편: 일보 생성 시 5개 사업부 행을 셀과 동일하게 미리 만들어두고,
 * 직전 일보의 값을 이어받는다({@code DailyReportService#ensureDefaultRemarks}).
 * 저장 시에는 이미 만들어져 있는 미래 일보 중 아직 사람이 손대지 않은 동일
 * 사업부 행에도 값을 전파한다({@code DailyReportService#propagateRemarkForward}).
 * CREATED_BY가 null이면 "이어받기 상태, 아직 사람이 직접 입력한 적 없음"을 뜻한다.
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

    /**
     * 최초 작성자 ID (core_user 참조)
     *
     * ★★ 2026-08 값 전파(forward propagation) 도입: null이면 "아직 아무도 직접
     * 입력하지 않은, 전일 값을 이어받기만 한 상태"를 의미한다 (셀의 LAST_EDITOR_ID
     * null과 동일한 개념). 사람이 실제로 저장하는 순간(최초 1회) 이 값이 채워지며,
     * 그 이후로는 이 행이 값 전파의 대상에서 제외되어(=의도적 입력으로 보존) 절대
     * 자동으로 덮어써지지 않는다. (과거 데이터는 항상 값이 채워져 있어 그대로 보존됨)
     */
    @Column(name = "CREATED_BY")
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

    /**
     * 내용 수정 (사람이 직접 저장) — updatedBy를 함께 기록하여 "누가 언제
     * 저장했는지" 추적한다.
     *
     * ★★ 2026-08: 이 행이 지금까지 "이어받기 상태"(createdBy == null)였다면,
     * 이번이 최초로 사람이 직접 저장하는 시점이므로 작성자로도 기록한다 —
     * 이후로는 값 전파(propagateRemarkForward) 대상에서 제외되어 보존된다.
     */
    public void updateContent(String content, String category, Long updatedBy) {
        if (this.createdBy == null) {
            this.createdBy = updatedBy;
        }
        this.content = content;
        this.category = category;
        this.updatedBy = updatedBy;
    }

    /**
     * ★★ 값 이어받기/전파(carry-over, 2026-08 추가) 전용 — 시스템이 자동으로
     * 이어받기 값을 채우거나 최신화할 때 사용한다. CREATED_BY/UPDATED_BY(사람이
     * 직접 손댄 기록)는 절대 건드리지 않는다 — 여전히 "이어받은 값일 뿐 아직
     * 아무도 직접 입력하지 않았다"는 상태가 그대로 유지되어, 계속 값 전파의
     * 대상이 될 수 있다.
     */
    public void carryOverContent(String content) {
        this.content = content;
    }
}
