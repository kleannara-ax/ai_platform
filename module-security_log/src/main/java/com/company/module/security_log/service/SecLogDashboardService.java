package com.company.module.security_log.service;

import com.company.module.security_log.support.SecLogTime;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.AccessSummaryResponse;
import com.company.module.security_log.dto.DashboardResponse;
import com.company.module.security_log.dto.LogUploadResponse;
import com.company.module.security_log.dto.SystemLogResponse;
import com.company.module.security_log.dto.MonthlyStatusResponse;
import com.company.module.security_log.dto.RuleHitCount;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogAccessSummary;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.repository.SecLogAccessDailyRepository;
import com.company.module.security_log.repository.SecLogAccessSummaryRepository;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ISMS 로그 점검 대시보드 (메뉴 첫 화면)
 * - 로그 등록(월 1회 업로드), 검토 현황, 위험 계정, 미검토, 조치 필요, 할 일 목록
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDashboardService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> FLAG_WEIGHT = List.of("PRIV_ACCOUNT", "IP_DENIED", "EXTERNAL_IP", "FAIL_MANY", "OFF_HOUR", "MULTI_IP");

    private final SecLogMonthlyStatusService monthlyStatusService;
    private final SecLogUploadRepository uploadRepository;
    private final SecLogDetectionRepository detectionRepository;
    private final SecLogAccessSummaryRepository accessSummaryRepository;
    private final SecLogAccessDailyRepository dailyRepository;

    /** 마감 기준: 대상 월 다음 달 이 날짜까지 최종 검토완료 (README 8-3) */
    public static final int CLOSE_DUE_DAY = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public DashboardResponse get(String targetYm) {
        if (targetYm == null || targetYm.isBlank()) {
            targetYm = SecLogTime.yearMonth()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        }
        if (!targetYm.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
        // 모든 집계는 '사용' 중인 점검 대상 시스템만 (점검 대상 시스템 화면의 사용 토글)
        MonthlyStatusResponse st = monthlyStatusService.getStatus(targetYm);
        List<MonthlyStatusResponse.Row> rows = st.getSystems();
        Set<Long> active = activeIds(rows);
        DetSum sum = detectionSummary(targetYm, active);
        int n = st.getSystemCount();

        int accessReg = (int) rows.stream().filter(r -> !"NONE".equals(r.getAccessStatus())).count();

        List<DashboardResponse.RiskAccount> risk = topRisk(targetYm, active);
        DashboardResponse.Aging aging = aging(targetYm, active);
        List<DashboardResponse.Todo> todos = todos(targetYm, rows);
        // 3. 오래 방치된 미검토 (7일 초과) → 할 일 맨 위
        for (DashboardResponse.AgingItem a : aging.getOldest()) {
            if (a.getDays() > 7) {
                todos.add(0, DashboardResponse.Todo.builder().level("DANGER").kind("REVIEW_DELAY")
                        .systemId(a.getSystemId()).systemName(a.getSystemName())
                        .message("미검토 탐지 " + a.getPendingCount() + "건이 " + a.getDays() + "일째 방치")
                        .link("detail.html?id=" + a.getUploadId()).build());
            }
        }
        List<DashboardResponse.UploadRow> uploadRows = uploadRows(targetYm, rows);
        for (DashboardResponse.UploadRow u : uploadRows) {
            if ("CHECK".equals(u.getStatus())) {
                todos.add(DashboardResponse.Todo.builder().level("WARN").kind("LOG_PERIOD_CHECK")
                        .systemId(u.getSystemId()).systemName(u.getSystemName())
                        .message("로그 기간 확인 필요 · " + String.join(", ", u.getCheckReasons()))
                        .link("detail.html?id=" + u.getLatestUploadId()).build());
            }
        }
        List<String> order = List.of("DANGER", "WARN", "INFO");
        todos.sort(Comparator.comparingInt(x -> order.indexOf(x.getLevel())));
        return DashboardResponse.builder()
                .targetYm(targetYm).generatedAt(SecLogTime.now())
                .uploadSummary(uploadSummary(targetYm, uploadRows))
                .uploadRows(uploadRows)
                .systemCount(n).accessRegistered(accessReg).accessReviewed(st.getAccessDoneCount())
                .closeRate(rate(st.getAccessDoneCount(), n))
                .riskAccountCount(rows.stream().mapToLong(MonthlyStatusResponse.Row::getAccessRiskAccounts).sum())
                .pendingDetectionCount(sum.pending()).detectionCount(sum.total())
                .criticalCount(sum.critical()).highCount(sum.high())
                .mediumCount(sum.medium()).lowCount(sum.low())
                .topRiskAccounts(risk.size() > 10 ? risk.subList(0, 10) : risk)
                .todos(todos)
                .topRules(sum.topRules().subList(0, Math.min(5, sum.topRules().size())))
                .systems(rows)
                .closeDueDate(closeDue(targetYm).toString())
                .closeDDay(ChronoUnit.DAYS.between(today(), closeDue(targetYm)))
                .closeRemainCount(closeRemain(rows))
                .closed(n > 0 && st.getAccessDoneCount() == n)
                .calendarDays(calendarDays(targetYm))
                .calendar(calendar(targetYm, rows))
                .aging(aging)
                .privAccounts(privAccounts(targetYm, active))
                .dailyTrend(dailyTrend(targetYm, active))
                .reviews(reviews(targetYm, rows, active))
                .reviewCounts(reviewCounts(targetYm, active))
                .build();
    }

    /** 같은 달 여러 번 수집된 같은 계정은 시스템+계정 기준으로 합산 */
    private List<DashboardResponse.RiskAccount> topRisk(String ym, Set<Long> active) {
        Map<String, long[]> nums = new LinkedHashMap<>();
        Map<String, Set<String>> flags = new LinkedHashMap<>();
        Map<String, Object[]> meta = new LinkedHashMap<>();
        if (active.isEmpty()) {
            return new ArrayList<>();
        }
        for (Object[] o : accessSummaryRepository.findRiskForMonth(ym, active)) {
            SecLogAccessSummary s = (SecLogAccessSummary) o[0];
            String key = o[1] + "|" + s.getAccountId();
            long[] v = nums.computeIfAbsent(key, k -> new long[4]);
            v[0] += s.getSuccessCount();
            v[1] += s.getFailCount();
            v[2] += s.getOffHourCount();
            v[3] += s.getExternalIpCount();
            flags.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(Arrays.asList(s.getRiskFlags().split(",")));
            Object[] m = meta.get(key);
            String last = s.getLastSeen();
            if (m == null || (last != null && (m[3] == null || last.compareTo((String) m[3]) > 0))) {
                meta.put(key, new Object[]{o[1], o[2], o[3], last});
            }
        }
        List<DashboardResponse.RiskAccount> list = new ArrayList<>();
        nums.forEach((key, v) -> {
            Object[] m = meta.get(key);
            List<String> f = flags.get(key).stream().filter(x -> !x.isBlank())
                    .sorted(Comparator.comparingInt(x -> FLAG_WEIGHT.indexOf(x) < 0 ? 99 : FLAG_WEIGHT.indexOf(x))).toList();
            list.add(DashboardResponse.RiskAccount.builder()
                    .systemId((Long) m[0]).systemName((String) m[1]).latestUploadId((Long) m[2]).lastSeen((String) m[3])
                    .accountId(key.substring(key.indexOf('|') + 1))
                    .successCount(v[0]).failCount(v[1]).offHourCount(v[2]).externalIpCount(v[3]).riskFlags(f)
                    .build());
        });
        list.sort(Comparator.comparingInt((DashboardResponse.RiskAccount a) -> score(a.getRiskFlags())).reversed()
                .thenComparing(Comparator.comparingLong(DashboardResponse.RiskAccount::getFailCount).reversed()));
        return list;
    }

    private static int score(List<String> flags) {
        int s = 0;
        for (String f : flags) {
            int i = FLAG_WEIGHT.indexOf(f);
            s += i < 0 ? 1 : (FLAG_WEIGHT.size() - i) * 10;
        }
        return s;
    }

    private List<DashboardResponse.Todo> todos(String ym, List<MonthlyStatusResponse.Row> rows) {
        List<DashboardResponse.Todo> t = new ArrayList<>();
        // 월 1회 업로드: 대상 월이 끝나기 전(이번 달)에는 '미등록'을 참고(INFO)로만 표시
        boolean waiting = !YearMonth.parse(ym, YM).isBefore(YearMonth.from(today()));
        for (MonthlyStatusResponse.Row r : rows) {
            if (r.getAccessRiskAccounts() > 0 && !"REVIEWED".equals(r.getAccessStatus())) {
                t.add(todo("WARN", "PENDING_DETECTION", r, "위험 접속 계정 " + r.getAccessRiskAccounts() + "개 · 탐지 "
                        + r.getDetectionCount() + "건 검토 필요", "detail.html?id=" + r.getLatestUploadId() + "#access"));
            }
            boolean noAccess = "NONE".equals(r.getAccessStatus());
            if (noAccess) {
                t.add(todo(waiting ? "INFO" : "WARN", "NO_DATA", r, "접속·행위 로그 미등록 · 로그 파일 업로드 필요",
                        "upload.html?systemId=" + r.getSystemId() + "&ym=" + ym));
            }
            if (!noAccess && !"REVIEWED".equals(r.getAccessStatus()) && r.getAccessRiskAccounts() == 0) {
                t.add(todo("INFO", "READY_TO_CLOSE", r, "위험 없음 · 최종 검토완료 가능", "detail.html?id=" + r.getLatestUploadId()));
            }
        }
        List<String> order = List.of("DANGER", "WARN", "INFO");
        t.sort(Comparator.comparingInt(x -> order.indexOf(x.getLevel())));
        return t;
    }

    private static DashboardResponse.Todo todo(String level, String kind, MonthlyStatusResponse.Row r, String msg, String link) {
        return DashboardResponse.Todo.builder().level(level).kind(kind).systemId(r.getSystemId())
                .systemName(r.getSystemName()).message(msg).link(link).build();
    }


    // ================================================================== 로그 수집 → 시스템 상세

    /**
     * 대시보드 로그 수집 표의 시스템을 눌렀을 때: 이번 달 ① 접속·행위 로그 ② 관리 계정 목록
     */
    public SystemLogResponse systemLogs(Long systemId, String targetYm) {
        String ym = (targetYm == null || targetYm.isBlank()) ? SecLogTime.yearMonth().format(YM) : targetYm;
        if (!ym.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
        MonthlyStatusResponse st = monthlyStatusService.getStatus(ym);
        MonthlyStatusResponse.Row row = st.getSystems().stream().filter(r -> r.getSystemId().equals(systemId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용 중인 점검 대상 시스템이 아닙니다."));
        DashboardResponse.CalendarRow cal = calendar(ym, List.of(row)).stream().findFirst().orElse(null);

        List<LogUploadResponse> uploads = uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N).stream()
                .filter(u -> systemId.equals(u.getSystemId()))
                .sorted(Comparator.comparing(SecLogUpload::getUploadId).reversed())
                .map(LogUploadResponse::from).toList();

        // 계정별 접속 집계: 같은 달 여러 번 수집된 계정은 합산
        Map<String, long[]> nums = new LinkedHashMap<>();
        Map<String, Set<String>> flags = new LinkedHashMap<>();
        Map<String, String[]> seen = new LinkedHashMap<>();
        Map<String, Set<String>> ips = new LinkedHashMap<>();
        for (Object[] o : accessSummaryRepository.findAllForMonth(ym, Set.of(systemId))) {
            SecLogAccessSummary x = (SecLogAccessSummary) o[0];
            String k = x.getAccountId();
            long[] v = nums.computeIfAbsent(k, z -> new long[5]);
            v[0] += x.getSuccessCount();
            v[1] += x.getFailCount();
            v[2] += x.getOffHourCount();
            v[3] += x.getExternalIpCount();
            v[4] = Math.max(v[4], ((Long) o[3]));
            if (x.getRiskFlags() != null && !x.getRiskFlags().isBlank()) {
                flags.computeIfAbsent(k, z -> new LinkedHashSet<>()).addAll(Arrays.asList(x.getRiskFlags().split(",")));
            }
            if (x.getIpList() != null && !x.getIpList().isBlank()) {
                Set<String> set = ips.computeIfAbsent(k, z -> new LinkedHashSet<>());
                for (String ip : x.getIpList().split(",\\s*")) {
                    if (set.size() < 20 && !ip.contains("외")) {
                        set.add(ip.trim());
                    }
                }
            }
            String[] fl = seen.computeIfAbsent(k, z -> new String[2]);
            if (x.getFirstSeen() != null && (fl[0] == null || x.getFirstSeen().compareTo(fl[0]) < 0)) {
                fl[0] = x.getFirstSeen();
            }
            if (x.getLastSeen() != null && (fl[1] == null || x.getLastSeen().compareTo(fl[1]) > 0)) {
                fl[1] = x.getLastSeen();
            }
        }
        List<AccessSummaryResponse> acc = new ArrayList<>();
        nums.forEach((k, v) -> acc.add(AccessSummaryResponse.builder()
                .uploadId(v[4] == 0 ? null : v[4]).accountId(k).successCount((int) v[0]).failCount((int) v[1]).offHourCount((int) v[2])
                .externalIpCount((int) v[3]).ipCount(ips.getOrDefault(k, Set.of()).size())
                .ipList(String.join(", ", ips.getOrDefault(k, Set.of())))
                .firstSeen(seen.get(k)[0]).lastSeen(seen.get(k)[1])
                .riskFlags(new ArrayList<>(flags.getOrDefault(k, Set.of()))).build()));
        acc.sort(Comparator.comparingInt((AccessSummaryResponse a) -> a.getRiskFlags().isEmpty() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(AccessSummaryResponse::getFailCount).reversed())
                .thenComparing(Comparator.comparingInt(AccessSummaryResponse::getSuccessCount).reversed()));

        return SystemLogResponse.builder()
                .systemId(systemId).systemName(row.getSystemName()).category(row.getCategory()).targetYm(ym)
                .calendar(cal)
                .uploads(uploads).accessAccounts(acc)
                .build();
    }

    // ================================================================== 검토 현황

    private List<DashboardResponse.ReviewRow> reviews(String ym, List<MonthlyStatusResponse.Row> rows, Set<Long> active) {
        Map<Long, Map<String, Long>> det = new LinkedHashMap<>();
        if (!active.isEmpty()) {
            for (Object[] o : detectionRepository.countBySystemAndReview(ym, active)) {
                det.computeIfAbsent((Long) o[0], k -> new LinkedHashMap<>()).put(((ReviewStatus) o[1]).name(), ((Number) o[2]).longValue());
            }
        }
        List<DashboardResponse.ReviewRow> list = new ArrayList<>();
        for (MonthlyStatusResponse.Row r : rows) {
            Map<String, Long> m = det.getOrDefault(r.getSystemId(), Map.of());
            long pending = m.getOrDefault("PENDING", 0L);
            long conf = m.getOrDefault("CONFIRMED", 0L);
            long res = m.getOrDefault("RESOLVED", 0L);
            long fp = m.getOrDefault("FALSE_POSITIVE", 0L);
            long total = pending + conf + res + fp;
            String next;
            String link;
            if ("NONE".equals(r.getAccessStatus())) {
                next = "로그 파일 업로드";
                link = "upload.html?systemId=" + r.getSystemId() + "&ym=" + ym;
            } else if (pending > 0) {
                next = "탐지 " + pending + "건 검토";
                link = "detail.html?id=" + r.getLatestUploadId();
            } else if (!"REVIEWED".equals(r.getAccessStatus())) {
                next = "접속 로그 최종 검토완료";
                link = "detail.html?id=" + r.getLatestUploadId();
            } else {
                next = "마감 완료";
                link = null;
            }
            list.add(DashboardResponse.ReviewRow.builder()
                    .systemId(r.getSystemId()).systemName(r.getSystemName())
                    .accessStatus(r.getAccessStatus()).uploadCount(r.getAccessUploadCount()).latestUploadId(r.getLatestUploadId())
                    .detectionTotal(total).detectionPending(pending).detectionConfirmed(conf).detectionResolved(res)
                    .detectionFalsePositive(fp).detectionRate(total == 0 ? 100 : rate((int) (total - pending), (int) total))
                    .nextAction(next).nextLink(link).build());
        }
        return list;
    }

    private Map<String, Long> reviewCounts(String ym, Set<Long> active) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (ReviewStatus st : ReviewStatus.values()) {
            m.put(st.name(), 0L);
        }
        if (!active.isEmpty()) {
            for (Object[] o : detectionRepository.countBySystemAndReview(ym, active)) {
                m.merge(((ReviewStatus) o[1]).name(), ((Number) o[2]).longValue(), Long::sum);
            }
        }
        return m;
    }

    // ================================================================== 2. 마감 D-day

    static LocalDate closeDue(String ym) {
        return YearMonth.parse(ym, YM).plusMonths(1).atDay(CLOSE_DUE_DAY);
    }

    private static LocalDate today() {
        return SecLogTime.today();
    }

    /** 마감까지 남은 시스템 수: 접속·행위 로그를 최종 검토완료하지 않은 시스템 */
    private static int closeRemain(List<MonthlyStatusResponse.Row> rows) {
        int n = 0;
        for (MonthlyStatusResponse.Row r : rows) {
            if (!"REVIEWED".equals(r.getAccessStatus())) {
                n++;
            }
        }
        return n;
    }

    // ================================================================== 1. 로그 업로드 현황

    /** 로그 기간 확인 기준: 첫 로그일이 이 날짜보다 늦거나, 마지막 로그일이 말일 - 이 일수보다 이르면 확인 필요 */
    static final int PERIOD_EDGE_DAYS = 3;
    /** 일시를 읽지 못한 줄이 전체의 이 비율(%) 이상이면 확인 필요 */
    static final int DATE_UNKNOWN_PERCENT = 10;

    private List<DashboardResponse.UploadRow> uploadRows(String ym, List<MonthlyStatusResponse.Row> rows) {
        YearMonth m = YearMonth.parse(ym, YM);
        Set<Long> ids = activeIds(rows);
        boolean monthOver = m.isBefore(YearMonth.from(today()));
        Map<Long, List<SecLogUpload>> ups = new LinkedHashMap<>();
        uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N).forEach(u -> {
            if (u.getSystemId() != null && ids.contains(u.getSystemId())) {
                ups.computeIfAbsent(u.getSystemId(), k -> new ArrayList<>()).add(u);
            }
        });
        // 대상 월 안의 로그 일자 (활동일, 첫/마지막 일자)
        Map<Long, List<LocalDate>> dates = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] o : dailyRepository.sumBySystemAndDate(ym, ids, m.atDay(1), m.atEndOfMonth().plusDays(1))) {
                if (((Number) o[2]).longValue() > 0) {
                    dates.computeIfAbsent((Long) o[0], k -> new ArrayList<>()).add((LocalDate) o[1]);
                }
            }
        }
        LocalDate end = m.atEndOfMonth();
        List<DashboardResponse.UploadRow> list = new ArrayList<>();
        for (MonthlyStatusResponse.Row r : rows) {
            List<SecLogUpload> us = ups.getOrDefault(r.getSystemId(), List.of());
            DashboardResponse.UploadRow.UploadRowBuilder b = DashboardResponse.UploadRow.builder()
                    .systemId(r.getSystemId()).category(r.getCategory()).systemName(r.getSystemName())
                    .monthDays(m.lengthOfMonth()).checkReasons(List.of()).accountCount(r.getAccessAccountCount());
            if (us.isEmpty()) {
                list.add(b.status(monthOver ? "NONE" : "WAIT").build());
                continue;
            }
            SecLogUpload latest = us.stream().max(Comparator.comparing(SecLogUpload::getCreatedAt)).orElseThrow();
            long lines = us.stream().mapToLong(SecLogUpload::getTotalLineCount).sum();
            boolean unchecked = us.stream().anyMatch(u -> u.getDateUnknownCount() == null);
            long unknown = us.stream().mapToLong(u -> u.getDateUnknownCount() == null ? 0 : u.getDateUnknownCount()).sum();
            long outside = us.stream().mapToLong(u -> u.getOutOfMonthCount() == null ? 0 : u.getOutOfMonthCount()).sum();
            List<LocalDate> ds = dates.getOrDefault(r.getSystemId(), List.of()).stream().sorted().toList();
            LocalDate first = ds.isEmpty() ? null : ds.get(0);
            LocalDate last = ds.isEmpty() ? null : ds.get(ds.size() - 1);

            List<String> reasons = new ArrayList<>();
            if (!unchecked) {
                if (first == null) {
                    reasons.add("대상 월 로그 없음");
                } else {
                    if (first.getDayOfMonth() > 1 + PERIOD_EDGE_DAYS) {
                        reasons.add(first.getMonthValue() + "/" + first.getDayOfMonth() + "부터 시작");
                    }
                    // 끝 확인은 대상 월이 끝난 뒤에만 (진행 중인 달은 아직 다 올 수 없음)
                    if (monthOver && last.isBefore(end.minusDays(PERIOD_EDGE_DAYS))) {
                        reasons.add(last.getMonthValue() + "/" + last.getDayOfMonth() + "까지만 있음");
                    }
                }
                if (lines > 0 && unknown * 100 >= lines * DATE_UNKNOWN_PERCENT) {
                    reasons.add("일시 인식 안 된 줄 " + unknown);
                }
                if (outside > 0) {
                    reasons.add("대상 월 밖 줄 " + outside);
                }
            }
            String status = unchecked ? "UNCHECKED" : reasons.isEmpty() ? "OK" : "CHECK";
            list.add(b.status(status).checkReasons(reasons).fileCount(us.size())
                    .fileName(latest.getOriginalFileName()).latestUploadId(latest.getUploadId())
                    .lastUploadedAt(latest.getCreatedAt())
                    .firstLogDate(first == null ? null : first.toString()).lastLogDate(last == null ? null : last.toString())
                    .activeDays(ds.size()).totalLines(lines).dateUnknownLines(unknown).outOfMonthLines(outside)
                    .build());
        }
        return list;
    }

    private DashboardResponse.UploadSummary uploadSummary(String ym, List<DashboardResponse.UploadRow> list) {
        int uploaded = 0, check = 0, files = 0;
        long lines = 0, accounts = 0;
        for (DashboardResponse.UploadRow u : list) {
            if (u.getFileCount() > 0) {
                uploaded++;
            }
            if ("CHECK".equals(u.getStatus())) {
                check++;
            }
            files += u.getFileCount();
            lines += u.getTotalLines();
            accounts += u.getAccountCount();
        }
        int unchecked = (int) uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N).stream()
                .filter(u -> u.getDateUnknownCount() == null).count();
        return DashboardResponse.UploadSummary.builder()
                .systemCount(list.size()).uploadedCount(uploaded).missingCount(list.size() - uploaded)
                .monthOver(YearMonth.parse(ym, YM).isBefore(YearMonth.from(today())))
                .checkCount(check).uncheckedUploadCount(unchecked)
                .fileCount(files).totalLines(lines).accountCount(accounts).build();
    }

    // ================================================================== 일별 수집 현황 달력 (시스템 상세 창·엑셀)

    private static List<String> calendarDays(String ym) {
        YearMonth m = YearMonth.parse(ym, YM);
        List<String> d = new ArrayList<>();
        for (int i = 1; i <= m.lengthOfMonth(); i++) {
            d.add(m.atDay(i).toString());
        }
        return d;
    }

    /**
     * 시스템 x 날짜 상태 (월 1회 파일 업로드 기준)
     * - OK: 그 날짜의 로그가 1줄 이상 있음
     * - EMPTY: 그 달 로그 파일은 올렸지만 그 날 활동(로그)이 없음
     * - MISS: 대상 월이 지났는데 그 달 로그 파일이 없음
     * - FUTURE: 대상 월이 아직 끝나지 않음 (월 1회 다음 달에 업로드)
     */
    private List<DashboardResponse.CalendarRow> calendar(String ym, List<MonthlyStatusResponse.Row> rows) {
        YearMonth m = YearMonth.parse(ym, YM);
        LocalDate from = m.atDay(1);
        LocalDate to = m.atEndOfMonth().plusDays(1);
        Set<Long> ids = activeIds(rows);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<LocalDate, Integer>> lines = new LinkedHashMap<>();
        for (Object[] o : dailyRepository.sumBySystemAndDate(ym, ids, from, to)) {
            lines.computeIfAbsent((Long) o[0], k -> new LinkedHashMap<>()).put((LocalDate) o[1], ((Number) o[2]).intValue());
        }
        // 그 달 접속 로그 파일이 올라온 시스템
        Set<Long> delivered = new LinkedHashSet<>();
        uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N).forEach(u -> {
            if (u.getSystemId() != null) {
                delivered.add(u.getSystemId());
            }
        });
        boolean monthOver = m.isBefore(YearMonth.from(today()));

        List<DashboardResponse.CalendarRow> list = new ArrayList<>();
        for (MonthlyStatusResponse.Row r : rows) {
            Map<LocalDate, Integer> lm = lines.getOrDefault(r.getSystemId(), Map.of());
            boolean got = delivered.contains(r.getSystemId());
            List<String> stat = new ArrayList<>();
            List<Integer> cnt = new ArrayList<>();
            int ok = 0, miss = 0, due = 0, empty = 0;
            for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
                int n = lm.getOrDefault(d, 0);
                cnt.add(n);
                if (n > 0) {
                    stat.add("OK");
                    ok++;
                    due++;
                } else if (got) {
                    stat.add("EMPTY");
                    empty++;
                    due++;
                } else if (!monthOver) {
                    stat.add("FUTURE");
                } else {
                    stat.add("MISS");
                    miss++;
                    due++;
                }
            }
            list.add(DashboardResponse.CalendarRow.builder()
                    .systemId(r.getSystemId()).systemName(r.getSystemName()).collectMode("UPLOAD")
                    .status(stat).counts(cnt).okDays(ok).emptyDays(empty).missDays(miss).failDays(0).dueDays(due)
                    .coverage(rate(ok + empty, due)).build());
        }
        return list;
    }

    private static boolean inRange(List<LocalDate[]> ranges, LocalDate d) {
        for (LocalDate[] r : ranges) {
            if (!d.isBefore(r[0]) && !d.isAfter(r[1])) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== 3. 미검토 지연

    private DashboardResponse.Aging aging(String ym, Set<Long> active) {
        if (active.isEmpty()) {
            return DashboardResponse.Aging.builder().oldest(List.of()).build();
        }
        LocalDate today = today();
        long a = 0, b = 0, c = 0, d = 0;
        for (LocalDateTime t : detectionRepository.findPendingCreatedAt(ym, active)) {
            long days = ChronoUnit.DAYS.between(t.toLocalDate(), today);
            if (days <= 3) {
                a++;
            } else if (days <= 7) {
                b++;
            } else if (days <= 14) {
                c++;
            } else {
                d++;
            }
        }
        List<DashboardResponse.AgingItem> oldest = new ArrayList<>();
        for (Object[] o : detectionRepository.findPendingByUpload(ym, active)) {
            LocalDateTime since = (LocalDateTime) o[3];
            oldest.add(DashboardResponse.AgingItem.builder()
                    .uploadId((Long) o[0]).systemId((Long) o[1]).systemName((String) o[2])
                    .since(since).days(ChronoUnit.DAYS.between(since.toLocalDate(), today))
                    .pendingCount(((Number) o[4]).longValue()).build());
            if (oldest.size() >= 5) {
                break;
            }
        }
        return DashboardResponse.Aging.builder().total(a + b + c + d).within3(a).d4to7(b).d8to14(c).over14(d)
                .oldest(oldest).build();
    }

    // ================================================================== 6. 특권 계정 활동

    private List<DashboardResponse.PrivAccount> privAccounts(String ym, Set<Long> active) {
        if (active.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> nums = new LinkedHashMap<>();
        Map<String, Set<String>> flags = new LinkedHashMap<>();
        Map<String, Object[]> meta = new LinkedHashMap<>();
        for (Object[] o : accessSummaryRepository.findAllForMonth(ym, active)) {
            SecLogAccessSummary s = (SecLogAccessSummary) o[0];
            if (!AccessLogAnalyzer.isPrivAccount(s.getAccountId())) {
                continue;
            }
            String key = o[1] + "|" + s.getAccountId();
            long[] v = nums.computeIfAbsent(key, k -> new long[4]);
            v[0] += s.getSuccessCount();
            v[1] += s.getFailCount();
            v[2] += s.getOffHourCount();
            v[3] += s.getExternalIpCount();
            if (s.getRiskFlags() != null && !s.getRiskFlags().isBlank()) {
                flags.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(Arrays.asList(s.getRiskFlags().split(",")));
            }
            Object[] m = meta.get(key);
            String last = s.getLastSeen();
            if (m == null || (last != null && (m[3] == null || last.compareTo((String) m[3]) > 0))) {
                meta.put(key, new Object[]{o[1], o[2], o[3], last});
            }
        }
        List<DashboardResponse.PrivAccount> list = new ArrayList<>();
        nums.forEach((key, v) -> {
            Object[] m = meta.get(key);
            list.add(DashboardResponse.PrivAccount.builder()
                    .systemId((Long) m[0]).systemName((String) m[1]).latestUploadId((Long) m[2]).lastSeen((String) m[3])
                    .accountId(key.substring(key.indexOf('|') + 1))
                    .successCount(v[0]).failCount(v[1]).offHourCount(v[2]).externalIpCount(v[3])
                    .riskFlags(new ArrayList<>(flags.getOrDefault(key, Set.of()))).build());
        });
        list.sort(Comparator.comparingLong((DashboardResponse.PrivAccount a) -> a.getOffHourCount() + a.getExternalIpCount() + a.getFailCount())
                .reversed().thenComparing(Comparator.comparingLong(DashboardResponse.PrivAccount::getSuccessCount).reversed()));
        return list;
    }

    // ================================================================== 이번 달 일별 추이

    private List<DashboardResponse.DailyTrend> dailyTrend(String ym, Set<Long> active) {
        YearMonth m = YearMonth.parse(ym, YM);
        Map<LocalDate, long[]> map = new LinkedHashMap<>();
        for (LocalDate d = m.atDay(1); !d.isAfter(m.atEndOfMonth()); d = d.plusDays(1)) {
            map.put(d, new long[5]);
        }
        if (!active.isEmpty()) {
            for (Object[] o : dailyRepository.sumBySystemAndDate(ym, active, m.atDay(1), m.atEndOfMonth().plusDays(1))) {
                long[] v = map.get((LocalDate) o[1]);
                if (v == null) {
                    continue;
                }
                v[0] += ((Number) o[2]).longValue();
                v[1] += ((Number) o[3]).longValue();
                v[2] += ((Number) o[4]).longValue();
                v[3] += ((Number) o[5]).longValue();
                v[4] += ((Number) o[7]).longValue();
            }
        }
        List<DashboardResponse.DailyTrend> list = new ArrayList<>();
        map.forEach((d, v) -> list.add(DashboardResponse.DailyTrend.builder().date(d.toString())
                .lines(v[0]).success(v[1]).fail(v[2]).offHour(v[3]).detections(v[4]).build()));
        return list;
    }

    static Set<Long> activeIds(List<MonthlyStatusResponse.Row> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(r -> ids.add(r.getSystemId()));
        return ids;
    }

    /** 사용 중 시스템의 월 탐지 집계 */
    private record DetSum(long critical, long high, long medium, long low, long pending, List<RuleHitCount> topRules) {
        long total() {
            return critical + high + medium + low;
        }
    }

    private DetSum detectionSummary(String ym, Set<Long> active) {
        if (active.isEmpty()) {
            return new DetSum(0, 0, 0, 0, 0, List.of());
        }
        long c = 0, h = 0, m = 0, l = 0;
        for (SecLogUpload u : uploadRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N)) {
            if (u.getSystemId() == null || !active.contains(u.getSystemId())) {
                continue;
            }
            c += u.getCriticalCount();
            h += u.getHighCount();
            m += u.getMediumCount();
            l += u.getLowCount();
        }
        List<RuleHitCount> top = detectionRepository.countByRuleForMonthAndSystems(ym, active).stream()
                .map(r -> {
                    Severity sev = (Severity) r[2];
                    return RuleHitCount.builder().ruleCode((String) r[0]).ruleName((String) r[1])
                            .severity(sev).severityLabel(sev.getLabel()).hitCount((Long) r[3]).build();
                }).toList();
        long pending = detectionRepository.countForMonthByReviewStatusAndSystems(ym, ReviewStatus.PENDING, active);
        return new DetSum(c, h, m, l, pending, top);
    }

    private static int rate(int a, int b) {
        return b == 0 ? 0 : Math.round(a * 100f / b);
    }
}
