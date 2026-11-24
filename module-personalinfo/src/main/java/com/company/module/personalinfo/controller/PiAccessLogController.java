package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.AccessLogResponse;
import com.company.module.personalinfo.entity.AccessActionType;
import com.company.module.personalinfo.service.PiAccessLogService;
import com.company.module.personalinfo.service.PiRoles;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 접근 기록 조회 API (보안팀 관리자·플랫폼 관리자 전용).
 * 계약 상세·점검 상세 조회와 계약서·점검 결과·엑셀 다운로드 기록을 기간·행위·사용자·업체로 찾는다.
 */
@RestController
@RequestMapping("/personalinfo-api/access-logs")
@RequiredArgsConstructor
public class PiAccessLogController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PiAccessLogService accessLogService;

    /** 행위 구분 목록 (화면 선택 상자) */
    @GetMapping("/action-types")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> actionTypes() {
        return ResponseEntity.ok(ApiResponse.success(Arrays.stream(AccessActionType.values())
                .map(a -> Map.of("code", a.name(), "label", a.getLabel())).toList()));
    }

    /** 접근 기록 검색 (기간 기본: 최근 30일, 최신순) */
    @GetMapping
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String vendor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AccessLogResponse> p = accessLogService.search(from, to, actionType, contractId, user, vendor, page, size);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", p.getContent(), "totalElements", p.getTotalElements(),
                "page", p.getNumber(), "size", p.getSize(), "totalPages", p.getTotalPages())));
    }

    /** 접근 기록 엑셀 (검색 조건 적용, 최대 10,000건, 사유 필수 — 이 다운로드도 기록됨) */
    @GetMapping("/excel")
    @PreAuthorize(PiRoles.CAN_MANAGE_FORMS)
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String reason) throws IOException {
        String r = PiAccessLogService.requireReason(reason);
        List<AccessLogResponse> rows = accessLogService.search(from, to, actionType, contractId, user, vendor, 0, 500).getContent();
        rows = new java.util.ArrayList<>(rows);
        for (int pg = 1; pg < 20 && rows.size() == pg * 500; pg++) {
            rows.addAll(accessLogService.search(from, to, actionType, contractId, user, vendor, pg, 500).getContent());
        }
        String[] headers = {"일시", "행위", "수탁업체명", "계약명", "대상", "행 수", "사유", "사용자", "로그인 ID", "접속 IP"};
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("접근 기록");
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            int n = 1;
            for (AccessLogResponse a : rows) {
                Row row = sh.createRow(n++);
                String[] v = {a.getAccessedAt() == null ? "" : a.getAccessedAt().format(DT), a.getActionTypeLabel(),
                        a.getVendorName(), a.getContractName(), a.getTargetName(),
                        a.getRecordCount() == null ? "" : String.valueOf(a.getRecordCount()), a.getReason(),
                        a.getUserName(), a.getUserLoginId(), a.getClientIp()};
                for (int i = 0; i < v.length; i++) {
                    row.createCell(i).setCellValue(v[i] == null ? "" : v[i]);
                }
            }
            int[] widths = {20, 20, 22, 22, 40, 8, 30, 12, 14, 16};
            for (int i = 0; i < widths.length; i++) {
                sh.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(out);
            wb.dispose();
            accessLogService.record(AccessActionType.DOWNLOAD_ACCESS_LOG, null, null, null, null,
                    "접근 기록 엑셀 (" + (from == null ? "최근 30일" : from + " ~ " + (to == null ? LocalDate.now() : to)) + ")",
                    rows.size(), r);
            return PiDownloads.of(new com.company.module.personalinfo.service.PiFileStorage.DownloadFile(
                    "개인정보위수탁_접근기록_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()));
        }
    }
}
