package com.company.module.dailyreport.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 세부공장일보 프론트엔드 페이지 라우팅
 *
 * <p>clean URL → static HTML 포워딩
 * <ul>
 *   <li>GET /dailyreport/page          → dailyreport/index.html (일보 입력)</li>
 *   <li>GET /dailyreport/page/safety-stats → dailyreport/safety-stats.html (사고 통계)</li>
 *   <li>GET /dailyreport/page/column-mgmt → dailyreport/cell-auth-admin.html (컬럼관리)</li>
 * </ul>
 */
@Controller
@RequestMapping("/dailyreport/page")
public class DailyReportPageController {

    /**
     * 세부공장일보 입력 페이지
     * GET /dailyreport/page
     */
    @GetMapping({"", "/"})
    public String inputPage() {
        return "forward:/dailyreport/index.html";
    }

    /**
     * 세부공장일보 사고 통계 페이지 (표5~8: 발생건수/손실금액/연도별·월별 추이)
     * GET /dailyreport/page/safety-stats
     */
    @GetMapping("/safety-stats")
    public String safetyStatsPage() {
        return "forward:/dailyreport/safety-stats.html";
    }

    /**
     * 세부공장일보 컬럼관리 페이지
     * GET /dailyreport/page/column-mgmt
     */
    @GetMapping("/column-mgmt")
    public String columnMgmtPage() {
        return "forward:/dailyreport/cell-auth-admin.html";
    }
}
