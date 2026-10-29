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

    public MonthlyStatusResponse getStatus(String targetYm) {
        if (targetYm == null || !targetYm.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
        List<SecLogSystem> systems = systemRepository.findByDeletedYnAndUseYnOrderBySortOrderAscSystemIdAsc(SecLogConstants.DELETED_N, "Y");

        Map<Long, List<SecLogUpload>> uploads = uploadRepository.findByTargetYmAndDeletedYn(targetYm, SecLogConstants.DELETED_N)
                .stream().filter(u -> u.getSystemId() != null)
                .collect(Collectors.groupingBy(SecLogUpload::getSystemId));

        Map<Long, Object[]> access = new HashMap<>();
        for (Object[] r : accessSummaryRepository.aggregateBySystem(targetYm)) {
            access.put((Long) r[0], r);
        }

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
                    .detectionCount(ups.stream().mapToLong(SecLogUpload::getDetectionCount).sum())
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
