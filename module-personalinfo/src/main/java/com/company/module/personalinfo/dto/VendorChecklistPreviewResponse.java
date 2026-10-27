package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 업체별 체크리스트 업로드 미리보기.
 * 파일을 저장하지 않고 파싱 결과와 "체크리스트 회사명으로 찾은 업체" 를 돌려준다.
 */
@Getter
@Builder
public class VendorChecklistPreviewResponse {

    private String sheetCompanyName;
    private String sheetTaskName;
    private LocalDate evaluationDate;
    private String evaluator;
    private String systemOwnedYn;
    private int totalCount;
    private int yCount;
    private int nCount;
    private int naCount;
    private int blankCount;
    private String result;
    private String resultLabel;
    private List<String> warnings;
    /** 체크리스트 회사명과 일치/유사한 업체 후보 (정확히 일치하는 업체가 앞) */
    private List<VendorSummaryResponse> matchedVendors;

    // Lombok getter(getYCount) 직렬화 이름 고정
    @com.fasterxml.jackson.annotation.JsonProperty("yCount")
    public int getYCount() { return yCount; }

    @com.fasterxml.jackson.annotation.JsonProperty("nCount")
    public int getNCount() { return nCount; }
}
