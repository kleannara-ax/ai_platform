package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualStepPhoto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StepPhotoResponse {

    private final Long photoId;
    private final String originalName;
    private final String contentType;
    private final long fileSize;
    private final int sortOrder;
    /** 프론트에서 <img src>에 바로 쓸 조회 URL */
    private final String url;

    public static StepPhotoResponse from(SafetyManualStepPhoto entity) {
        return StepPhotoResponse.builder()
                .photoId(entity.getPhotoId())
                .originalName(entity.getOriginalName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .sortOrder(entity.getSortOrder())
                .url("/safety-api/photos/" + entity.getPhotoId() + "/view")
                .build();
    }
}
