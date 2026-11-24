package com.company.module.epr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 제품 마스터 단건 등록·수정 요청 (같은 제품코드가 있으면 수정) */
@Getter
@Setter
public class ProductMasterSaveRequest {

    @NotBlank(message = "제품코드를 입력하세요.")
    @Size(min = 3, max = 30, message = "제품코드는 3~30자여야 합니다. (앞 3자리 파트코드 필요)")
    private String productCode;

    @NotBlank(message = "제품명을 입력하세요.")
    @Size(max = 255, message = "제품명은 255자 이내여야 합니다.")
    private String productName;

    @Min(value = 0, message = "EA/BAG 은 0 이상 입력하세요.")
    private Integer eaPerBag = 0;

    @Min(value = 0, message = "BAG/BOX 는 0 이상 입력하세요.")
    private Integer bagPerBox = 0;

    @Min(value = 0, message = "EA/BOX 는 0 이상 입력하세요.")
    private Integer eaPerBox = 0;

    @Min(value = 0, message = "BAG/PLT 는 0 이상 입력하세요.")
    private Integer bagPerPlt = 0;

    @Min(value = 0, message = "BOX/PLT 는 0 이상 입력하세요.")
    private Integer boxPerPlt = 0;

    @DecimalMin(value = "0.0", message = "BAG 포장중량은 0 이상 입력하세요.")
    private BigDecimal bagWeight = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "BOX 포장중량은 0 이상 입력하세요.")
    private BigDecimal boxWeight = BigDecimal.ZERO;

    @Pattern(regexp = "자사|타사", message = "구분은 자사 또는 타사만 선택하세요.")
    private String companyType = "자사";
}
