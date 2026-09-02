package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyNotice;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NoticeResponse {

    private final Long noticeId;
    private final String title;
    private final String content;
    private final boolean pinned;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static NoticeResponse from(SafetyNotice entity) {
        return NoticeResponse.builder()
                .noticeId(entity.getNoticeId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .pinned(entity.isPinned())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
