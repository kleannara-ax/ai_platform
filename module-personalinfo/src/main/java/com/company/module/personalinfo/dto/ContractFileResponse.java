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
                .createdBy(f.getCreatedBy())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
