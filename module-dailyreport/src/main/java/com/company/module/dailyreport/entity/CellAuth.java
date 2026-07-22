package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 셀 단위 접근 권한 엔티티 (★ Phase 4 신규)
 * - 관리자가 '세부공장일보 컬럼관리' 페이지에서 설정
 * - 사용자별 담당 표의 셀 좌표(JSON 배열)와 입력 주기를 관리
 * - 레거시 daily_report_cell_permission 을 대체
 *
 * @see <a href="daily_report_cell_auth 테이블">01_schema.sql</a>
 */
@Entity
@Table(name = "daily_report_cell_auth", uniqueConstraints = {
        @UniqueConstraint(name = "UK_CELL_AUTH_USER_TABLE",
                columnNames = {"USER_ID", "TABLE_CODE"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUTH_ID")
    private Long authId;

    /** 담당 사용자 ID (core_user FK) */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 대상 표 코드 (TBL_PRODUCTION_INDEX, TBL_INVENTORY, TBL_ENERGY, TBL_BOILER) */
    @Column(name = "TABLE_CODE", nullable = false, length = 50)
    private String tableCode;

    /** 담당 셀 좌표 목록 (JSON 배열: ["B7","C7","D7"]) */
    @Column(name = "CELL_COORDS", nullable = false, columnDefinition = "TEXT")
    private String cellCoords;

    /** 입력 주기: daily / monthly / yearly / event */
    @Column(name = "FREQ_CODE", nullable = false, length = 20)
    private String freqCode;

    /** 주기 한글 라벨: 매일 / 매월 / 매년 / 발생 시 */
    @Column(name = "FREQ_LABEL", length = 50)
    private String freqLabel;

    /** 활성 여부 */
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    /** 권한 부여자 (관리자 USER_ID) */
    @Column(name = "GRANTED_BY")
    private Long grantedBy;

    /** 설명 (예: 수율 담당, 에너지 담당) */
    @Column(name = "DESCRIPTION", length = 300)
    private String description;

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
        if (this.freqCode == null) {
            this.freqCode = "daily";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public CellAuth(Long userId, String tableCode, String cellCoords,
                    String freqCode, String freqLabel, Boolean isActive,
                    Long grantedBy, String description) {
        this.userId = userId;
        this.tableCode = tableCode;
        this.cellCoords = cellCoords;
        this.freqCode = freqCode != null ? freqCode : "daily";
        this.freqLabel = freqLabel;
        this.isActive = isActive != null ? isActive : true;
        this.grantedBy = grantedBy;
        this.description = description;
    }

    // ─────────────────────────────────────────────
    // 비즈니스 메서드
    // ─────────────────────────────────────────────

    /** JSON 배열 문자열을 좌표 리스트로 파싱 */
    public List<String> getCellCoordList() {
        if (cellCoords == null || cellCoords.isBlank()) {
            return Collections.emptyList();
        }
        // ["B7","C7","D7"] → List<"B7","C7","D7">
        String cleaned = cellCoords.replaceAll("[\\[\\]\"\\s]", "");
        if (cleaned.isEmpty()) return Collections.emptyList();
        return Arrays.asList(cleaned.split(","));
    }

    /** 특정 엑셀 좌표가 이 권한에 포함되는지 확인 */
    public boolean coversCoord(String excelCoord) {
        if (excelCoord == null) return false;
        return getCellCoordList().stream()
                .anyMatch(coord -> coord.equalsIgnoreCase(excelCoord));
    }

    /** 셀 좌표 목록 업데이트 */
    public void updateCellCoords(String cellCoords) {
        this.cellCoords = cellCoords;
    }

    /** 주기 업데이트 */
    public void updateFrequency(String freqCode, String freqLabel) {
        this.freqCode = freqCode;
        this.freqLabel = freqLabel;
    }

    /** 활성/비활성 전환 */
    public void updateActive(boolean active) {
        this.isActive = active;
    }

    /** 설명 업데이트 */
    public void updateDescription(String description) {
        this.description = description;
    }

    /** 전체 업데이트 (관리자 편집) */
    public void updateAll(String cellCoords, String freqCode, String freqLabel,
                          String description, Long grantedBy) {
        this.cellCoords = cellCoords;
        this.freqCode = freqCode;
        this.freqLabel = freqLabel;
        this.description = description;
        this.grantedBy = grantedBy;
    }
}
