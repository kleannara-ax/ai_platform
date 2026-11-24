package com.company.module.steamenergy.legacy.service.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 작은 Excel 수식 평가기. 세부 운영내역 시트에 실제 쓰이는 함수만 지원:
 * - SUM(range or list of cells/numbers)
 * - AVERAGE(range or list of cells/numbers)
 * - IFERROR(expr, fallback)
 * - 산술 + − × ÷, 단항 부호, 괄호
 * - 셀 참조 (A1, AH103, $A$1)
 * - 셀 범위 (A1:AG1)
 * - 숫자 리터럴
 *
 * 미지원: VLOOKUP, IF, INDEX, MATCH, 문자열 함수 등 (시트에 없음).
 */
public final class FormulaEvaluator {

    private FormulaEvaluator() {}

    public static Object eval(String formula, SheetData sheet) {
        if (formula == null) return null;
        try {
            return new Parser(formula, sheet).parseExpr();
        } catch (Exception ex) {
            return null;  // 평가 실패 시 빈 값 (Excel #VALUE! 같은 의미)
        }
    }

    /** 셀 범위 (예: "A1:AG1") → 셀 주소 리스트로 확장. */
    public static List<String> expandRange(String range) {
        int colon = range.indexOf(':');
        if (colon < 0) return List.of(range);
        String from = range.substring(0, colon).replace("$", "");
        String to = range.substring(colon + 1).replace("$", "");
        int[] f = SheetData.parseRef(from);
        int[] t = SheetData.parseRef(to);
        int c1 = Math.min(f[0], t[0]), c2 = Math.max(f[0], t[0]);
        int r1 = Math.min(f[1], t[1]), r2 = Math.max(f[1], t[1]);
        List<String> out = new ArrayList<>();
        for (int r = r1; r <= r2; r += 1) {
            for (int c = c1; c <= c2; c += 1) {
                out.add(SheetData.columnLetter(c) + r);
            }
        }
        return out;
    }

    /** 토큰 기반 재귀하강 파서. */
    private static class Parser {
        private final String src;
        private final SheetData sheet;
        private int pos = 0;

        Parser(String src, SheetData sheet) {
            this.src = src;
            this.sheet = sheet;
        }

        Object parseExpr() {
            return parseAddSub();
        }

        private Double parseAddSub() {
            Double left = parseMulDiv();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '+' || c == '-') {
                    pos += 1;
                    Double right = parseMulDiv();
                    if (left == null) left = 0d;
                    if (right == null) right = 0d;
                    left = (c == '+') ? left + right : left - right;
                } else break;
            }
            return left;
        }

        private Double parseMulDiv() {
            Double left = parseUnary();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '*' || c == '/') {
                    pos += 1;
                    Double right = parseUnary();
                    if (left == null) left = 0d;
                    if (right == null || right == 0d) {
                        if (c == '/') return null;  // div-by-zero 는 #DIV/0! — null 로
                        right = 0d;
                    }
                    left = (c == '*') ? left * right : left / right;
                } else break;
            }
            return left;
        }

        private Double parseUnary() {
            skipWs();
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                char sign = src.charAt(pos);
                pos += 1;
                Double v = parseUnary();
                if (v == null) return null;
                return sign == '-' ? -v : v;
            }
            return parsePrimary();
        }

        private Double parsePrimary() {
            skipWs();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '(') {
                pos += 1;
                Double v = parseAddSub();
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ')') pos += 1;
                return v;
            }
            // 함수 또는 셀 참조 또는 숫자
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '$')) {
                    pos += 1;
                }
                String token = src.substring(start, pos);
                if (pos < src.length() && src.charAt(pos) == '(') {
                    pos += 1;
                    Object result = callFunction(token.toUpperCase());
                    skipWs();
                    if (pos < src.length() && src.charAt(pos) == ')') pos += 1;
                    return result instanceof Number n ? n.doubleValue() : null;
                }
                // 셀 참조 또는 범위 시작
                String ref = token.replace("$", "");
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos += 1;
                    int s2 = pos;
                    while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '$')) {
                        pos += 1;
                    }
                    String end = src.substring(s2, pos).replace("$", "");
                    // 범위는 산술에서 직접 못 씀, SUM 등 함수 컨텍스트에서만 의미.
                    // 여기에 도달했다면 비정상 → 첫 셀만 사용.
                    return sheet.evaluateNumeric(ref);
                }
                return sheet.evaluateNumeric(ref);
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                    pos += 1;
                }
                try { return Double.parseDouble(src.substring(start, pos)); }
                catch (NumberFormatException e) { return null; }
            }
            return null;
        }

        private Object callFunction(String name) {
            // 인자 리스트 파싱 — 콤마 구분. 범위 인자도 처리.
            List<Object> args = new ArrayList<>();
            skipWs();
            if (pos >= src.length() || src.charAt(pos) == ')') return computeFunction(name, args);
            while (true) {
                Object arg = parseArgument();
                args.add(arg);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos += 1;
                    continue;
                }
                break;
            }
            return computeFunction(name, args);
        }

        /** 함수 인자: 셀 범위면 String "A1:B2", 그 외엔 Double. */
        private Object parseArgument() {
            skipWs();
            int saved = pos;
            // 범위 패턴 시도: [A-Z]+\d+:[A-Z]+\d+
            Pattern rangePat = Pattern.compile("\\$?[A-Z]+\\$?[0-9]+:\\$?[A-Z]+\\$?[0-9]+", Pattern.CASE_INSENSITIVE);
            Matcher m = rangePat.matcher(src);
            if (m.find(pos) && m.start() == pos) {
                pos = m.end();
                return "RANGE:" + m.group().replace("$", "");
            }
            // 그 외엔 일반 expression
            return parseAddSub();
        }

        private Object computeFunction(String name, List<Object> args) {
            switch (name) {
                case "SUM": {
                    double sum = 0;
                    for (Object a : args) sum += sumArg(a);
                    return sum;
                }
                case "AVERAGE": {
                    double sum = 0;
                    int cnt = 0;
                    for (Object a : args) {
                        int[] count = new int[]{0};
                        sum += sumArgCounted(a, count);
                        cnt += count[0];
                    }
                    return cnt == 0 ? null : sum / cnt;
                }
                case "IFERROR": {
                    // arg0 평가 시 null/예외였으면 arg1 반환
                    Object a0 = args.size() > 0 ? args.get(0) : null;
                    if (a0 instanceof Number) return a0;
                    if (a0 == null) return args.size() > 1 ? args.get(1) : null;
                    return a0;
                }
                default:
                    return null;
            }
        }

        private double sumArg(Object a) {
            if (a == null) return 0;
            if (a instanceof Number n) return n.doubleValue();
            String s = a.toString();
            if (s.startsWith("RANGE:")) {
                double sum = 0;
                for (String cell : expandRange(s.substring(6))) {
                    Double v = sheet.evaluateNumeric(cell);
                    if (v != null) sum += v;
                }
                return sum;
            }
            return 0;
        }

        private double sumArgCounted(Object a, int[] count) {
            if (a == null) return 0;
            if (a instanceof Number n) { count[0]++; return n.doubleValue(); }
            String s = a.toString();
            if (s.startsWith("RANGE:")) {
                double sum = 0;
                for (String cell : expandRange(s.substring(6))) {
                    Double v = sheet.evaluateNumeric(cell);
                    if (v != null) { sum += v; count[0]++; }
                }
                return sum;
            }
            return 0;
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos += 1;
        }
    }
}
