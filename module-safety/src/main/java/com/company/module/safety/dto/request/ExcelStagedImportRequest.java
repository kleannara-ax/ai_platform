package com.company.module.safety.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 분할 업로드로 서버에 올려 둔 파일의 확정 업로드 요청.
 *
 * <p>파일을 다시 보내지 않으므로 multipart 가 아니라 평범한 JSON 으로 받는다.
 * (기존 {@code /confirm} 은 파일과 함께 보내야 해서 assignments 를 문자열로 받았다)
 */
@Getter
@Setter
@NoArgsConstructor
public class ExcelStagedImportRequest {

    @NotBlank(message = "업로드 식별자는 필수입니다.")
    private String uploadId;

    @Valid
    @NotEmpty(message = "가져올 시트를 하나 이상 선택하세요.")
    private List<ExcelSheetAssignRequest> assignments;
}
