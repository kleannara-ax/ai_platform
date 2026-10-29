package com.company.module.security_log.service;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 접속 행위로그 분석기 (상태 없음)
 *
 * <p>로그에서 <b>로그인 성공/실패 이벤트</b>를 추출해 계정별로 집계한다.
 * 장비마다 형식이 달라도 인식할 수 있도록 여러 패턴을 순서대로 시도한다.
 *
 * <pre>
 * 지원 형식 (대소문자 무시)
 *  - Linux sshd     : "Accepted password for root from 1.2.3.4" / "Failed password for invalid user x from ..."
 *  - Windows 이벤트 : "4624 ... Account Name: kim ... Source Network Address: 1.2.3.4" (4625 = 실패)
 *  - key=value 형식 : user=kim src=1.2.3.4 action=login result=success  (UTM, SSL VPN, WAF, DLP, EPP, DBMS 등)
 *  - AWS CloudTrail : "eventName":"ConsoleLogin" ... "userName":"kim" ... "ConsoleLogin":"Failure"
 *  - 표 형식(CSV/TSV/엑셀): 머리글로 계정/IP/일시/유형/결과/내용 열을 찾아 인식
 *                     (DLP·EPP 관리 콘솔 감사로그 등: "발생 일시 | 사용자(관리자) 아이디 | IP | 유형 | 내용")
 *  - 일반 텍스트    : 한 줄에 로그인 키워드 + 성공/실패 키워드 + 계정 + IP 가 있으면 인식
 * </pre>
 *
 * <p>위험 표시 (RISK_FLAGS)
 * <ul>
 *   <li>FAIL_MANY   : 로그인 실패 5회 이상</li>
 *   <li>OFF_HOUR    : 업무시간 외(22시~06시) 접속 성공</li>
 *   <li>EXTERNAL_IP : 내부 IP 대역(관리 > 내부 IP 설정)에 없는 곳에서 접속</li>
 *   <li>MULTI_IP    : 서로 다른 IP 3곳 이상에서 접속</li>
 *   <li>PRIV_ACCOUNT: 특권/공용 계정(root, admin, administrator, sa, sys 등) 사용</li>
 *   <li>IP_DENIED   : 허용되지 않은 IP 에서 접속 시도 (장비의 접속 IP 제한에 걸림)</li>
 * </ul>
 */
@Component
public class AccessLogAnalyzer {

    public static final int FAIL_MANY_THRESHOLD = 5;
    public static final int MULTI_IP_THRESHOLD = 3;
    private static final int MAX_ACCOUNTS = 5_000;
    private static final int MAX_IP_LIST = 20;

    private static final Set<String> PRIV_ACCOUNTS = Set.of(
            "root", "admin", "administrator", "sa", "sys", "system", "oracle", "postgres", "dba", "superuser", "guest");

    private static final String IP = "(\\d{1,3}(?:\\.\\d{1,3}){3})";
    private static final String USER = "([A-Za-z0-9._@\\-\\\\$가-힣]{1,100})";

    // ---- 형식별 패턴 ----
    private static final Pattern SSH_OK = Pattern.compile(
            "Accepted (?:password|publickey|keyboard-interactive\\S*) for " + USER + " from " + IP, Pattern.CASE_INSENSITIVE);
    private static final Pattern SSH_FAIL = Pattern.compile(
            "Failed (?:password|publickey|keyboard-interactive\\S*) for (?:invalid user )?" + USER + " from " + IP, Pattern.CASE_INSENSITIVE);
    private static final Pattern SSH_INVALID = Pattern.compile(
            "Invalid user " + USER + " from " + IP, Pattern.CASE_INSENSITIVE);

