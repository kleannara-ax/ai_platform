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

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * module-fire 전용 페이지 컨트롤러.
 * 기존 core/MobilePageController에서 소방 모듈 관련 라우팅을 이관.
 *
 * <p>core_menu 테이블의 menuUrl (/fire/xxx) 경로와
 * 실제 정적 HTML 파일 경로를 매핑합니다.</p>
 */
@Controller
public class FirePageController {

    // ── /fire/* 메뉴 URL → 실제 HTML 페이지 서빙 ──

    /** /fire/dashboard → SPA index.html (대시보드는 SPA 내부 렌더링) */
    @GetMapping("/fire/dashboard")
    public String fireDashboard() {
        return "redirect:/index.html#fire_dashboard";
    }

    /** /fire/map → fire-map.html */
    @GetMapping("/fire/map")
    @ResponseBody
    public ResponseEntity<String> fireMap() throws IOException {
        return serveHtml("static/fire-map.html");
    }

    /** /fire/extinguishers → extinguishers.html */
    @GetMapping("/fire/extinguishers")
    @ResponseBody
    public ResponseEntity<String> fireExtinguishers() throws IOException {
        return serveHtml("static/extinguishers.html");
    }

    /** /fire/hydrants → hydrants.html */
    @GetMapping("/fire/hydrants")
    @ResponseBody
    public ResponseEntity<String> fireHydrants() throws IOException {
        return serveHtml("static/hydrants.html");
    }

    /** /fire/receivers → receivers.html */
    @GetMapping("/fire/receivers")
    @ResponseBody
    public ResponseEntity<String> fireReceivers() throws IOException {
        return serveHtml("static/receivers.html");
    }

    /** /fire/pumps → pumps.html */
    @GetMapping("/fire/pumps")
    @ResponseBody
    public ResponseEntity<String> firePumps() throws IOException {
        return serveHtml("static/pumps.html");
    }

    /** /fire/floor → maps/floor.html */
    @GetMapping("/fire/floor")
    @ResponseBody
    public ResponseEntity<String> fireFloor() throws IOException {
        return serveHtml("static/maps/floor.html");
    }

    /** /fire/qr → qr/index.html */
    @GetMapping("/fire/qr")
    @ResponseBody
    public ResponseEntity<String> fireQr() throws IOException {
        return serveHtml("static/qr/index.html");
    }

    // ── 모바일 점검 페이지 ──

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

    /**
     * /login.html → SPA(index.html)로 리다이렉트.
     * 소방 모듈 HTML 페이지에서 인증 실패 시 /login.html로 이동하는데,
     * AI Platform에서는 index.html이 SPA 로그인을 담당하므로 리다이렉트 처리.
     * returnUrl 파라미터가 있으면 그대로 전달하여 로그인 후 원래 페이지로 복귀.
     */
    @GetMapping({"/login.html", "/login"})
    public String loginRedirect(HttpServletRequest request) {
        String returnUrl = request.getParameter("returnUrl");
        if (returnUrl != null && !returnUrl.isBlank()) {
            return "redirect:/index.html?returnUrl=" + java.net.URLEncoder.encode(returnUrl.trim(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return "redirect:/index.html";
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
