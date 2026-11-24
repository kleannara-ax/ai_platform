package com.company.module.epr.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * EPR API 공용 — 권한 식과 엑셀 파일 응답.
 *
 * <p>권한: 플랫폼 메뉴 권한을 그대로 쓴다. 역할-메뉴 매핑(core_role_menu)에서 {@code EPR_REPORT} 메뉴가 열린
 * 역할만 EPR API 를 쓸 수 있다 (접근 권한 화면에서 관리). 별도 권한 코드는 두지 않는다.
 */
final class EprAccess {

    static final String MENU_CODE = "EPR_REPORT";

    /** @PreAuthorize 식 — 컨트롤러 클래스에 붙인다. */
    static final String HAS_MENU = "@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'EPR_REPORT')";

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private EprAccess() {
    }

    /** 파일 내려받기는 ApiResponse 로 감싸지 않는다 (본문이 엑셀 바이트). 실패는 ApiResponse 오류로 나간다. */
    static ResponseEntity<ByteArrayResource> xlsx(byte[] bytes, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