    private static final Pattern WIN_EVENT = Pattern.compile("\\b(4624|4625)\\b");
    private static final Pattern WIN_ACCOUNT = Pattern.compile(
            "(?:Account Name|계정 이름|TargetUserName)\\s*[:=]\\s*\"?" + USER, Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_IP = Pattern.compile(
            "(?:Source Network Address|원본 네트워크 주소|IpAddress)\\s*[:=]\\s*\"?" + IP, Pattern.CASE_INSENSITIVE);

    private static final Pattern KV_USER = Pattern.compile(
            "\\b(?:user(?:name|_name|id|_id)?|login(?:_?id)?|account|uid|usr|id|사용자|계정)\\s*[=:]\\s*\"?" + USER,
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KV_IP = Pattern.compile(
            "\\b(?:src(?:ip|_ip)?|source(?:_?ip)?|client(?:_?ip)?|remote(?:_?(?:ip|addr))?|from|ip|sourceIPAddress|접속IP|접속 IP)\\s*[=:]\\s*\"?" + IP,
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_IP = Pattern.compile("\\b" + IP + "\\b");

    private static final Pattern AWS_LOGIN = Pattern.compile("\"eventName\"\\s*:\\s*\"ConsoleLogin\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_USER = Pattern.compile("\"userName\"\\s*:\\s*\"" + USER + "\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_IP = Pattern.compile("\"sourceIPAddress\"\\s*:\\s*\"" + IP + "\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_FAIL = Pattern.compile("\"ConsoleLogin\"\\s*:\\s*\"Failure\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOGOUT_WORD = Pattern.compile(
            "(log ?out|log ?off|sign ?out|로그아웃|세션 ?만료)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_DENIED_WORD = Pattern.compile(
            "(접속 ?ip ?제한|ip ?제한|ip ?차단|허용되지 않은 ?ip|미허용 ?ip|not allowed ip|ip (?:restrict|denied|blocked)|source ip not allowed)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_ONLY = Pattern.compile("^" + IP + "$");

    // ---- 표 형식 머리글 (소문자, 공백 1칸으로 정규화한 뒤 비교) ----
    private static final List<String> H_ACCOUNT_EXACT = List.of("account", "account id", "user", "user id", "userid", "username",
            "user name", "login id", "loginid", "id", "uid", "계정", "계정id", "계정 id", "아이디", "사용자 id", "사용자id",
            "관리자 id", "관리자 아이디", "사용자 아이디", "접속 계정", "로그인 id", "로그인 아이디");
    private static final List<String> H_ACCOUNT_CONTAINS = List.of("아이디", "account id", "user id", "userid", "login id", "계정id", "계정 id");
    private static final List<String> H_TIME_CONTAINS = List.of("일시", "시각", "시간", "일자", "time", "date");
    private static final List<String> H_TYPE_EXACT = List.of("유형", "구분", "이벤트", "행위", "동작", "작업", "작업 유형", "이벤트 유형",
            "로그 유형", "action", "event", "type", "event type", "event name", "eventname", "activity");
    private static final List<String> H_RESULT_CONTAINS = List.of("결과", "성공여부", "성공 여부", "result", "outcome");
    private static final List<String> H_CONTENT_EXACT = List.of("내용", "메시지", "message", "msg", "description", "설명",
            "로그 내용", "이벤트 내용", "작업 내용");
    private static final Pattern H_IP = Pattern.compile("(^|[^a-z])(ip|ip ?address|src|source|client)([^a-z]|$)");

    private static final Pattern LOGIN_WORD = Pattern.compile(
            "(log ?in|log ?on|sign ?in|auth(?:entication|enticate)?|session opened|connect(?:ed)?|접속|로그인|인증)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAIL_WORD = Pattern.compile(
            "(fail(?:ed|ure)?|denied|invalid|reject(?:ed)?|incorrect|wrong|error|locked|실패|거부|오류)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OK_WORD = Pattern.compile(
            "(success(?:ful)?|succeeded|accepted|allow(?:ed)?|permit(?:ted)?|opened|ok\\b|성공|허용)",
            Pattern.CASE_INSENSITIVE);

    /** 시각 추출: "12:34:56" 형태 (첫 번째 등장) */
    private static final Pattern TIME = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d):([0-5]\\d)\\b");
    /** 날짜+시각 문자열 (표시용) */
    private static final Pattern STAMP = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}|[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}|\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2})");

