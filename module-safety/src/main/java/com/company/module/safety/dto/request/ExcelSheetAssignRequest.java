package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 엑셀 일괄업로드 2단계(확정)에서 시트 하나를 어느 분류에 넣을지 지정하는 항목.
 *
 * <p>업로드 화면에서 시트마다 분류를 따로 고를 수 있으므로, 확정 요청은 이 항목의 목록으로 전달된다.
 * 목록에 없는 시트는 가져오지 않는다(= 선택 해제와 같다).
 */
@Getter
@Setter
@NoArgsConstructor
public class ExcelSheetAssignRequest {

    @NotBlank(message = "시트명은 필수입니다.")
    private String sheetName;

    /** 이 시트를 등록할 중분류(2단계) ID */
    @NotNull(message = "시트별 등록 분류는 필수입니다.")
    private Long categoryId;
}
