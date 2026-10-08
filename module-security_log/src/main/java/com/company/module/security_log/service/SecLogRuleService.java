package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.RuleResponse;
import com.company.module.security_log.dto.RuleSaveRequest;
import com.company.module.security_log.dto.RuleTestRequest;
import com.company.module.security_log.dto.RuleTestResponse;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.repository.SecLogRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 탐지 룰셋 관리 (룰셋 편집 화면)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogRuleService {

    private final SecLogRuleRepository ruleRepository;
    private final LogAnalysisEngine analysisEngine;

    public Page<RuleResponse> getList(String q, LogType logType, String useYn, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("ruleId")));
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        String use = StringUtils.hasText(useYn) ? useYn : null;
        return ruleRepository.search(keyword, logType, use, pageable).map(RuleResponse::from);
    }

    public RuleResponse getDetail(Long ruleId) {
        return RuleResponse.from(findRule(ruleId));
    }

    @Transactional
    public RuleResponse create(RuleSaveRequest request, Long userId) {
        if (!StringUtils.hasText(request.getRuleCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "룰 코드는 필수입니다.");
        }
        // 삭제된 룰 코드도 재사용 불가 (탐지 이력의 룰 코드 일관성 보장)
        if (ruleRepository.existsByRuleCode(request.getRuleCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 사용 중인 룰 코드입니다(삭제된 룰 포함): " + request.getRuleCode());
        }
        analysisEngine.validatePattern(request.getMatchType(), request.getPattern());

        SecLogRule rule = SecLogRule.builder()
                .ruleCode(request.getRuleCode())
                .ruleName(request.getRuleName())
                .description(request.getDescription())
                .logType(request.getLogType())
                .matchType(request.getMatchType())
                .pattern(request.getPattern())
                .severity(request.getSeverity())
                .thresholdCount(request.getThresholdCount())
                .useYn(request.getUseYn())
                .sortOrder(request.getSortOrder())
                .createdBy(userId)
                .build();

        return RuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public RuleResponse update(Long ruleId, RuleSaveRequest request, Long userId) {
        SecLogRule rule = findRule(ruleId);
        analysisEngine.validatePattern(request.getMatchType(), request.getPattern());

        rule.update(
                request.getRuleName(),
                request.getDescription(),
                request.getLogType(),
                request.getMatchType(),
                request.getPattern(),
                request.getSeverity(),
                request.getThresholdCount(),
                request.getUseYn(),
                request.getSortOrder(),
                userId
        );
        return RuleResponse.from(rule);
    }

    @Transactional
    public RuleResponse changeUseYn(Long ruleId, String useYn, Long userId) {
        SecLogRule rule = findRule(ruleId);
        rule.changeUseYn(useYn, userId);
        return RuleResponse.from(rule);
    }

    @Transactional
    public void delete(Long ruleId, Long userId) {
        SecLogRule rule = findRule(ruleId);
        rule.delete(userId);
    }

    /** 패턴 사전 테스트 (저장하지 않음) */
    public RuleTestResponse test(RuleTestRequest request) {
        List<LogAnalysisEngine.MatchedLine> matched = analysisEngine.test(
                request.getMatchType(), request.getPattern(), request.getSampleLog(),
                SecLogConstants.MAX_TEST_RESULT_LINES);

        return RuleTestResponse.builder()
                .totalLineCount(analysisEngine.countLines(request.getSampleLog()))
                .matchedCount(matched.size())
                .matchedLines(matched.stream()
                        .map(m -> RuleTestResponse.MatchedLine.builder()
                                .lineNumber(m.lineNumber())
                                .content(m.content())
                                .build())
                        .toList())
                .build();
    }

    /** 분석용 활성 룰 조회 (해당 로그 유형 + ALL) */
    public List<SecLogRule> getActiveRules(LogType logType) {
        return ruleRepository.findActiveRules(List.of(logType, LogType.ALL));
    }

    private SecLogRule findRule(Long ruleId) {
        return ruleRepository.findByRuleIdAndDeletedYn(ruleId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 50;
        }
        return Math.min(size, SecLogConstants.MAX_PAGE_SIZE);
    }
}
