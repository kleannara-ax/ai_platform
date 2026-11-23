package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 소모품 지급 등록 요청 DTO.
 * <p>업무 요청(requestId)에 연결해 지급하거나, 업무 요청 없이 요청자명(requesterName)만으로
 * 지급할 수 있다. 둘 중 하나는 반드시 있어야 한다.
 */
@Getter
@Setter
public class SupplyIssueCreateRequest {

    /** 연결할 업무 요청 ID (선택 — 업무요청 상세에서 지급할 때) */
    private Long requestId;

    /** 요청자명 (업무 요청 없이 지급할 때 필수, 요청을 연결하면 그 요청의 요청자가 쓰인다) */
    private String requesterName;

    @JsonIgnore
    @AssertTrue(message = "요청자명은 필수입니다.")
    public boolean isRequesterPresent() {
        return requestId != null || (requesterName != null && !requesterName.isBlank());
    }

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

    /**
     * 세부 구분(신형/구형, 제조사 등) — 해당 구분이 있는 품목(마우스/키보드/노트북/모니터/
     * 데스크탑 본체 → "신형"/"구형", 태블릿 → "레노버"/"갤럭시"/"대여")을 지급할 때만 선택되어
     * 전달된다. 구분이 없는 품목이면 null/빈값으로 온다.
     * <p>값이 있으면 지급 수량만큼 품목의 비고(remark)에 기록된 해당 구분 수치도 함께
     * 차감되어, 대시보드 재고 현황 막대그래프의 세부 구분 비율이 실제 지급 내역과 일치하게 된다.
     */
    private String subType;
}
