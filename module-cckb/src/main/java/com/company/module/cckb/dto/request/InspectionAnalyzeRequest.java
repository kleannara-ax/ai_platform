package com.company.module.cckb.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 색상검사 실행(판정) 요청 DTO — multipart 폼 파트에 함께 실려 오는 메타 정보.
 * 실제 이미지 파일은 {@code @RequestParam("file") MultipartFile} 로 별도 수신한다
 * (InspectionController 참고). 이 DTO 자체는 바디가 아니라 폼 파라미터 수신에만 쓰이므로
 * 컨트롤러에서 @RequestParam 개별 수신 + 이 클래스는 문서화/장래 확장용으로 둔다.
 */
@Getter
@NoArgsConstructor
public class InspectionAnalyzeRequest {

    /** 재검사인 경우 원본 검사 ID (data-seqno) */
    private String originalInspectionId;
}
