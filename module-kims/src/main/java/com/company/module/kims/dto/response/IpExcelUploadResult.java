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
    /** 기존 행 중 내용이 바뀌어 갱신된 행 수 (IP관리/PC관리 표 업로드) */
    private final int updated;
    /** 건너뛴 사유 (예: "5행: 제조번호 미등록") — 최대 20건 */
    private final List<String> skipReasons;
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

    /** IP관리/PC관리 표 업로드 결과 (신규·갱신·제외 건수와 제외 사유) */
    public static IpExcelUploadResult success(int imported, int updated, int skipped, List<String> skipReasons) {
        StringBuilder msg = new StringBuilder();
        msg.append("신규 ").append(imported).append("건, 갱신 ").append(updated).append("건");
        if (skipped > 0) msg.append(", 제외 ").append(skipped).append("건");
        msg.append(" 처리되었습니다.");
        return IpExcelUploadResult.builder()
                .ok(true).imported(imported).updated(updated).skipped(skipped)
                .skipReasons(skipReasons).message(msg.toString()).build();
    }

    public static IpExcelUploadResult headerMismatch(List<String> expected, List<String> actual) {
        return IpExcelUploadResult.builder()
                .ok(false).imported(0).skipped(0)
                .message("엑셀 헤더가 양식과 일치하지 않아 업로드되지 않았습니다.")
                .expectedHeaders(expected).actualHeaders(actual).build();
    }
}
