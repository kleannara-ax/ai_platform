package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 한 달치 일괄 저장 결과 (엑셀 가져오기·단가 일괄 적용용)
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamBulkSaveResponse {

    /** 새로 만든 행 수 */
    private int inserted;
    /** 값을 고친 행 수 */
    private int updated;
    /** 파일에 없어 값을 비운 행 수 (clearMissing 일 때) */
    private int cleared;

    public static SteamBulkSaveResponse of(int inserted, int updated, int cleared) {
        return SteamBulkSaveResponse.builder().inserted(inserted).updated(updated).cleared(cleared).build();
    }
}
