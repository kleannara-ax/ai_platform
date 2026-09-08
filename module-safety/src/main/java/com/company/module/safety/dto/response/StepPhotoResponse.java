package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualStepPhoto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StepPhotoResponse {

    private final Long photoId;
    /** 이 사진이 표시될 열. null 이면 매뉴얼의 기본 '사진' 열에 나온다. */
    private final Long columnId;
    private final String originalName;
    private final String contentType;
    private final long fileSize;
    private final int sortOrder;
    /** 프론트에서 <img src>에 바로 쓸 조회 URL */
    private final String url;

    public static StepPhotoResponse from(SafetyManualStepPhoto entity) {
        return StepPhotoResponse.builder()
                .photoId(entity.getPhotoId())
                .columnId(entity.getColumn() != null ? entity.getColumn().getColumnId() : null)
                .originalName(entity.getOriginalName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .sortOrder(entity.getSortOrder())
                .url("/safety-api/photos/" + entity.getPhotoId() + "/view")
                .build();
    }
}
