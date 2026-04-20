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
 * <p>code_group 'PS_INSP_PPM_LIMIT' 하위 code_detail을 읽고 수정합니다.
 *
 * <p>코드 구조:
 * <ul>
 *   <li>PPM_LIMIT    - extraValue1: PPM 기준값 (0 = 비활성)</li>
 *   <li>PPM_ADMIN    - extraValue1: 수정 권한자 ID 목록 (콤마 구분)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PsInspConfigService {

    private final PsInspCodeDetailRepository codeDetailRepository;

    /** 공통코드 그룹 코드 */
    private static final String GROUP_CODE = "PS_INSP_PPM_LIMIT";

    /** 코드 키 상수 */
    public static final String CODE_PPM_LIMIT = "PPM_LIMIT";
    public static final String CODE_PPM_ADMIN = "PPM_ADMIN";

    // ──────────── PPM 기준값 ────────────

    /**
     * PPM 기준값 조회 (기본값: 0 = 비활성)
     */
    public double getPpmLimit() {
        return codeDetailRepository.findByGroupCodeAndCode(GROUP_CODE, CODE_PPM_LIMIT)
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
     *
     * @param ppmLimit   PPM 기준값 (0 = 비활성)
     * @param operatorId 현재 로그인 사용자 ID
     * @return 저장 결과 Map
     * @throws IllegalArgumentException 권한 없는 사용자
     */
    @Transactional
    public Map<String, Object> savePpmLimit(double ppmLimit, String operatorId) {
        // 권한자 검증
        verifyPpmAdmin(operatorId);

        PsInspCodeDetail detail = codeDetailRepository
                .findByGroupCodeAndCode(GROUP_CODE, CODE_PPM_LIMIT)
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

    // ──────────── 권한자 관리 ────────────

    /**
     * PPM 수정 권한자 ID 목록 조회
     */
    public List<String> getPpmAdminIds() {
        return codeDetailRepository.findByGroupCodeAndCode(GROUP_CODE, CODE_PPM_ADMIN)
                .map(detail -> parseCommaSeparated(detail.getExtraValue1()))
                .orElse(List.of());
    }

    /**
     * 현재 사용자가 PPM 수정 권한이 있는지 확인
     */
    public boolean isPpmAdmin(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) return false;
        List<String> admins = getPpmAdminIds();
        return admins.contains(operatorId.trim());
    }

    /**
     * PPM 수정 권한자 목록 업데이트
     *
     * @param adminIds   새 권한자 ID 목록
     * @param operatorId 현재 로그인 사용자 ID (기존 권한자만 가능)
     */
    @Transactional
    public List<String> updatePpmAdminIds(List<String> adminIds, String operatorId) {
        // 현재 권한자만 수정 가능
        verifyPpmAdmin(operatorId);

        // 요청자 자신이 목록에서 빠지지 않도록 보호 (자기 삭제 방지)
        List<String> cleanIds = adminIds.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (!cleanIds.contains(operatorId)) {
            cleanIds.add(operatorId); // 자기 자신은 항상 유지
        }

        PsInspCodeDetail detail = codeDetailRepository
                .findByGroupCodeAndCode(GROUP_CODE, CODE_PPM_ADMIN)
                .orElseThrow(() -> new IllegalStateException("PPM_ADMIN 공통코드가 없습니다."));

        String value = String.join(",", cleanIds);
        detail.updateExtraValue1(value);
        codeDetailRepository.save(detail);

        log.info("[PS-INSP-CONFIG] PPM 권한자 목록 변경 - ids: {}, operator: {}", value, operatorId);
        return cleanIds;
    }

    // ──────────── 전체 설정 조회 ────────────

    /**
     * PS_INSP_PPM_LIMIT 그룹의 모든 설정 조회
     */
    public List<Map<String, Object>> getAllConfigs() {
        return codeDetailRepository.findAllByGroupCode(GROUP_CODE).stream()
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

    /**
     * PPM 관리자 권한 검증
     */
    private void verifyPpmAdmin(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("사용자 ID를 확인할 수 없습니다.");
        }
        if (!isPpmAdmin(operatorId)) {
            throw new IllegalArgumentException(
                    "PPM 기준값 수정 권한이 없습니다. (현재 사용자: " + operatorId + ") 권한자 목록에 등록이 필요합니다.");
        }
    }

    /**
     * 콤마 구분 문자열 → List 변환
     */
    private static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
