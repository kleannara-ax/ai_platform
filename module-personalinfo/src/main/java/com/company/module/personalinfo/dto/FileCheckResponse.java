package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 첨부 업로드 전 파일 내용 확인 결과.
 * <ul>
 *   <li>errors 가 있으면 업로드할 수 없다 (서류 종류 불일치·중복 파일·날짜 불일치·업체명 불일치)</li>
 *   <li>needsManualConfirm 이면 글자를 읽을 수 없거나 서류 종류를 알아볼 수 없어 자동 확인을 못 한 것 —
 *       담당자가 '내용 직접 확인했습니다'에 체크해야(contentConfirmed=Y) 올라간다</li>
 * </ul>
 */
@Getter
@Builder
public class FileCheckResponse {

    /** 바로 업로드해도 됨 (오류 없음 + 자동 확인 통과) */
    private boolean ok;
    /** 업로드 불가 (errors 있음) */
    private boolean blocked;
    /** 자동 확인을 못 해 담당자 직접 확인이 필요함 */
    private boolean needsManualConfirm;
    /** 파일에서 글자를 읽었는지 */
    private boolean textReadable;
    private String fileName;
    /** 고른 파일 구분 */
    private String fileType;
    private String fileTypeLabel;
    /** 문서 내용으로 판단한 서류 종류 (모르면 null) */
    private String detectedType;
    private String detectedTypeLabel;
    /** 문서에서 찾은 날짜 (서류 일자 비교용, 계약기간 날짜 제외) */
    private List<LocalDate> documentDates;
    /** 문서에서 찾은 수탁업체명 (라벨 옆 값, 없으면 null) */
    private String foundVendorName;
    /** 업로드를 막는 문제 */
    private List<Issue> errors;
    /** 확인 결과 안내 (통과한 항목·참고) */
    private List<String> notes;
    /** 같은 파일 (중복일 때) */
    private DuplicateFile duplicate;

    @Getter
    @Builder
    public static class Issue {
        /** DUPLICATE / TYPE_MISMATCH / DATE_MISMATCH / VENDOR_MISMATCH / FILE */
        private String code;
        private String message;
    }

    @Getter
    @Builder
    public static class DuplicateFile {
        private Long fileId;
        private String fileTypeLabel;
        private String originalName;
        private String destructionPeriodLabel;
        private LocalDateTime createdAt;
    }
}
