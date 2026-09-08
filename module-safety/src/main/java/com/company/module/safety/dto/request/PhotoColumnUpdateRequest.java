package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사진을 어느 칸으로 옮길지 지정하는 요청. (표에서 사진을 끌어다 놓을 때 쓴다)
 *
 * <p>{@code columnId} 가 null 이면 매뉴얼의 기본 '사진' 열로 되돌린다.
 * {@code stepId} 를 주면 다른 행으로도 옮긴다 — 비우면 지금 행에 그대로 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PhotoColumnUpdateRequest {

    private Long columnId;

    /** 옮겨 갈 행(단계). 같은 매뉴얼 안이어야 한다. null 이면 행은 그대로. */
    private Long stepId;
}
