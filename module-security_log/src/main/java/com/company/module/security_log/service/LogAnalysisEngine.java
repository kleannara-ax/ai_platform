package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.SecLogRule;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 로그 이상징후 탐지 엔진 (상태 없음)
 *
 * <pre>
 * - KEYWORD : 쉼표(,)로 구분된 키워드 중 하나라도 포함되면 매칭 (대소문자 무시)
 * - REGEX   : Java 정규식 find() 매칭 (대소문자 무시)
 * - EXTERNAL_IP : 줄에서 처음 나오는 IPv4 가 내부 IP 대역(관리 › 내부 IP 설정)에 없고, 패턴(대상 줄 조건 정규식)에도 맞으면 매칭
 * - 임계 건수(THRESHOLD_COUNT) : 파일 내 매칭 건수가 임계값 이상일 때만 탐지로 확정
 *   예) 로그인 실패 5회 이상
 * - ReDoS 방지 : 라인 길이 제한 + 매칭 시간 제한
 * </pre>
 */
@Component
public class LogAnalysisEngine {

    /** 줄 안의 IPv4 (앞뒤가 숫자·점이 아닌 것) */
    private static final Pattern IPV4_IN_LINE = Pattern.compile(
            "(?<![\\d.])((?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})(?![\\d.])");

    /** 내부 IP 판정기가 없을 때(테스트 등): 모든 IP 를 내부로 봄 → EXTERNAL_IP 룰은 일치 없음 */
    private static final Predicate<String> ALL_INTERNAL = ip -> true;

    /** 줄에서 처음 나오는 IPv4 (없으면 null) */
    static String firstIpv4(String line) {
        Matcher m = IPV4_IN_LINE.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 로그 파일 분석
     *
     * @param content 원본 로그 바이트
     * @param charset 파일 인코딩
     * @param rules   적용할 활성 룰 목록
     */
    public AnalysisResult analyze(byte[] content, Charset charset, List<SecLogRule> rules) {
        return analyze(content, charset, rules, ALL_INTERNAL);
    }

    /**
     * 로그 파일 분석 (내부 IP 판정기 포함)
     *
     * @param isInternal 내부 IP 판정기 (관리 › 내부 IP 설정의 사용 중 대역) — EXTERNAL_IP 룰에 사용
     */
    public AnalysisResult analyze(byte[] content, Charset charset, List<SecLogRule> rules, Predicate<String> isInternal) {
        Predicate<String> internal = isInternal == null ? ALL_INTERNAL : isInternal;
        List<CompiledRule> compiledRules = rules.stream().map(r -> compile(r.getMatchType(), r.getPattern(), r, internal)).toList();

        // 룰별 매칭 라인 (최대 저장 건수까지만 보관), 실제 매칭 건수
        Map<Long, List<MatchedLine>> matchedByRule = new LinkedHashMap<>();
        Map<Long, Integer> hitCountByRule = new LinkedHashMap<>();
        for (CompiledRule cr : compiledRules) {
            matchedByRule.put(cr.rule().getRuleId(), new ArrayList<>());
            hitCountByRule.put(cr.rule().getRuleId(), 0);
        }

        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber > SecLogConstants.MAX_LINE_COUNT) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그 파일은 최대 2,000,000 라인까지 분석할 수 있습니다.");
                }
                if (line.isBlank()) {
                    continue;
                }
                String target = line.length() > SecLogConstants.MAX_MATCH_LINE_LENGTH
                        ? line.substring(0, SecLogConstants.MAX_MATCH_LINE_LENGTH)
                        : line;

