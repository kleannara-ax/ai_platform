package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiInspectionItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class InspectionItemResponse {

    private Long itemId;
    private String sheetType;
    private String sheetLabel;
    private Integer rowNo;
    private String category;
    private String importantYn;
    private String question;
    private String checkMethod;
    private String currentStatus;
    private String evaluation;
    private String evaluationRaw;
    private String actionPlan;
    private String actionSchedule;
    private LocalDate confirmDate;
    private String confirmerName;
    private String itemRemark;

    public static InspectionItemResponse from(PiInspectionItem it) {
        return InspectionItemResponse.builder()
                .itemId(it.getItemId())
                .sheetType(it.getSheetType().name())
                .sheetLabel(it.getSheetType().getLabel())
                .rowNo(it.getRowNo())
                .category(it.getCategory())
                .importantYn(it.getImportantYn())
                .question(it.getQuestion())
                .checkMethod(it.getCheckMethod())
                .currentStatus(it.getCurrentStatus())
                .evaluation(it.getEvaluation())
                .evaluationRaw(it.getEvaluationRaw())
                .actionPlan(it.getActionPlan())
                .actionSchedule(it.getActionSchedule())
                .confirmDate(it.getConfirmDate())
                .confirmerName(it.getConfirmerName())
                .itemRemark(it.getItemRemark())
                .build();
    }
}
