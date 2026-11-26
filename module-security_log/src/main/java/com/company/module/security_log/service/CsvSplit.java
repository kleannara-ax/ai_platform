package com.company.module.security_log.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 표 형식 로그(CSV/TSV/세미콜론) 한 줄 분리 도우미
 * - 접속 행위 분석(AccessLogAnalyzer)에서 머리글/열 위치를 읽을 때 사용
 */
final class CsvSplit {

    private CsvSplit() {
    }

    /** 머리글 줄에서 구분자 판단 (탭 > 세미콜론 > 쉼표) */
    static char detectSeparator(String header) {
        int c = count(header, ','), t = count(header, '\t'), s = count(header, ';');
        if (t >= c && t >= s && t > 0) {
            return '\t';
        }
        if (s > c) {
            return ';';
        }
        return ',';
    }

    /** 따옴표를 고려한 단순 CSV 분리 */
    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    q = !q;
                }
            } else if (ch == sep && !q) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString().trim());
        return out;
    }

    private static int count(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ch) {
                n++;
            }
        }
        return n;
    }
}
