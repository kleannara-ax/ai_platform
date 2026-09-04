package com.company.module.dailyreport.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.dailyreport.dto.DailyReportResponse;
import com.company.module.dailyreport.dto.ReportTableResponse;
import com.company.module.dailyreport.service.CellService;
import com.company.module.dailyreport.service.DailyReportService;
import com.company.module.dailyreport.service.MenuPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 세부공장일보 화면 렌더링 컨트롤러 (★ Phase 4 개선)
 *
 * 변경사항:
 * - 1계층 권한 확인: core_menu_permission에서 '세부공장일보 입력' 접근 여부 검증
 * - accounts 엔드포인트 제거: AI 플랫폼 로그인으로 대체
 * - 인증 정보: Spring Security @AuthenticationPrincipal에서 추출
 * - 접근 권한 메타 정보 포함 (canAccessAuthPage)
 */
@RestController
@RequestMapping("/dailyreport-api/view")
@RequiredArgsConstructor
public class ViewRenderController {

    private final DailyReportService reportService;
    private final CellService cellService;
    private final MenuPermissionService menuPermissionService;

    /** 표 코드 목록 (HTML 원본 순서) */
    private static final String[] TABLE_CODES = {
            "TBL_PRODUCTION_INDEX",
            "TBL_INVENTORY",
            "TBL_ENERGY",
            "TBL_BOILER"
    };

    /** ★ 세부공장일보 사고 통계 표 코드 목록 (표5~8, 2026-08 신규) */
    private static final String[] SAFETY_TABLE_CODES = {
            "TBL_SAFETY_INCIDENT_COUNT",
            "TBL_SAFETY_INCIDENT_AMOUNT",
            "TBL_SAFETY_YEARLY_TREND",
            "TBL_SAFETY_MONTHLY_TREND"
    };

    /**
     * 일보 전체 렌더링용 데이터 반환
     * - ★ 1계층 권한 검증: 로그인된 사용자의 '세부공장일보 입력' 접근 여부 확인
     * - 일보 마스터 + 4개 표 전체 셀 데이터 (사용자별 editable 포함)
     *
     * @param reportDate 일보 날짜 (예: 2024-07-20)
     * @param userId     사용자 PK (Spring Security 인증 정보)
     * @param loginId    로그인 ID (Spring Security 인증 정보)
     */
    @GetMapping("/render")
    public ApiResponse<Map<String, Object>> renderReport(
            @RequestParam LocalDate reportDate,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @AuthenticationPrincipal(expression = "username") String loginId) {

        // ★ 1계층: 세부공장일보 입력 페이지 접근 권한 확인
        if (userId != null && !menuPermissionService.canAccessInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 입력 페이지에 대한 접근 권한이 없습니다.");
        }

        DailyReportResponse report = reportService.getReportByDate(reportDate, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);

        // 4개 표 데이터 (사용자별 editable 계산)
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String tableCode : TABLE_CODES) {
            try {
                if (loginId != null && userId != null) {
                    tables.put(tableCode,
                            cellService.getTableDataForUser(
                                    report.getReportId(), tableCode, userId, loginId));
                } else {
                    tables.put(tableCode,
                            reportService.getTableData(report.getReportId(), tableCode));
                }
            } catch (Exception e) {
                // 표 조회 실패 시 빈 구조라도 반환 (프론트엔드에서 표 섹션 표시)
                Map<String, Object> emptyTable = new LinkedHashMap<>();
                emptyTable.put("tableCode", tableCode);
                emptyTable.put("cells", List.of());
                tables.put(tableCode, emptyTable);
            }
        }
        result.put("tables", tables);

        // 특이사항 (사업부별 5행 고정, 담당자/편집가능여부/최종저장자 포함)
        result.put("remarks", reportService.getRemarksForUser(report.getReportId(), userId));

        // 이미지
        result.put("images", reportService.getImages(report.getReportId()));

        // ★ 접근 권한 메타 정보 (프론트엔드에서 UI 분기에 사용)
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        if (userId != null) {
            permissions.put("canAccessInput", menuPermissionService.canAccessInputPage(userId));
            permissions.put("canWriteInput", menuPermissionService.canWriteInputPage(userId));
            permissions.put("canAccessAuth", menuPermissionService.canAccessAuthPage(userId));
            // ★ canManageAuth == canAccessAuth (2026-07부터 동일 기준) — 컬럼관리 대시보드에
            //   등록된 사용자는 admin과 동일하게 등록/수정/삭제/재동기화 가능
            permissions.put("canManageAuth", menuPermissionService.canAdminAuthPage(userId));
            // ★ 화면 라벨 분기용(실제 admin 계정인지) — 권한 범위와는 무관
            permissions.put("isAdmin", menuPermissionService.isRealAdmin(userId));
        }
        result.put("permissions", permissions);

