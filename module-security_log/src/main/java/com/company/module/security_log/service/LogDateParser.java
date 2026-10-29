package com.company.module.security_log.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그 한 줄에서 로그 일자(한국시간)를 읽는다.
 * - 2026-10-02 09:01:11 / 2026-10-02T00:20:00Z(UTC → +9h) / 2026.10.02 / 2026/10/02
 * - syslog "Oct  2 09:01:11" (연도 없음 → 기준 연도, 기준 월보다 2달 이상 뒤면 전년도)
 * - Apache "02/Oct/2026:09:01:11"
 */
final class LogDateParser {

    private LogDateParser() {
    }

    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?)?");
    private static final Pattern DOT = Pattern.compile("\\b(\\d{4})[./](\\d{1,2})[./](\\d{1,2})\\b");
    private static final Pattern SYSLOG = Pattern.compile("\\b([A-Z][a-z]{2})\\s+(\\d{1,2})\\s+\\d{2}:\\d{2}:\\d{2}\\b");
    private static final Pattern APACHE = Pattern.compile("\\b(\\d{2})/([A-Za-z]{3})/(\\d{4}):\\d{2}:\\d{2}:\\d{2}");
    private static final Map<String, Integer> MON = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    /**
     * @param baseYear  syslog 처럼 연도가 없는 형식에 쓸 기준 연도 (업로드 대상 연월의 연도)
     * @param baseMonth 기준 월 (연말·연초 경계 보정용)
     * @return 읽지 못하면 null
     */
    static LocalDate parse(String text, int baseYear, int baseMonth) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            Matcher m = ISO.matcher(text);
            if (m.find()) {
                LocalDate d = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                if (m.group(4) != null && m.group(7) != null && isUtc(m.group(7))) {
                    return LocalDateTime.of(d.getYear(), d.getMonth(), d.getDayOfMonth(),
                            Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))).plusHours(9).toLocalDate();
                }
                return d;
            }
            m = APACHE.matcher(text);
            if (m.find()) {
                Integer mon = MON.get(m.group(2).toLowerCase(Locale.ROOT));
                if (mon != null) {
                    return LocalDate.of(Integer.parseInt(m.group(3)), mon, Integer.parseInt(m.group(1)));
                }
            }
            m = DOT.matcher(text);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = SYSLOG.matcher(text);
            if (m.find()) {
                Integer mon = MON.get(m.group(1).toLowerCase(Locale.ROOT));
                if (mon != null) {
                    int year = mon - baseMonth >= 2 ? baseYear - 1 : baseYear;
                    return LocalDate.of(year, mon, Integer.parseInt(m.group(2)));
                }
            }
        } catch (RuntimeException e) {
            return null; // 2026-13-45 같은 잘못된 날짜
        }
        return null;
    }

    private static boolean isUtc(String zone) {
        return "Z".equals(zone) || zone.replace(":", "").equals("+0000");
    }
}
