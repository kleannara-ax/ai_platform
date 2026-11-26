package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.MonthlyStatusResponse;
import com.company.module.security_log.entity.SecLogSystem;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.repository.SecLogAccessSummaryRepository;
import com.company.module.security_log.repository.SecLogSystemRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 월별 시스템별 점검 현황 (ISMS 증적용)
 * - 시스템 마스터의 사용 중인 시스템마다
 *   접속 행위로그 업로드/검토 여부, 장기 미접속 계정 점검/조치 여부를 한 눈에 보여준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogMonthlyStatusService {

    private final SecLogSystemRepository systemRepository;
    private final SecLogUploadRepository uploadRepository;
    private final SecLogAccessSummaryRepository accessSummaryRepository;
    private final SecLogAccountReviewService accountReviewService;
    private final SecLogPiiDownloadService piiDownloadService;

    public MonthlyStatusResponse getStatus(String targetYm) {
        if (targetYm == null || !targetYm.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
        List<SecLogSystem> systems = systemRepository.findByDeletedYnAndUseYnOrderBySortOrderAscSystemIdAsc(SecLogConstants.DELETED_N, "Y");

        // 시스템별 가장 최근에 올린 파일 1개만 분석 대상 (이전 파일은 집계에서 제외)
        Map<Long, List<SecLogUpload>> uploads = uploadRepository.findLatestByTargetYm(targetYm)
                .stream().collect(Collectors.groupingBy(SecLogUpload::getSystemId));

        Map<Long, Object[]> access = new HashMap<>();
        for (Object[] r : accessSummaryRepository.aggregateBySystem(targetYm)) {
            access.put((Long) r[0], r);
        }

        // 위험 표시 계정 중 검토가 남은 수 (최신 파일 기준)
        Map<Long, Long> riskPending = accountReviewService.riskPending(
                uploads.values().stream().flatMap(List::stream).map(SecLogUpload::getUploadId).toList());

        // 개인정보 다운로드 [전체, 미검토] (자사몰·임직원몰, 최신 파일 기준)
        Map<Long, long[]> pii = piiDownloadService.countsByUploads(
                uploads.values().stream().flatMap(List::stream).map(SecLogUpload::getUploadId).toList());

        List<MonthlyStatusResponse.Row> rows = new ArrayList<>();
        int accessDone = 0;
        for (SecLogSystem s : systems) {
            List<SecLogUpload> ups = uploads.getOrDefault(s.getSystemId(), List.of());
            String accessStatus = ups.isEmpty() ? "NONE"
                    : ups.stream().allMatch(SecLogUpload::isReviewed) ? "REVIEWED" : "ANALYZED";
            if ("REVIEWED".equals(accessStatus)) {
                accessDone++;
            }
            SecLogUpload latest = ups.stream().max(Comparator.comparing(SecLogUpload::getUploadId)).orElse(null);
            Object[] a = access.get(s.getSystemId());
            boolean piiTarget = s.getSystemCode() != null
                    && SecLogPiiDownloadService.TARGET_CODES.contains(s.getSystemCode().trim().toUpperCase(java.util.Locale.ROOT));
            long[] pc = latest == null ? null : pii.get(latest.getUploadId());

            rows.add(MonthlyStatusResponse.Row.builder()
                    .systemId(s.getSystemId()).category(s.getCategory()).systemName(s.getSystemName())
                    .managerName(s.getManagerName())
                    .accessUploadCount(ups.size())
                    .latestUploadId(latest == null ? null : latest.getUploadId())
                    .accessStatus(accessStatus)
                    .accessAccountCount(a == null ? 0 : toLong(a[1]))
                    .accessSuccess(a == null ? 0 : toLong(a[2]))
                    .accessFail(a == null ? 0 : toLong(a[3]))
                    .accessRiskAccounts(a == null ? 0 : toLong(a[4]))
                    .accessRiskPending(latest == null ? 0 : riskPending.getOrDefault(latest.getUploadId(), 0L))
                    .detectionCount(ups.stream().mapToLong(SecLogUpload::getDetectionCount).sum())
                    .piiTarget(piiTarget)
                    .piiSheetStatus(piiTarget && latest != null ? latest.getPiiSheetStatus() : null)
                    .piiDownloadCount(piiTarget && pc != null ? pc[0] : 0)
                    .piiPending(piiTarget && pc != null ? pc[1] : 0)
                    .build());
        }
        return MonthlyStatusResponse.builder()
                .targetYm(targetYm).systemCount(systems.size())
                .accessDoneCount(accessDone)
                .systems(rows).build();
    }

    private static long toLong(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
