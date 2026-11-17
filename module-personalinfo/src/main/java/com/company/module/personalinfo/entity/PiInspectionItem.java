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
 * 위수탁 점검 체크리스트 항목별 결과 (업로드 엑셀의 평가 항목 1행 = 1건).
 */
@Entity
@Table(name = "pi_inspection_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiInspectionItem extends PiBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ITEM_ID")
    private Long itemId;

    @Column(name = "INSPECTION_ID", nullable = false)
    private Long inspectionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "SHEET_TYPE", nullable = false, length = 20)
    private ChecklistSheetType sheetType;

    @Column(name = "ROW_NO", nullable = false)
    private Integer rowNo;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    @Column(name = "CATEGORY", length = 100)
    private String category;

    @Column(name = "IMPORTANT_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String importantYn;

    @Column(name = "QUESTION", nullable = false, length = 1000)
    private String question;

    @Column(name = "CHECK_METHOD", length = 2000)
    private String checkMethod;

    @Column(name = "CURRENT_STATUS", length = 4000)
    private String currentStatus;

    @Column(name = "EVALUATION", length = 10)
    private String evaluation;

    @Column(name = "EVALUATION_RAW", length = 100)
    private String evaluationRaw;

    @Column(name = "ACTION_PLAN", length = 2000)
    private String actionPlan;

    @Column(name = "ACTION_SCHEDULE", length = 200)
    private String actionSchedule;

    @Column(name = "CONFIRM_DATE")
    private LocalDate confirmDate;

    @Column(name = "CONFIRMER_NAME", length = 100)
    private String confirmerName;

    @Column(name = "ITEM_REMARK", length = 1000)
    private String itemRemark;

    @Builder
    private PiInspectionItem(Long inspectionId, ChecklistSheetType sheetType, Integer rowNo, Integer sortOrder,
                             String category, String importantYn, String question, String checkMethod,
                             String currentStatus, String evaluation, String evaluationRaw, String actionPlan,
                             String actionSchedule, LocalDate confirmDate, String confirmerName, String itemRemark,
                             Long createdBy) {
        this.inspectionId = inspectionId;
        this.sheetType = sheetType;
        this.rowNo = rowNo;
        this.sortOrder = sortOrder;
        this.category = category;
        this.importantYn = "Y".equals(importantYn) ? "Y" : "N";
        this.question = question;
        this.checkMethod = checkMethod;
        this.currentStatus = currentStatus;
        this.evaluation = evaluation;
        this.evaluationRaw = evaluationRaw;
        this.actionPlan = actionPlan;
        this.actionSchedule = actionSchedule;
        this.confirmDate = confirmDate;
        this.confirmerName = confirmerName;
        this.itemRemark = itemRemark;
        markCreatedBy(createdBy);
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }
}
