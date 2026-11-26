package com.company.module.security_log.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IPv4 대역 (CIDR) 파싱·비교
 * - 입력: "10.0.0.0/8" 또는 단일 IP "203.0.113.10" (→ /32)
 * - 호스트 비트가 섞인 CIDR(10.1.2.3/8)은 네트워크 주소(10.0.0.0/8)로 맞춘다.
 * - 잘못된 형식이면 null (호출 측에서 업무 오류로 바꿈)
 */
public final class IpRange {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final String cidr;
    private final long start;
    private final long end;

    private IpRange(String cidr, long start, long end) {
        this.cidr = cidr;
        this.start = start;
        this.end = end;
    }

    /** @return 형식이 틀리면 null */
    public static IpRange parse(String text) {
        if (text == null) {
            return null;
        }
        String v = text.trim();
        int prefix = 32;
        int slash = v.indexOf('/');
        if (slash >= 0) {
            String p = v.substring(slash + 1).trim();
            if (!p.matches("\\d{1,2}")) {
                return null;
            }
            prefix = Integer.parseInt(p);
            if (prefix > 32) {
                return null;
            }
            v = v.substring(0, slash).trim();
        }
        Long ip = toLong(v);
        if (ip == null) {
            return null;
        }
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long start = ip & mask;
        long end = start | (~mask & 0xFFFFFFFFL);
        return new IpRange(toText(start) + "/" + prefix, start, end);
    }

    /** IPv4 문자열 → 숫자 (IPv6·형식 오류는 null) */
    public static Long toLong(String ip) {
        if (ip == null) {
            return null;
        }
        Matcher m = IPV4.matcher(ip.trim());
        if (!m.matches()) {
            return null;
        }
        long v = 0;
        for (int i = 1; i <= 4; i++) {
            int o = Integer.parseInt(m.group(i));
            if (o > 255) {
                return null;
            }
            v = (v << 8) | o;
        }
        return v;
    }

    public static String toText(long v) {
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    public String getCidr() {
        return cidr;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public int getPrefix() {
        return Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1));
    }

    public long size() {
        return end - start + 1;
    }
}
