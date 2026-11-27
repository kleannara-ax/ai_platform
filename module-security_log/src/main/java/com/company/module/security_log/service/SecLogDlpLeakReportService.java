package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpReportDataResponse;
import com.company.module.security_log.dto.OriginalFileResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.sl.usermodel.Insets2D;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TableCell.BorderEdge;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;

/**
 * DLP 모니터링 보고서 PPT (8장, 16:9, 흰 바탕 + 포인트 색 1개 디자인 - 보고서 작성 탭 내용만)
 * <ol>
 *   <li>표지 (보고 기간 · 보고 구분 · 대상 시스템 · 작성 부서)</li>
 *   <li>목차 (쪽 번호는 실제 쪽 기준으로 계산)</li>
 *   <li>01 Removable Drive (외장하드 및 USB) 사용 현황</li>
 *   <li>02 PC 카카오톡 사용 현황</li>
 *   <li>03 Privacy-I 재설치 인원 현황</li>
 *   <li>04 Privacy-I 장기 미접속 인원 현황</li>
 *   <li>05 생성형 AI 사용현황</li>
 *   <li>06 생성형 AI 업로드 파일 내역</li>
 * </ol>
 * - 01·04 숫자는 카드, 표는 가로 얇은 선 / 02 는 미승인 사용자만
 * - 목록이 한 장에 넘치면 같은 제목 '(계속)' 장을 이어서 만듦
 * - 내려받을 때마다 생성 (저장하지 않음), 모든 글자에 한글 글꼴(맑은 고딕, 동아시아 글꼴 포함) 지정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDlpLeakReportService {

    private static final String FONT = "맑은 고딕";
    private static final int W = 960;
    private static final int H = 540;
    private static final Color ACCENT = new Color(0x4F, 0x46, 0xE5);
    private static final Color ACCENT_SOFT = new Color(0xEE, 0xF2, 0xFF);
    private static final Color ACCENT_PALE = new Color(0xE0, 0xE7, 0xFF);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color GRAY = new Color(0x6B, 0x72, 0x80);
    private static final Color LINE = new Color(0xE5, 0xE7, 0xEB);
    private static final Color SOFT = new Color(0xF9, 0xFA, 0xFB);
    private static final Color RED = new Color(0xDC, 0x26, 0x26);
    private static final Color RED_SOFT = new Color(0xFE, 0xF2, 0xF2);
    private static final Color RED_LINE = new Color(0xFE, 0xCA, 0xCA);
    private static final int LIST_ROWS = 15;

    private final SecLogDlpMonitorReportService dataService;

    private record Toc(String no, String title, XSLFSlide first) {
    }

    public OriginalFileResponse build(String targetYm) {
        DlpReportDataResponse d = dataService.getData(targetYm);
        // ① 유출 로그 또는 ② Privacy-I Agent 현황(03·04 파일 이름) 중 하나만 있어도 만듦
        boolean hasAgent = (d.getReinstall() != null && d.getReinstall().getFileName() != null)
                || (d.getOffline() != null && d.getOffline().getFileName() != null);
        if (d.getUpload() == null && !hasAgent) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    ym(targetYm) + " 올린 파일이 없습니다. 유출 로그 또는 Privacy-I Agent 현황 중 하나를 먼저 올려 주세요.");
        }
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension(W, H));
            String header = "DLP 모니터링 보고서 | " + targetYm.substring(0, 4) + ". " + targetYm.substring(4)
                    + (d.getCover().getWriterDept() != null ? " | " + d.getCover().getWriterDept() : "");
            cover(ppt, d);
            XSLFSlide toc = page(ppt, "목차", header);
            List<Toc> sections = new ArrayList<>();
            sections.add(new Toc("01", "Removable Drive (외장하드 및 USB) 사용 현황", usb(ppt, d, header)));
            sections.add(new Toc("02", "PC 카카오톡 사용 현황", kakao(ppt, d, header)));
            sections.add(new Toc("03", "Privacy-I 재설치 인원 현황", reinstall(ppt, d, header)));
            sections.add(new Toc("04", "Privacy-I 장기 미접속 인원 현황", offline(ppt, d, header)));
            sections.add(new Toc("05", "생성형 AI 사용현황", aiUsage(ppt, d, header)));
            sections.add(new Toc("06", "생성형 AI 업로드 파일 내역", aiFiles(ppt, d, header)));
            fillToc(ppt, toc, sections);

            ppt.write(out);
            byte[] bytes = out.toByteArray();
            String name = "DLP_모니터링_보고서_" + targetYm + ".pptx";
            return OriginalFileResponse.builder().uploadId(d.getUpload() == null ? null : d.getUpload().getLeakUploadId()).fileName(name)
                    .fileSize(bytes.length).contentBase64(Base64.getEncoder().encodeToString(bytes)).build();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "보고서를 만들지 못했습니다.");
        }
    }

    // ================================================================== 1. 표지 · 2. 목차

    private void cover(XMLSlideShow ppt, DlpReportDataResponse d) {
        XSLFSlide s = ppt.createSlide();
        rect(s, 0, 0, 14, H, ACCENT);
        rect(s, 80, 150, 48, 4, ACCENT);
        text(s, "DLP 모니터링 보고서", 80, 170, 800, 64, 40, true, INK, TextParagraph.TextAlign.LEFT);
        DlpReportDataResponse.Cover c = d.getCover();
        String[][] info = {{"보고 기간", c.getReportPeriod()}, {"보고 구분", c.getReportType()},
                {"대상 시스템", c.getTargetSystem()}, {"작성 부서", c.getWriterDept()}};
        for (int i = 0; i < info.length; i++) {
            int x = 80 + (i % 2) * 404;
            int y = 290 + (i / 2) * 78;
            box(s, x, y, 388, 64, SOFT, LINE);
            text(s, info[i][0], x + 18, y + 12, 350, 16, 10, false, GRAY, TextParagraph.TextAlign.LEFT);
            text(s, nz(info[i][1]), x + 18, y + 32, 350, 22, 14, true, INK, TextParagraph.TextAlign.LEFT);
        }
    }

    private void fillToc(XMLSlideShow ppt, XSLFSlide toc, List<Toc> sections) {
        int y = 86;
        for (Toc t : sections) {
            int page = ppt.getSlides().indexOf(t.first()) + 1;
            text(toc, t.no(), 70, y, 50, 34, 22, true, ACCENT, TextParagraph.TextAlign.LEFT);
            text(toc, t.title(), 130, y + 3, 640, 30, 17, true, INK, TextParagraph.TextAlign.LEFT);
            text(toc, "P. " + page, 800, y + 6, 90, 24, 11, false, GRAY, TextParagraph.TextAlign.RIGHT);
            rect(toc, 70, y + 44, 820, 1, LINE);
            y += 64;
        }
    }

    // ================================================================== 3. Removable Drive

    private XSLFSlide usb(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.Usb u = d.getUsb();
        XSLFSlide s = page(ppt, "Removable Drive (외장하드 및 USB) 사용 현황", header);
        long allowed = u.getAllowedRegistered() + u.getAllowedUnregistered();
        cards(s, 78, new String[][]{
                {"업로드 차단", num(u.getBlocked()) + "건", "0", "차단", "외장하드 및 USB로 업로드 시 차단된 건수"},
                {"등록된 외장하드로 업로드", num(u.getAllowedRegistered()) + "건", "0", "허용", "등록된 외장하드로 업로드 시 허용된 건수"},
                {"등록 안 된 외장하드로 업로드", num(u.getAllowedUnregistered()) + "건", "2", "허용", "등록되지 않은 외장하드로 업로드된 건수"},
                {"외장하드·USB 업로드 전체", num(u.getTotal()) + "건", "1", "차단 " + num(u.getBlocked()) + "건 · 허용 " + num(allowed) + "건",
                        "외장하드 및 USB 사용 건의 차단·허용 건수 합계"}});

        List<DlpReportDataResponse.PersonRow> users = u.getUnregUsers();
        List<String[]> list = new ArrayList<>();
        for (DlpReportDataResponse.PersonRow p : users) {
            list.add(new String[]{nz(p.getDeptName()), person(p.getUserName(), p.getUserId()), num(p.getCount()), nz(p.getValue1()), nz(p.getValue2())});
        }
        String[] total = {"합계", "", num(u.getUnregTotalCount()), "", ""};
        String[] head = {"부서", "이름", "건 수", "승인여부", "검토 결과"};
        int[] widths = {140, 130, 70, 90, 450};
        return paged(ppt, s, "Removable Drive (외장하드 및 USB) 사용 현황", header, "등록 안 된 외장하드로 파일 업로드한 인원",
                "(총 " + users.size() + "명)", 228, head, widths, list, total, 10,
                (c, v) -> c == 3 && "미승인".equals(v) ? RED : null);
    }

    // ================================================================== 4. PC 카카오톡

    private XSLFSlide kakao(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.Kakao k = d.getKakao();
        XSLFSlide s = page(ppt, "PC 카카오톡 사용 현황", header);
        label(s, "※ PC 카카오톡 사용 부서 현황", null, 40, 72);
        if (k.getBasisDate() != null) {
            text(s, k.getBasisDate() + " 기준 (카카오톡 사용자 현황)", 300, 74, 240, 16, 8, true, GRAY, TextParagraph.TextAlign.RIGHT);
        }
        List<String[]> depts = new ArrayList<>();
        for (DlpReportDataResponse.DeptRow r : k.getDepts()) {
            depts.add(new String[]{r.getDeptName(), num(r.getUserCount()), r.getHeadcount() == null ? "-" : num(r.getHeadcount())});
        }
        if (depts.isEmpty()) {
            depts.add(new String[]{"해당 없음", "-", "-"});
        }
        String[] head = {"부서", "사용 인원", "부서 인원"};
        int[] widths = {120, 60, 60};
        int perCol = 16;
        List<String[]> left = depts.subList(0, Math.min(perCol, depts.size()));
        table(s, head, widths, left, null, 40, 96, 9, 20, null);
        if (depts.size() > perCol) {
            table(s, head, widths, depts.subList(perCol, Math.min(perCol * 2, depts.size())), null, 300, 96, 9, 20, null);
        }
        String extra = depts.size() > perCol * 2 ? " 외 " + (depts.size() - perCol * 2) + "개 부서" : "";
        int totalY = 96 + 20 * (left.size() + 1) + 8;
        table(s, new String[]{"합계", num(k.getUserTotal()) + "명", k.getHeadcountTotal() > 0 ? num(k.getHeadcountTotal()) + "명" : "-"},
                new int[]{depts.size() > perCol ? 380 : 120, 60, 60}, List.of(), null, 40, totalY, 9, 22, null, true);
        if (!extra.isEmpty()) {
            text(s, "※ 표시 생략" + extra, 40, totalY + 28, 400, 16, 8, false, GRAY, TextParagraph.TextAlign.LEFT);
        }

        List<DlpReportDataResponse.PersonRow> unapproved = k.getUsers().stream().filter(p -> !"승인".equals(p.getValue1())).toList();
        label(s, "※ 미승인 사용자 현황", "(총 " + unapproved.size() + "명)", 590, 72);
        List<String[]> rows = new ArrayList<>();
        long sum = 0;
        for (DlpReportDataResponse.PersonRow p : unapproved.subList(0, Math.min(16, unapproved.size()))) {
            rows.add(new String[]{nz(p.getDeptName()), person(p.getUserName(), null), num(p.getCount()),
                    p.getValue1() == null ? "확인 필요" : p.getValue1(), nz(p.getValue2())});
        }
        for (DlpReportDataResponse.PersonRow p : unapproved) {
            sum += p.getCount();
        }
        if (rows.isEmpty()) {
            rows.add(new String[]{"해당 없음", "", "", "", ""});
        }
        rows.add(new String[]{"합계", "", num(sum), "", ""});
        table(s, new String[]{"부서", "이름", "건 수", "사전승인", "사유"}, new int[]{70, 55, 40, 55, 110}, rows, null, 590, 96, 8, 20,
                (c, v) -> c == 3 && "미승인".equals(v) ? RED : null, true);
        if (unapproved.size() > 16) {
            text(s, "※ 표시 생략 " + (unapproved.size() - 16) + "명", 590, 96 + 20 * 19 + 6, 330, 16, 8, false, GRAY, TextParagraph.TextAlign.LEFT);
        }
        return s;
    }

    // ================================================================== 5. Privacy-I 재설치

    private XSLFSlide reinstall(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.PrivacyiList p = d.getReinstall();
        XSLFSlide s = page(ppt, "Privacy-I 재설치 인원 현황", header);
        List<String[]> list = new ArrayList<>();
        for (DlpReportDataResponse.PiRow r : p.getRows()) {
            list.add(new String[]{nz(r.getDeptName()), person(r.getUserName(), r.getUserId()), nz(r.getEventAt()), nz(r.getValue2())});
        }
        String[] total = {"합계", p.getRows().size() + "명", "", ""};
        return paged(ppt, s, "Privacy-I 재설치 인원 현황", header, "※ Privacy-I 재설치 인원 리스트", "(총 " + p.getRows().size() + "명)",
                74, new String[]{"부서 이름", "사용자 이름", "설치 일시", "검토내용"},
                new int[]{170, 150, 150, 410}, list, total, 10, null);
    }

    // ================================================================== 6. Privacy-I 장기 미접속

    private XSLFSlide offline(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.PrivacyiList p = d.getOffline();
        XSLFSlide s = page(ppt, "Privacy-I 장기 미접속 인원 현황", header);
        cards(s, 78, new String[][]{{"장기 미접속 (7일 이상)", num(p.getLongOffline()), "1"}, {"조치완료", num(p.getActionDone()), "0"},
                {"휴직 및 퇴사", num(p.getLeaveOrQuit()), "0"}, {"미조치 및 보류", num(p.getPending()), "2"}});
        List<String[]> list = new ArrayList<>();
        for (DlpReportDataResponse.PiRow r : p.getRows()) {
            String act = r.getValue1() != null ? r.getValue1() + (r.getValue2() != null ? " - " + r.getValue2() : "") : nz(r.getValue2());
            list.add(new String[]{String.valueOf(r.getRank()), nz(r.getDeptName()), person(r.getUserName(), r.getUserId()), nz(r.getEventAt()),
                    r.getOfflineDays() == null ? "-" : r.getOfflineDays() + "일", act});
        }
        return paged(ppt, s, "Privacy-I 장기 미접속 인원 현황", header, "Privacy-I 장기 미접속 인원 리스트", "(총 " + p.getRows().size() + "명)",
                186, new String[]{"순위", "부서 이름", "사용자 이름", "최종 오프라인 일시", "미접속일", "조치 내용"},
                new int[]{45, 150, 130, 130, 70, 355}, list, null, 9, null);
    }

    // ================================================================== 7. 생성형 AI 사용현황

    private XSLFSlide aiUsage(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.Ai a = d.getAi();
        XSLFSlide s = page(ppt, "생성형 AI 사용현황", header);
        label(s, "※ 서비스 별 사용현황", null, 40, 72);
        unit(s, "[단위 : 건]", 370, 74);
        List<String[]> svc = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DlpReportDataResponse.CountRow r = i < a.getServices().size() ? a.getServices().get(i) : null;
            svc.add(new String[]{String.valueOf(i + 1), r == null ? "" : r.getName(), r == null ? "" : num(r.getCount())});
        }
        long svcEtc = a.getServices().stream().skip(10).mapToLong(DlpReportDataResponse.CountRow::getCount).sum();
        svc.add(new String[]{"합계", a.getServices().size() > 10 ? "기타 " + (a.getServices().size() - 10) + "개 " + num(svcEtc) + "건 포함" : "", num(a.getEventCount())});
        table(s, new String[]{"순위", "주요 AI 서비스", "업로드 건수"}, new int[]{50, 200, 170}, svc, null, 40, 96, 9, 22, null, true);

        label(s, "※ 부서 별 사용현황", "(상위 10개 부서)", 490, 72);
        unit(s, "[단위 : 건]", 860, 74);
        List<String[]> dep = new ArrayList<>();
        long depSum = 0;
        for (int i = 0; i < 10; i++) {
            DlpReportDataResponse.AiDeptRow r = i < a.getDepts().size() ? a.getDepts().get(i) : null;
            if (r != null) {
                depSum += r.getCount();
            }
            dep.add(new String[]{String.valueOf(i + 1), r == null ? "" : r.getDeptName(), r == null ? "" : num(r.getCount()), r == null ? "" : nz(r.getMainService())});
        }
        dep.add(new String[]{"합계", "", num(depSum), ""});
        table(s, new String[]{"순위", "부서", "접속 건수", "주 사용 서비스"}, new int[]{45, 175, 90, 120}, dep, null, 490, 96, 9, 22, null, true);
        return s;
    }

    // ================================================================== 8. 생성형 AI 업로드 파일 내역

    private XSLFSlide aiFiles(XMLSlideShow ppt, DlpReportDataResponse d, String header) {
        DlpReportDataResponse.Ai a = d.getAi();
        XSLFSlide s = page(ppt, "생성형 AI 업로드 파일 내역", header);
        label(s, "※ 생성형 AI 업로드 파일 내역", "(서비스 이용 빈도 상위 10명)", 40, 72);
        List<String[]> rows = new ArrayList<>();
        for (DlpReportDataResponse.AiUserRow u : a.getUsers()) {
            List<String> files = u.getFiles();
            String f = files.isEmpty() ? "-" : String.join(", ", files.subList(0, Math.min(3, files.size())))
                    + (files.size() > 3 ? " 외 " + (files.size() - 3) + "건" : "");
            rows.add(new String[]{nz(u.getDeptName()), person(u.getUserName(), null), num(u.getCount()), nz(u.getRisk()), cut(f, 140),
                    String.join(", ", u.getServices())});
        }
        if (rows.isEmpty()) {
            rows.add(new String[]{"해당 없음", "", "", "", "", ""});
        }
        table(s, new String[]{"부서", "이름", "건 수", "보안 위험도", "주요 업로드 파일 이름", "사용 서비스"}, new int[]{110, 80, 50, 70, 450, 120},
                rows, null, 40, 96, 9, 30, (c, v) -> c == 3 && "상".equals(v) ? RED : null);
        text(s, "보안 위험도 기준 — 상: 회사명 등 식별정보가 포함된 업무 관련 파일 / 중: 업무 관련 파일이나 회사 식별정보가 없는 경우 / 하: 개인용 또는 회사 업무와 관련성이 낮은 파일",
                40, 470, 880, 30, 8, false, GRAY, TextParagraph.TextAlign.LEFT);
        return s;
    }

    // ================================================================== 목록 쪽 나누기

    /**
     * 목록 표를 쪽마다 LIST_ROWS 행씩 (넘치면 '(계속)' 장), 마지막 장에 합계 행
     *
     * @return 첫 장 (목차 쪽 번호용)
     */
    private XSLFSlide paged(XMLSlideShow ppt, XSLFSlide first, String title, String header, String caption, String count, int y,
                            String[] head, int[] widths, List<String[]> list, String[] total, double size,
                            BiFunction<Integer, String, Color> color) {
        List<String[]> rows = list.isEmpty() ? List.<String[]>of(emptyRow(head.length)) : list;
        int firstCap = Math.max(4, (int) ((H - 60 - y - 30 - (total == null ? 0 : 22)) / 22.0));
        int idx = 0;
        XSLFSlide s = first;
        int top = y;
        boolean firstPage = true;
        while (idx < rows.size()) {
            int cap = firstPage ? Math.min(firstCap, LIST_ROWS) : LIST_ROWS;
            List<String[]> part = new ArrayList<>(rows.subList(idx, Math.min(rows.size(), idx + cap)));
            idx += part.size();
            boolean last = idx >= rows.size();
            if (last && total != null) {
                part.add(total);
            }
            label(s, caption, count + (firstPage ? "" : " (계속)"), 40, top);
            table(s, head, widths, part, null, 40, top + 24, size, 22, color, last && total != null);
            if (!last) {
                s = page(ppt, title + " (계속)", header);
                top = 72;
                firstPage = false;
            }
        }
        return first;
    }

    private static String[] emptyRow(int n) {
        String[] r = new String[n];
        java.util.Arrays.fill(r, "");
        r[0] = "해당 없음";
        return r;
    }

    // ================================================================== 그리기 도구

    private XSLFSlide page(XMLSlideShow ppt, String title, String header) {
        XSLFSlide s = ppt.createSlide();
        rect(s, 40, 22, 5, 24, ACCENT);
        text(s, title, 54, 20, 560, 30, 19, true, INK, TextParagraph.TextAlign.LEFT);
        text(s, header, 600, 27, 320, 18, 9, false, GRAY, TextParagraph.TextAlign.RIGHT);
        rect(s, 40, 58, W - 80, 1, LINE);
        rect(s, 40, H - 34, W - 80, 1, LINE);
        text(s, String.format("%02d", ppt.getSlides().size()), W - 90, H - 28, 50, 20, 10, false, GRAY, TextParagraph.TextAlign.RIGHT);
        return s;
    }

    /** 소제목 (왼쪽 포인트 색 막대 + 굵은 글자, red = 빨간 덧붙임) */
    private void label(XSLFSlide s, String t, String red, int x, int y) {
        rect(s, x, y + 4, 3, 12, ACCENT);
        XSLFTextBox b = s.createTextBox();
        b.setAnchor(new Rectangle2D.Double(x + 9, y, 520, 20));
        b.setInsets(new Insets2D(0, 0, 0, 0));
        XSLFTextParagraph p = b.getTextParagraphs().get(0);
        style(p.addNewTextRun(), t.startsWith("※ ") ? t.substring(2) : t, 11, true, INK);
        if (red != null) {
            style(p.addNewTextRun(), " " + red, 10, true, RED);
        }
    }

    private void unit(XSLFSlide s, String t, int x, int y) {
        text(s, t, x, y, 70, 16, 8, false, GRAY, TextParagraph.TextAlign.RIGHT);
    }

    /**
     * 숫자 카드 한 줄 (값 [이름, 숫자, 색: 0 기본 / 1 포인트 색 / 2 빨강, (숫자 아래 구분: 차단·허용), (이름 아래 괄호 설명)])
     * - 설명·구분이 있으면 카드가 커짐
     */
    private static void cards(XSLFSlide s, int y, String[][] items) {
        int gap = 16;
        int w = (W - 80 - gap * (items.length - 1)) / items.length;
        boolean anyDesc = false;
        for (String[] it : items) {
            anyDesc |= it.length > 4 && it[4] != null;
        }
        int descH = anyDesc ? 26 : 0;
        for (int i = 0; i < items.length; i++) {
            int x = 40 + i * (w + gap);
            String tone = items[i][2];
            boolean accent = "1".equals(tone);
            boolean red = "2".equals(tone);
            String tag = items[i].length > 3 ? items[i][3] : null;
            String desc = items[i].length > 4 ? items[i][4] : null;
            box(s, x, y, w, 84 + descH + (tag != null ? 16 : 0), accent ? ACCENT : red ? RED_SOFT : Color.WHITE, accent ? ACCENT : red ? RED_LINE : LINE);
            text(s, items[i][0], x + 16, y + 12, w - 32, 16, 10.5, true, accent ? Color.WHITE : INK, TextParagraph.TextAlign.LEFT);
            if (desc != null) {
                text(s, "(" + desc + ")", x + 16, y + 30, w - 32, 24, 8, false, accent ? ACCENT_PALE : GRAY, TextParagraph.TextAlign.LEFT);
            }
            text(s, items[i][1], x + 16, y + 34 + descH, w - 32, 34, 26, true, accent ? Color.WHITE : red ? RED : INK, TextParagraph.TextAlign.LEFT);
            if (tag != null) {
                text(s, tag, x + 16, y + 74 + descH, w - 32, 16, 9.5, true, accent ? ACCENT_PALE : red ? RED : GRAY, TextParagraph.TextAlign.LEFT);
            }
        }
    }

    /** 둥근 모서리 상자 */
    private static void box(XSLFSlide s, int x, int y, int w, int h, Color fill, Color line) {
        XSLFAutoShape r = s.createAutoShape();
        r.setShapeType(ShapeType.ROUND_RECT);
        r.setAnchor(new Rectangle2D.Double(x, y, w, h));
        r.setFillColor(fill);
        r.setLineColor(line);
        r.setLineWidth(0.75);
    }

    private static XSLFAutoShape rect(XSLFSlide s, int x, int y, int w, int h, Color fill) {
        XSLFAutoShape r = s.createAutoShape();
        r.setShapeType(ShapeType.RECT);
        r.setAnchor(new Rectangle2D.Double(x, y, w, h));
        r.setFillColor(fill);
        r.setLineColor(null);
        return r;
    }

    private static void text(XSLFSlide s, String t, int x, int y, int w, int h, double size, boolean bold, Color color,
                             TextParagraph.TextAlign align) {
        XSLFTextBox b = s.createTextBox();
        b.setAnchor(new Rectangle2D.Double(x, y, w, h));
        b.setInsets(new Insets2D(0, 0, 0, 0));
        b.setWordWrap(true);
        XSLFTextParagraph p = b.getTextParagraphs().get(0);
        p.setTextAlign(align);
        style(p.addNewTextRun(), t == null ? "" : t, size, bold, color);
    }

    private static void table(XSLFSlide s, String[] head, int[] widths, List<String[]> rows, Object unused, int x, int y, double size,
                              int rowH, BiFunction<Integer, String, Color> color) {
        table(s, head, widths, rows, unused, x, y, size, rowH, color, false);
    }

    /** 표 (머리글 연한 포인트 색, 가로 얇은 선만, lastIsTotal = 마지막 행 합계: 연회색 + 굵게 + 위쪽 진한 선) */
    private static void table(XSLFSlide s, String[] head, int[] widths, List<String[]> rows, Object unused, int x, int y, double size,
                              int rowH, BiFunction<Integer, String, Color> color, boolean lastIsTotal) {
        XSLFTable t = s.createTable();
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        t.setAnchor(new Rectangle2D.Double(x, y, total, rowH));
        XSLFTableRow hr = t.addRow();
        hr.setHeight(rowH);
        boolean headIsTotal = rows.isEmpty() && lastIsTotal;
        for (String h : head) {
            if (headIsTotal) {
                cell(hr.addCell(), h, size, true, SOFT, INK, TextParagraph.TextAlign.CENTER, INK, LINE);
            } else {
                cell(hr.addCell(), h, size, true, ACCENT_SOFT, ACCENT, TextParagraph.TextAlign.CENTER, null, ACCENT);
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            boolean tot = lastIsTotal && i == rows.size() - 1;
            XSLFTableRow tr = t.addRow();
            tr.setHeight(rowH);
            String[] r = rows.get(i);
            for (int c = 0; c < r.length; c++) {
                Color special = !tot && color != null ? color.apply(c, r[c]) : null;
                boolean long_ = widths[c] >= 200;
                cell(tr.addCell(), r[c], size, tot || special != null, tot ? SOFT : Color.WHITE, special != null ? special : INK,
                        long_ && !tot ? TextParagraph.TextAlign.LEFT : TextParagraph.TextAlign.CENTER, tot ? INK : null, LINE);
            }
        }
        for (int i = 0; i < widths.length; i++) {
            t.setColumnWidth(i, widths[i]);
        }
    }

    /** 표 칸 (세로 선 없음, top = 위쪽 선 색(없으면 선 없음), bottom = 아래쪽 선 색) */
    private static void cell(XSLFTableCell c, String v, double size, boolean bold, Color bg, Color fg, TextParagraph.TextAlign align,
                             Color top, Color bottom) {
        c.setFillColor(bg);
        c.setVerticalAlignment(VerticalAlignment.MIDDLE);
        c.setInsets(new Insets2D(2, 6, 2, 6));
        // 세로 선은 칸 바탕색으로 칠해 안 보이게 (선을 지우면 보는 프로그램에 따라 검은 기본 선이 나옴)
        for (BorderEdge e : new BorderEdge[]{BorderEdge.left, BorderEdge.right}) {
            c.setBorderColor(e, bg);
            c.setBorderWidth(e, 0.5);
        }
        c.setBorderColor(BorderEdge.top, top != null ? top : bg);
        c.setBorderWidth(BorderEdge.top, top != null ? 1.0 : 0.5);
        c.setBorderColor(BorderEdge.bottom, bottom);
        c.setBorderWidth(BorderEdge.bottom, bottom == ACCENT ? 1.0 : 0.75);
        c.setWordWrap(true);
        c.clearText();
        XSLFTextParagraph p = c.addNewTextParagraph();
        p.setTextAlign(align);
        style(p.addNewTextRun(), v == null ? "" : v, size, bold, fg);
    }

    /** 글자 + 모양 + 한글(동아시아) 글꼴 */
    private static void style(XSLFTextRun r, String t, double size, boolean bold, Color color) {
        r.setText(t);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontColor(color);
        r.setFontFamily(FONT);
        if (r.getXmlObject() instanceof CTRegularTextRun ct) {
            CTTextCharacterProperties pr = ct.isSetRPr() ? ct.getRPr() : ct.addNewRPr();
            (pr.isSetEa() ? pr.getEa() : pr.addNewEa()).setTypeface(FONT);
            (pr.isSetLatin() ? pr.getLatin() : pr.addNewLatin()).setTypeface(FONT);
            pr.setLang("ko-KR");
            pr.setAltLang("en-US");
        }
    }

    // ================================================================== 표시

    private static String person(String name, String id) {
        if (name == null) {
            return id == null ? "-" : id;
        }
        return id == null ? name : name + " (" + id + ")";
    }

    private static String ym(String ym) {
        return ym != null && ym.length() == 6 ? ym.substring(0, 4) + "년 " + Integer.parseInt(ym.substring(4)) + "월" : String.valueOf(ym);
    }

    private static String num(long n) {
        return String.format("%,d", n);
    }

    private static String nz(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
