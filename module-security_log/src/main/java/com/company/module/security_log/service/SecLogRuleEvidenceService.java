package com.company.module.security_log.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpLogRowResponse;
import com.company.module.security_log.dto.PiiDownloadResponse;
import com.company.module.security_log.dto.RuleEvidenceResponse;
import com.company.module.security_log.dto.RuleResultsResponse;
import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogInternalIp;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.SecLogRuleRun;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogInternalIpRepository;
import com.company.module.security_log.repository.SecLogRuleRepository;
import com.company.module.security_log.repository.SecLogRuleRunRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 룰셋 분석 근거 — '로그 분석 및 검토 > 룰셋별 분석 결과'에서 룰을 눌렀을 때
 * 그 룰에 왜 검토 대상이 잡혔는지(탐지 조건 · 일치한 로그 줄 · 일치 부분)를 보여 준다. (조회 전용)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogRuleEvidenceService {

    private final SecLogUploadRepository uploadRepository;
    private final SecLogDetectionRepository detectionRepository;
    private final SecLogRuleRepository ruleRepository;
    private final SecLogRuleRunRepository ruleRunRepository;
    private final SecLogInternalIpRepository internalIpRepository;
    private final SecLogInternalIpService internalIpService;
    private final SecLogRuleService ruleService;
    private final SecLogUploadService uploadService;
    private final SecLogDlpReportService dlpReportService;
    private final LogAnalysisEngine analysisEngine;
    private final SecLogPiiDownloadService piiDownloadService;

    public RuleEvidenceResponse getEvidence(Long uploadId, String ruleCode) {
        SecLogUpload upload = uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        List<SecLogDetection> dets = detectionRepository
                .findByUploadIdAndDeletedYnOrderByLineNumberAsc(uploadId, SecLogConstants.DELETED_N)
                .stream().filter(d -> ruleCode.equals(d.getRuleCode())).toList();
        RuleResultsResponse.Row resRow = uploadService.getRuleResults(uploadId).getRows().stream()
                .filter(r -> ruleCode.equals(r.getRuleCode())).findFirst().orElse(null);
        if (dets.isEmpty() && resRow == null) {
            throw new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        SecLogRuleRun run = ruleRunRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N).stream()
                .filter(r -> ruleCode.equals(r.getRuleCode())).findFirst().orElse(null);

        // 룰: 탐지·분석 기록의 룰 ID → 없으면 지금 적용 중인 룰 (삭제된 룰도 설명용으로 조회)
        Long ruleId = !dets.isEmpty() ? dets.get(0).getRuleId() : run != null ? run.getRuleId() : null;
        SecLogRule rule = ruleId != null ? ruleRepository.findById(ruleId).orElse(null) : null;
        if (rule == null) {
            rule = ruleService.getActiveRules(upload.getSystemId(), upload.getLogType()).stream()
                    .filter(r -> ruleCode.equals(r.getRuleCode())).findFirst().orElse(null);
        }
        LocalDateTime analyzed = upload.getAnalyzedAt();
        boolean deleted = rule != null && !SecLogConstants.DELETED_N.equals(rule.getDeletedYn());
        LocalDateTime ruleTime = rule == null ? null
                : rule.getDeletedAt() != null ? rule.getDeletedAt() : rule.getUpdatedAt() != null ? rule.getUpdatedAt() : rule.getCreatedAt();
        boolean ruleChanged = analyzed != null && ruleTime != null && ruleTime.isAfter(analyzed);

        // 내부 IP 기준 (EXTERNAL_IP 룰)
        boolean external = rule != null && rule.getMatchType() == MatchType.EXTERNAL_IP;
        List<String> ips = null;
        boolean ipChanged = false;
        Predicate<String> internal = null;
        if (external) {
            List<SecLogInternalIp> all = internalIpRepository.findByDeletedYnOrderBySortOrderAscStartIpNumAsc(SecLogConstants.DELETED_N);
            ips = all.stream().filter(x -> "Y".equals(x.getUseYn())).map(x -> x.getIpName() + " " + x.getIpRange()).toList();
            ipChanged = analyzed != null && all.stream().anyMatch(x -> {
                LocalDateTime t = x.getDeletedAt() != null ? x.getDeletedAt() : x.getUpdatedAt() != null ? x.getUpdatedAt() : x.getCreatedAt();
                return t != null && t.isAfter(analyzed);
            });
            internal = internalIpService.internalMatcher();
        }

        // DLP: 탐지 줄 → 원본 엑셀 행 (점검 보고서와 같은 기준, 계정별 묶음)
        SecLogDlpReportService.DlpRows dlp = dlpReportService.rowsByEngineLine(uploadId);
        List<RuleEvidenceResponse.Evidence> items = new ArrayList<>();
        int pending = 0;
        for (SecLogDetection d : dets) {
            if (d.getReviewStatus() == ReviewStatus.PENDING) {
                pending++;
            }
            LogAnalysisEngine.MatchExplain mx = rule == null ? null
                    : analysisEngine.explain(rule.getMatchType(), rule.getPattern(), d.getLogContent(), internal);
            DlpLogRowResponse r = dlp == null || d.getLineNumber() == null ? null : dlp.byLine().get(d.getLineNumber());
            RuleEvidenceResponse.Evidence.EvidenceBuilder e = RuleEvidenceResponse.Evidence.builder()
                    .detectionId(d.getDetectionId()).lineNumber(d.getLineNumber())
                    .row(r != null ? Integer.valueOf(r.getRow()) : d.getLineNumber())
                    .logContent(d.getLogContent())
                    .reviewStatus(d.getReviewStatus().name()).reviewStatusLabel(d.getReviewStatus().getLabel())
                    .actionContent(d.getActionContent()).reviewedAt(d.getReviewedAt()).reviewedBy(d.getReviewedBy());
            if (dlp != null) {
                e.account(r == null || r.getAccount() == null || r.getAccount().isEmpty() ? "-" : r.getAccount());
            }
            if (r != null) {
                e.name(r.getName()).time(r.getTime()).ip(r.getIp()).type(r.getType()).content(r.getContent()).detail(r.getDetail());
            }
            if (mx != null) {
                e.matched(mx.matched()).matchText(mx.text()).matchStart(mx.start()).matchEnd(mx.end()).matchIp(mx.ip());
            } else {
                e.matchStart(-1).matchEnd(-1);
            }
            items.add(e.build());
        }

        String status = resRow != null ? resRow.getStatus() : "DONE";
        String statusLabel = resRow != null ? resRow.getStatusLabel() : "분석 완료";
        Integer matchCount = run != null ? run.getMatchCount() : null;
        long groups = dlp == null ? dets.size()
                : items.stream().map(RuleEvidenceResponse.Evidence::getAccount).filter(Objects::nonNull).distinct().count();

        List<String> reasons = new ArrayList<>();
        if (rule != null) {
            reasons.add("탐지 조건: " + howText(rule.getMatchType()));
        }
        int th = rule == null || rule.getThresholdCount() == null ? 1 : rule.getThresholdCount();
        int hit = matchCount != null ? matchCount : dets.size();
        if (!dets.isEmpty()) {
            reasons.add("일치한 로그 " + fmt(hit) + "줄 → 탐지 기준 '" + fmt(th) + "줄 이상' 충족 → 검토 대상 등록");
            if (matchCount != null && dets.size() < matchCount) {
                reasons.add("룰당 저장 상한 " + fmt(SecLogConstants.MAX_DETECTION_PER_RULE) + "건 → 앞 " + fmt(dets.size()) + "줄만 검토 대상 (나머지 "
                        + fmt(matchCount - dets.size()) + "줄은 원본 파일에서 확인)");
            }
            reasons.add(dlp != null
                    ? "검토 대상 " + fmt(groups) + "건 = 계정별로 묶은 수 (탐지 " + fmt(dets.size()) + "줄)"
                    : "검토 대상 " + fmt(dets.size()) + "건 = 탐지된 로그 줄 수");
        } else if ("NOT_RUN".equals(status)) {
            reasons.add("마지막 분석 이후 추가·변경·사용 전환된 룰 → 아직 이 로그에 적용 안 됨 ([재분석] 시 반영)");
        } else if ("FAILED".equals(status)) {
            reasons.add("분석 실패: " + (resRow.getFailReason() == null ? "사유 없음" : resRow.getFailReason()));
        } else {
            reasons.add("일치한 로그 " + (matchCount != null ? fmt(matchCount) + "줄" : "없음")
                    + (th > 1 && matchCount != null && matchCount > 0 ? " → 탐지 기준 '" + fmt(th) + "줄 이상' 미달" : "") + " → 검토 대상 없음");
        }
        if (external) {
            reasons.add("내부 IP 기준: [관리 › 내부 IP 설정] 사용 중 대역 " + ips.size() + "개"
                    + (ips.isEmpty() ? " (없음 → IP 가 있는 모든 줄이 '내부 IP 외')" : " (" + String.join(", ", ips) + ")") + " · 줄에서 처음 나오는 IP 기준");
        }
        // 개인정보 다운로드 이력 연동 룰 (자사몰·임직원몰): 다운로드 이력 + 검토 상태 함께 표시
        PiiDownloadResponse pii = ruleCode.equals(piiDownloadService.linkedRuleCode(upload.getSystemId()))
                ? piiDownloadService.list(uploadId) : null;
        if (pii != null) {
            if (dets.isEmpty()) {
                reasons.removeIf(t -> t.startsWith("일치한 로그") || t.startsWith("마지막 분석 이후"));
                reasons.add("로그(첫 번째 탭)에서 이 룰과 일치한 줄 없음");
            }
            if ("FOUND".equals(pii.getSheetStatus())) {
                reasons.add("개인정보 다운로드 이력 연동: 올린 엑셀의 '" + pii.getSheetName() + "' 탭 다운로드 " + fmt(pii.getTotalCount())
                        + "건 → 건마다 다운로드 사유 검토 대상 (미검토 " + fmt(pii.getPendingCount()) + "건, 남아 있으면 마감 불가)");
            } else if ("NOT_FOUND".equals(pii.getSheetStatus())) {
                reasons.add("개인정보 다운로드 이력 연동: 올린 파일에 다운로드 내역 탭 없음 (탭 이름·제목에 '개인정보'와 '다운로드'가 들어간 탭 필요)");
            } else {
                reasons.add("개인정보 다운로드 이력 연동: 이 기능 이전에 올린 로그 → [재분석] 시 다운로드 내역 탭 읽음");
            }
        }
        List<String> notes = new ArrayList<>();
        if (ruleChanged) {
            notes.add((deleted ? "마지막 분석 이후 룰 삭제" : "마지막 분석 이후 룰 수정")
                    + " → 아래 일치 표시는 현재 룰 기준 (검토 대상은 분석 당시 기준), [재분석] 시 반영");
        }
        if (ipChanged) {
            notes.add("마지막 분석 이후 내부 IP 설정 변경 → 아래 일치 표시는 현재 설정 기준, [재분석] 시 반영");
        }
        long unmatched = items.stream().filter(x -> !x.isMatched()).count();
        if (rule != null && unmatched > 0) {
            notes.add("현재 룰로 다시 확인 시 일치하지 않는 줄 " + fmt(unmatched) + "건 (분석 이후 룰·설정 변경 또는 긴 줄 일부만 저장)");
        }

        RuleEvidenceResponse.RuleInfo info = RuleEvidenceResponse.RuleInfo.builder()
                .ruleCode(ruleCode)
                .ruleName(rule != null ? rule.getRuleName() : !dets.isEmpty() ? dets.get(0).getRuleName() : resRow.getRuleName())
                .description(rule != null ? rule.getDescription() : null)
                .severity(rule != null ? rule.getSeverity() : !dets.isEmpty() ? dets.get(0).getSeverity() : resRow.getSeverity())
                .severityLabel((rule != null ? rule.getSeverity() : !dets.isEmpty() ? dets.get(0).getSeverity() : resRow.getSeverity()).getLabel())
                .matchType(rule != null ? rule.getMatchType() : null)
                .matchTypeLabel(rule != null ? rule.getMatchType().getLabel() : null)
                .howText(rule != null ? howText(rule.getMatchType()) : null)
                .pattern(rule != null ? rule.getPattern() : null)
                .thresholdCount(th)
                .scopeLabel(rule == null ? null : rule.getSystemId() == null ? "공통 룰" : "시스템 전용 룰")
                .changedAfterAnalysis(ruleChanged)
                .deleted(deleted)
                .build();

        return RuleEvidenceResponse.builder()
                .uploadId(uploadId).systemName(upload.getSystemName()).targetYm(upload.getTargetYm())
                .fileName(upload.getOriginalFileName()).analyzedAt(analyzed).reviewLocked(upload.isReviewed())
                .dlp(dlp != null).rowLabel(dlp != null ? dlp.rowLabel() : "로그 줄")
                .rule(info).status(status).statusLabel(statusLabel)
                .failReason(resRow != null ? resRow.getFailReason() : null)
                .matchCount(matchCount).detectionCount(dets.size()).detectionPending(pending)
                .internalIps(ips).reasons(reasons).notes(notes).items(items).pii(pii)
                .build();
    }

    static String howText(MatchType t) {
        return switch (t) {
            case KEYWORD -> "로그 한 줄에 지정 키워드 중 하나 포함 (대소문자 무시)";
            case REGEX -> "로그 한 줄이 지정 문장 형태(정규식)와 일치";
            case EXTERNAL_IP -> "로그 한 줄의 첫 IP 가 내부 IP 대역 밖 + 대상 줄 조건(정규식) 일치";
        };
    }

    private static String fmt(long n) {
        return String.format("%,d", n);
    }
}
