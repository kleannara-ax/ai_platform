package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 표의 열 추가/수정 요청 (입력 항목이 같아 하나로 쓴다) */
@Getter
@Setter
@NoArgsConstructor
public class ColumnSaveRequest {

    @NotBlank(message = "열 이름은 필수입니다.")
    private String label;

    /** TEXT(글) / CHECK(체크버튼) / PHOTO(사진) */
    private String columnType = "TEXT";

    /** 0 이하면 맨 뒤에 붙인다 */
    private int sortOrder;

    /** 화면에서 가용 폭을 나눌 비중 */
    private int widthWeight = 200;
}
