package com.company.module.fire.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * module-fire 전용 페이지 컨트롤러.
 * 기존 core/MobilePageController에서 소방 모듈 관련 라우팅을 이관.
 */
@Controller
public class FirePageController {

    @GetMapping("/minspection/extinguishers/{serial}")
    @ResponseBody
    public ResponseEntity<String> extinguisherInspectionPage(@PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/extinguishers/index.html");
    }

    @GetMapping("/minspection/hydrants/{serial}")
    @ResponseBody
    public ResponseEntity<String> hydrantInspectionPage(@PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/hydrants/index.html");
    }

    @GetMapping("/minspection/receivers/{serial}")
    @ResponseBody
    public ResponseEntity<String> receiverInspectionPage(@PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/receivers/index.html");
    }

    @GetMapping("/minspection/pumps/{serial}")
    @ResponseBody
    public ResponseEntity<String> pumpInspectionPage(@PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/pumps/index.html");
    }

    @GetMapping("/minspection/complete")
    @ResponseBody
    public ResponseEntity<String> completePage() throws IOException {
        return serveHtml("static/minspection/complete.html");
    }

    @GetMapping({"/qr", "/qr/", "/QR", "/QR/"})
    @ResponseBody
    public ResponseEntity<String> qrPage() throws IOException {
        return serveHtml("static/qr/index.html");
    }

    @GetMapping({"/maps/floor", "/maps/floor/", "/maps/floor.html", "/maps/floor-v2", "/maps/floor-v2.html"})
    @ResponseBody
    public ResponseEntity<String> floorPage() throws IOException {
        return serveHtml("static/maps/floor.html");
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
