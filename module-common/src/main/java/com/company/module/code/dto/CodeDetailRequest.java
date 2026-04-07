package com.company.module.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CodeDetailRequest {

    @NotBlank(message = "코드값은 필수입니다.")
    @Size(max = 50, message = "코드값은 50자 이하여야 합니다.")
    private String code;

    @NotBlank(message = "코드명은 필수입니다.")
    @Size(max = 100, message = "코드명은 100자 이하여야 합니다.")
    private String codeName;

    @Size(max = 200)
    private String description;

    @Size(max = 200)
    private String extraValue1;

    @Size(max = 200)
    private String extraValue2;

    private Boolean isActive;
    private Integer sortOrder;
}
