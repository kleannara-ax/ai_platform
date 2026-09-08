package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 분할 업로드로 올려 둔 파일에서 시트 하나를 미리보기 요청 */
@Getter
@Setter
@NoArgsConstructor
public class ExcelStagedSheetRequest {

    @NotBlank(message = "업로드 식별자는 필수입니다.")
    private String uploadId;

    @NotBlank(message = "시트명은 필수입니다.")
    private String sheetName;
}