        return ApiResponse.success(result);
    }

    /**
     * ★ 세부공장일보 사고 통계 렌더링용 데이터 반환 (표5~8, 2026-08 신규)
     * - 접근 권한 검증은 기존 '세부공장일보 입력' 접근 권한(cell_auth 기준)을 그대로 재사용한다
     *   (MenuPermissionService가 core_menu_permission을 더 이상 사용하지 않고 cell_auth 등록
     *    여부만으로 판단하는 단일 기준 구조이므로, 새 페이지를 위한 별도 권한 로직이 필요 없음).
     * - 이미지는 해당 없음 (images 미포함)
     * - ★★ 2026-09 추가: 표9(사고 금액 특이사항, TBL_SAFETY_AMOUNT_NOTE)/
     *   표10(안전사고 발생추이 특이사항, TBL_SAFETY_TREND_NOTE) 응답 포함
     *
     * @param reportDate 일보 날짜 (예: 2026-08-20)
     * @param userId     사용자 PK (Spring Security 인증 정보)
     * @param loginId    로그인 ID (Spring Security 인증 정보)
     */
    @GetMapping("/safety-render")
    public ApiResponse<Map<String, Object>> renderSafetyStats(
            @RequestParam LocalDate reportDate,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @AuthenticationPrincipal(expression = "username") String loginId) {

        // ★ 1계층: 세부공장일보 입력 페이지 접근 권한 확인 (기존과 동일 기준 재사용)
        if (userId != null && !menuPermissionService.canAccessInputPage(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "세부공장일보 사고 통계 페이지에 대한 접근 권한이 없습니다.");
        }

        DailyReportResponse report = reportService.getReportByDate(reportDate, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);

        // 사고 통계 4개 표 데이터 (사용자별 editable 계산)
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String tableCode : SAFETY_TABLE_CODES) {
            try {
                if (loginId != null && userId != null) {
                    tables.put(tableCode,
                            cellService.getTableDataForUser(
                                    report.getReportId(), tableCode, userId, loginId));
                } else {
                    tables.put(tableCode,
                            reportService.getTableData(report.getReportId(), tableCode));
                }
            } catch (Exception e) {
                // 표 조회 실패 시 빈 구조라도 반환 (프론트엔드에서 표 섹션 표시)
                Map<String, Object> emptyTable = new LinkedHashMap<>();
                emptyTable.put("tableCode", tableCode);
                emptyTable.put("cells", List.of());
                tables.put(tableCode, emptyTable);
            }
        }
        result.put("tables", tables);

        // ★★ 2026-09 추가 — 사고 금액/안전사고 발생추이 특이사항표 2종
        // (각각 독립된 tableCode+카테고리 그룹, 서로/TBL_SPECIAL_NOTE와 값이 섞이지 않음)
        result.put("amountNoteRemarks", reportService.getRemarksForUser(
                report.getReportId(), userId, DailyReportService.SAFETY_AMOUNT_NOTE_TABLE_CODE));
        result.put("trendNoteRemarks", reportService.getRemarksForUser(
                report.getReportId(), userId, DailyReportService.SAFETY_TREND_NOTE_TABLE_CODE));

        // ★ 접근 권한 메타 정보 (프론트엔드에서 UI 분기에 사용) — 기존 /render와 동일 항목
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        if (userId != null) {
            permissions.put("canAccessInput", menuPermissionService.canAccessInputPage(userId));
            permissions.put("canWriteInput", menuPermissionService.canWriteInputPage(userId));
            permissions.put("canAccessAuth", menuPermissionService.canAccessAuthPage(userId));
            permissions.put("canManageAuth", menuPermissionService.canAdminAuthPage(userId));
            permissions.put("isAdmin", menuPermissionService.isRealAdmin(userId));
        }
        result.put("permissions", permissions);

        return ApiResponse.success(result);
    }

    /**
     * 사용자의 메뉴 접근 권한 확인 (프론트엔드 초기 로드 시 호출)
     * - 로그인 후 어떤 페이지에 접근 가능한지 반환
     */
    @GetMapping("/my-permissions")
    public ApiResponse<Map<String, Object>> getMyPermissions(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @AuthenticationPrincipal(expression = "username") String loginId) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("loginId", loginId);

        Map<String, Boolean> permissions = new LinkedHashMap<>();
        if (userId != null) {
            permissions.put("canAccessInput", menuPermissionService.canAccessInputPage(userId));
            permissions.put("canWriteInput", menuPermissionService.canWriteInputPage(userId));
            permissions.put("canAccessAuth", menuPermissionService.canAccessAuthPage(userId));
            permissions.put("canManageAuth", menuPermissionService.canAdminAuthPage(userId));
            permissions.put("isAdmin", menuPermissionService.isRealAdmin(userId));
        }
        result.put("permissions", permissions);

        return ApiResponse.success(result);
    }
}
