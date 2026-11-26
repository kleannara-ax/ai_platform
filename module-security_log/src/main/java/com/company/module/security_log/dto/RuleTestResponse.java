package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RuleTestResponse {

    private int totalLineCount;
    private int matchedCount;
    private List<MatchedLine> matchedLines;

    @Getter
    @Builder
    public static class MatchedLine {
        private int lineNumber;
        private String content;
    }
}
