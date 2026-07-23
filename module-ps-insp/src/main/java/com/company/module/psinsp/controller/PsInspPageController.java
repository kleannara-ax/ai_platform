package com.company.module.psinsp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * PS 지분 검사 프론트엔드 페이지 라우팅
 *
 * <p>/ps-insp/page/** -> index.html (Thymeleaf 렌더링)
 * <p>MES 등 외부 시스템에서 USERID 파라미터와 함께 직접 접근
 * <p>기존 /ps-insp-api/page 경로도 호환 유지
 */
@Controller
public class PsInspPageController {

    /**
     * 메인 페이지 (검사 도구)
     * GET /ps-insp/page  (신규 - MES 연동용)
     * GET /ps-insp-api/page  (기존 호환)
     */
    @GetMapping({"/ps-insp/page", "/ps-insp/page/", "/ps-insp-api/page", "/ps-insp-api/page/"})
    public String index() {
        return "ps-insp/index";
    }

    /**
     * SPA catch-all: 프론트엔드 경로를 index.html로 포워딩
     */
    @GetMapping({
            "/ps-insp/page/inspection", "/ps-insp/page/inspection/**",
            "/ps-insp/page/history", "/ps-insp/page/history/**",
            "/ps-insp-api/page/inspection", "/ps-insp-api/page/inspection/**",
            "/ps-insp-api/page/history", "/ps-insp-api/page/history/**"
    })
    public String spaForward() {
        return "ps-insp/index";
    }
}
