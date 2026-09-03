package com.company.module.autodrawing.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * module-autodrawing 전용 페이지 컨트롤러.
 *
 * <p>core_menu 테이블의 menuUrl (/autodrawing/xxx) 경로와
 * 실제 정적 HTML 파일 경로를 매핑합니다.</p>
 */
@Controller
public class AutodrawingPageController {

    /** /autodrawing → 메인 도면 생성 페이지 (SPA) */
    @GetMapping({"/autodrawing", "/autodrawing/"})
    @ResponseBody
    public ResponseEntity<String> autodrawingMain() throws IOException {
        return serveHtml("static/autodrawing/index.html");
    }

    /** /autodrawing/editor → 도면 편집기 */
    @GetMapping("/autodrawing/editor")
    @ResponseBody
    public ResponseEntity<String> autodrawingEditor() throws IOException {
        return serveHtml("static/autodrawing/index.html");
    }

    /** /autodrawing/bearing-preview → 베어링 미리보기 */
    @GetMapping("/autodrawing/bearing-preview")
    @ResponseBody
    public ResponseEntity<String> bearingPreview() throws IOException {
        return serveHtml("static/autodrawing/bearing_preview.html");
    }

    private ResponseEntity<String> serveHtml(String resourcePath) throws IOException {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate().cachePrivate().sMaxAge(0, TimeUnit.SECONDS))
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }
}
