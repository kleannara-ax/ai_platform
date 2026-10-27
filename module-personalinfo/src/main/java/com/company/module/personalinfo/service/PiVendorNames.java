package com.company.module.personalinfo.service;

import java.util.Locale;

/**
 * 수탁업체명 비교 규칙.
 * ㈜, (주), 주식회사, 공백, 대소문자 차이는 같은 업체로 본다.
 */
public final class PiVendorNames {

    private PiVendorNames() {
    }

    /** 업체 그룹 키 (정규화한 업체명) */
    public static String key(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "")
                .replace("㈜", "").replace("(주)", "").replace("（주）", "").replace("(株)", "")
                .replace("주식회사", "")
                .toLowerCase(Locale.ROOT);
    }

    /** 같은 업체 (정확히 같은 키) */
    public static boolean same(String a, String b) {
        String x = key(a);
        return !x.isEmpty() && x.equals(key(b));
    }

    /** 비슷한 업체 (한쪽이 다른 쪽을 포함). 체크리스트 회사명으로 업체를 찾을 때 사용 */
    public static boolean similar(String a, String b) {
        String x = key(a);
        String y = key(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }
}
