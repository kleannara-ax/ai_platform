package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공지사항 등록/수정 요청 (등록과 수정의 입력 항목이 같아 하나로 쓴다) */
@Getter
@Setter
@NoArgsConstructor
public class NoticeSaveRequest {

    @NotBlank(message = "공지 제목은 필수입니다.")
    @Size(max = 200, message = "공지 제목은 200자를 넘을 수 없습니다.")
    private String title;

    private String content;

    /** 상단 고정 여부 */
    private boolean pinned;
}
