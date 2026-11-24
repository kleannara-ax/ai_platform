package com.company.module.epr.dto;

import java.util.List;

/** 마스터·포장중량 엑셀 업로드 결과 */
public record UploadSummary(String type, int processedRows, int insertedRows, int updatedRows,
        int failedRows, List<UploadIssue> issues) {
}
