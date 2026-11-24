package com.company.module.epr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    // 입수량은 정수. Integer 로 받으면 JSON 1.5 가 말없이 1 로 잘려 들어오므로 BigDecimal 로 받아 소수를 거부한다.
    @DecimalMin(value = "0", message = "EA/BAG 은 0 이상 입력하세요.")
    @Digits(integer = 9, fraction = 0, message = "EA/BAG 은 정수로 입력하세요.")
    private BigDecimal eaPerBag = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "BAG/BOX 는 0 이상 입력하세요.")
    @Digits(integer = 9, fraction = 0, message = "BAG/BOX 는 정수로 입력하세요.")
    private BigDecimal bagPerBox = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "EA/BOX 는 0 이상 입력하세요.")
    @Digits(integer = 9, fraction = 0, message = "EA/BOX 는 정수로 입력하세요.")
    private BigDecimal eaPerBox = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "BAG/PLT 는 0 이상 입력하세요.")
    @Digits(integer = 9, fraction = 0, message = "BAG/PLT 는 정수로 입력하세요.")
    private BigDecimal bagPerPlt = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "BOX/PLT 는 0 이상 입력하세요.")
    @Digits(integer = 9, fraction = 0, message = "BOX/PLT 는 정수로 입력하세요.")
    private BigDecimal boxPerPlt = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "BAG 포장중량은 0 이상 입력하세요.")
    private BigDecimal bagWeight = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "BOX 포장중량은 0 이상 입력하세요.")
    private BigDecimal boxWeight = BigDecimal.ZERO;

    @Pattern(regexp = "자사|타사", message = "구분은 자사 또는 타사만 선택하세요.")
    private String companyType = "자사";
}
