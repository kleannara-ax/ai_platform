package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 미리보기 화면에서 사진 한 장을 가져오는 요청 */
@Getter
@Setter
@NoArgsConstructor
public class ExcelStagedPhotoRequest {

    @NotBlank(message = "업로드 식별자는 필수입니다.")
    private String uploadId;

    @NotBlank(message = "시트명은 필수입니다.")
    private String sheetName;

    /** 미리보기 응답이 알려 준 사진 번호 */
    @PositiveOrZero(message = "사진 번호가 올바르지 않습니다.")
    private int photoIndex;
}
