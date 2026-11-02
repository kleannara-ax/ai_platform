package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 개인정보처리 위탁계약서 파일 분석 결과.
 * <p>파일은 저장하지 않고 글자만 읽어 계약일자(체결일) 후보를 돌려준다.
 * 화면은 찾은 계약일자를 '개인정보처리 위탁계약 체결일'에 그대로 넣고 작성 여부를 Y 로 바꾼다.
 * (개인정보처리 위탁계약은 원계약과 따로 체결될 수 있으므로 원계약·올해 날짜와 비교하지 않는다)</p>
 */
@Getter
@Builder
public class ContractDocAnalyzeResponse {

    private String fileName;
    /** 파일에서 글자를 읽었는지 (이미지·압축·스캔 PDF·암호 파일은 false) */
    private boolean textReadable;
    /** 계약일자로 판단한 날짜 (없으면 null) */
    private LocalDate detectedDate;
    /** 판단 근거 (예: '체결일' 표기 옆 날짜) */
    private String detectedReason;
    /** 문서에서 찾은 날짜 후보 (가능성 높은 순, 최대 5개) */
    private List<Candidate> candidates;
    /** 화면 안내 문구 */
    private String message;

    @Getter
    @Builder
    public static class Candidate {
        private LocalDate date;
        /** SIGNED(체결일·계약일 표기 옆) / DOC(문서 안 날짜, 서명란 추정) / PERIOD(계약기간) */
        private String kind;
        private String kindLabel;
        /** 날짜 앞뒤 문장 일부 */
        private String context;
    }
}
