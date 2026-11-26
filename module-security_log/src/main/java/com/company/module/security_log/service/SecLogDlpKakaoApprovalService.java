package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpKakaoApprovalResponse;
import com.company.module.security_log.dto.DlpKakaoApprovalSaveRequest;
import com.company.module.security_log.entity.SecLogDlpKakaoApproval;
import com.company.module.security_log.repository.SecLogDlpKakaoApprovalRepository;
import com.company.module.security_log.support.SecLogTime;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DLP 모니터링 - PC 카카오톡 사용 승인 대장 (전자결재 승인 건, 기존 엑셀 대장 대체)
 * <ul>
 *   <li>신청 상태는 '신청중' 만 사용 (화면 등록·수정으로만 관리, 엑셀 올리기 없음)</li>
 *   <li>상태(사용 중·7일 내 만료·만료·시작 전)·최신여부는 오늘 날짜로 계산</li>
 *   <li>사람 맞추기 = 부서 + 이름 (띄어쓰기·대소문자 무시, 괄호 안 표기는 있어도 없어도 같은 부서로 봄)</li>
 *   <li>등록·수정: ADMIN, MANAGER / 삭제: ADMIN / 조회·엑셀 내려받기: 전원</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDlpKakaoApprovalService {

    public static final int EXPIRING_DAYS = 7;

    private final SecLogDlpKakaoApprovalRepository repository;

    // ================================================================== 조회

    public DlpKakaoApprovalResponse getList() {
        LocalDate today = SecLogTime.now().toLocalDate();
        List<SecLogDlpKakaoApproval> all = active();
        Map<String, Long> latest = latestIds(all);
        long[] c = new long[4];
        List<DlpKakaoApprovalResponse.Row> rows = new ArrayList<>();
        for (SecLogDlpKakaoApproval a : all) {
            String st = state(a, today);
            switch (st) {
                case "ACTIVE" -> c[0]++;
                case "EXPIRING" -> c[1]++;
                case "EXPIRED" -> c[2]++;
                default -> c[3]++;
            }
            rows.add(DlpKakaoApprovalResponse.Row.builder()
                    .kakaoApprovalId(a.getKakaoApprovalId()).divisionName(a.getDivisionName()).deptName(a.getDeptName())
                    .userName(a.getUserName()).startDate(a.getStartDate()).endDate(a.getEndDate()).applyStatus(a.getApplyStatus())
                    .approvalDocNo(a.getApprovalDocNo()).reason(a.getReason())
                    .state(st).stateLabel(stateLabel(st)).daysLeft(ChronoUnit.DAYS.between(today, a.getEndDate()))
                    .latestYn(a.getKakaoApprovalId().equals(latest.get(personKey(a.getDeptName(), a.getUserName()))) ? "Y" : "N")
                    .createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt())
                    .build());
        }
        return DlpKakaoApprovalResponse.builder().today(today)
                .activeCount(c[0]).expiringCount(c[1]).expiredCount(c[2]).beforeCount(c[3]).rows(rows).build();
    }

    /** 삭제되지 않은 대장 전체 ('신청중' 만) */
    public List<SecLogDlpKakaoApproval> active() {
        return repository.findByDeletedYnOrderByEndDateDescKakaoApprovalIdDesc(SecLogConstants.DELETED_N).stream()
                .filter(a -> SecLogDlpKakaoApproval.STATUS_APPLY.equals(a.getApplyStatus())).toList();
    }

    /**
     * 부서별 사용 중 인원 (카카오톡 사용자 현황 '부서별 사용 중 현황' 과 같은 기준)
     * - 사용 중 = today 기준 신청 기간 안 (7일 내 만료 포함), 같은 부서·이름 = 1명
     * @return 부서명 → 인원 (인원 많은 순, 같으면 부서명 순)
     */
    public Map<String, Long> activeByDept(LocalDate today) {
        Map<String, SecLogDlpKakaoApproval> people = new HashMap<>();
        for (SecLogDlpKakaoApproval a : active()) {
            String st = state(a, today);
            if ("ACTIVE".equals(st) || "EXPIRING".equals(st)) {
                people.merge(personKey(a.getDeptName(), a.getUserName()), a,
                        (x, y) -> x.getEndDate().isAfter(y.getEndDate()) ? x : y);
            }
        }
        Map<String, Long> cnt = new HashMap<>();
        people.values().forEach(a -> cnt.merge(a.getDeptName(), 1L, Long::sum));
        Map<String, Long> out = new LinkedHashMap<>();
        cnt.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    static String state(SecLogDlpKakaoApproval a, LocalDate today) {
        if (today.isBefore(a.getStartDate())) {
            return "BEFORE";
        }
        if (today.isAfter(a.getEndDate())) {
            return "EXPIRED";
        }
        return ChronoUnit.DAYS.between(today, a.getEndDate()) <= EXPIRING_DAYS ? "EXPIRING" : "ACTIVE";
    }

    private static String stateLabel(String st) {
        return switch (st) {
            case "ACTIVE" -> "사용 중";
            case "EXPIRING" -> "7일 내 만료";
            case "EXPIRED" -> "만료";
            default -> "시작 전";
        };
    }

    /** 같은 사람(부서+이름) 중 종료일이 가장 늦은 건 (같으면 나중에 등록한 건) */
    private static Map<String, Long> latestIds(List<SecLogDlpKakaoApproval> all) {
        Map<String, SecLogDlpKakaoApproval> best = new HashMap<>();
        for (SecLogDlpKakaoApproval a : all) {
            best.merge(personKey(a.getDeptName(), a.getUserName()), a, (x, y) -> {
                int c = x.getEndDate().compareTo(y.getEndDate());
                return c > 0 || (c == 0 && x.getKakaoApprovalId() > y.getKakaoApprovalId()) ? x : y;
            });
        }
        Map<String, Long> out = new HashMap<>();
        best.forEach((k, v) -> out.put(k, v.getKakaoApprovalId()));
        return out;
    }

    // ================================================================== 사전승인 자동 판정

    /**
     * 판정 결과
     * @param approval 승인 / 미승인 / null(확인 필요)
     * @param reason   대장 사유 (승인일 때)
     * @param note     판정 근거 한 줄
     * @param check    확인 필요 (같은 이름이 다른 부서에만 있음)
     * @param confirm  담당자 확인 필요 (승인 기간 밖 사용 / 같은 이름이 다른 부서에만 있음)
     */
    public record Match(String approval, String reason, String note, boolean check, boolean confirm) {
    }

    /**
     * 카카오톡 사용자 1명의 사전승인 판정
     * - 부서+이름이 맞는 '신청중' 건 중 사용한 날(없으면 그 달)이 모두 신청 기간 안 → 승인
     * - 맞는 건이 있지만 기간 밖 사용 → 미승인 / 같은 이름이 다른 부서에만 있음 → 확인 필요 / 없음 → 미승인
     */
    public static Match match(List<SecLogDlpKakaoApproval> ledger, String dept, String name, List<LocalDate> usedDays, YearMonth ym) {
        if (!StringUtils.hasText(name)) {
            return new Match("미승인", null, "이름 없음 - 대장과 맞출 수 없음", false, false);
        }
        String n = norm(name);
        List<SecLogDlpKakaoApproval> sameName = ledger.stream().filter(a -> norm(a.getUserName()).equals(n)).toList();
        List<SecLogDlpKakaoApproval> mine = sameName.stream().filter(a -> sameDept(a.getDeptName(), dept)).toList();
        if (mine.isEmpty()) {
            if (!sameName.isEmpty()) {
                String others = String.join(", ", sameName.stream().map(SecLogDlpKakaoApproval::getDeptName).distinct().toList());
                return new Match(null, null, "확인 필요 - 대장에 같은 이름이 다른 부서(" + others + ")로 있음", true, true);
            }
            return new Match("미승인", null, "승인 대장에 없음", false, false);
        }
        List<LocalDate> days = usedDays.isEmpty() ? List.of(ym.atDay(1), ym.atEndOfMonth()) : usedDays;
        long outside = days.stream().filter(d -> mine.stream().noneMatch(a -> !d.isBefore(a.getStartDate()) && !d.isAfter(a.getEndDate()))).count();
        SecLogDlpKakaoApproval best = mine.stream().max(Comparator.comparing(SecLogDlpKakaoApproval::getEndDate)).get();
        String period = best.getStartDate() + " ~ " + best.getEndDate();
        if (outside == 0) {
            return new Match("승인", best.getReason(), "승인 대장 " + period, false, false);
        }
        return new Match("미승인", null, usedDays.isEmpty() ? "승인 기간이 그 달 전체를 덮지 않음 (대장 " + period + ")"
                : "승인 기간 밖 사용 " + outside + "일 (대장 " + period + ")", false, true);
    }

    static String personKey(String dept, String name) {
        return deptNorm(dept) + "|" + norm(name);
    }

    private static String norm(String v) {
        return v == null ? "" : v.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 괄호 안 표기 제거 (예: SCM팀(PS) → scm팀) */
    private static String deptNorm(String v) {
        return norm(v == null ? null : v.replaceAll("\\(.*?\\)|\\[.*?\\]", ""));
    }

    static boolean sameDept(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        return x.equals(y) || deptNorm(a).equals(deptNorm(b));
    }

    // ================================================================== 등록 · 수정 · 삭제

    @Transactional
    public Long create(DlpKakaoApprovalSaveRequest r, Long userId) {
        checkPeriod(r.getStartDate(), r.getEndDate());
        return repository.save(SecLogDlpKakaoApproval.builder()
                .divisionName(blank(r.getDivisionName())).deptName(r.getDeptName().trim()).userName(r.getUserName().trim())
                .startDate(r.getStartDate()).endDate(r.getEndDate())
                .approvalDocNo(blank(r.getApprovalDocNo())).reason(blank(r.getReason())).createdBy(userId).build()).getKakaoApprovalId();
    }

    @Transactional
    public void update(Long id, DlpKakaoApprovalSaveRequest r, Long userId) {
        checkPeriod(r.getStartDate(), r.getEndDate());
        find(id).update(blank(r.getDivisionName()), r.getDeptName().trim(), r.getUserName().trim(), r.getStartDate(), r.getEndDate(),
                blank(r.getApprovalDocNo()), blank(r.getReason()), userId);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        find(id).delete(userId);
    }

    private SecLogDlpKakaoApproval find(Long id) {
        return repository.findByKakaoApprovalIdAndDeletedYn(id, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void checkPeriod(LocalDate s, LocalDate e) {
        if (s == null || e == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신청 시작일과 종료일을 입력해 주세요.");
        }
        if (e.isBefore(s)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신청 종료일이 시작일보다 앞입니다.");
        }
    }

    // ================================================================== 엑셀 내려받기

    public record ExcelFile(String fileName, byte[] content) {
    }

    /**
     * 승인 대장 엑셀 내려받기 (화면과 같은 거르기·정렬)
     * - state: ACTIVE / EXPIRING / EXPIRED / BEFORE (없으면 전체), latestOnly: 최신 건만, q: 사업부·부서·이름·문서번호 검색
     */
    public ExcelFile export(String state, boolean latestOnly, String q) {
        DlpKakaoApprovalResponse d = getList();
        String kw = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<DlpKakaoApprovalResponse.Row> rows = d.getRows().stream()
                .filter(r -> !StringUtils.hasText(state) || state.equals(r.getState()))
                .filter(r -> !latestOnly || "Y".equals(r.getLatestYn()))
                .filter(r -> kw.isEmpty() || java.util.stream.Stream.of(r.getDivisionName(), r.getDeptName(), r.getUserName(), r.getApprovalDocNo())
                        .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(kw)))
                .sorted(Comparator.comparing(DlpKakaoApprovalResponse.Row::getEndDate).reversed()
                        .thenComparing(DlpKakaoApprovalResponse.Row::getStartDate, Comparator.reverseOrder())
                        .thenComparing(DlpKakaoApprovalResponse.Row::getKakaoApprovalId, Comparator.reverseOrder()))
                .toList();
        String[] head = {"사업부", "부서", "이름", "신청 시작일", "신청 종료일", "신청 상태", "최신여부", "상태", "남은 일수", "전자결재 문서번호", "사유"};
        int[] width = {16, 18, 12, 13, 13, 10, 9, 12, 10, 20, 40};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("카카오톡 사용 승인 대장");
            Font titleFont = font(wb, 14, true);
            Font headFont = font(wb, 10, true);
            Font bodyFont = font(wb, 10, false);
            CellStyle title = wb.createCellStyle();
            title.setFont(titleFont);
            CellStyle sub = wb.createCellStyle();
            sub.setFont(bodyFont);
            CellStyle hd = boxed(wb, headFont, HorizontalAlignment.CENTER);
            hd.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hd.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle txt = boxed(wb, bodyFont, HorizontalAlignment.LEFT);
            CellStyle mid = boxed(wb, bodyFont, HorizontalAlignment.CENTER);

            sh.createRow(0).createCell(0).setCellValue("카카오톡 사용 승인 대장");
            sh.getRow(0).getCell(0).setCellStyle(title);
            StringBuilder cond = new StringBuilder("오늘(" + d.getToday() + ") 기준 · 신청 상태 '신청중' 건만 · ")
                    .append(StringUtils.hasText(state) ? stateLabel(state) : "상태 전체")
                    .append(latestOnly ? " · 최신 건만" : "");
            if (!kw.isEmpty()) {
                cond.append(" · 검색 '").append(q.trim()).append("'");
            }
            cond.append(" · ").append(rows.size()).append("건");
            sh.createRow(1).createCell(0).setCellValue(cond.toString());
            sh.getRow(1).getCell(0).setCellStyle(sub);

            Row hr = sh.createRow(3);
            for (int i = 0; i < head.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(head[i]);
                c.setCellStyle(hd);
                sh.setColumnWidth(i, width[i] * 256);
            }
            int n = 4;
            for (DlpKakaoApprovalResponse.Row r : rows) {
                Row x = sh.createRow(n++);
                String[] v = {nz(r.getDivisionName()), r.getDeptName(), r.getUserName(), String.valueOf(r.getStartDate()),
                        String.valueOf(r.getEndDate()), r.getApplyStatus(), r.getLatestYn(), r.getStateLabel(),
                        r.getDaysLeft() >= 0 ? r.getDaysLeft() + "일" : "-", nz(r.getApprovalDocNo()), nz(r.getReason())};
                for (int i = 0; i < v.length; i++) {
                    Cell c = x.createCell(i);
                    c.setCellValue(v[i]);
                    c.setCellStyle(i == 0 || i == 1 || i == 2 || i == 9 || i == 10 ? txt : mid);
                }
            }
            if (rows.isEmpty()) {
                sh.createRow(n).createCell(0).setCellValue("조건에 맞는 건 없음");
            }
            sh.createFreezePane(0, 4);
            sh.setAutoFilter(new CellRangeAddress(3, Math.max(3, n - 1), 0, head.length - 1));
            wb.write(out);
            return new ExcelFile("카카오톡_사용_승인_대장_" + d.getToday() + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "엑셀 파일을 만들지 못했습니다.");
        }
    }

    private static Font font(XSSFWorkbook wb, int size, boolean bold) {
        Font f = wb.createFont();
        f.setFontName("맑은 고딕");
        f.setFontHeightInPoints((short) size);
        f.setBold(bold);
        return f;
    }

    private static CellStyle boxed(XSSFWorkbook wb, Font f, HorizontalAlignment align) {
        CellStyle c = wb.createCellStyle();
        c.setFont(f);
        c.setAlignment(align);
        c.setVerticalAlignment(VerticalAlignment.CENTER);
        c.setBorderTop(BorderStyle.THIN);
        c.setBorderBottom(BorderStyle.THIN);
        c.setBorderLeft(BorderStyle.THIN);
        c.setBorderRight(BorderStyle.THIN);
        c.setWrapText(true);
        return c;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String blank(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

}
