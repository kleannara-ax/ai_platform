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
import com.company.module.security_log.entity.SecLogSystem;
import com.company.module.security_log.repository.SecLogRuleRepository;
import com.company.module.security_log.repository.SecLogSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 탐지 룰셋 관리 (룰셋 편집 화면)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogRuleService {

    private final SecLogRuleRepository ruleRepository;
    private final SecLogSystemRepository systemRepository;
    private final LogAnalysisEngine analysisEngine;

    /**
     * 룰 목록
     * @param scope    COMMON(공통) / SYSTEM(시스템 전용) / 비움(전체)
     * @param systemId 지정 시 그 시스템의 전용 룰만
     */
    public Page<RuleResponse> getList(String q, LogType logType, String useYn, String scope, Long systemId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("ruleId")));
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        String use = StringUtils.hasText(useYn) ? useYn : null;
        String sc = "COMMON".equals(scope) || "SYSTEM".equals(scope) ? scope : null;
        Map<Long, String> names = systemNames();
        return ruleRepository.search(keyword, logType, use, sc, systemId, pageable)
                .map(r -> RuleResponse.from(r, r.getSystemId() == null ? null : names.get(r.getSystemId())));
    }

    public RuleResponse getDetail(Long ruleId) {
        SecLogRule r = findRule(ruleId);
        return RuleResponse.from(r, r.getSystemId() == null ? null : systemNames().get(r.getSystemId()));
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
        Long systemId = checkSystem(request.getSystemId());

        SecLogRule rule = SecLogRule.builder()
                .systemId(systemId)
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

        return getDetail(ruleRepository.save(rule).getRuleId());
    }

    @Transactional
    public RuleResponse update(Long ruleId, RuleSaveRequest request, Long userId) {
        SecLogRule rule = findRule(ruleId);
        analysisEngine.validatePattern(request.getMatchType(), request.getPattern());

        rule.update(
                checkSystem(request.getSystemId()),
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
        return getDetail(rule.getRuleId());
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

    /** 분석용 활성 공통 룰 (해당 로그 유형 + ALL) - 시스템을 지정하지 않은 직접 입력 업로드용 */
    public List<SecLogRule> getActiveRules(LogType logType) {
        return ruleRepository.findActiveRules(List.of(logType, LogType.ALL));
    }

    /**
     * 분석용 활성 룰 = 공통 룰셋(사용 시) + 시스템 전용 룰셋(사용 시)
     * - systemId 가 없으면(직접 입력 시스템) 공통 룰만
     * - 시스템의 룰셋 사용 여부는 [탐지 룰셋] 화면의 시스템별 스위치로 바꾼다
     */
    public List<SecLogRule> getActiveRules(Long systemId, LogType logType) {
        if (systemId == null) {
            return getActiveRules(logType);
        }
        SecLogSystem sys = systemRepository.findById(systemId).orElse(null);
        if (sys == null) {
            return getActiveRules(logType);
        }
        List<SecLogRule> list = new ArrayList<>();
        if (sys.useCommonRules()) {
            list.addAll(getActiveRules(logType));
        }
        if (sys.useSystemRules()) {
            list.addAll(ruleRepository.findActiveSystemRules(systemId));
        }
        return list;
    }

    // ------------------------------------------------------------------ 시스템별 룰셋 사용 여부

    /** 시스템별 룰셋 현황 (사용 중인 점검 대상 시스템) */
    public List<Map<String, Object>> getRulesets() {
        Map<Long, long[]> sysCnt = new java.util.HashMap<>();
        for (Object[] o : ruleRepository.countBySystem()) {
            sysCnt.put((Long) o[0], new long[]{((Number) o[1]).longValue(), o[2] == null ? 0 : ((Number) o[2]).longValue()});
        }
        Map<LogType, long[]> comCnt = new java.util.HashMap<>();
        for (Object[] o : ruleRepository.countCommonByLogType()) {
            comCnt.put((LogType) o[0], new long[]{((Number) o[1]).longValue(), o[2] == null ? 0 : ((Number) o[2]).longValue()});
        }
        long[] all = comCnt.getOrDefault(LogType.ALL, new long[2]);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SecLogSystem s : systemRepository.findByDeletedYnAndUseYnOrderBySortOrderAscSystemIdAsc(SecLogConstants.DELETED_N, "Y")) {
            long[] t = comCnt.getOrDefault(s.getDefaultLogType(), new long[2]);
            long[] sc = sysCnt.getOrDefault(s.getSystemId(), new long[2]);
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("systemId", s.getSystemId());
            m.put("systemCode", s.getSystemCode());
            m.put("systemName", s.getSystemName());
            m.put("category", s.getCategory());
            m.put("logType", s.getDefaultLogType().name());
            m.put("logTypeLabel", s.getDefaultLogType().getLabel());
            m.put("commonRuleYn", s.useCommonRules() ? "Y" : "N");
            m.put("systemRuleYn", s.useSystemRules() ? "Y" : "N");
            m.put("commonRuleCount", all[0] + t[0]);
            m.put("commonRuleActive", all[1] + t[1]);
            m.put("systemRuleCount", sc[0]);
            m.put("systemRuleActive", sc[1]);
            list.add(m);
        }
        return list;
    }

    /** 시스템의 공통 룰셋 / 시스템 전용 룰셋 사용 여부 변경 (이후 분석·재분석부터 적용) */
    @Transactional
    public Map<String, Object> changeRuleset(Long systemId, String commonRuleYn, String systemRuleYn, Long userId) {
        SecLogSystem s = systemRepository.findById(systemId)
                .filter(x -> SecLogConstants.DELETED_N.equals(x.getDeletedYn()))
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        s.changeRuleset(commonRuleYn, systemRuleYn, userId);
        return getRulesets().stream().filter(m -> systemId.equals(m.get("systemId"))).findFirst()
                .orElseGet(() -> Map.of("systemId", systemId, "commonRuleYn", s.getCommonRuleYn(), "systemRuleYn", s.getSystemRuleYn()));
    }

    private Long checkSystem(Long systemId) {
        if (systemId == null) {
            return null;
        }
        systemRepository.findById(systemId)
                .filter(x -> SecLogConstants.DELETED_N.equals(x.getDeletedYn()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시스템 전용 룰의 대상 시스템이 없습니다."));
        return systemId;
    }

    private Map<Long, String> systemNames() {
        return systemRepository.findAll().stream()
                .collect(Collectors.toMap(SecLogSystem::getSystemId, SecLogSystem::getSystemName, (a, b) -> a));
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
