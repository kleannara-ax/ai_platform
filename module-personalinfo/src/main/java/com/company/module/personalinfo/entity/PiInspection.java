package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 위수탁 점검 이력 (협력사가 작성한 점검 체크리스트 엑셀 1건 업로드 = 1건).
 * 업로드한 엑셀 원본 파일 메타데이터와 평가 집계 결과를 함께 보관한다.
 */
@Entity
@Table(name = "pi_inspection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiInspection extends PiBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INSPECTION_ID")
    private Long inspectionId;

    @Column(name = "CONTRACT_ID", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "INSPECTION_TYPE", nullable = false, length = 20)
    private InspectionType inspectionType;

    @Column(name = "INSPECTION_YEAR", nullable = false)
    private Integer inspectionYear;

    @Column(name = "INSPECTION_DATE", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "INSPECTOR_NAME", length = 100)
    private String inspectorName;

    @Column(name = "SHEET_COMPANY_NAME", length = 200)
    private String sheetCompanyName;

    @Column(name = "SHEET_TASK_NAME", length = 500)
    private String sheetTaskName;

    @Column(name = "SYSTEM_OWNED_YN", length = 1, columnDefinition = "CHAR(1)")
    private String systemOwnedYn;

    @Column(name = "SYSTEM_NAME", length = 200)
    private String systemName;

    @Column(name = "TOTAL_COUNT", nullable = false)
    private Integer totalCount;

    @Column(name = "Y_COUNT", nullable = false)
    private Integer yCount;

    @Column(name = "N_COUNT", nullable = false)
    private Integer nCount;

    @Column(name = "NA_COUNT", nullable = false)
    private Integer naCount;

    @Column(name = "BLANK_COUNT", nullable = false)
    private Integer blankCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "RESULT", nullable = false, length = 20)
    private InspectionResult result;

    @Column(name = "ACTION_DUE_DATE")
    private LocalDate actionDueDate;

    @Column(name = "ACTION_COMPLETED_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String actionCompletedYn;

    @Column(name = "ACTION_COMPLETED_DATE")
    private LocalDate actionCompletedDate;

    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    @Column(name = "STORED_PATH", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "REMARK", length = 2000)
    private String remark;

    @Builder
    private PiInspection(Long contractId, InspectionType inspectionType, LocalDate inspectionDate, String inspectorName,
                         String sheetCompanyName, String sheetTaskName, String systemOwnedYn, String systemName,
                         int totalCount, int yCount, int nCount, int naCount, int blankCount, InspectionResult result,
                         LocalDate actionDueDate, String originalName, String storedPath, String contentType,
                         Long fileSize, String fileHash, String remark, Long createdBy) {
        this.contractId = contractId;
        this.inspectionType = inspectionType == null ? InspectionType.REGULAR : inspectionType;
        this.inspectionDate = inspectionDate == null ? LocalDate.now() : inspectionDate;
        this.inspectionYear = this.inspectionDate.getYear();
        this.inspectorName = inspectorName;
        this.sheetCompanyName = sheetCompanyName;
        this.sheetTaskName = sheetTaskName;
        this.systemOwnedYn = systemOwnedYn;
        this.systemName = systemName;
        this.totalCount = totalCount;
        this.yCount = yCount;
        this.nCount = nCount;
        this.naCount = naCount;
        this.blankCount = blankCount;
        this.result = result;
        this.actionDueDate = actionDueDate;
        this.actionCompletedYn = result == InspectionResult.NEED_ACTION ? "N" : "Y";
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.remark = remark;
        markCreatedBy(createdBy);
    }

    /** 점검 후속 조치 정보 수정 */
    public void updateFollowUp(LocalDate inspectionDate, String inspectorName, LocalDate actionDueDate,
                               String actionCompletedYn, LocalDate actionCompletedDate, String remark, Long updatedBy) {
        if (inspectionDate != null) {
            this.inspectionDate = inspectionDate;
            this.inspectionYear = inspectionDate.getYear();
        }
        this.inspectorName = inspectorName;
        this.actionDueDate = actionDueDate;
        this.actionCompletedYn = "Y".equalsIgnoreCase(actionCompletedYn) ? "Y" : "N";
        this.actionCompletedDate = "Y".equals(this.actionCompletedYn)
                ? (actionCompletedDate == null ? LocalDate.now() : actionCompletedDate)
                : null;
        this.remark = remark;
        markUpdatedBy(updatedBy);
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }

    public void restore(Long updatedBy) {
        restoreDeleted(updatedBy);
    }
}
