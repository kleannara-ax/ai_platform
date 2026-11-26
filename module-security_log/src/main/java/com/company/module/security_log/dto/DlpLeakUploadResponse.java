package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogDlpLeakUpload;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** DLP 유출 로그 업로드 1건 요약 */
@Getter
@Builder
public class DlpLeakUploadResponse {

    private Long leakUploadId;
    private String targetYm;
    private String originalFileName;
    private long fileSize;
    private String sheetName;
    private Integer headerRow;
    private int eventCount;
    private int matchedEventCount;
    private int userCount;
    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;
    private int dateUnknownCount;
    private int outOfMonthCount;
    private int appliedRuleCount;
    private String analysisNote;
    private LocalDateTime analyzedAt;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;

    public static DlpLeakUploadResponse from(SecLogDlpLeakUpload u) {
        return DlpLeakUploadResponse.builder()
                .leakUploadId(u.getLeakUploadId()).targetYm(u.getTargetYm())
                .originalFileName(u.getOriginalFileName()).fileSize(u.getFileSize() == null ? 0 : u.getFileSize())
                .sheetName(u.getSheetName()).headerRow(u.getHeaderRow())
                .eventCount(u.getEventCount()).matchedEventCount(u.getMatchedEventCount()).userCount(u.getUserCount())
                .periodFrom(u.getPeriodFrom()).periodTo(u.getPeriodTo())
                .dateUnknownCount(u.getDateUnknownCount()).outOfMonthCount(u.getOutOfMonthCount())
                .appliedRuleCount(u.getAppliedRuleCount()).analysisNote(u.getAnalysisNote())
                .analyzedAt(u.getAnalyzedAt()).remark(u.getRemark())
                .createdAt(u.getCreatedAt()).createdBy(u.getCreatedBy())
                .build();
    }
}
