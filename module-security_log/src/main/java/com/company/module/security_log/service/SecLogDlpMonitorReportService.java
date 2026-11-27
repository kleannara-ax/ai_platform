package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpLeakUploadResponse;
import com.company.module.security_log.dto.DlpReportDataResponse;
import com.company.module.security_log.dto.DlpReportSaveRequest;
import com.company.module.security_log.dto.DlpUsbUnregEventResponse;
import com.company.module.security_log.entity.SecLogDlpKakaoApproval;
import com.company.module.security_log.entity.SecLogDlpLeakEvent;
import com.company.module.security_log.entity.SecLogDlpLeakUpload;
import com.company.module.security_log.entity.SecLogDlpPrivacyiRow;
import com.company.module.security_log.entity.SecLogDlpReport;
import com.company.module.security_log.entity.SecLogDlpReportItem;
import com.company.module.security_log.repository.SecLogDlpLeakEventRepository;
import com.company.module.security_log.repository.SecLogDlpLeakUploadRepository;
import com.company.module.security_log.repository.SecLogDlpPrivacyiRowRepository;
import com.company.module.security_log.repository.SecLogDlpReportItemRepository;
import com.company.module.security_log.repository.SecLogDlpReportRepository;
import com.company.module.security_log.support.SecLogTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DLP 모니터링 보고서 자료 (회사 보고서 양식 8장 기준)
 * <ul>
 *   <li>3. Removable Drive: 채널·서브 채널이 USB·이동식·Removable 인 유출 로그 → 차단 / 허용(등록된·등록 안 된 외장하드)</li>
 *   <li>4. PC 카카오톡: 사용 부서 현황 = 카카오톡 사용자 현황(승인 대장) 최신 기준(보고서 만든 날), 미승인 사용자 = 채널·서브 채널·내용·상세 내용·파일·정책에 '카카오'·'kakao' 가 들어간 유출 로그</li>
 *   <li>5·6. Privacy-I 재설치 / 장기 미접속: Privacy-I Agent 현황 파일 1개 (재설치 = 설치 일시가 그 달, 장기 미접속 = 미접속일 7일 이상)</li>
 *   <li>7·8. 생성형 AI: 유출 로그의 생성형 AI 서비스 (접속 주소 → {@link GenAiCatalog})</li>
 *   <li>사람이 적는 칸(승인여부·검토 결과·사전승인·사유·조치·부서 인원·보안 위험도·표지 정보)은 sec_log_dlp_report(_item) 에 저장</li>
 * </ul>
 * 조회: ADMIN, MANAGER, USER / 저장·목록 파일 올리기: ADMIN, MANAGER
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDlpMonitorReportService {

    public static final String S_USB = "USB_UNREG";
    public static final String S_KAKAO = "KAKAO_USER";
    public static final String S_HEAD = "DEPT_HEADCOUNT";
    public static final String S_REINSTALL = "PI_REINSTALL";
    public static final String S_OFFLINE = "PI_OFFLINE";
    public static final String S_AI = "AI_USER";
    public static final String KIND_REINSTALL = "REINSTALL";
    public static final String KIND_OFFLINE = "OFFLINE";
    /** Privacy-I Agent 현황 파일 1개 = 재설치(설치 일시) + 장기 미접속(미접속일) 함께 */
    public static final String KIND_AGENT = "AGENT";
    /** Agent 현황 파일 정보 (보고서 항목 테이블에 1행: VALUE1 = 읽은 인원, VALUE2 = 파일 이름) */
    public static final String S_AGENT = "PI_AGENT";
    public static final String AGENT_KEY = "FILE";

    public static final List<String> APPROVAL = List.of("승인", "미승인");
    public static final List<String> OFFLINE_ACTIONS = List.of("조치완료", "휴직 및 퇴사", "미조치 및 보류");
    public static final List<String> RISKS = List.of("상", "중", "하");
    private static final Set<String> SECTIONS = Set.of(S_USB, S_KAKAO, S_HEAD, S_REINSTALL, S_OFFLINE, S_AI);

    private static final int TOP = 10;
    private static final int LONG_OFFLINE_DAYS = 7;
    private static final long MAX_LIST_FILE = 20L * 1024 * 1024;
    private static final int MAX_LIST_ROWS = 20_000;
    /** Privacy-I 목록 머리글 행을 찾는 범위 (위쪽 부서별 요약 표 포함) */
    private static final int HEADER_SCAN_ROWS = 300;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern REMOVABLE = Pattern.compile("(?i)usb|이동식|외장|removable|media");
    private static final Pattern KAKAO = Pattern.compile("(?i)카카오|kakao");
    private static final Pattern FILE_SIZE_SUFFIX = Pattern.compile("\\s*\\([0-9.,]+\\s*[KMGT]?B\\)\\s*$", Pattern.CASE_INSENSITIVE);
    /** 업무 문서로 보는 확장자 (보안 위험도 '중' 제안) */
    private static final Set<String> WORK_EXT = Set.of("xlsx", "xls", "xlsm", "csv", "doc", "docx", "ppt", "pptx", "pdf",
            "hwp", "hwpx", "txt", "zip", "7z", "sql", "json", "xml", "java", "py", "js", "ts", "dwg");
    /** 업무 관련 단어 (파일 이름에 있으면 '중' 제안) */
    private static final Pattern WORK_WORDS = Pattern.compile(
            "보고|계획|기획|매출|실적|예산|정산|마감|견적|계약|발주|제안|회의|업무|프로젝트|설계|사양|품의|결재|고객|거래|단가|원가|재고|인사|급여|교육|점검|분석|현황|자료|양식|명단|리스트|report|plan|budget|sales|contract|invoice|project|spec|meeting",
            Pattern.CASE_INSENSITIVE);

    private final SecLogDlpLeakUploadRepository uploadRepository;
    private final SecLogDlpLeakEventRepository eventRepository;
    private final SecLogDlpReportRepository reportRepository;
    private final SecLogDlpReportItemRepository itemRepository;
    private final SecLogDlpPrivacyiRowRepository privacyiRepository;
    private final ExcelTextConverter excelTextConverter;
    private final SecLogDlpKakaoApprovalService approvalService;

    // ================================================================== 조회

    public DlpReportDataResponse getData(String targetYm) {
        validateYm(targetYm);
        List<SecLogDlpLeakUpload> ups = uploadRepository.findByTargetYmAndDeletedYnOrderByLeakUploadIdDesc(targetYm, SecLogConstants.DELETED_N);
        SecLogDlpLeakUpload up = ups.isEmpty() ? null : ups.get(0);
        List<SecLogDlpLeakEvent> events = up == null ? List.of()
                : eventRepository.findByLeakUploadIdAndDeletedYnOrderByRowNoAsc(up.getLeakUploadId(), SecLogConstants.DELETED_N);
        Map<String, SecLogDlpReportItem> items = new HashMap<>();
        itemRepository.findByTargetYmAndDeletedYn(targetYm, SecLogConstants.DELETED_N)
                .forEach(i -> items.put(i.getSection() + "|" + i.getItemKey(), i));
        DlpReportDataResponse.Cover cover = cover(targetYm);
        return DlpReportDataResponse.builder()
                .targetYm(targetYm)
                .upload(up == null ? null : DlpLeakUploadResponse.from(up))
                .cover(cover)
                .usb(usb(events, items))
                .kakao(kakao(targetYm, events, items))
                .reinstall(privacyi(targetYm, KIND_REINSTALL, items))
                .offline(privacyi(targetYm, KIND_OFFLINE, items))
                .ai(ai(events, items, cover.getCompanyKeywords()))
                .aiCatalogSize(GenAiCatalog.size())
                .build();
    }

    private DlpReportDataResponse.Cover cover(String ym) {
        YearMonth y = YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM"));
        String period = String.format("%d. %02d. 01 ~ %d. %02d. %02d", y.getYear(), y.getMonthValue(), y.getYear(), y.getMonthValue(), y.lengthOfMonth());
        SecLogDlpReport r = reportRepository.findByTargetYmAndDeletedYn(ym, SecLogConstants.DELETED_N).orElse(null);
        boolean carried = false;
        if (r == null) {
            r = reportRepository.findFirstByTargetYmLessThanAndDeletedYnOrderByTargetYmDesc(ym, SecLogConstants.DELETED_N).orElse(null);
            carried = r != null;
        }
        return DlpReportDataResponse.Cover.builder()
                .reportPeriod(period)
                .reportType(r != null && r.getReportType() != null ? r.getReportType() : "월간 정기 보고")
                .targetSystem(r != null && r.getTargetSystem() != null ? r.getTargetSystem() : "DLP (Privacy-I)")
                .writerDept(r != null ? r.getWriterDept() : null)
                .companyKeywords(r != null ? r.getCompanyKeywords() : null)
                .carriedOver(carried)
                .build();
    }

    // ---- 3. Removable Drive
    private DlpReportDataResponse.Usb usb(List<SecLogDlpLeakEvent> events, Map<String, SecLogDlpReportItem> items) {
        long blocked = 0;
        long reg = 0;
        long unreg = 0;
        Map<String, long[]> cnt = new LinkedHashMap<>();
        Map<String, String[]> info = new HashMap<>();
        for (SecLogDlpLeakEvent e : events) {
            if (!REMOVABLE.matcher(nz(e.getChannel()) + " " + nz(e.getSubChannel())).find()) {
                continue;
            }
            if (nz(e.getActionName()).contains("차단")) {
                blocked++;
                continue;
            }
            if (isUnregistered(e.getMediaRegistered())) {
                unreg++;
                String k = userKey(e.getUserId(), e.getUserName());
                cnt.computeIfAbsent(k, x -> new long[1])[0]++;
                info.putIfAbsent(k, new String[]{e.getDeptName(), e.getUserName(), e.getUserId()});
            } else {
                reg++;
            }
        }
        List<DlpReportDataResponse.PersonRow> users = persons(S_USB, cnt, info, items);
        return DlpReportDataResponse.Usb.builder()
                .blocked(blocked).allowedRegistered(reg).allowedUnregistered(unreg).total(blocked + reg + unreg)
                .unregUsers(users).unregTotalCount(unreg).build();
    }

    /** 01 '등록 안 된 외장하드로 업로드' 근거 로그 — 화면에 보내는 최대 건수 */
    public static final int USB_UNREG_LIMIT = 500;

    /**
     * 01 '등록 안 된 외장하드로 업로드' 카드의 근거 로그 (카드 숫자와 같은 기준, 최근 발생 순 최대 500건)
     * - 기준 파일 = 그 달 가장 최근에 올린 유출 로그 1개 (보고서와 같음)
     */
    public DlpUsbUnregEventResponse getUsbUnregEvents(String targetYm) {
        validateYm(targetYm);
        List<SecLogDlpLeakUpload> ups = uploadRepository.findByTargetYmAndDeletedYnOrderByLeakUploadIdDesc(targetYm, SecLogConstants.DELETED_N);
        if (ups.isEmpty()) {
            return DlpUsbUnregEventResponse.builder().targetYm(targetYm).total(0).limit(USB_UNREG_LIMIT).rows(List.of()).build();
        }
        SecLogDlpLeakUpload up = ups.get(0);
        List<SecLogDlpLeakEvent> hit = new ArrayList<>();
        for (SecLogDlpLeakEvent e : eventRepository.findByLeakUploadIdAndDeletedYnOrderByRowNoAsc(up.getLeakUploadId(), SecLogConstants.DELETED_N)) {
            if (REMOVABLE.matcher(nz(e.getChannel()) + " " + nz(e.getSubChannel())).find()
                    && !nz(e.getActionName()).contains("차단") && isUnregistered(e.getMediaRegistered())) {
                hit.add(e);
            }
        }
        hit.sort(Comparator.comparing(SecLogDlpLeakEvent::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SecLogDlpLeakEvent::getRowNo, Comparator.nullsLast(Comparator.naturalOrder())));
        List<DlpUsbUnregEventResponse.Row> rows = hit.stream().limit(USB_UNREG_LIMIT)
                .map(e -> DlpUsbUnregEventResponse.Row.builder().rowNo(e.getRowNo()).occurredAt(e.getOccurredAt())
                        .occurredAtText(e.getOccurredAtText()).deptName(e.getDeptName()).userName(e.getUserName())
                        .userId(e.getUserId()).computerName(e.getComputerName()).fileList(e.getFileList())
                        .fileSizeText(e.getFileSizeText()).subChannel(e.getSubChannel()).build())
                .toList();
        return DlpUsbUnregEventResponse.builder().targetYm(targetYm).fileName(up.getOriginalFileName())
                .total(hit.size()).limit(USB_UNREG_LIMIT).rows(rows).build();
    }

    static boolean isUnregistered(String media) {
        String m = nz(media).replace(" ", "");
        return m.contains("등록안") || m.contains("미등록") || m.equalsIgnoreCase("n") || m.toLowerCase(Locale.ROOT).contains("unregist");
    }

    // ---- 4. PC 카카오톡
    private DlpReportDataResponse.Kakao kakao(String ym, List<SecLogDlpLeakEvent> events, Map<String, SecLogDlpReportItem> items) {
        Map<String, long[]> cnt = new LinkedHashMap<>();
        Map<String, String[]> info = new HashMap<>();
        Map<String, Set<java.time.LocalDate>> days = new HashMap<>();
        long ev = 0;
        for (SecLogDlpLeakEvent e : events) {
            if (!isKakao(e)) {
                continue;
            }
            ev++;
            String k = userKey(e.getUserId(), e.getUserName());
            cnt.computeIfAbsent(k, x -> new long[1])[0]++;
            info.putIfAbsent(k, new String[]{e.getDeptName(), e.getUserName(), e.getUserId()});
            if (e.getOccurredAt() != null) {
                days.computeIfAbsent(k, x -> new java.util.TreeSet<>()).add(e.getOccurredAt().toLocalDate());
            }
        }
        // 사용 부서 현황: 카카오톡 사용자 현황(승인 대장) 기준 — 보고서를 만든 날의 최신 대장으로 사용 중 인원
        java.time.LocalDate basis = SecLogTime.today();
        Map<String, Integer> head = headcounts(ym);
        List<DlpReportDataResponse.DeptRow> depts = new ArrayList<>();
        long headTotal = 0;
        long ledgerUsers = 0;
        for (Map.Entry<String, Long> en : approvalService.activeByDept(basis).entrySet()) {
            Integer h = head.get(en.getKey());
            if (h != null) {
                headTotal += h;
            }
            ledgerUsers += en.getValue();
            depts.add(DlpReportDataResponse.DeptRow.builder().deptName(en.getKey()).userCount(en.getValue()).headcount(h).build());
        }
        // 사전승인: 직접 입력한 값이 있으면 그 값, 없으면 승인 대장(부서+이름, '신청중')으로 자동 판정
        List<SecLogDlpKakaoApproval> ledger = approvalService.active();
        YearMonth y = YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM"));
        List<DlpReportDataResponse.PersonRow> users = persons(S_KAKAO, cnt, info, items).stream().map(p -> {
            SecLogDlpKakaoApprovalService.Match m = SecLogDlpKakaoApprovalService.match(ledger, p.getDeptName(), p.getUserName(),
                    new ArrayList<>(days.getOrDefault(p.getItemKey(), Set.of())), y);
            boolean manual = p.getManual1() != null;
            return DlpReportDataResponse.PersonRow.builder().itemKey(p.getItemKey()).deptName(p.getDeptName())
                    .userName(p.getUserName()).userId(p.getUserId()).count(p.getCount())
                    .value1(manual ? p.getManual1() : m.approval())
                    .value2(p.getManual2() != null ? p.getManual2() : m.reason())
                    .manual1(p.getManual1()).manual2(p.getManual2())
                    .autoApproval(m.approval()).autoReason(m.reason()).autoNote(m.note())
                    .source(manual ? "MANUAL" : m.check() ? "CHECK" : "LEDGER").needConfirm(m.confirm()).build();
        }).toList();
        long unapproved = users.stream().filter(u -> !"승인".equals(u.getValue1())).count();
        return DlpReportDataResponse.Kakao.builder()
                .depts(depts).userTotal(ledgerUsers).basisDate(basis).headcountTotal(headTotal)
                .users(users).unapprovedCount(unapproved).eventCount(ev).build();
    }

    static boolean isKakao(SecLogDlpLeakEvent e) {
        return KAKAO.matcher(String.join(" ", nz(e.getChannel()), nz(e.getSubChannel()), nz(e.getContent()), nz(e.getDetail()),
                nz(e.getFileList()), nz(e.getPolicyName()))).find();
    }

    /** 부서 인원 (그 달 값, 없으면 가장 최근 달 값) */
    private Map<String, Integer> headcounts(String ym) {
        Map<String, Integer> m = new HashMap<>();
        for (SecLogDlpReportItem i : itemRepository.findBySectionAndTargetYmLessThanEqualAndDeletedYnOrderByTargetYmDesc(S_HEAD, ym, SecLogConstants.DELETED_N)) {
            if (!m.containsKey(i.getItemKey()) && StringUtils.hasText(i.getValue1())) {
                try {
                    m.put(i.getItemKey(), Integer.parseInt(i.getValue1().trim()));
                } catch (NumberFormatException ignore) {
                    // 숫자가 아닌 값은 무시
                }
            }
        }
        return m;
    }

    private static List<DlpReportDataResponse.PersonRow> persons(String section, Map<String, long[]> cnt, Map<String, String[]> info,
                                                                 Map<String, SecLogDlpReportItem> items) {
        List<DlpReportDataResponse.PersonRow> out = new ArrayList<>();
        cnt.forEach((k, c) -> {
            String[] i = info.get(k);
            SecLogDlpReportItem it = items.get(section + "|" + k);
            out.add(DlpReportDataResponse.PersonRow.builder().itemKey(k).deptName(i[0]).userName(i[1]).userId(i[2]).count(c[0])
                    .value1(it == null ? null : it.getValue1()).value2(it == null ? null : it.getValue2())
                    .manual1(it == null ? null : it.getValue1()).manual2(it == null ? null : it.getValue2()).build());
        });
        out.sort(Comparator.comparingLong(DlpReportDataResponse.PersonRow::getCount).reversed()
                .thenComparing(p -> nz(p.getDeptName())).thenComparing(p -> nz(p.getUserName())));
        return out;
    }

    // ---- 5·6. Privacy-I
    private DlpReportDataResponse.PrivacyiList privacyi(String ym, String kind, Map<String, SecLogDlpReportItem> items) {
        List<SecLogDlpPrivacyiRow> rows = privacyiRepository.findByTargetYmAndKindAndDeletedYnOrderByRowNoAsc(ym, kind, SecLogConstants.DELETED_N);
        String section = KIND_REINSTALL.equals(kind) ? S_REINSTALL : S_OFFLINE;
        if (KIND_OFFLINE.equals(kind)) {
            rows = new ArrayList<>(rows);
            rows.sort(Comparator.comparing((SecLogDlpPrivacyiRow r) -> r.getOfflineDays() == null ? -1 : r.getOfflineDays()).reversed()
                    .thenComparing(SecLogDlpPrivacyiRow::getRowNo));
        }
        List<DlpReportDataResponse.PiRow> out = new ArrayList<>();
        long longOff = 0;
        long done = 0;
        long leave = 0;
        long pending = 0;
        int rank = 0;
        for (SecLogDlpPrivacyiRow r : rows) {
            String key = "R" + r.getRowNo() + "|" + nz(r.getUserId()) + "|" + nz(r.getUserName());
            SecLogDlpReportItem it = items.get(section + "|" + key);
            String v1 = it == null ? null : it.getValue1();
            out.add(DlpReportDataResponse.PiRow.builder().itemKey(key).rank(++rank).rowNo(r.getRowNo())
                    .deptName(r.getDeptName()).userName(r.getUserName()).userId(r.getUserId())
                    .eventAt(r.getEventAt() != null ? r.getEventAt().format(DT) : r.getEventAtText())
                    .offlineDays(r.getOfflineDays()).value1(v1).value2(it == null ? null : it.getValue2()).build());
            if (KIND_OFFLINE.equals(kind)) {
                if (r.getOfflineDays() == null || r.getOfflineDays() >= LONG_OFFLINE_DAYS) {
                    longOff++;
                }
                if ("조치완료".equals(v1)) {
                    done++;
                } else if ("휴직 및 퇴사".equals(v1)) {
                    leave++;
                } else {
                    pending++;
                }
            }
        }
        return DlpReportDataResponse.PrivacyiList.builder()
                .fileName(!rows.isEmpty() ? rows.get(0).getFileName()
                        : items.containsKey(S_AGENT + "|" + AGENT_KEY) ? items.get(S_AGENT + "|" + AGENT_KEY).getValue2() : null).rows(out)
                .longOffline(longOff).actionDone(done).leaveOrQuit(leave).pending(pending).build();
    }

    // ---- 7·8. 생성형 AI
    private DlpReportDataResponse.Ai ai(List<SecLogDlpLeakEvent> events, Map<String, SecLogDlpReportItem> items, String companyKeywords) {
        Map<String, Long> svc = new LinkedHashMap<>();
        Map<String, Map<String, Long>> dept = new LinkedHashMap<>();
        Map<String, long[]> userCnt = new LinkedHashMap<>();
        Map<String, String[]> info = new HashMap<>();
        Map<String, Set<String>> userFiles = new HashMap<>();
        Map<String, Map<String, Long>> userSvc = new HashMap<>();
        long ev = 0;
        for (SecLogDlpLeakEvent e : events) {
            String s = e.getAiService();
            if (s == null) {
                continue;
            }
            ev++;
            svc.merge(s, 1L, Long::sum);
            dept.computeIfAbsent(e.getDeptName() == null ? "(부서 없음)" : e.getDeptName(), x -> new LinkedHashMap<>()).merge(s, 1L, Long::sum);
            String k = userKey(e.getUserId(), e.getUserName());
            userCnt.computeIfAbsent(k, x -> new long[1])[0]++;
            info.putIfAbsent(k, new String[]{e.getDeptName(), e.getUserName(), e.getUserId()});
            userFiles.computeIfAbsent(k, x -> new LinkedHashSet<>()).addAll(fileNames(e.getFileList()));
            userSvc.computeIfAbsent(k, x -> new LinkedHashMap<>()).merge(s, 1L, Long::sum);
        }
        List<DlpReportDataResponse.CountRow> services = new ArrayList<>();
        svc.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(en -> services.add(DlpReportDataResponse.CountRow.builder().rank(services.size() + 1).name(en.getKey()).count(en.getValue()).build()));
        List<DlpReportDataResponse.AiDeptRow> depts = new ArrayList<>();
        dept.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<String, Long>> en) -> -sum(en.getValue())).thenComparing(Map.Entry::getKey))
                .limit(TOP)
                .forEach(en -> depts.add(DlpReportDataResponse.AiDeptRow.builder().rank(depts.size() + 1).deptName(en.getKey())
                        .count(sum(en.getValue())).mainService(top(en.getValue())).build()));
        List<String> kw = keywords(companyKeywords);
        // 순위: 건 수 많은 순 → 같으면 보안 위험도 높은 순(상 > 중 > 하, 직접 고른 값 우선) → 같으면 사람 구분 값 순, 10명에서 자름
        Map<String, Integer> riskRank = new HashMap<>();
        for (String k : userCnt.keySet()) {
            SecLogDlpReportItem it = items.get(S_AI + "|" + k);
            String manual = it == null ? null : it.getValue1();
            String r = manual != null && RISKS.contains(manual) ? manual
                    : suggestRisk(new ArrayList<>(userFiles.getOrDefault(k, Set.of())), kw)[0];
            riskRank.put(k, RISKS.indexOf(r));
        }
        List<DlpReportDataResponse.AiUserRow> users = new ArrayList<>();
        userCnt.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> en) -> -en.getValue()[0])
                        .thenComparingInt(en -> riskRank.getOrDefault(en.getKey(), RISKS.size()))
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP)
                .forEach(en -> {
                    String k = en.getKey();
                    String[] i = info.get(k);
                    List<String> files = new ArrayList<>(userFiles.getOrDefault(k, Set.of()));
                    String[] sug = suggestRisk(files, kw);
                    SecLogDlpReportItem it = items.get(S_AI + "|" + k);
                    String manual = it == null ? null : it.getValue1();
                    boolean isManual = manual != null && RISKS.contains(manual);
                    List<String> svcs = userSvc.getOrDefault(k, Map.of()).entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(Map.Entry::getKey).toList();
                    users.add(DlpReportDataResponse.AiUserRow.builder().itemKey(k).deptName(i[0]).userName(i[1]).userId(i[2])
                            .count(en.getValue()[0]).risk(isManual ? manual : sug[0]).riskSuggested(sug[0]).riskReason(sug[1])
                            .riskManual(isManual).files(files).services(svcs).build());
                });
        return DlpReportDataResponse.Ai.builder().eventCount(ev).services(services).depts(depts).users(users).build();
    }

    /**
     * 보안 위험도 제안 (사용자 기준)
     * - 상: 회사명 등 식별정보가 포함된 업무 관련 파일 (파일 이름에 회사 식별 단어)
     * - 중: 업무 관련 파일이나 회사 식별정보가 없는 경우 (업무 문서 확장자 또는 업무 단어)
     * - 하: 개인용 또는 회사 업무와 관련성이 낮은 파일 (그 외, 예: 사진·이미지)
     */
    static String[] suggestRisk(List<String> files, List<String> companyWords) {
        for (String f : files) {
            String lf = f.toLowerCase(Locale.ROOT);
            for (String w : companyWords) {
                if (lf.contains(w.toLowerCase(Locale.ROOT))) {
                    return new String[]{"상", "파일 이름에 회사 식별 단어 '" + w + "' (" + f + ")"};
                }
            }
        }
        for (String f : files) {
            String ext = ext(f);
            if (WORK_EXT.contains(ext) || WORK_WORDS.matcher(f).find()) {
                return new String[]{"중", "업무 관련 파일로 보임 (" + f + ")"};
            }
        }
        return new String[]{"하", files.isEmpty() ? "파일 정보 없음" : "업무 관련성이 낮은 파일로 보임 (" + files.get(0) + ")"};
    }

    /** 파일 목록 → 파일 이름들 (크기 표기 제거) */
    static List<String> fileNames(String fileList) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(fileList)) {
            return out;
        }
        for (String p : fileList.split("[,;\\n]")) {
            String n = FILE_SIZE_SUFFIX.matcher(p.trim()).replaceAll("").trim();
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        if (out.isEmpty()) {
            out.add(fileList.trim());
        }
        return out;
    }

    private static String ext(String f) {
        int i = f.lastIndexOf('.');
        return i < 0 ? "" : f.substring(i + 1).toLowerCase(Locale.ROOT).trim();
    }

    private static List<String> keywords(String v) {
        List<String> out = new ArrayList<>();
        if (StringUtils.hasText(v)) {
            for (String p : v.split(",")) {
                if (!p.trim().isEmpty()) {
                    out.add(p.trim());
                }
            }
        }
        return out;
    }

    private static long sum(Map<String, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    private static String top(Map<String, Long> m) {
        return m.entrySet().stream().max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse(null);
    }

    // ================================================================== 저장

    @Transactional
    public void save(String targetYm, DlpReportSaveRequest req, Long userId) {
        validateYm(targetYm);
        if (req.getCover() != null) {
            DlpReportSaveRequest.Cover c = req.getCover();
            SecLogDlpReport r = reportRepository.findByTargetYmAndDeletedYn(targetYm, SecLogConstants.DELETED_N).orElse(null);
            if (r == null) {
                reportRepository.save(SecLogDlpReport.builder().targetYm(targetYm).reportType(blank(c.getReportType()))
                        .targetSystem(blank(c.getTargetSystem())).writerDept(blank(c.getWriterDept()))
                        .companyKeywords(blank(c.getCompanyKeywords())).createdBy(userId).build());
            } else {
                r.update(blank(c.getReportType()), blank(c.getTargetSystem()), blank(c.getWriterDept()), blank(c.getCompanyKeywords()), userId);
            }
        }
        if (req.getItems() == null) {
            return;
        }
        for (DlpReportSaveRequest.Item i : req.getItems()) {
            String section = i.getSection().trim().toUpperCase(Locale.ROOT);
            if (!SECTIONS.contains(section)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "알 수 없는 보고서 항목입니다: " + i.getSection());
            }
            String v1 = blank(i.getValue1());
            String v2 = blank(i.getValue2());
            checkValue(section, v1);
            String key = i.getItemKey().trim();
            SecLogDlpReportItem it = itemRepository.findByTargetYmAndSectionAndItemKey(targetYm, section, key).orElse(null);
            if (it == null) {
                if (v1 == null && v2 == null) {
                    continue;
                }
                itemRepository.save(SecLogDlpReportItem.builder().targetYm(targetYm).section(section).itemKey(key)
                        .value1(v1).value2(v2).createdBy(userId).build());
            } else if (!eq(it.getValue1(), v1) || !eq(it.getValue2(), v2)) {
                it.changeValues(v1, v2, userId);
            }
        }
    }

    private static void checkValue(String section, String v1) {
        if (v1 == null) {
            return;
        }
        switch (section) {
            case S_USB, S_KAKAO -> {
                if (!APPROVAL.contains(v1)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "승인 여부는 '승인' 또는 '미승인' 입니다.");
                }
            }
            case S_OFFLINE -> {
                if (!OFFLINE_ACTIONS.contains(v1)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조치 구분은 조치완료 / 휴직 및 퇴사 / 미조치 및 보류 중 하나입니다.");
                }
            }
            case S_AI -> {
                if (!RISKS.contains(v1)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "보안 위험도는 상 / 중 / 하 중 하나입니다.");
                }
            }
            case S_HEAD -> {
                if (!v1.matches("^\\d{1,6}$")) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "부서 인원은 숫자로 입력하세요.");
                }
            }
            default -> {
                // 재설치: 선택 값 없음
            }
        }
    }

    // ================================================================== Privacy-I 목록 파일

    /**
     * 목록 파일 올리기 결과
     * @param saved   저장한 인원 (장기 미접속은 미접속일 7일 이상만)
     * @param total   파일에서 읽은 인원
     * @param shortCount   뺀 인원 - 장기 미접속: 미접속일 7일 미만 / 재설치: 설치 일시가 그 달이 아님
     * @param unknownCount 판단할 수 없어 뺀 인원 - 장기 미접속: 미접속일·최종 오프라인 일시 모두 없음 / 재설치: 설치 일시 없음
     */
    public record PiUploadResult(int saved, int total, int shortCount, int unknownCount) {
    }

    /** Privacy-I Agent 현황 파일 올리기 결과 (재설치 + 장기 미접속) */
    public record AgentUploadResult(String fileName, PiUploadResult reinstall, PiUploadResult offline) {
    }

    /**
     * 목록 파일 올리기 (같은 달·같은 종류는 이전 목록을 소프트 삭제 후 교체)
     * - Privacy-I 'Agent List' 엑셀 그대로 올림: 위쪽 부서별 요약 표 아래의 '사용자 이름' 머리글 행을 찾아 그 아래를 읽음
     * - 재설치: '설치 일시'가 그 달인 사람만 저장
     * - 장기 미접속: '미접속일' 열이 7일 이상인 사람만 저장 (비어 있으면 최종 오프라인 일시로 계산)
     */
    @Transactional
    public PiUploadResult uploadPrivacyi(String targetYm, String kind, MultipartFile file, Long userId) {
        validateYm(targetYm);
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KIND_REINSTALL.equals(k) && !KIND_OFFLINE.equals(k)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "목록 종류는 REINSTALL(재설치) 또는 OFFLINE(장기 미접속) 입니다.");
        }
        return importKind(readPiFile(file), targetYm, k, userId);
    }

    /**
     * Privacy-I Agent 현황 파일 1개로 재설치·장기 미접속 목록을 함께 반영 (DLP 모니터링 ② 단계)
     * - 재설치 = 설치 일시가 그 달인 사람, 장기 미접속 = 미접속일 7일 이상인 사람
     */
    @Transactional
    public AgentUploadResult uploadAgent(String targetYm, MultipartFile file, Long userId) {
        validateYm(targetYm);
        PiFile f = readPiFile(file);
        PiUploadResult re = importKind(f, targetYm, KIND_REINSTALL, userId);
        PiUploadResult off = importKind(f, targetYm, KIND_OFFLINE, userId);
        String total = String.valueOf(off.total());
        SecLogDlpReportItem it = itemRepository.findByTargetYmAndSectionAndItemKey(targetYm, S_AGENT, AGENT_KEY).orElse(null);
        if (it == null) {
            itemRepository.save(SecLogDlpReportItem.builder().targetYm(targetYm).section(S_AGENT).itemKey(AGENT_KEY)
                    .value1(total).value2(cut(f.name(), 1000)).createdBy(userId).build());
        } else {
            it.changeValues(total, cut(f.name(), 1000), userId);
        }
        return new AgentUploadResult(f.name(), re, off);
    }

    /** 올린 목록 파일 (이름 + 시트별 행) */
    private record PiFile(String name, List<List<ExcelTextConverter.SheetRow>> tables) {
    }

    private PiFile readPiFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "올릴 목록 파일이 없습니다.");
        }
        if (file.getSize() > MAX_LIST_FILE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "목록 파일은 최대 20MB 까지 올릴 수 있습니다.");
        }
        String name = StringUtils.getFilename(StringUtils.cleanPath(nz(file.getOriginalFilename())).replace('\\', '/'));
        String ext = nz(StringUtils.getFilenameExtension(name)).toLowerCase(Locale.ROOT);
        if (!Set.of("xlsx", "xls", "csv", "tsv", "txt").contains(ext) || name.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "목록 파일은 엑셀(.xlsx, .xls) 또는 CSV(.csv) 파일만 올릴 수 있습니다.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "목록 파일을 읽을 수 없습니다.");
        }
        List<List<ExcelTextConverter.SheetRow>> tables = new ArrayList<>();
        if (ExcelTextConverter.isExcel(name)) {
            excelTextConverter.readAllSheets(content).forEach(sh -> tables.add(sh.rows()));
        } else {
            tables.add(SecLogDlpLeakService.csvRows(content));
        }
        return new PiFile(name, tables);
    }

    /** 종류 1개 반영 (머리글 행을 찾아 그 아래를 읽고, 이 달·이 종류 목록 교체) */
    private PiUploadResult importKind(PiFile f, String targetYm, String k, Long userId) {
        LocalDateTime base = baseTime(targetYm);
        for (List<ExcelTextConverter.SheetRow> rows : f.tables()) {
            // 머리글 행 찾기 (위쪽 부서별 요약 표가 길어도 찾도록 앞쪽 300행까지)
            for (int h = 0; h < Math.min(HEADER_SCAN_ROWS, rows.size()); h++) {
                Map<String, Integer> col = piColumns(rows.get(h).cells(), k);
                if (col.containsKey("user")) {
                    int[] cnt = new int[3];
                    List<SecLogDlpPrivacyiRow> out = toPiRows(rows, h, col, targetYm, k, f.name(), base, userId, cnt);
                    privacyiRepository.softDelete(targetYm, k, userId, SecLogTime.now());
                    privacyiRepository.saveAll(out);
                    return new PiUploadResult(out.size(), cnt[0], cnt[1], cnt[2]);
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "Privacy-I 목록 형식이 아닙니다. 머리글에 '사용자 이름'(또는 이름·성명) 열이 있어야 합니다.");
    }

    private static Map<String, Integer> piColumns(List<String> header, String kind) {
        Map<String, Integer> col = new LinkedHashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = nz(header.get(c)).replaceAll("[\\s_\\-]", "").toLowerCase(Locale.ROOT);
            if (h.isEmpty()) {
                continue;
            }
            if (!col.containsKey("dept") && (h.contains("부서"))) {
                col.put("dept", c);
            } else if (!col.containsKey("user") && (h.equals("사용자이름") || h.equals("사용자명") || h.equals("이름") || h.equals("성명") || h.equals("사용자"))) {
                col.put("user", c);
            } else if (!col.containsKey("id") && (h.contains("아이디") || h.equals("사용자id") || h.equals("id") || h.equals("계정") || h.equals("사번"))) {
                col.put("id", c);
            } else if (!col.containsKey("days") && (h.contains("미접속일") || h.contains("미접속일수") || h.contains("경과일") || h.contains("오프라인일수"))) {
                col.put("days", c);
            } else if (!col.containsKey("at") && (KIND_REINSTALL.equals(kind)
                    ? (h.contains("설치") && (h.contains("일시") || h.contains("일자") || h.contains("시간") || h.contains("날짜")))
                    : (h.contains("오프라인") || h.contains("최종접속") || h.contains("마지막접속") || h.contains("최근접속")))) {
                col.put("at", c);
            }
        }
        if (!col.containsKey("at")) {
            for (int c = 0; c < header.size(); c++) {
                String h = nz(header.get(c)).replaceAll("\\s", "");
                if (h.contains("일시") || h.contains("일자") || h.contains("날짜")) {
                    col.put("at", c);
                    break;
                }
            }
        }
        return col;
    }

    /** cnt[0] = 읽은 인원, cnt[1] = 기준 밖이라 뺀 인원 (7일 미만 / 설치 일시가 그 달 아님), cnt[2] = 날짜를 알 수 없어 뺀 인원 */
    private static List<SecLogDlpPrivacyiRow> toPiRows(List<ExcelTextConverter.SheetRow> rows, int h, Map<String, Integer> col,
                                                       String ym, String kind, String fileName, LocalDateTime base, Long userId,
                                                       int[] cnt) {
        List<SecLogDlpPrivacyiRow> out = new ArrayList<>();
        for (int i = h + 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i).cells();
            String user = cell(cells, col.get("user"));
            String id = cell(cells, col.get("id"));
            if (user == null && id == null) {
                continue;
            }
            cnt[0]++;
            if (cnt[0] > MAX_LIST_ROWS) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "목록은 최대 20,000행까지 올릴 수 있습니다.");
            }
            String atText = cell(cells, col.get("at"));
            LocalDateTime at = SecLogPiiDownloadService.parseDateTime(atText);
            Integer days = null;
            if (KIND_REINSTALL.equals(kind)) {
                // 재설치 = 설치 일시가 그 달인 사람
                if (at == null) {
                    cnt[2]++;
                    continue;
                }
                if (!YearMonth.from(at).equals(YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM")))) {
                    cnt[1]++;
                    continue;
                }
            }
            if (KIND_OFFLINE.equals(kind)) {
                days = toInt(cell(cells, col.get("days")));
                if (days == null && at != null) {
                    days = (int) Math.max(0, Duration.between(at, base).toDays());
                }
                // 장기 미접속 = 미접속일 7일 이상만
                if (days == null) {
                    cnt[2]++;
                    continue;
                }
                if (days < LONG_OFFLINE_DAYS) {
                    cnt[1]++;
                    continue;
                }
            }
            out.add(SecLogDlpPrivacyiRow.builder().targetYm(ym).kind(kind).fileName(fileName).rowNo(rows.get(i).rowNo())
                    .deptName(cut(cell(cells, col.get("dept")), 200)).userName(cut(user, 200)).userId(cut(id, 200))
                    .eventAt(at).eventAtText(cut(atText, 50)).offlineDays(days).createdBy(userId).build());
        }
        return out;
    }

    /** 미접속일 계산 기준: 그 달 말일 끝 (지금이 더 이르면 지금) */
    private static LocalDateTime baseTime(String ym) {
        YearMonth y = YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDateTime end = y.atEndOfMonth().atTime(23, 59, 59);
        LocalDateTime now = SecLogTime.now();
        return now.isBefore(end) ? now : end;
    }

    // ================================================================== 공통

    private static void validateYm(String ym) {
        if (ym == null || !ym.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
    }

    static String userKey(String id, String name) {
        return StringUtils.hasText(id) ? id.trim() : StringUtils.hasText(name) ? name.trim() : "(사용자 없음)";
    }

    private static String cell(List<String> cells, Integer c) {
        if (c == null || c >= cells.size() || cells.get(c) == null) {
            return null;
        }
        String v = cells.get(c).trim();
        return v.isEmpty() ? null : v;
    }

    private static Integer toInt(String v) {
        if (v == null) {
            return null;
        }
        Matcher m = Pattern.compile("\\d{1,6}").matcher(v.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String blank(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String cut(String v, int max) {
        return v == null ? null : v.length() <= max ? v : v.substring(0, max);
    }
}
