package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogPiiDownload;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 개인정보 다운로드 검토 화면 데이터 (자사몰·임직원몰)
 * - target=false 이면 대상 시스템이 아님 (화면에 표시하지 않음)
 * - sheetStatus: FOUND(다운로드 내역 탭 있음) / NOT_FOUND(탭 없음) / null(대상 아님·15 적용 전 업로드)
 */
@Getter
@Builder
public class PiiDownloadResponse {

    private Long uploadId;
    private boolean target;
    /** 연동 룰 코드 (룰셋별 분석 결과에서 이 룰을 누르면 다운로드 이력 표시) */
    private String ruleCode;
    private String sheetStatus;
    private String sheetName;
    /** 최종 검토완료 상태 (검토 변경 불가) */
    private boolean locked;
    private long totalCount;
    private long pendingCount;
    private long normalCount;
    private long confirmedCount;
    private long resolvedCount;
    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private Long piiDownloadId;
        private Integer rowNo;
        private LocalDateTime downloadAt;
        private String downloadAtText;
        private String adminId;
        private String accessIp;
        private String accessPage;
        private String taskName;
        private String fileTarget;
        private String formName;
        private String downloadReason;
        private String reviewStatus;
        private String reviewStatusLabel;
        private String reviewComment;
        private LocalDateTime reviewedAt;
        private Long reviewedBy;

        public static Item from(SecLogPiiDownload p) {
            return Item.builder()
                    .piiDownloadId(p.getPiiDownloadId()).rowNo(p.getRowNo())
                    .downloadAt(p.getDownloadAt()).downloadAtText(p.getDownloadAtText())
                    .adminId(p.getAdminId()).accessIp(p.getAccessIp()).accessPage(p.getAccessPage())
                    .taskName(p.getTaskName()).fileTarget(p.getFileTarget()).formName(p.getFormName())
                    .downloadReason(p.getDownloadReason())
                    .reviewStatus(p.getReviewStatus().name()).reviewStatusLabel(p.getReviewStatus().getLabel())
                    .reviewComment(p.getReviewComment()).reviewedAt(p.getReviewedAt()).reviewedBy(p.getReviewedBy())
                    .build();
        }
    }
}
