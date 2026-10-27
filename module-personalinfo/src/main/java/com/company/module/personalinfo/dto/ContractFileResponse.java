package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiContractFile;
import lombok.Builder;
import lombok.Getter;

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
    private Long createdBy;
    private LocalDateTime createdAt;

    public static ContractFileResponse from(PiContractFile f) {
        return ContractFileResponse.builder()
                .fileId(f.getFileId())
                .contractId(f.getContractId())
                .fileType(f.getFileType().name())
                .fileTypeLabel(f.getFileType().getLabel())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .description(f.getDescription())
                .createdBy(f.getCreatedBy())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
