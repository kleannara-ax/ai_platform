package com.company.module.cckb.service;

import com.company.module.cckb.dto.response.JudgmentCodeResponse;
import com.company.module.cckb.entity.JudgmentCode;
import com.company.module.cckb.repository.JudgmentCodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 판정 코드(정상/경계/불량) 조회 서비스.
 * 애플리케이션 기동 시 3건이 없으면 기본값으로 자동 시딩한다 (SQL 시드와 별개로
 * 로컬 개발/테스트 편의를 위한 안전망 — SQL 02 적용을 잊어도 화면이 깨지지 않게 함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JudgmentCodeService {

    private final JudgmentCodeRepository judgmentCodeRepository;

    public List<JudgmentCodeResponse> getAll() {
        return judgmentCodeRepository.findByDeletedYnOrderBySortOrderAsc("N")
                .stream().map(JudgmentCodeResponse::from).toList();
    }

    @Transactional
    @PostConstruct
    public void ensureDefaults() {
        if (!judgmentCodeRepository.findAll().isEmpty()) {
            return;
        }
        Map<String, Object[]> defaults = Map.of(
                JudgmentCode.NORMAL, new Object[]{"정상", "#22c55e", 1, "모든 값이 정상 기준 이내"},
                JudgmentCode.BOUNDARY, new Object[]{"경계", "#f59e0b", 2, "정상 기준은 벗어났으나 불량 기준 미만"},
                JudgmentCode.DEFECT, new Object[]{"불량", "#ef4444", 3, "불량 기준을 벗어남"}
        );
        defaults.forEach((code, v) -> judgmentCodeRepository.save(JudgmentCode.builder()
                .judgmentCode(code)
                .judgmentNameKr((String) v[0])
                .colorHex((String) v[1])
                .sortOrder((Integer) v[2])
                .description((String) v[3])
                .build()));
    }
}