                for (CompiledRule cr : compiledRules) {
                    if (cr.matches(target)) {
                        Long ruleId = cr.rule().getRuleId();
                        hitCountByRule.merge(ruleId, 1, Integer::sum);
                        List<MatchedLine> list = matchedByRule.get(ruleId);
                        if (list.size() < SecLogConstants.MAX_DETECTION_PER_RULE) {
                            list.add(new MatchedLine(lineNumber, truncate(line)));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그 파일을 읽을 수 없습니다. 인코딩을 확인하세요.");
        }

        // 임계 건수 적용 + 업로드당 최대 저장 건수 적용 + 룰별 실행 결과(분석 완료 / 실패) 기록
        List<RuleHit> hits = new ArrayList<>();
        List<RuleRun> runs = new ArrayList<>();
        int stored = 0;
        for (CompiledRule cr : compiledRules) {
            Long ruleId = cr.rule().getRuleId();
            int hitCount = hitCountByRule.get(ruleId);
            int timeouts = cr.timeouts()[0];
            boolean qualified = hitCount > 0 && hitCount >= cr.rule().getThresholdCount();
            int storedHere = 0;
            if (qualified) {
                List<MatchedLine> lines = matchedByRule.get(ruleId);
                int remain = Math.max(0, SecLogConstants.MAX_DETECTION_PER_UPLOAD - stored);
                if (lines.size() > remain) {
                    lines = lines.subList(0, remain);
                }
                if (!lines.isEmpty()) {
                    stored += lines.size();
                    storedHere = lines.size();
                    hits.add(new RuleHit(cr.rule(), hitCount, lines));
                }
            }
            String fail = null;
            if (timeouts > 0) {
                fail = "패턴 검사 시간 초과 " + timeouts + "줄 (해당 줄 판정 불가)";
            } else if (qualified && storedHere == 0) {
                fail = "업로드당 탐지 저장 상한(" + SecLogConstants.MAX_DETECTION_PER_UPLOAD + "건) 초과로 결과 미저장";
            }
            runs.add(new RuleRun(cr.rule(), hitCount, storedHere, timeouts, fail));
        }

        return AnalysisResult.builder()
                .totalLineCount(lineNumber)
                .ruleHits(hits)
                .ruleRuns(runs)
                .build();
    }

    /** 룰셋 편집 화면 - 패턴 테스트 */
    public List<MatchedLine> test(MatchType matchType, String pattern, String sampleLog, int limit) {
        return test(matchType, pattern, sampleLog, limit, ALL_INTERNAL);
    }

    /** 룰셋 편집 화면 - 패턴 테스트 (내부 IP 판정기 포함) */
    public List<MatchedLine> test(MatchType matchType, String pattern, String sampleLog, int limit, Predicate<String> isInternal) {
        CompiledRule cr = compile(matchType, pattern, null, isInternal == null ? ALL_INTERNAL : isInternal);
        List<MatchedLine> result = new ArrayList<>();
        String[] lines = sampleLog.split("\\r?\\n");
        for (int i = 0; i < lines.length && result.size() < limit; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String target = line.length() > SecLogConstants.MAX_MATCH_LINE_LENGTH
                    ? line.substring(0, SecLogConstants.MAX_MATCH_LINE_LENGTH)
                    : line;
            if (cr.matches(target)) {
                result.add(new MatchedLine(i + 1, truncate(line)));
            }
        }
        return result;
    }

    /**
     * 로그 한 줄이 룰에 걸린 이유 설명 (룰셋 분석 근거 화면)
     * - KEYWORD: 처음 일치한 키워드와 위치 / REGEX: 정규식 일치 부분 / EXTERNAL_IP: 줄의 첫 IPv4 위치(내부 대역 밖)
     * - 패턴 오류·시간 초과·불일치(분석 이후 룰·내부 IP 설정 변경 등)는 matched=false
     */
    public MatchExplain explain(MatchType matchType, String pattern, String line, Predicate<String> isInternal) {
        if (line == null) {
            return MatchExplain.none(null);
        }
        CompiledRule cr;
        try {
            cr = compile(matchType, pattern, null, isInternal == null ? ALL_INTERNAL : isInternal);
        } catch (BusinessException e) {
            return MatchExplain.none(null);
        }
        String target = line.length() > SecLogConstants.MAX_MATCH_LINE_LENGTH
                ? line.substring(0, SecLogConstants.MAX_MATCH_LINE_LENGTH) : line;
        if (cr.externalOf() != null) {
            Matcher im = IPV4_IN_LINE.matcher(target);
            if (!im.find()) {
                return MatchExplain.none(null);
            }
            String ip = im.group(1);
            if (cr.externalOf().test(ip) || !cr.matches(target)) {
                return MatchExplain.none(ip);
            }
            return new MatchExplain(true, ip, im.start(1), im.end(1), ip, null);
        }
        if (cr.regex() != null) {
            try {
                Matcher m = cr.regex().matcher(TimeLimitedCharSequence.of(target, SecLogConstants.REGEX_TIMEOUT_MS));
                if (m.find()) {
                    return new MatchExplain(true, target.substring(m.start(), m.end()), m.start(), m.end(), null, null);
                }
            } catch (RegexTimeoutException e) {
                return MatchExplain.none(null);
            }
            return MatchExplain.none(null);
        }
        String lower = target.toLowerCase(Locale.ROOT);
        for (String k : cr.keywords()) {
            int i = lower.indexOf(k);
            if (i >= 0) {
                return new MatchExplain(true, target.substring(i, i + k.length()), i, i + k.length(), null, k);
            }
        }
        return MatchExplain.none(null);
    }

    /** 패턴 유효성 검증 (룰 저장 시) */
    public void validatePattern(MatchType matchType, String pattern) {
        compile(matchType, pattern, null, ALL_INTERNAL);
    }

    public int countLines(String sampleLog) {
        return sampleLog.split("\\r?\\n").length;
    }

    // ------------------------------------------------------------------

    private CompiledRule compile(MatchType matchType, String pattern, SecLogRule rule, Predicate<String> isInternal) {
        if (pattern == null || pattern.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    matchType == MatchType.EXTERNAL_IP ? "대상 줄 조건을 입력하세요 (모든 줄은 .*)." : "탐지 패턴은 필수입니다.");
        }
        if (matchType == MatchType.REGEX || matchType == MatchType.EXTERNAL_IP) {
            try {
                Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                return new CompiledRule(rule, compiled, null, matchType == MatchType.EXTERNAL_IP ? isInternal : null, new int[1]);
            } catch (PatternSyntaxException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정규식 문법 오류: " + e.getDescription());
            }
        }
        List<String> keywords = Arrays.stream(pattern.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        if (keywords.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "키워드를 1개 이상 입력하세요(쉼표 구분).");
        }
        return new CompiledRule(rule, null, keywords, null, new int[1]);
    }

