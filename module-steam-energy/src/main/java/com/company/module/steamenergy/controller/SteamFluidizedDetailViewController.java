package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailActualsResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailRenderResponse;
import com.company.module.steamenergy.legacy.service.FluidizedDetailWorkbookService;
import com.company.module.steamenergy.legacy.service.FluidizedSummaryWorkbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 계산된 유동상 세부 운영내역 조회 (읽기 전용)
 *
 * <p>URL Prefix: /steam-energy-api/fluidized-detail
 * <p>입력은 steam_fluidized_detail_daily · _month · _custom_row 에 있고, 여기서는 그 값에 서식의 수식(운휴시간 · 재고 · 소계 · 비용)을
 * 적용한 결과를 돌려준다. 세부 운영내역 · 월별 운영내역 · 사업계획대비 실적 화면이 읽는다.
 */
@RestController
@RequestMapping("/steam-energy-api/fluidized-detail")
@RequiredArgsConstructor
public class SteamFluidizedDetailViewController {

    private final FluidizedDetailWorkbookService detailService;
    private final FluidizedSummaryWorkbookService summaryService;

    /** 한 달의 계산된 세부 운영내역. view: all(기본) 또는 화면의 보기 구역 이름 */
    @GetMapping("/rendered")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailRenderResponse>> rendered(
            @RequestParam("month") String month, @RequestParam(value = "view", required = false) String view) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(YYYY-MM)이 올바르지 않습니다: " + month);
        }
        return ResponseEntity.ok(ApiResponse.success(SteamFluidizedDetailRenderResponse.of(detailService.renderMonth(month, view))));
    }

    /** 전년 1월 ~ 그 해 12월의 월별 실적 (운영비 · 전력비 · SRF 수입금 · 폐기물처리비) */
    @GetMapping("/actuals")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailActualsResponse>> actuals(@RequestParam("year") int year) {
        if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
        return ResponseEntity.ok(ApiResponse.success(SteamFluidizedDetailActualsResponse.of(summaryService.detailActuals(year))));
    }
}
