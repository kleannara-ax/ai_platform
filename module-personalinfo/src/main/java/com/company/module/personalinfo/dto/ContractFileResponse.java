package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiContractFile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ContractFileResponse {

    private Long fileId;
    private Long contractId;
    private String fileType;
    private String fileTypeLabel;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String description;
    /** 계약서상 체결일 (위수탁 계약서만) */
    private LocalDate signedDate;
    /** 체결일이 가장 최근인 위수탁 계약서인지 (계약의 체결일로 표시되는 파일) */
    private boolean latestContractDoc;
    /** 서류 일자 (확약서 서명일 / 파기 확인서 파기 완료일) */
    private LocalDate documentDate;
    /** 서류 일자 항목 이름 (서명일 / 파기 완료일, 해당 없으면 null) */
    private String documentDateLabel;
    /** 서류 일자 메모 (파기 확인서: 파기 지연 사유) */
    private String documentNote;
    /** 정기 파기 확인서 대상 기간 키 (예: 2026-Q3, 없으면 계약 종료 파기 확인서) */
    private String destructionPeriod;
    /** 정기 파기 확인서 대상 기간 이름 (예: 2026년 3분기) */
    private String destructionPeriodLabel;
    /** 업로드 내용 확인: AUTO 자동 확인 / MANUAL 담당자 직접 확인 / null 확인 기능 이전 파일 */
    private String contentCheck;
    private Long createdBy;
    private LocalDateTime createdAt;

    public static ContractFileResponse from(PiContractFile f) {
        return from(f, null);
    }

    public static ContractFileResponse from(PiContractFile f, Long latestFileId) {
        return ContractFileResponse.builder()
                .fileId(f.getFileId())
                .contractId(f.getContractId())
                .fileType(f.getFileType().name())
                .fileTypeLabel(f.getFileType().getLabel())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .description(f.getDescription())
                .signedDate(f.getSignedDate())
                .latestContractDoc(latestFileId != null && latestFileId.equals(f.getFileId()))
                .documentDate(f.getDocumentDate())
                .documentDateLabel(f.getFileType().getDocumentDateLabel())
                .documentNote(f.getDocumentNote())
                .destructionPeriod(f.getDestructionPeriod())
                .contentCheck(f.getContentCheck())
                .destructionPeriodLabel(com.company.module.personalinfo.service.PiPeriodicDestruction.labelOf(f.getDestructionPeriod()))
                .createdBy(f.getCreatedBy())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
