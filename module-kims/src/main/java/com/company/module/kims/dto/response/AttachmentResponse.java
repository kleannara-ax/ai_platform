package com.company.module.kims.dto.response;

import com.company.module.kims.entity.RequestAttachment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 첨부파일 응답 DTO.
 */
@Getter
@Builder
public class AttachmentResponse {

    private final Long attachmentId;
    private final Long requestId;
    private final String originalName;
    private final String contentType;
    private final long fileSize;
    private final String uploadedBy;
    private final LocalDateTime createdAt;

    public static AttachmentResponse from(RequestAttachment e) {
        return AttachmentResponse.builder()
                .attachmentId(e.getAttachmentId())
                .requestId(e.getServiceRequest().getRequestId())
                .originalName(e.getOriginalName())
                .contentType(e.getContentType())
                .fileSize(e.getFileSize())
                .uploadedBy(e.getUploadedBy())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
