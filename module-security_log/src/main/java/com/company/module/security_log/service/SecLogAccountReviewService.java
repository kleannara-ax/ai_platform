package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.AccountReviewRequest;
import com.company.module.security_log.dto.AccountReviewResponse;
import com.company.module.security_log.entity.AccountReviewStatus;
import com.company.module.security_log.entity.SecLogAccessSummary;
import com.company.module.security_log.entity.SecLogAccountReview;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.repository.SecLogAccessSummaryRepository;
import com.company.module.security_log.repository.SecLogAccountReviewRepository;
import com.company.module.security_log.repository.SecLogSystemRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 접속 행위 분석 > 계정별 검토 (담당자 소명·조치 기록)
 * - 검토자: ADMIN, MANAGER (모든 시스템 - 권한 기준, 매핑안 A)
 * - 위험 표시 계정은 모두 검토해야 분석 결과 검토 완료·최종 검토완료 가능
 * - 위험 표시 없는 계정은 선택 기록
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogAccountReviewService {

    public static final String PENDING = "PENDING";

    private final SecLogAccountReviewRepository reviewRepository;
    private final SecLogAccessSummaryRepository accessSummaryRepository;
    private final SecLogUploadRepository uploadRepository;
    private final SecLogSystemRepository systemRepository;

    /** 선택한 계정 일괄 검토 (PENDING 이면 미검토로 되돌림) */
    @Transactional
    public AccountReviewResponse review(Long uploadId, AccountReviewRequest request, Long userId) {
        SecLogUpload upload = uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (upload.isReviewed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요.");
        }
        String statusText = request.getReviewStatus().trim().toUpperCase(Locale.ROOT);
        AccountReviewStatus status = null;
        if (!PENDING.equals(statusText)) {
            try {
                status = AccountReviewStatus.valueOf(statusText);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "검토 상태는 NORMAL(정상 확인), CONFIRMED(이상 확인), RESOLVED(조치완료), PENDING(미검토로 되돌리기) 중 하나입니다.");
            }
        }
        String comment = StringUtils.hasText(request.getReviewComment()) ? request.getReviewComment().trim() : null;
        if (status != null && status.commentRequired() && comment == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "'" + status.getLabel() + "'은(는) 소명·조치 내용을 입력해야 합니다.");
        }

        Set<String> ids = new LinkedHashSet<>();
        request.getAccountIds().stream().filter(StringUtils::hasText).map(String::trim).forEach(ids::add);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "검토할 계정을 선택하세요.");
        }
        Map<String, SecLogAccessSummary> summaries = accessSummaryRepository
                .findByUploadIdAndDeletedYnOrderByFailCountDescSuccessCountDesc(uploadId, SecLogConstants.DELETED_N)
                .stream().collect(Collectors.toMap(SecLogAccessSummary::getAccountId, Function.identity(), (a, b) -> a));
        List<String> unknown = ids.stream().filter(a -> !summaries.containsKey(a)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 로그의 접속 계정이 아닙니다: " + String.join(", ", unknown.subList(0, Math.min(5, unknown.size())))
                            + (unknown.size() > 5 ? " 외 " + (unknown.size() - 5) + "개" : ""));
        }

        Map<String, SecLogAccountReview> current = reviewRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .stream().collect(Collectors.toMap(SecLogAccountReview::getAccountId, Function.identity(), (a, b) -> a));
        int n = 0;
        for (String accountId : ids) {
            SecLogAccountReview r = current.get(accountId);
            String flags = summaries.get(accountId).getRiskFlags();
            if (status == null) {
                if (r != null) {
                    r.delete(userId);
                    n++;
                }
                continue;
            }
            if (r == null) {
                reviewRepository.save(SecLogAccountReview.builder().uploadId(uploadId).accountId(accountId)
                        .reviewStatus(status).reviewComment(comment).riskFlags(flags).reviewedBy(userId).build());
            } else {
                r.review(status, comment, flags, userId);
            }
            n++;
        }
        reviewRepository.flush();
        return AccountReviewResponse.builder().uploadId(uploadId).updatedCount(n)
                .riskPendingCount(riskPending(List.of(uploadId)).getOrDefault(uploadId, 0L)).build();
    }

    /** 업로드의 계정별 검토 (accountId → 검토) */
    public Map<String, SecLogAccountReview> reviewsOf(Long uploadId) {
        return reviewRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N).stream()
                .collect(Collectors.toMap(SecLogAccountReview::getAccountId, Function.identity(), (a, b) -> a));
    }

    /** 여러 업로드의 계정별 검토 (uploadId → accountId → 검토) */
    public Map<Long, Map<String, SecLogAccountReview>> reviewsOf(Collection<Long> uploadIds) {
        Map<Long, Map<String, SecLogAccountReview>> m = new HashMap<>();
        if (uploadIds == null || uploadIds.isEmpty()) {
            return m;
        }
        for (SecLogAccountReview r : reviewRepository.findByUploadIdInAndDeletedYn(uploadIds, SecLogConstants.DELETED_N)) {
            m.computeIfAbsent(r.getUploadId(), k -> new HashMap<>()).putIfAbsent(r.getAccountId(), r);
        }
        return m;
    }

    /**
     * DLP 업로드 ID (DLP 는 점검 보고서 화면만 쓰고 계정 검토 화면이 없음 → 위험 계정 검토 조건 제외,
     * 마감 조건은 '미검토 탐지 0건' = 보고서 항목 모두 검토)
     */
    private Set<Long> dlpUploads(Collection<Long> uploadIds) {
        Map<Long, String> codes = new HashMap<>();
        systemRepository.findAll().forEach(x -> codes.put(x.getSystemId(), x.getSystemCode()));
        Set<Long> out = new HashSet<>();
        uploadRepository.findAllById(uploadIds).forEach(u -> {
            if (SecLogDlpReportService.isDlp(u.getSystemId() == null ? null : codes.get(u.getSystemId()), u.getSystemName())) {
                out.add(u.getUploadId());
            }
        });
        return out;
    }

    /** 업로드별 '검토가 남은 위험 표시 계정' 수 (uploadId → 개수, 0 이면 생략될 수 있음) */
    public Map<Long, Long> riskPending(Collection<Long> uploadIds) {
        Map<Long, Long> result = new HashMap<>();
        if (uploadIds == null || uploadIds.isEmpty()) {
            return result;
        }
        Map<Long, Map<String, SecLogAccountReview>> reviews = reviewsOf(uploadIds);
        Set<Long> dlp = dlpUploads(uploadIds);
        Map<Long, Set<String>> riskAccounts = new HashMap<>();
        for (SecLogAccessSummary s : accessSummaryRepository.findByUploadIdInAndDeletedYn(uploadIds, SecLogConstants.DELETED_N)) {
            if (s.getRiskFlags() != null && !s.getRiskFlags().isBlank() && !dlp.contains(s.getUploadId())) {
                riskAccounts.computeIfAbsent(s.getUploadId(), k -> new HashSet<>()).add(s.getAccountId());
            }
        }
        riskAccounts.forEach((uploadId, accounts) -> {
            Map<String, SecLogAccountReview> rv = reviews.getOrDefault(uploadId, Map.of());
            result.put(uploadId, accounts.stream().filter(a -> !rv.containsKey(a)).count());
        });
        return result;
    }

    /** 재분석·업로드 삭제 시 그 업로드의 계정 검토를 소프트 삭제 */
    @Transactional
    public void softDeleteByUpload(Long uploadId, Long userId) {
        reviewRepository.softDeleteByUploadId(uploadId, userId, com.company.module.security_log.support.SecLogTime.now());
    }
}
