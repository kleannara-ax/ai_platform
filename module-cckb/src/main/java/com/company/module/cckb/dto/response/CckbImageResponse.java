package com.company.module.cckb.dto.response;

import com.company.module.cckb.entity.CckbImage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CckbImageResponse {
    private Long imageId;
    private String inspectionId;
    private String imageType;
    private String fileName;
    /** 다운로드 API 경로 — 프론트엔드는 이 경로를 인증된 fetch()+blob 으로 조회해야 한다 */
    private String fileUrl;
    private Integer widthPx;
    private Integer heightPx;
    private Integer fileSizeKb;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;

    public static CckbImageResponse from(CckbImage e) {
        return CckbImageResponse.builder()
                .imageId(e.getImageId())
                .inspectionId(e.getInspectionRecord() != null ? e.getInspectionRecord().getInspectionId() : null)
                .imageType(e.getImageType())
                .fileName(e.getFileName())
                .fileUrl(e.getFileUrl())
                .widthPx(e.getWidthPx())
                .heightPx(e.getHeightPx())
                .fileSizeKb(e.getFileSizeKb())
                .uploadedBy(e.getUploadedBy())
                .uploadedAt(e.getUploadedAt())
                .build();
    }
}
