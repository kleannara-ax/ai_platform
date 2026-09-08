package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 분할 업로드로 서버에 올려 둔 파일의 형식 확인 요청 */
@Getter
@Setter
@NoArgsConstructor
public class ExcelStagedPreviewRequest {

    @NotBlank(message = "업로드 식별자는 필수입니다.")
    private String uploadId;
}