    /** 기본 사설 대역으로 내부 IP 판정 (테스트·하위 호환용) */
    public Result analyze(byte[] content, Charset charset) {
        return analyze(content, charset, AccessLogAnalyzer::isPrivateIp);
    }

    /**
     * @param isInternal 내부 IP 판정기 (관리 > 내부 IP 설정의 사용 중인 대역). false 면 외부 IP 로 집계
     */
    public Result analyze(byte[] content, Charset charset, java.util.function.Predicate<String> isInternal) {
        Map<String, Acc> map = new LinkedHashMap<>();
        int eventCount = 0;
        int successTotal = 0;
        int failTotal = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), charset))) {
            String line;
            int lineNo = 0;
            Table table = null;
            boolean headerChecked = false;
            while ((line = reader.readLine()) != null) {
                if (++lineNo > SecLogConstants.MAX_LINE_COUNT) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!headerChecked) {
                    // 첫 번째 내용 있는 줄이 표 머리글이면 표 형식으로 분석
                    headerChecked = true;
                    table = Table.detect(line.replace("\uFEFF", ""));
                    if (table != null) {
                        continue;
                    }
                }
                String target = line.length() > SecLogConstants.MAX_MATCH_LINE_LENGTH
                        ? line.substring(0, SecLogConstants.MAX_MATCH_LINE_LENGTH) : line;
                Event ev = table != null ? table.parse(target) : parse(target);
                if (ev == null) {
                    continue;
                }
                Acc acc = map.get(ev.account);
                if (acc == null) {
                    if (map.size() >= MAX_ACCOUNTS) {
                        continue;
                    }
                    acc = new Acc(ev.account, isInternal);
                    map.put(ev.account, acc);
                }
                acc.add(ev);
                eventCount++;
                if (ev.success) {
                    successTotal++;
                } else {
                    failTotal++;
                }
            }
        } catch (IOException e) {
            return Result.builder().accounts(List.of()).build();
        }

        List<AccountStat> stats = new ArrayList<>();
        for (Acc a : map.values()) {
            stats.add(a.toStat());
        }
        stats.sort(Comparator.comparingInt((AccountStat s) -> s.getRiskFlags().isEmpty() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(AccountStat::getFailCount).reversed())
                .thenComparing(Comparator.comparingInt(AccountStat::getSuccessCount).reversed()));

        return Result.builder()
                .accounts(stats)
                .eventCount(eventCount)
                .successTotal(successTotal)
                .failTotal(failTotal)
                .build();
    }

    /**
     * 일자별 집계 (대시보드 일별 수집 현황·추이, 월 점검 증적)
     * - 줄 번호는 LogAnalysisEngine 과 같은 방식(빈 줄 포함 1부터)이라 탐지 줄 번호로 일자를 찾을 수 있다.
     * - 일시를 읽을 수 없는 줄은 집계하지 않는다.
     *
     * @param detectedLines 탐지 룰에 걸린 줄 번호 → 건수 (null 허용)
     */
    public DailyResult analyzeDaily(byte[] content, Charset charset, int baseYear, int baseMonth,
                                    Map<Integer, Integer> detectedLines) {
        Map<LocalDate, DayStat> days = new TreeMap<>();
        int unknown = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), charset))) {
            String line;
            int lineNo = 0;
            Table table = null;
            boolean headerChecked = false;
            while ((line = reader.readLine()) != null) {
                if (++lineNo > SecLogConstants.MAX_LINE_COUNT) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!headerChecked) {
                    headerChecked = true;
                    table = Table.detect(line.replace("\uFEFF", ""));
                    if (table != null) {
                        continue;
                    }
                }
                String target = line.length() > SecLogConstants.MAX_MATCH_LINE_LENGTH
                        ? line.substring(0, SecLogConstants.MAX_MATCH_LINE_LENGTH) : line;
                // 표 형식은 '일시' 열에서, 그 외는 줄 앞쪽 날짜에서 읽는다 (다른 날짜 열 오인 방지)
                String dateText = table != null && table.iTime >= 0
                        ? Table.get(CsvSplit.split(target, table.sep), table.iTime) : target;
                LocalDate day = LogDateParser.parse(dateText, baseYear, baseMonth);
                if (day == null) {
                    unknown++; // 일시를 읽지 못한 줄 (일별 통계에서 빠짐)
                    continue;
                }
                DayStat d = days.computeIfAbsent(day, k -> new DayStat());
                d.lines++;
                if (detectedLines != null) {
                    d.detections += detectedLines.getOrDefault(lineNo, 0);
                }
                Event ev = table != null ? table.parse(target) : parse(target);
                if (ev == null) {
                    continue;
                }
                if (ev.success) {
                    d.success++;
                    if (isOffHour(ev.line)) {
                        d.offHour++;
                    }
                    if (PRIV_ACCOUNTS.contains(ev.account)) {
                        d.priv++;
                    }
                } else {
                    d.fail++;
                }
            }
        } catch (IOException e) {
            return new DailyResult(days, unknown);
        }
        return new DailyResult(days, unknown);
    }

    /** 일자별 집계 + 일시를 읽지 못한 줄 수 */
    public record DailyResult(Map<LocalDate, DayStat> days, int dateUnknownLines) {
    }

    /** 특권/공용 계정 여부 (대시보드 특권 계정 활동) */
    public static boolean isPrivAccount(String accountId) {
        return accountId != null && PRIV_ACCOUNTS.contains(accountId.toLowerCase(Locale.ROOT));
    }

    /** 일자별 집계 값 */
    public static final class DayStat {
        public int lines;
        public int success;
        public int fail;
        public int offHour;
        public int priv;
        public int detections;
    }

    // ------------------------------------------------------------------ 파싱

    Event parse(String line) {
        Matcher m;
        // 1) Linux sshd
        if ((m = SSH_OK.matcher(line)).find()) {
            return new Event(norm(m.group(1)), m.group(2), true, line);
        }
        if ((m = SSH_FAIL.matcher(line)).find()) {
            return new Event(norm(m.group(1)), m.group(2), false, line);
        }
        if ((m = SSH_INVALID.matcher(line)).find()) {
            return new Event(norm(m.group(1)), m.group(2), false, line);
        }
        // 2) AWS CloudTrail ConsoleLogin
        if (AWS_LOGIN.matcher(line).find()) {
            Matcher u = AWS_USER.matcher(line);
            Matcher ip = AWS_IP.matcher(line);
            if (u.find()) {
                return new Event(norm(u.group(1)), ip.find() ? ip.group(1) : null, !AWS_FAIL.matcher(line).find(), line);
            }
        }
        // 3) Windows 4624/4625
        Matcher w = WIN_EVENT.matcher(line);
        if (w.find()) {
            Matcher u = WIN_ACCOUNT.matcher(line);
            if (u.find()) {
                String acc = norm(u.group(1));
                if (!acc.isEmpty() && !acc.equals("-") && !acc.endsWith("$")) {
                    Matcher ip = WIN_IP.matcher(line);
                    return new Event(acc, ip.find() ? ip.group(1) : null, "4624".equals(w.group(1)), line);
                }
            }
        }
        // 4) key=value / CSV / 일반 텍스트 (로그인 키워드 + 성공/실패 키워드 + 계정)
        if (!LOGIN_WORD.matcher(line).find()) {
            return null;
        }
        boolean fail = FAIL_WORD.matcher(line).find();
        boolean ok = OK_WORD.matcher(line).find();
        if (!fail && !ok) {
            return null;
        }
        Matcher u = KV_USER.matcher(line);
        if (!u.find()) {
            return null;
        }
        String acc = norm(u.group(1));
        if (acc.isEmpty() || acc.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        Matcher ip = KV_IP.matcher(line);
        String ipVal = ip.find() ? ip.group(1) : null;
        if (ipVal == null) {
            Matcher any = ANY_IP.matcher(line);
            ipVal = any.find() ? any.group(1) : null;
        }
        return new Event(acc, ipVal, !fail, line);
    }

    // ------------------------------------------------------------------ 표 형식

    /** 머리글 기반 표 형식 (CSV / TSV / 엑셀 변환본) */
    static final class Table {
        final char sep;
        final int iAccount;
        final int iIp;
        final int iTime;
        final int iType;
        final int iResult;
        final int iContent;

        private Table(char sep, int iAccount, int iIp, int iTime, int iType, int iResult, int iContent) {
            this.sep = sep;
            this.iAccount = iAccount;
            this.iIp = iIp;
            this.iTime = iTime;
            this.iType = iType;
            this.iResult = iResult;
            this.iContent = iContent;
        }

        static Table detect(String header) {
            char sep = CsvSplit.detectSeparator(header);
            List<String> cols = CsvSplit.split(header, sep).stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT).replace("\"", "").replaceAll("\\s+", " ")).toList();
            if (cols.size() < 3 || cols.stream().anyMatch(c -> c.length() > 60)) {
                return null; // 머리글이 아님 (일반 로그 줄)
            }
            int acc = findExact(cols, H_ACCOUNT_EXACT);
            if (acc < 0) {
                acc = findContains(cols, H_ACCOUNT_CONTAINS, -1);
            }
            int type = findExact(cols, H_TYPE_EXACT);
            int result = findContains(cols, H_RESULT_CONTAINS, -1);
            int contentCol = findExact(cols, H_CONTENT_EXACT);
            if (acc < 0 || (type < 0 && result < 0 && contentCol < 0)) {
                return null;
            }
            int ip = -1;
            for (int i = 0; i < cols.size(); i++) {
                if (i != acc && H_IP.matcher(cols.get(i)).find()) {
                    ip = i;
                    break;
                }
            }
            int time = findContains(cols, H_TIME_CONTAINS, acc);
            return new Table(sep, acc, ip, time, type, result, contentCol);
        }

        Event parse(String line) {
            List<String> v = CsvSplit.split(line, sep);
            String acc = norm(get(v, iAccount));
            if (acc.isEmpty() || acc.equals("-")) {
                return null;
            }
            // 로그인 이벤트 여부는 '유형' 열이 있으면 유형 열로만 판단한다.
            //  (내용 열의 메뉴명 '장기 미접속 에이전트 현황' 같은 단어로 오인식 방지)
            // 성공/실패·IP 제한 사유는 유형 + 결과 + 내용 열에서 찾는다. (상세 정보 열은 사용하지 않음)
            String kind = iType >= 0 ? get(v, iType) : (get(v, iResult) + " " + get(v, iContent)).trim();
            if (!LOGIN_WORD.matcher(kind).find() || LOGOUT_WORD.matcher(kind).find()) {
                return null;
            }
            String text = (get(v, iType) + " " + get(v, iResult) + " " + get(v, iContent)).trim();
            boolean fail = FAIL_WORD.matcher(text).find();
            boolean denied = IP_DENIED_WORD.matcher(text).find();
            String ip = get(v, iIp);
            if (!IP_ONLY.matcher(ip).matches()) {
                Matcher any = ANY_IP.matcher(ip);
                ip = any.find() ? any.group(1) : null;
            }
            String time = get(v, iTime);
            return new Event(acc, ip, !fail && !denied, time.isEmpty() ? line : time, denied);
        }

        private static String get(List<String> v, int i) {
            return i >= 0 && i < v.size() ? v.get(i).trim() : "";
        }

        private static int findExact(List<String> cols, List<String> cands) {
            for (String c : cands) {
                int i = cols.indexOf(c);
                if (i >= 0) {
                    return i;
                }
            }
            return -1;
        }

        private static int findContains(List<String> cols, List<String> keys, int exclude) {
            for (int i = 0; i < cols.size(); i++) {
                if (i == exclude) {
                    continue;
                }
                for (String k : keys) {
                    if (cols.get(i).contains(k)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        String v = s.trim();
        int bs = v.lastIndexOf('\\');
        if (bs >= 0 && bs < v.length() - 1) {
            v = v.substring(bs + 1); // DOMAIN\\user -> user
        }
        return v.toLowerCase(Locale.ROOT);
    }

    /** 기본 사설·예약 대역 (09 스크립트 기본값과 같음) */
    static boolean isPrivateIp(String ip) {
        if (ip == null) {
            return true;
        }
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return true;
        }
        try {
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            return a == 10 || a == 127 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                    || (a == 169 && b == 254) || a == 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** UTC 표기 시각 (CloudTrail 등 "...T15:20:00Z" / "+00:00") → 한국시간(+9) 보정 대상 */
    private static final Pattern UTC_TIME = Pattern.compile(
            "T([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+)?(?:Z|\\+00:?00)");

    static boolean isOffHour(String line) {
        int h;
        Matcher u = UTC_TIME.matcher(line);
        if (u.find()) {
            h = (Integer.parseInt(u.group(1)) + 9) % 24; // KST
        } else {
            Matcher t = TIME.matcher(line);
            if (!t.find()) {
                return false;
            }
            h = Integer.parseInt(t.group(1));
        }
        return h >= 22 || h < 6;
    }

    static String stamp(String line) {
        Matcher m = STAMP.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------ 집계

    record Event(String account, String ip, boolean success, String line, boolean ipDenied) {
        Event(String account, String ip, boolean success, String line) {
            this(account, ip, success, line, false);
        }
    }

    private static final class Acc {
        final String account;
        int success;
        int fail;
        int offHour;
        int external;
        int ipDenied;
        final Set<String> ips = new LinkedHashSet<>();
        String first;
        String last;

        final java.util.function.Predicate<String> isInternal;

        Acc(String account, java.util.function.Predicate<String> isInternal) {
            this.account = account;
            this.isInternal = isInternal;
        }

        void add(Event e) {
            if (e.success) {
                success++;
                if (isOffHour(e.line)) {
                    offHour++;
                }
            } else {
                fail++;
            }
            if (e.ipDenied) {
                ipDenied++;
            }
            if (e.ip != null) {
                ips.add(e.ip);
                if (!isInternal.test(e.ip)) {
                    external++;
                }
            }
            String st = stamp(e.line);
            if (st != null) {
                // 로그가 최신순으로 정렬된 경우도 있으므로 시각 문자열 비교로 최초/최종을 정한다
                if (first == null || st.compareTo(first) < 0) {
                    first = st;
                }
                if (last == null || st.compareTo(last) > 0) {
                    last = st;
                }
            }
        }

        AccountStat toStat() {
            List<String> flags = new ArrayList<>();
            if (fail >= FAIL_MANY_THRESHOLD) {
                flags.add("FAIL_MANY");
            }
            if (offHour > 0) {
                flags.add("OFF_HOUR");
            }
            if (external > 0) {
                flags.add("EXTERNAL_IP");
            }
            if (ips.size() >= MULTI_IP_THRESHOLD) {
                flags.add("MULTI_IP");
            }
            if (PRIV_ACCOUNTS.contains(account)) {
                flags.add("PRIV_ACCOUNT");
            }
            if (ipDenied > 0) {
                flags.add("IP_DENIED");
            }
            String ipList = String.join(", ", ips.stream().limit(MAX_IP_LIST).toList())
                    + (ips.size() > MAX_IP_LIST ? " 외 " + (ips.size() - MAX_IP_LIST) + "개" : "");
            return AccountStat.builder()
                    .accountId(account.length() > 200 ? account.substring(0, 200) : account)
                    .successCount(success).failCount(fail).offHourCount(offHour).externalIpCount(external)
                    .ipCount(ips.size())
                    .ipList(ipList.length() > 1000 ? ipList.substring(0, 1000) : ipList)
                    .firstSeen(first).lastSeen(last)
                    .riskFlags(String.join(",", flags))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AccountStat {
        private final String accountId;
        private final int successCount;
        private final int failCount;
        private final int offHourCount;
        private final int externalIpCount;
        private final int ipCount;
        private final String ipList;
        private final String firstSeen;
        private final String lastSeen;
        private final String riskFlags;
    }

    @Getter
    @Builder
    public static class Result {
        private final List<AccountStat> accounts;
        private final int eventCount;
        private final int successTotal;
        private final int failTotal;
    }
}
