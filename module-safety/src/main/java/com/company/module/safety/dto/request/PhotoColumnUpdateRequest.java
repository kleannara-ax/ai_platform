package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사진을 어느 칸에 보여줄지 옮기는 요청.
 *
 * <p>{@code columnId} 가 null 이면 매뉴얼의 기본 '사진' 열로 되돌린다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PhotoColumnUpdateRequest {

    private Long columnId;
}
