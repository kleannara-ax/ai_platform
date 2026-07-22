package com.company.module.psinsp.service;

import com.company.module.psinsp.entity.PsInspCodeDetail;
import com.company.module.psinsp.repository.PsInspCodeDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PS 지분 검사 설정 서비스 (공통코드 기반)
 *
 * <p>PPM 기준값: code_group 'PS_INSP_DEFAULT' > code 'PPM_LIMIT'
 * <p>PPM 수정 권한자: code_group 'PS_INSP_AUTH'의 활성 코드값(사용자 로그인 ID)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PsInspConfigService {

    private final PsInspCodeDetailRepository codeDetailRepository;

    /** PPM 기준값 그룹 */
    private static final String GROUP_DEFAULT = "PS_INSP_DEFAULT";
    /** PPM 수정 권한자 그룹 */
    private static final String GROUP_ADMIN = "PS_INSP_AUTH";

    /** 코드 키 상수 */
    public static final String CODE_PPM_LIMIT = "PPM_LIMIT";
    private static final String LEGACY_PPM_ADMIN_CODE = "PPM_ADMIN";

    // ──────────── PPM 기준값 ────────────

    /**
     * PPM 기준값 조회 (기본값: 0 = 비활성)
     */
    public double getPpmLimit() {
        return codeDetailRepository.findByGroupCodeAndCode(GROUP_DEFAULT, CODE_PPM_LIMIT)
                .map(detail -> {
                    try {
                        double v = Double.parseDouble(detail.getExtraValue1());
                        return v > 0 ? v : 0.0;
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .orElse(0.0);
    }

    /**
     * PPM 기준값 저장 (권한자 검증 포함)
     */
    @Transactional
    public Map<String, Object> savePpmLimit(double ppmLimit, String operatorId) {
        verifyPpmAdmin(operatorId);

        PsInspCodeDetail detail = codeDetailRepository
                .findByGroupCodeAndCode(GROUP_DEFAULT, CODE_PPM_LIMIT)
                .orElseThrow(() -> new IllegalStateException("PPM_LIMIT 공통코드가 없습니다. DB 초기화를 확인해주세요."));

        detail.updateExtraValue1(String.valueOf(ppmLimit));
        codeDetailRepository.save(detail);

        log.info("[PS-INSP-CONFIG] PPM 기준값 변경 - value: {}, operator: {}", ppmLimit, operatorId);

        return Map.of(
                "ppmLimit", ppmLimit,
                "enabled", ppmLimit > 0,
                "updatedBy", operatorId
        );
    }

    // ──────────── 권한자 관리 (PS_INSP_AUTH 사용자별 코드값) ────────────

    /**
     * PPM 수정 권한자 ID 목록 조회
     */
    public List<String> getPpmAdminIds() {
        return codeDetailRepository.findAllByGroupCode(GROUP_ADMIN).stream()
                .filter(detail -> !LEGACY_PPM_ADMIN_CODE.equalsIgnoreCase(detail.getCode()))
                .map(PsInspCodeDetail::getCode)
                .toList();
    }

    /**
     * 현재 사용자가 PPM 수정 권한이 있는지 확인한다.
     * PS_INSP_AUTH의 활성 코드값 하나가 권한자 로그인 ID 한 명을 의미한다.
     */
    public boolean isPpmAdmin(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) return false;
        String loginId = operatorId.trim();
        return getPpmAdminIds().stream().anyMatch(id -> id.equalsIgnoreCase(loginId));
    }

    /**
     * PPM 수정 권한자 목록 업데이트
     *
     * @param adminIds   새 권한자 ID 목록
     * @param operatorId 현재 로그인 사용자 ID (기존 권한자만 가능)
     */
    @Transactional
    public List<String> updatePpmAdminIds(List<String> adminIds, String operatorId) {
        verifyPpmAdmin(operatorId);

        List<String> cleanIds = adminIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toMap(
                        id -> id.toUpperCase(Locale.ROOT),
                        id -> id,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values().stream()
                .toList();

        // 자기 자신은 항상 유지
        if (cleanIds.stream().noneMatch(id -> id.equalsIgnoreCase(operatorId.trim()))) {
            cleanIds = new ArrayList<>(cleanIds);
            cleanIds.add(operatorId.trim());
        }

        List<PsInspCodeDetail> currentDetails = codeDetailRepository.findAllByGroupCode(GROUP_ADMIN).stream()
                .filter(detail -> !LEGACY_PPM_ADMIN_CODE.equalsIgnoreCase(detail.getCode()))
                .toList();
        Long groupId = currentDetails.stream()
                .findFirst()
                .map(PsInspCodeDetail::getGroupId)
                .orElseThrow(() -> new IllegalStateException("PS_INSP_AUTH 권한 코드가 없습니다."));

        Set<String> requestedCodes = cleanIds.stream()
                .map(id -> id.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, PsInspCodeDetail> existingByCode = currentDetails.stream()
                .collect(Collectors.toMap(detail -> detail.getCode().toUpperCase(Locale.ROOT), detail -> detail));

        codeDetailRepository.deleteAll(currentDetails.stream()
                .filter(detail -> !requestedCodes.contains(detail.getCode().toUpperCase(Locale.ROOT)))
                .toList());
        for (String userId : cleanIds) {
            String code = userId.toUpperCase(Locale.ROOT);
            if (!existingByCode.containsKey(code)) {
                codeDetailRepository.save(PsInspCodeDetail.builder()
                        .groupId(groupId)
                        .code(code)
                        .codeName(userId)
                        .description("PS 지분검사 PPM 기준값 수정 권한 사용자 로그인 ID")
                        .extraValue1(null)
                        .extraValue2(null)
                        .isActive(true)
                        .sortOrder(100)
                        .build());
            }
        }

        log.info("[PS-INSP-CONFIG] PPM 권한자 코드 목록 변경 - ids: {}, operator: {}", cleanIds, operatorId);
        return cleanIds;
    }

    // ──────────── 전체 설정 조회 ────────────

    /**
     * PS_INSP_DEFAULT 그룹의 모든 설정 조회
     */
    public List<Map<String, Object>> getAllConfigs() {
        return codeDetailRepository.findAllByGroupCode(GROUP_DEFAULT).stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("codeId", d.getCodeId());
                    m.put("code", d.getCode());
                    m.put("codeName", d.getCodeName());
                    m.put("description", d.getDescription());
                    m.put("value", d.getExtraValue1());
                    m.put("extraValue2", d.getExtraValue2());
                    m.put("updatedAt", d.getUpdatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ──────────── Private Helpers ────────────

    private void verifyPpmAdmin(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("사용자 ID를 확인할 수 없습니다.");
        }
        if (!isPpmAdmin(operatorId)) {
            throw new IllegalArgumentException(
                    "PPM 기준값 수정 권한이 없습니다. (현재 사용자: " + operatorId + ") PS_INSP_AUTH 사용자 코드 등록이 필요합니다.");
        }
    }

}
