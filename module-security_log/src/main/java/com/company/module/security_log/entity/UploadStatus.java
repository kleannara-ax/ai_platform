package com.company.module.security_log.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그 업로드 처리 상태
 */
@Getter
@RequiredArgsConstructor
public enum UploadStatus {
    ANALYZED("분석완료(검토대기)"),
    REVIEWED("검토완료");

    private final String label;
}
