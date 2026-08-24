package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 소모품 지급 등록 요청 DTO.
 * <p>업무 요청(requestId)과 품목(itemId)을 연결하여 지급 내역을 등록한다.
 */
@Getter
@Setter
public class SupplyIssueCreateRequest {

    @NotNull(message = "연결할 업무 요청 ID는 필수입니다.")
    private Long requestId;

    @NotNull(message = "지급할 품목 ID는 필수입니다.")
    private Long itemId;

    @NotNull(message = "지급 수량은 필수입니다.")
    @Positive(message = "지급 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @NotBlank(message = "지급 대상자는 필수입니다.")
    private String receiverName;

    private String department;

    @NotBlank(message = "지급 담당자는 필수입니다.")
    private String issuedBy;

    /** 지급일 (미입력 시 서버에서 오늘 날짜로 설정) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issuedAt;
}
