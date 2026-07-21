package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 셀 접근 권한 등록/수정 요청 DTO (★ Phase 4 신규)
 * - 관리자가 '세부공장일보 접근권한' 페이지에서 사용
 */
@Getter
@Setter
public class CellAuthRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "표 코드는 필수입니다.")
    private String tableCode;

    /** 셀 좌표 목록 (예: ["B7", "C7", "D7"]) */
    @NotNull(message = "셀 좌표 목록은 필수입니다.")
    private List<String> cellCoords;

    /** 입력 주기: daily / monthly / yearly / event */
    @NotBlank(message = "입력 주기는 필수입니다.")
    private String freqCode;

    /** 주기 한글 라벨 */
    private String freqLabel;

    /** 설명 */
    private String description;

    /** JSON 배열 문자열로 변환 */
    public String getCellCoordsAsJson() {
        if (cellCoords == null || cellCoords.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cellCoords.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(cellCoords.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
