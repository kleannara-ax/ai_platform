package com.company.module.kims.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * PC 목록 엑셀 업로드 결과.
 * <p>{@code ok=false} 이면 헤더가 양식과 달라 업로드가 거부된 것(경고 모달 대상).
 */
@Getter
@Builder
public class IpExcelUploadResult {

    /** 헤더가 양식과 일치해 업로드가 수행됐는지 여부 */
    private final boolean ok;
    /** 신규 등록된 행 수 */
    private final int imported;
    /** 건너뛴 행 수 (IP 없음/중복 등) */
    private final int skipped;
    /** 사용자에게 보여줄 메시지 */
    private final String message;
    /** 기대한 헤더(양식) */
    private final List<String> expectedHeaders;
    /** 실제 업로드된 파일의 헤더 */
    private final List<String> actualHeaders;

    public static IpExcelUploadResult success(int imported, int skipped) {
        String msg = imported + "건이 업로드되었습니다."
                + (skipped > 0 ? (" (" + skipped + "건은 IP 누락/중복으로 제외)") : "");
        return IpExcelUploadResult.builder()
                .ok(true).imported(imported).skipped(skipped).message(msg).build();
    }

    public static IpExcelUploadResult headerMismatch(List<String> expected, List<String> actual) {
        return IpExcelUploadResult.builder()
                .ok(false).imported(0).skipped(0)
                .message("엑셀 헤더가 양식과 일치하지 않아 업로드되지 않았습니다.")
                .expectedHeaders(expected).actualHeaders(actual).build();
    }
}