    private static String truncate(String line) {
        return line.length() > SecLogConstants.MAX_LOG_CONTENT_LENGTH
                ? line.substring(0, SecLogConstants.MAX_LOG_CONTENT_LENGTH)
                : line;
    }

    // ------------------------------------------------------------------

    /** externalOf != null 이면 EXTERNAL_IP 룰 (줄의 첫 IPv4 가 내부 IP 가 아닐 때만 regex 검사) */
    private record CompiledRule(SecLogRule rule, Pattern regex, List<String> keywords, Predicate<String> externalOf, int[] timeouts) {

        boolean matches(String line) {
            if (externalOf != null) {
                String ip = firstIpv4(line);
                if (ip == null || externalOf.test(ip)) {
                    return false;
                }
            }
            if (regex != null) {
                try {
                    return regex.matcher(TimeLimitedCharSequence.of(line, SecLogConstants.REGEX_TIMEOUT_MS)).find();
                } catch (RegexTimeoutException e) {
                    // 시간 초과 라인은 매칭 실패로 간주 (분석 전체 중단 방지) + 룰별 시간 초과 줄 수 기록
                    timeouts[0]++;
                    return false;
                }
            }
            String lower = line.toLowerCase(Locale.ROOT);
            for (String k : keywords) {
                if (lower.contains(k)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 정규식 매칭 시간 제한용 CharSequence */
    private static final class TimeLimitedCharSequence implements CharSequence {
        private final CharSequence inner;
        private final long deadline;
        private int counter;

        private TimeLimitedCharSequence(CharSequence inner, long deadline) {
            this.inner = inner;
            this.deadline = deadline;
        }

        static TimeLimitedCharSequence of(CharSequence inner, long timeoutMs) {
            return new TimeLimitedCharSequence(inner, System.currentTimeMillis() + timeoutMs);
        }

        @Override
        public char charAt(int index) {
            if ((++counter & 0x3FF) == 0 && System.currentTimeMillis() > deadline) {
                throw new RegexTimeoutException();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new TimeLimitedCharSequence(inner.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    /** 내부 제어 흐름용 (외부로 전파되지 않음) */
    private static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException() {
            super(null, null, false, false);
        }
    }

    // ------------------------------------------------------------------

    public record MatchedLine(int lineNumber, String content) {
    }

    public record RuleHit(SecLogRule rule, int hitCount, List<MatchedLine> lines) {
    }

    /**
     * 한 줄 일치 설명
     *
     * @param text    일치 부분 (KEYWORD 는 원문 표기 그대로, EXTERNAL_IP 는 IP)
     * @param start   줄 안 시작 위치 (불일치면 -1)
     * @param ip      EXTERNAL_IP 룰에서 판정한 IP (불일치여도 IP 가 있으면 채움)
     * @param keyword KEYWORD 룰에서 일치한 키워드 (소문자)
     */
    public record MatchExplain(boolean matched, String text, int start, int end, String ip, String keyword) {
        static MatchExplain none(String ip) {
            return new MatchExplain(false, null, -1, -1, ip, null);
        }
    }

    /**
     * 룰별 실행 결과 (탐지 0건 룰 포함)
     *
     * @param failReason null 이면 분석 완료, 값이 있으면 분석 실패 사유
     */
    public record RuleRun(SecLogRule rule, int matchCount, int storedCount, int timeoutLines, String failReason) {
    }

    @Getter
    @Builder
    public static class AnalysisResult {
        private final int totalLineCount;
        private final List<RuleHit> ruleHits;
        private final List<RuleRun> ruleRuns;
    }
}
