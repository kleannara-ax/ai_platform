package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 원본 로그 파일 응답
 * - ApiResponse 규칙 준수를 위해 바이너리 스트림 대신 Base64 로 전달한다.
 * - 프론트에서 Blob 으로 변환하여 다운로드 처리한다.
 */
@Getter
@Builder
public class OriginalFileResponse {

    private Long uploadId;
    private String fileName;
    private long fileSize;
    private String contentBase64;
}
