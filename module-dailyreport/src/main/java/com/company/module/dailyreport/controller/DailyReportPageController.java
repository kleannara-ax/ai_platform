package com.company.module.dailyreport.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 세부공장일보 프론트엔드 페이지 라우팅
 *
 * <p>clean URL → static HTML 포워딩
 * <ul>
 *   <li>GET /dailyreport-api/page          → dailyreport/index.html (일보 입력)</li>
 *   <li>GET /dailyreport-api/page/column-mgmt → dailyreport/cell-auth-admin.html (컬럼관리)</li>
 * </ul>
 */
@Controller
@RequestMapping("/dailyreport-api/page")
public class DailyReportPageController {

    /**
     * 세부공장일보 입력 페이지
     * GET /dailyreport-api/page
     */
    @GetMapping({"", "/"})
    public String inputPage() {
        return "forward:/dailyreport/index.html";
    }

    /**
     * 세부공장일보 컬럼관리 페이지
     * GET /dailyreport-api/page/column-mgmt
     */
    @GetMapping("/column-mgmt")
    public String columnMgmtPage() {
        return "forward:/dailyreport/cell-auth-admin.html";
    }
}
