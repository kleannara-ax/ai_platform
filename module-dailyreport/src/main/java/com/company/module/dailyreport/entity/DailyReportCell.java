package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일보 표 셀 데이터 엔티티
 * - 표 내 각 셀의 좌표(행/열)와 값을 저장
 * - 셀 유형: HEADER(헤더), DATA(입력값), FORMULA(계산값), READONLY(읽기전용)
 * - 엑셀 좌표(EXCEL_COORD): 원본 엑셀 기준 좌표 (예: "B5", "O10")
 * - 셀 소유자(OWNER_IDS): 공백 구분 사용자 로그인ID (예: "kim", "jang lee")
 * - 입력 주기(FREQ_CODE): daily/monthly/yearly/event
 */
@Entity
@Table(name = "daily_report_cell", uniqueConstraints = {
        @UniqueConstraint(name = "UK_CELL_POSITION",
                columnNames = {"TABLE_ID", "ROW_INDEX", "COL_INDEX"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReportCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CELL_ID")
    private Long cellId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TABLE_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_CELL_TABLE"))
    private DailyReportTable reportTable;

    /** 행 인덱스 (0-based, 표 내부 상대 인덱스) */
    @Column(name = "ROW_INDEX", nullable = false)
    private Integer rowIndex;

    /** 열 인덱스 (0-based, 표 내부 상대 인덱스) */
    @Column(name = "COL_INDEX", nullable = false)
    private Integer colIndex;

    /** 엑셀 원본 좌표 (예: "B5", "O10") — HTML data-coord 매핑 */
    @Column(name = "EXCEL_COORD", length = 10)
    private String excelCoord;

    /** 셀 값 (문자열로 저장, 숫자도 문자열 변환) */
    @Column(name = "CELL_VALUE", length = 2000)
    private String cellValue;

    /** 셀 유형: HEADER / DATA / FORMULA / READONLY */
    @Column(name = "CELL_TYPE", nullable = false, length = 20)
    private String cellType;

    /** 셀 라벨 (헤더인 경우 표시할 텍스트) */
    @Column(name = "CELL_LABEL", length = 200)
    private String cellLabel;

    /** 데이터 형식: TEXT / NUMBER / PERCENT / DATE */
    @Column(name = "DATA_FORMAT", length = 20)
    private String dataFormat;

    /** 수식 (FORMULA 유형일 때 계산식) */
    @Column(name = "FORMULA", length = 500)
    private String formula;

    /** 입력 주기(레거시): DAILY / WEEKLY / MONTHLY / NONE */
    @Column(name = "INPUT_CYCLE", nullable = false, length = 20)
    private String inputCycle;

    /** 입력 주기(HTML 기준): daily / monthly / yearly / event / none */
    @Column(name = "FREQ_CODE", length = 20)
    private String freqCode;

    /** 주기 한글 라벨: 매일 / 매월 / 매년 / 발생 시 */
    @Column(name = "FREQ_LABEL", length = 50)
    private String freqLabel;

    /** 셀 소유자 로그인ID 목록 (공백 구분, 예: "kim", "jang lee") */
    @Column(name = "OWNER_IDS", length = 200)
    private String ownerIds;

    /** 셀 소유자 이름 목록 (쉼표 구분, 예: "김완중 팀장", "장석환 선임, 이도형 사원") */
    @Column(name = "OWNER_NAMES", length = 500)
    private String ownerNames;

    /** 셀 잠금 여부 (주기에 따라 자동 제어) */
    @Column(name = "IS_LOCKED", nullable = false)
    private Boolean isLocked;

    /** rowspan (병합 행 수, 기본 1) */
    @Column(name = "ROW_SPAN")
    private Integer rowSpan;

    /** colspan (병합 열 수, 기본 1) */
    @Column(name = "COL_SPAN")
    private Integer colSpan;

    /** 최종 입력자 ID (core_user 참조) */
    @Column(name = "LAST_EDITOR_ID")
    private Long lastEditorId;

    /** 최종 입력 시각 */
    @Column(name = "LAST_EDITED_AT")
    private LocalDateTime lastEditedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isLocked == null) {
            this.isLocked = false;
        }
        if (this.inputCycle == null) {
            this.inputCycle = "NONE";
        }
        if (this.rowSpan == null) {
            this.rowSpan = 1;
        }
        if (this.colSpan == null) {
            this.colSpan = 1;
        }
    }

    @Builder
    public DailyReportCell(Integer rowIndex, Integer colIndex, String excelCoord,
                           String cellValue, String cellType, String cellLabel,
                           String dataFormat, String formula, String inputCycle,
                           String freqCode, String freqLabel,
                           String ownerIds, String ownerNames,
                           Boolean isLocked, Integer rowSpan, Integer colSpan) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.excelCoord = excelCoord;
        this.cellValue = cellValue;
        this.cellType = cellType;
        this.cellLabel = cellLabel;
        this.dataFormat = dataFormat;
        this.formula = formula;
        this.inputCycle = inputCycle != null ? inputCycle : "NONE";
        this.freqCode = freqCode;
        this.freqLabel = freqLabel;
        this.ownerIds = ownerIds;
        this.ownerNames = ownerNames;
        this.isLocked = isLocked != null ? isLocked : false;
        this.rowSpan = rowSpan != null ? rowSpan : 1;
        this.colSpan = colSpan != null ? colSpan : 1;
    }

    /** 부모 테이블 연결 (양방향 매핑용) */
    void assignTable(DailyReportTable reportTable) {
        this.reportTable = reportTable;
    }

    /** 셀 값 업데이트 (setter 대신 비즈니스 메서드) */
    public void updateValue(String cellValue, Long editorId) {
        this.cellValue = cellValue;
        this.lastEditorId = editorId;
        this.lastEditedAt = LocalDateTime.now();
    }

    /**
     * ★ 이전 일보 값 이어받기 전용 — 새로 생성된 표의 DATA 셀에 직전 일보의
     *   값을 초기값으로 채워 넣을 때 사용한다.
     *
     * updateValue()와 달리 LAST_EDITOR_ID/LAST_EDITED_AT은 절대 변경하지
     * 않는다 — 오늘 표에서는 아직 아무도 실제로 입력한 적이 없으므로
     * "누가 언제 입력했는지" 기록은 비워둔 상태를 그대로 유지해야 한다
     * (해당 정보는 DB 컬럼에는 존재하지만 화면 툴팁 등 표시용일 뿐이며,
     * 이 메서드는 값만 이어받고 그 기록은 건드리지 않는다).
     */
    public void carryOverValue(String cellValue) {
        this.cellValue = cellValue;
    }

    /** 셀 잠금/해제 */
    public void updateLock(boolean locked) {
        this.isLocked = locked;
    }

    /** 셀 메타 정보 업데이트 */
    public void updateMeta(String cellLabel, String dataFormat, String formula, String inputCycle) {
        this.cellLabel = cellLabel;
        this.dataFormat = dataFormat;
        this.formula = formula;
        this.inputCycle = inputCycle;
    }

    /** 소유자 정보 업데이트 (freqCode/freqLabel도 함께 변경) */
    public void updateOwnership(String ownerIds, String ownerNames, String freqCode, String freqLabel) {
        this.ownerIds = ownerIds;
        this.ownerNames = ownerNames;
        this.freqCode = freqCode;
        this.freqLabel = freqLabel;
    }

    /**
     * ★ CellAuth 동기화 전용 — 담당자 캐시(OWNER_IDS/OWNER_NAMES)만 갱신한다.
     *
     * daily_report_cell_auth(관리자 설정)가 유일한 담당자 출처이며,
     * 이 메서드는 {@code CellOwnershipSyncService}가 CellAuth 변경 시점에
     * 호출하여 OWNER_IDS/OWNER_NAMES를 다시 계산해 넣는 용도로만 사용한다.
     * FREQ_CODE/FREQ_LABEL은 셀 생성 시점(DefaultCellTemplate)에 정해진
     * 값을 그대로 유지해야 하므로 여기서는 건드리지 않는다 — CellAuth 한 건은
     * (user, tableCode)당 freqCode 하나만 가지므로, 이 값으로 셀의 FREQ_CODE를
     * 덮어쓰면 yearly/monthly/daily가 섞인 셀들의 편집 가능 주기가 잘못 뭉개진다.
     */
    public void syncOwnerCache(String ownerIds, String ownerNames) {
        this.ownerIds = ownerIds;
        this.ownerNames = ownerNames;
    }

    /** 특정 사용자가 이 셀의 소유자인지 확인 */
    public boolean isOwnedBy(String loginId) {
        if (this.ownerIds == null || loginId == null) return false;
        for (String id : this.ownerIds.split("\\s+")) {
            if (id.equalsIgnoreCase(loginId)) return true;
        }
        return false;
    }

    /** 입력 가능한 셀(assignable)인지 확인 */
    public boolean isAssignable() {
        return this.ownerIds != null && !this.ownerIds.isBlank();
    }
}
