package com.company.module.security_log.service;

import com.company.module.security_log.support.SecLogTime;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DashboardResponse;
import com.company.module.security_log.dto.MonthlyStatusResponse;
import com.company.module.security_log.entity.SecLogAccessSummary;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.repository.SecLogAccessSummaryRepository;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 월 점검 결과 내보내기 (ISMS 2.9.4 로그 관리 / 2.9.5 로그 점검 증적)
 * - 엑셀(xlsx) 1개 파일에 시트 5개: 요약 / 일별 수집 현황 / 접속 로그 검토 / 탐지 내역 / 특권 계정 활동
 * - 사용 중인 점검 대상 시스템만 포함 (대시보드와 같은 기준)
 * - 한글 글꼴(맑은 고딕) 지정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogMonthlyReportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FONT = "맑은 고딕";
    /** 탐지 내역 시트 최대 행 (엑셀 용량 보호) */
    private static final int MAX_DETECTION_ROWS = 50_000;
    private static final Map<String, String> FLAG = Map.of(
            "FAIL_MANY", "로그인 실패 5회↑", "OFF_HOUR", "업무시간 외 접속", "EXTERNAL_IP", "외부 IP",
            "MULTI_IP", "다중 IP(3개↑)", "PRIV_ACCOUNT", "특권/공용 계정", "IP_DENIED", "허용 외 IP 접속 시도");

    private final SecLogDashboardService dashboardService;
    private final SecLogMonthlyStatusService monthlyStatusService;
    private final SecLogUploadRepository uploadRepository;
    private final SecLogDetectionRepository detectionRepository;
    private final SecLogAccessSummaryRepository accessSummaryRepository;

    public record ReportFile(String fileName, byte[] content) {
    }

    public ReportFile export(String targetYm, String exportedBy) {
        DashboardResponse d = dashboardService.get(targetYm);
        String ym = d.getTargetYm();
        MonthlyStatusResponse st = monthlyStatusService.getStatus(ym);
        Set<Long> active = SecLogDashboardService.activeIds(st.getSystems());

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            summarySheet(wb, s, d, st, exportedBy);
            calendarSheet(wb, s, d);
            accessSheet(wb, s, ym, active);
            detectionSheet(wb, s, ym, active);
            privSheet(wb, s, d);
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                printSetup(wb.getSheetAt(i), i == 0);
            }
            wb.write(out);
            String file = "ISMS_로그점검결과_" + ym.substring(0, 4) + "년" + ym.substring(4) + "월.xlsx";
            return new ReportFile(file, out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "엑셀 파일을 만들지 못했습니다.");
        }
    }

    // ------------------------------------------------------------------ 1. 요약

    private void summarySheet(XSSFWorkbook wb, Styles s, DashboardResponse d, MonthlyStatusResponse st, String by) {
        Sheet sh = wb.createSheet("1.요약");
        String ym = d.getTargetYm();
        int r = 0;
        Row title = sh.createRow(r++);
        title.setHeightInPoints(28);
        cell(title, 0, ym.substring(0, 4) + "년 " + Integer.parseInt(ym.substring(4)) + "월 ISMS 로그 점검 결과", s.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
        Row sub = sh.createRow(r++);
        cell(sub, 0, "근거: ISMS-P 2.9.4 로그 및 접속기록 관리 / 2.9.5 로그 및 접속기록 점검   ·   출력일시 "
                + SecLogTime.now().format(DT) + "   ·   출력자 " + (by == null ? "-" : by), s.muted);
        sh.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));
        r++;

        // 핵심 지표
        cell(sh.createRow(r++), 0, "■ 점검 요약", s.section);
        String[][] kv = {
                {"점검 대상 시스템", d.getSystemCount() + "개 (사용 중)"},
                {"월 마감률", d.getCloseRate() + "%  (접속·행위 로그 검토완료 " + d.getAccessReviewed() + " / " + d.getSystemCount() + " 시스템)"},
                {"마감 기준일", d.getCloseDueDate() + (d.isClosed() ? "  · 마감 완료" : "  · 남은 시스템 " + d.getCloseRemainCount() + "개")},
                {"로그 수집률", collectRate(d) + "%  (수집된 날 / 대상 일수, 시스템 합계 · 활동 없는 날도 수집된 것으로 계산)"},
                {"탐지 건수", d.getDetectionCount() + "건  (심각 " + d.getCriticalCount() + " · 높음 " + d.getHighCount()
                        + " · 보통 " + d.getMediumCount() + " · 낮음 " + d.getLowCount() + ")"},
                {"미검토 탐지", d.getPendingDetectionCount() + "건  (7일 초과 " + (d.getAging().getD8to14() + d.getAging().getOver14()) + "건)"},
                {"위험 접속 계정", d.getRiskAccountCount() + "개"},
                {"특권/공용 계정", d.getPrivAccounts().size() + "개 활동"},
        };
        for (String[] e : kv) {
            Row row = sh.createRow(r++);
            cell(row, 0, e[0], s.th);
            cell(row, 1, e[1], s.td);
            sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, 7));
        }
        r++;

        // 시스템별 점검 결과
        cell(sh.createRow(r++), 0, "■ 시스템별 점검 결과", s.section);
        String[] h = {"구분", "시스템", "담당자", "접속·행위 로그", "업로드", "위험 계정", "탐지"};
        header(sh.createRow(r++), h, s);
        for (MonthlyStatusResponse.Row x : st.getSystems()) {
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, x.getCategory(), s.td);
            cell(row, c++, x.getSystemName(), s.tdBold);
            cell(row, c++, x.getManagerName() == null ? "-" : x.getManagerName(), s.td);
            cell(row, c++, statusLabel(x.getAccessStatus()), statusStyle(s, x.getAccessStatus()));
            num(row, c++, x.getAccessUploadCount(), s);
            num(row, c++, x.getAccessRiskAccounts(), s);
            num(row, c++, x.getDetectionCount(), s);
        }
        r += 2;
        Row sign = sh.createRow(r++);
        cell(sign, 0, "검토자", s.th);
        cell(sign, 1, "", s.td);
        cell(sign, 2, "검토일", s.th);
        cell(sign, 3, "", s.td);
        cell(sign, 4, "승인자", s.th);
        cell(sign, 5, "", s.td);
        sign.setHeightInPoints(30);
        widths(sh, 30, 20, 12, 14, 10, 10, 14, 20, 10);
    }

    // ------------------------------------------------------------------ 2. 일별 수집 현황

    private void calendarSheet(XSSFWorkbook wb, Styles s, DashboardResponse d) {
        Sheet sh = wb.createSheet("2.일별 수집 현황");
        int r = 0;
        cell(sh.createRow(r++), 0, "일별 로그 수집 현황 (칸 숫자 = 그 날짜 로그 줄 수)   숫자 = 수집(로그 있음)   0 = 수집됨·활동 없음   ✕ 수집 실패   — 누락   · 오늘 이후", s.muted);
        List<String> days = d.getCalendarDays();
        Row hr = sh.createRow(r++);
        cell(hr, 0, "시스템", s.th);
        cell(hr, 1, "수집 방식", s.th);
        for (int i = 0; i < days.size(); i++) {
            cell(hr, 2 + i, String.valueOf(i + 1), s.thCenter);
        }
        cell(hr, 2 + days.size(), "수집일(활동 없음 포함)", s.th);
        cell(hr, 3 + days.size(), "누락", s.th);
        cell(hr, 4 + days.size(), "실패", s.th);
        cell(hr, 5 + days.size(), "수집률", s.th);
        for (DashboardResponse.CalendarRow c : d.getCalendar()) {
            Row row = sh.createRow(r++);
            cell(row, 0, c.getSystemName(), s.tdBold);
            cell(row, 1, "월 1회 업로드", s.td);
            for (int i = 0; i < c.getStatus().size(); i++) {
                String stt = c.getStatus().get(i);
                int n = c.getCounts().get(i);
                switch (stt) {
                    case "OK" -> cell(row, 2 + i, String.valueOf(n), s.calOk);
                    case "EMPTY" -> cell(row, 2 + i, "0", s.calEmpty);
                    case "FAIL" -> cell(row, 2 + i, "✕", s.calFail);
                    case "MISS" -> cell(row, 2 + i, "—", s.calMiss);
                    default -> cell(row, 2 + i, "·", s.calNa);
                }
            }
            num(row, 2 + days.size(), c.getOkDays() + c.getEmptyDays(), s);
            num(row, 3 + days.size(), c.getMissDays(), s);
            num(row, 4 + days.size(), c.getFailDays(), s);
            cell(row, 5 + days.size(), c.getCoverage() + "%", s.tdCenter);
        }
        sh.setColumnWidth(0, 22 * 256);
        sh.setColumnWidth(1, 12 * 256);
        for (int i = 0; i < days.size(); i++) {
            sh.setColumnWidth(2 + i, 5 * 256);
        }
        for (int i = 0; i < 4; i++) {
            sh.setColumnWidth(2 + days.size() + i, 8 * 256);
        }
        sh.createFreezePane(2, 2);
    }

    // ------------------------------------------------------------------ 3. 접속 로그 검토

    private void accessSheet(XSSFWorkbook wb, Styles s, String ym, Set<Long> active) {
        Sheet sh = wb.createSheet("3.접속 로그 검토");
        String[] h = {"시스템", "파일명", "등록 방식", "분석 줄 수", "탐지", "심각", "높음", "보통", "낮음",
                "상태", "검토 의견", "검토일시", "등록일시"};
        header(sh.createRow(0), h, s);
        int r = 1;
        for (SecLogUpload u : uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N)) {
            if (u.getSystemId() == null || !active.contains(u.getSystemId())) {
                continue;
            }
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, u.getSystemName(), s.tdBold);
            cell(row, c++, u.getOriginalFileName(), s.td);
            cell(row, c++, u.getCreatedBy() == null ? "자동 수집(이전)" : "업로드", s.td);
            num(row, c++, nz(u.getTotalLineCount()), s);
            num(row, c++, nz(u.getDetectionCount()), s);
            num(row, c++, nz(u.getCriticalCount()), s);
            num(row, c++, nz(u.getHighCount()), s);
            num(row, c++, nz(u.getMediumCount()), s);
            num(row, c++, nz(u.getLowCount()), s);
            cell(row, c++, u.isReviewed() ? "검토완료" : "검토대기", u.isReviewed() ? s.ok : s.warn);
            cell(row, c++, u.getReviewComment() == null ? "" : u.getReviewComment(), s.tdWrap);
            cell(row, c++, fmt(u.getReviewedAt()), s.td);
            cell(row, c++, fmt(u.getCreatedAt()), s.td);
        }
        if (r == 1) {
            cell(sh.createRow(r), 0, "이번 달 등록된 접속 로그가 없습니다.", s.muted);
        }
        widths(sh, 18, 38, 13, 10, 8, 7, 7, 7, 7, 10, 36, 16, 16);
        sh.createFreezePane(0, 1);
        sh.setAutoFilter(new CellRangeAddress(0, Math.max(r - 1, 0), 0, h.length - 1));
    }

    // ------------------------------------------------------------------ 4. 탐지 내역

    private void detectionSheet(XSSFWorkbook wb, Styles s, String ym, Set<Long> active) {
        Sheet sh = wb.createSheet("4.탐지 내역");
        String[] h = {"시스템", "위험도", "룰 코드", "룰 이름", "줄 번호", "로그 내용", "검토 상태", "조치 내용", "검토일시"};
        header(sh.createRow(0), h, s);
        int r = 1;
        List<Object[]> rows = active.isEmpty() ? List.of()
                : detectionRepository.findForMonthAndSystems(ym, active, PageRequest.of(0, MAX_DETECTION_ROWS));
        for (Object[] o : rows) {
            SecLogDetection x = (SecLogDetection) o[0];
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, (String) o[1], s.td);
            cell(row, c++, x.getSeverity().getLabel(), sevStyle(s, x.getSeverity().name()));
            cell(row, c++, x.getRuleCode(), s.td);
            cell(row, c++, x.getRuleName(), s.td);
            num(row, c++, nz(x.getLineNumber()), s);
            cell(row, c++, x.getLogContent(), s.tdSmall);
            cell(row, c++, x.getReviewStatus().getLabel(), x.getReviewStatus().name().equals("PENDING") ? s.warn : s.ok);
            cell(row, c++, x.getActionContent() == null ? "" : x.getActionContent(), s.tdWrap);
            cell(row, c++, fmt(x.getReviewedAt()), s.td);
        }
        if (r == 1) {
            cell(sh.createRow(r), 0, "이번 달 탐지 내역이 없습니다.", s.muted);
        } else if (rows.size() >= MAX_DETECTION_ROWS) {
            cell(sh.createRow(r + 1), 0, "※ 최대 " + MAX_DETECTION_ROWS + "건까지 출력했습니다. 전체 내역은 화면에서 확인하세요.", s.muted);
        }
        widths(sh, 16, 8, 24, 30, 8, 80, 12, 30, 16);
        sh.createFreezePane(0, 1);
        sh.setAutoFilter(new CellRangeAddress(0, Math.max(r - 1, 0), 0, h.length - 1));
    }

    // ------------------------------------------------------------------ 5. 특권 계정 활동

    private void privSheet(XSSFWorkbook wb, Styles s, DashboardResponse d) {
        Sheet sh = wb.createSheet("5.특권 계정 활동");
        String[] h = {"시스템", "계정", "로그인 성공", "로그인 실패", "업무시간 외", "외부 IP", "최종 접속", "위험 표시"};
        header(sh.createRow(0), h, s);
        int r = 1;
        for (DashboardResponse.PrivAccount p : d.getPrivAccounts()) {
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, p.getSystemName(), s.td);
            cell(row, c++, p.getAccountId(), s.tdBold);
            num(row, c++, p.getSuccessCount(), s);
            num(row, c++, p.getFailCount(), s);
            num(row, c++, p.getOffHourCount(), s);
            num(row, c++, p.getExternalIpCount(), s);
            cell(row, c++, p.getLastSeen() == null ? "-" : p.getLastSeen(), s.td);
            cell(row, c++, flags(p.getRiskFlags()), s.td);
        }
        if (r == 1) {
            cell(sh.createRow(r), 0, "이번 달 특권/공용 계정(root, admin, administrator, sa 등) 접속 기록이 없습니다.", s.muted);
        }
        r += 2;
        cell(sh.createRow(r), 0, "특권/공용 계정 기준: root, admin, administrator, sa, sys, system, oracle, postgres, dba, superuser, guest", s.muted);
        widths(sh, 18, 18, 11, 11, 11, 9, 20, 40);
        sh.createFreezePane(0, 1);
    }

    // ------------------------------------------------------------------ util

    private static int collectRate(DashboardResponse d) {
        int ok = 0, due = 0;
        for (DashboardResponse.CalendarRow c : d.getCalendar()) {
            ok += c.getOkDays() + c.getEmptyDays();
            due += c.getDueDays();
        }
        return due == 0 ? 0 : Math.round(ok * 100f / due);
    }

    private static String flags(List<String> f) {
        if (f == null || f.isEmpty()) {
            return "";
        }
        List<String> l = new ArrayList<>();
        f.forEach(x -> l.add(FLAG.getOrDefault(x, x)));
        return String.join(", ", l);
    }

    private static String statusLabel(String st) {
        return switch (st == null ? "NONE" : st) {
            case "REVIEWED" -> "검토완료";
            case "ANALYZED" -> "검토대기";
            default -> "미등록";
        };
    }

    private static CellStyle statusStyle(Styles s, String st) {
        return "REVIEWED".equals(st) ? s.ok : "ANALYZED".equals(st) ? s.warn : s.bad;
    }

    private static CellStyle sevStyle(Styles s, String sev) {
        return switch (sev) {
            case "CRITICAL", "HIGH" -> s.bad;
            case "MEDIUM" -> s.warn;
            default -> s.tdCenter;
        };
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DT);
    }

    private static void cell(Row row, int col, String v, CellStyle st) {
        Cell c = row.createCell(col);
        c.setCellValue(v == null ? "" : (v.length() > 32_000 ? v.substring(0, 32_000) : v));
        c.setCellStyle(st);
    }

    private static void num(Row row, int col, long v, Styles s) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(s.num);
    }

    private static void header(Row row, String[] h, Styles s) {
        row.setHeightInPoints(22);
        for (int i = 0; i < h.length; i++) {
            cell(row, i, h[i], s.th);
        }
    }

    /** 인쇄: A4, 가로(요약은 세로), 너비 1페이지 맞춤, 머리글 반복 */
    private static void printSetup(Sheet sh, boolean portrait) {
        org.apache.poi.ss.usermodel.PrintSetup ps = sh.getPrintSetup();
        ps.setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
        ps.setLandscape(!portrait);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
        sh.setFitToPage(true);
        sh.setHorizontallyCenter(true);
        sh.setMargin(Sheet.LeftMargin, 0.4);
        sh.setMargin(Sheet.RightMargin, 0.4);
        sh.getFooter().setCenter("&P / &N");
        if (!portrait) {
            int head = sh.getSheetName().startsWith("2.") ? 1 : 0;
            sh.setRepeatingRows(new CellRangeAddress(head, head, -1, -1));
        }
    }

    private static void widths(Sheet sh, int... w) {
        for (int i = 0; i < w.length; i++) {
            sh.setColumnWidth(i, w[i] * 256);
        }
    }

    /** 셀 스타일 모음 (맑은 고딕) */
    private static final class Styles {
        final CellStyle title, section, muted, th, thCenter, td, tdBold, tdCenter, tdWrap, tdSmall, num;
        final CellStyle ok, warn, bad, calOk, calEmpty, calFail, calMiss, calNa;

        Styles(XSSFWorkbook wb) {
            title = base(wb, 16, true, null, false);
            section = base(wb, 12, true, null, false);
            muted = base(wb, 9, false, null, false);
            ((XSSFCellStyle) muted).getFont().setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            th = bordered(wb, 10, true, rgb(238, 242, 255));
            thCenter = bordered(wb, 9, true, rgb(238, 242, 255));
            thCenter.setAlignment(HorizontalAlignment.CENTER);
            td = bordered(wb, 10, false, null);
            tdBold = bordered(wb, 10, true, null);
            tdCenter = bordered(wb, 10, false, null);
            tdCenter.setAlignment(HorizontalAlignment.CENTER);
            tdWrap = bordered(wb, 10, false, null);
            tdWrap.setWrapText(true);
            tdSmall = bordered(wb, 9, false, null);
            num = bordered(wb, 10, false, null);
            num.setAlignment(HorizontalAlignment.RIGHT);
            num.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            ok = colored(wb, rgb(220, 252, 231), rgb(22, 101, 52));
            warn = colored(wb, rgb(254, 243, 199), rgb(146, 64, 14));
            bad = colored(wb, rgb(254, 226, 226), rgb(153, 27, 27));
            calOk = colored(wb, rgb(187, 247, 208), rgb(22, 101, 52));
            calEmpty = colored(wb, rgb(220, 252, 231), rgb(134, 239, 172));
            calFail = colored(wb, rgb(254, 202, 202), rgb(185, 28, 28));
            calMiss = colored(wb, rgb(254, 240, 138), rgb(133, 77, 14));
            calNa = colored(wb, rgb(248, 250, 252), rgb(148, 163, 184));
            for (CellStyle c : List.of(calOk, calEmpty, calFail, calMiss, calNa)) {
                c.setFont(fontOf(wb, 8, true, null));
            }
        }

        private static XSSFColor rgb(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }

        private static Font fontOf(XSSFWorkbook wb, int size, boolean bold, XSSFColor color) {
            org.apache.poi.xssf.usermodel.XSSFFont f = wb.createFont();
            f.setFontName(FONT);
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            if (color != null) {
                f.setColor(color);
            }
            return f;
        }

        private static CellStyle base(XSSFWorkbook wb, int size, boolean bold, XSSFColor fill, boolean border) {
            XSSFCellStyle c = wb.createCellStyle();
            c.setFont(fontOf(wb, size, bold, null));
            c.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fill != null) {
                c.setFillForegroundColor(fill);
                c.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (border) {
                c.setBorderTop(BorderStyle.THIN);
                c.setBorderBottom(BorderStyle.THIN);
                c.setBorderLeft(BorderStyle.THIN);
                c.setBorderRight(BorderStyle.THIN);
                XSSFColor line = rgb(203, 213, 225);
                c.setTopBorderColor(line);
                c.setBottomBorderColor(line);
                c.setLeftBorderColor(line);
                c.setRightBorderColor(line);
            }
            return c;
        }

        private static CellStyle bordered(XSSFWorkbook wb, int size, boolean bold, XSSFColor fill) {
            return base(wb, size, bold, fill, true);
        }

        private static CellStyle colored(XSSFWorkbook wb, XSSFColor fill, XSSFColor font) {
            XSSFCellStyle c = (XSSFCellStyle) base(wb, 10, true, fill, true);
            c.setAlignment(HorizontalAlignment.CENTER);
            c.setFont(fontOf(wb, 10, true, font));
            return c;
        }
    }

    static LocalDate today() {
        return SecLogTime.today();
    }
}
