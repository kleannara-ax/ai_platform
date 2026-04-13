package com.company.module.psinsp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * PS 커버리지 검사 프론트엔드 페이지 라우팅
 *
 * <p>/ps-insp-api/page/** -> index.html (Thymeleaf 렌더링)
 * <p>context-path 없이 사용하므로 Prefix로 분리
 */
@Controller
@RequestMapping("/ps-insp-api/page")
public class PsInspPageController {

    /**
     * 메인 페이지 (검사 도구)
     * GET /ps-insp-api/page
     */
    @GetMapping({"", "/"})
    public String index() {
        return "ps-insp/index";
    }

    /**
     * SPA catch-all: 프론트엔드 경로를 index.html로 포워딩
     */
    @GetMapping({
            "/inspection",
            "/inspection/**",
            "/history",
            "/history/**"
    })
    public String spaForward() {
        return "ps-insp/index";
    }
}
