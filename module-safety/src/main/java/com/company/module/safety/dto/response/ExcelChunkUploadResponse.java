package com.company.module.safety.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 분할 업로드에서 조각 하나를 받은 뒤 돌려주는 진행 상태 */
@Getter
@Builder
public class ExcelChunkUploadResponse {

    /** 이어지는 조각을 보낼 때 그대로 다시 보내야 하는 식별자 (첫 조각의 응답에서 발급된다) */
    private final String uploadId;

    /** 지금까지 받은 조각 수 */
    private final int receivedChunks;

    /** 받아야 할 전체 조각 수 */
    private final int totalChunks;

    /** 조각이 모두 도착했는지 — true 면 형식 확인(preview-staged)을 호출하면 된다 */
    private final boolean completed;
}
