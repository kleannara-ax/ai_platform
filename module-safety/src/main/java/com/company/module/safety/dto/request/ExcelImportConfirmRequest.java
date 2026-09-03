package com.company.module.safety.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 엑셀 일괄업로드 2단계(확정) 요청.
 * <p>1단계(미리보기)에서 사용자가 확인/선택한 시트명만 이 목록에 담아 보낸다.
 * (파일은 1단계와 마찬가지로 함께 다시 업로드한다 — 서버에 임시 저장하지 않는 무상태 방식)
 */
@Getter
@NoArgsConstructor
public class ExcelImportConfirmRequest {

    @NotNull(message = "등록할 분류는 필수입니다.")
    private Long categoryId;

    /** 확정(가져오기)할 시트명 목록. 1단계 미리보기에서 recognized=true 이고 사용자가 선택한 시트만 담는다. */
    private List<String> selectedSheetNames = new ArrayList<>();
}
