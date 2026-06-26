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

    /** /fire/sprinkler-pipes → sprinkler-pipes.html */
    @GetMapping("/fire/sprinkler-pipes")
    @ResponseBody
    public ResponseEntity<String> fireSprinklerPipes() throws IOException {
        return serveHtml("static/sprinkler-pipes.html");
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

    /** /facility/air-conditioners → facility/air-conditioners.html */
    @GetMapping({"/facility/air-conditioners", "/facility/air-conditioners.html"})
    @ResponseBody
    public ResponseEntity<String> airConditioners() throws IOException {
        return serveHtml("static/facility/air-conditioners.html");
    }

    /** /facility/water-purifiers → facility/water-purifiers.html */
    @GetMapping({"/facility/water-purifiers", "/facility/water-purifiers.html"})
    @ResponseBody
    public ResponseEntity<String> waterPurifiers() throws IOException {
        return serveHtml("static/facility/water-purifiers.html");
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

    @GetMapping("/minspection/sprinkler-pipes/{serial}")
    @ResponseBody
    public ResponseEntity<String> sprinklerPipeInspectionPage(@PathVariable String serial) throws IOException {
        return serveHtml("static/minspection/sprinkler-pipes/index.html");
    }

    @GetMapping("/minspection/air-conditioners/{qrKey}")
    @ResponseBody
    public ResponseEntity<String> airConditionerMobilePlaceholder(@PathVariable String qrKey) {
        return mobileFacilityPlaceholder("에어컨", qrKey, "/images/facility/aircon.png");
    }

    @GetMapping("/minspection/water-purifiers/{qrKey}")
    @ResponseBody
    public ResponseEntity<String> waterPurifierMobilePlaceholder(@PathVariable String qrKey) {
        return mobileFacilityPlaceholder("정수기", qrKey, "/images/facility/water_purifier_icon.png");
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

    private ResponseEntity<String> mobileFacilityPlaceholder(String label, String qrKey, String iconPath) {
        String safeLabel = org.springframework.web.util.HtmlUtils.htmlEscape(label == null ? "기타설비" : label);
        String safeQrKey = org.springframework.web.util.HtmlUtils.htmlEscape(qrKey == null ? "" : qrKey);
        String safeIconPath = org.springframework.web.util.HtmlUtils.htmlEscape(iconPath == null ? "/images/facility/aircon.png" : iconPath);
        String html = "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + safeLabel + " 모바일 점검 준비중</title>"
                + "<style>body{font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f4f7fb;margin:0;padding:24px;color:#0f172a}"
                + ".card{max-width:520px;margin:8vh auto;background:#fff;border-radius:22px;padding:28px;box-shadow:0 20px 50px rgba(15,23,42,.12);text-align:center}"
                + ".icon{width:120px;height:120px;object-fit:contain;margin:0 auto 18px;display:block}"
                + "h1{font-size:24px;margin:0 0 12px}.muted{color:#64748b;line-height:1.6;text-align:left}.key{margin-top:18px;padding:12px;border-radius:12px;background:#f8fafc;word-break:break-all;font-family:monospace;text-align:left}</style></head>"
                + "<body><main class=\"card\"><img class=\"icon\" src=\"" + safeIconPath + "\" alt=\"" + safeLabel + " 아이콘\">"
                + "<h1>" + safeLabel + " 모바일 점검 화면 준비중</h1>"
                + "<p class=\"muted\">QR 연결 주소는 생성되어 있으며, 실제 모바일 점검/추가 화면은 추후 상세 점검항목 확정 후 확장할 수 있도록 열어두었습니다.</p>"
                + "<div class=\"key\">QR KEY: " + safeQrKey + "</div>"
                + "</main></body></html>";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate().cachePrivate().sMaxAge(0, TimeUnit.SECONDS))
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
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
