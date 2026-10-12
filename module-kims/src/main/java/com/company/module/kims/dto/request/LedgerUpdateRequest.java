package com.company.module.kims.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 입출고 이력 수정 요청 DTO (소모품 업무요청 이력 '상세').
 * <p>품목·수량·세부 구분은 재고와 연결되어 있어 수정할 수 없다(취소 후 다시 등록).
 * 요청자명·지급대상자·부서·지급 담당자는 출고(지급 내역과 연결된 이력)에만 적용된다.
 */
@Getter
@Setter
public class LedgerUpdateRequest {

    /** 입출고일 (입고일/출고일) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;

    /** 요청자명 (출고, 업무요청 없이 지급한 건만) */
    private String requesterName;

    /** 지급 대상자 (출고) */
    private String receiverName;

    /** 부서 (출고) */
    private String department;

    /** 지급 담당자 (출고) */
    private String issuedBy;

    /** 비고 */
    private String note;
}
