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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 로그 이상징후 탐지 엔진 (상태 없음)
 *
 * <pre>
 * - KEYWORD : 쉼표(,)로 구분된 키워드 중 하나라도 포함되면 매칭 (대소문자 무시)
 * - REGEX   : Java 정규식 find() 매칭 (대소문자 무시)
 * - 임계 건수(THRESHOLD_COUNT) : 파일 내 매칭 건수가 임계값 이상일 때만 탐지로 확정
 *   예) 로그인 실패 5회 이상
 * - ReDoS 방지 : 라인 길이 제한 + 매칭 시간 제한
 * </pre>
 */
@Component
public class LogAnalysisEngine {

    /**
     * 로그 파일 분석
     *
     * @param content 원본 로그 바이트
     * @param charset 파일 인코딩
     * @param rules   적용할 활성 룰 목록
     */
    public AnalysisResult analyze(byte[] content, Charset charset, List<SecLogRule> rules) {
        List<CompiledRule> compiledRules = rules.stream().map(this::compile).toList();

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

        // 임계 건수 적용 + 업로드당 최대 저장 건수 적용
        List<RuleHit> hits = new ArrayList<>();
        int stored = 0;
        for (CompiledRule cr : compiledRules) {
            Long ruleId = cr.rule().getRuleId();
            int hitCount = hitCountByRule.get(ruleId);
            if (hitCount == 0 || hitCount < cr.rule().getThresholdCount()) {
                continue;
            }
            List<MatchedLine> lines = matchedByRule.get(ruleId);
            int remain = SecLogConstants.MAX_DETECTION_PER_UPLOAD - stored;
            if (remain <= 0) {
                break;
            }
            if (lines.size() > remain) {
                lines = lines.subList(0, remain);
            }
            stored += lines.size();
            hits.add(new RuleHit(cr.rule(), hitCount, lines));
        }

        return AnalysisResult.builder()
                .totalLineCount(lineNumber)
                .ruleHits(hits)
                .build();
    }

    /** 룰셋 편집 화면 - 패턴 테스트 */
    public List<MatchedLine> test(MatchType matchType, String pattern, String sampleLog, int limit) {
        CompiledRule cr = compile(matchType, pattern, null);
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

    /** 패턴 유효성 검증 (룰 저장 시) */
    public void validatePattern(MatchType matchType, String pattern) {
        compile(matchType, pattern, null);
    }

    public int countLines(String sampleLog) {
        return sampleLog.split("\\r?\\n").length;
    }

    // ------------------------------------------------------------------

    private CompiledRule compile(SecLogRule rule) {
        return compile(rule.getMatchType(), rule.getPattern(), rule);
    }

    private CompiledRule compile(MatchType matchType, String pattern, SecLogRule rule) {
        if (pattern == null || pattern.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탐지 패턴은 필수입니다.");
        }
        if (matchType == MatchType.REGEX) {
            try {
                Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                return new CompiledRule(rule, compiled, null);
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
        return new CompiledRule(rule, null, keywords);
    }

    private static String truncate(String line) {
        return line.length() > SecLogConstants.MAX_LOG_CONTENT_LENGTH
                ? line.substring(0, SecLogConstants.MAX_LOG_CONTENT_LENGTH)
                : line;
    }

    // ------------------------------------------------------------------

    private record CompiledRule(SecLogRule rule, Pattern regex, List<String> keywords) {

        boolean matches(String line) {
            if (regex != null) {
                try {
                    return regex.matcher(TimeLimitedCharSequence.of(line, SecLogConstants.REGEX_TIMEOUT_MS)).find();
                } catch (RegexTimeoutException e) {
                    // 시간 초과 라인은 매칭 실패로 간주 (분석 전체 중단 방지)
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

    @Getter
    @Builder
    public static class AnalysisResult {
        private final int totalLineCount;
        private final List<RuleHit> ruleHits;
    }
}
