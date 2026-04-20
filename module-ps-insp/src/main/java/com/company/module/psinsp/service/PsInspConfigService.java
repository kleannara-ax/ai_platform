package com.company.module.psinsp.service;

import com.company.module.psinsp.entity.PsInspCodeDetail;
import com.company.module.psinsp.entity.PsInspCodeGroup;
import com.company.module.psinsp.repository.PsInspCodeDetailRepository;
import com.company.module.psinsp.repository.PsInspCodeGroupRepository;
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
 * <p>PPM 수정 권한자: code_group 'PS_INSP_ADMIN' > 각 행의 CODE = 사용자 ID
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PsInspConfigService {

    private final PsInspCodeDetailRepository codeDetailRepository;
    private final PsInspCodeGroupRepository codeGroupRepository;

    /** PPM 기준값 그룹 */
    private static final String GROUP_DEFAULT = "PS_INSP_DEFAULT";
    /** PPM 수정 권한자 그룹 */
    private static final String GROUP_ADMIN = "PS_INSP_ADMIN";

    /** 코드 키 상수 */
    public static final String CODE_PPM_LIMIT = "PPM_LIMIT";

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

    // ──────────── 권한자 관리 (PS_INSP_ADMIN 그룹 개별 행) ────────────

    /**
     * PPM 수정 권한자 ID 목록 조회
     * PS_INSP_ADMIN 그룹의 활성 code_detail 행들의 CODE 값 = 사용자 ID
     */
    public List<String> getPpmAdminIds() {
        return codeDetailRepository.findAllByGroupCode(GROUP_ADMIN).stream()
                .map(PsInspCodeDetail::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 현재 사용자가 PPM 수정 권한이 있는지 확인
     */
    public boolean isPpmAdmin(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) return false;
        return codeDetailRepository.findByGroupCodeAndCode(GROUP_ADMIN, operatorId.trim()).isPresent();
    }

    /**
     * PPM 수정 권한자 추가
     *
     * @param newAdminId 추가할 사용자 ID
     * @param operatorId 현재 로그인 사용자 ID (기존 권한자만 가능)
     * @return 갱신된 권한자 목록
     */
    @Transactional
    public List<String> addPpmAdmin(String newAdminId, String operatorId) {
        verifyPpmAdmin(operatorId);

        if (newAdminId == null || newAdminId.isBlank()) {
            throw new IllegalArgumentException("추가할 사용자 ID를 입력해주세요.");
        }
        String cleanId = newAdminId.trim();

        // 이미 존재하는지 확인
        if (codeDetailRepository.findByGroupCodeAndCode(GROUP_ADMIN, cleanId).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 권한자입니다: " + cleanId);
        }

        // PS_INSP_ADMIN 그룹 ID 조회
        PsInspCodeGroup group = codeGroupRepository.findByGroupCode(GROUP_ADMIN)
                .orElseThrow(() -> new IllegalStateException("PS_INSP_ADMIN 공통코드 그룹이 없습니다."));

        // 다음 SORT_ORDER 계산
        List<PsInspCodeDetail> existing = codeDetailRepository.findAllByGroupCode(GROUP_ADMIN);
        int nextSort = existing.stream()
                .mapToInt(d -> d.getSortOrder() != null ? d.getSortOrder() : 0)
                .max().orElse(0) + 1;

        PsInspCodeDetail newDetail = PsInspCodeDetail.builder()
                .groupId(group.getGroupId())
                .code(cleanId)
                .codeName(cleanId)
                .description("PPM 기준값 수정 권한자")
                .isActive(true)
                .sortOrder(nextSort)
                .build();

        codeDetailRepository.save(newDetail);

        log.info("[PS-INSP-CONFIG] PPM 권한자 추가 - newAdmin: {}, operator: {}", cleanId, operatorId);
        return getPpmAdminIds();
    }

    /**
     * PPM 수정 권한자 삭제
     *
     * @param targetAdminId 삭제할 사용자 ID
     * @param operatorId    현재 로그인 사용자 ID (기존 권한자만 가능)
     * @return 갱신된 권한자 목록
     */
    @Transactional
    public List<String> removePpmAdmin(String targetAdminId, String operatorId) {
        verifyPpmAdmin(operatorId);

        if (targetAdminId == null || targetAdminId.isBlank()) {
            throw new IllegalArgumentException("삭제할 사용자 ID를 입력해주세요.");
        }
        String cleanId = targetAdminId.trim();

        // 자기 자신 삭제 방지
        if (cleanId.equals(operatorId.trim())) {
            throw new IllegalArgumentException("자기 자신은 삭제할 수 없습니다.");
        }

        PsInspCodeDetail detail = codeDetailRepository.findByGroupCodeAndCode(GROUP_ADMIN, cleanId)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 권한자입니다: " + cleanId));

        codeDetailRepository.delete(detail);

        log.info("[PS-INSP-CONFIG] PPM 권한자 삭제 - removed: {}, operator: {}", cleanId, operatorId);
        return getPpmAdminIds();
    }

    /**
     * PPM 수정 권한자 목록 전체 업데이트 (기존 프론트엔드 호환)
     *
     * @param adminIds   새 권한자 ID 목록
     * @param operatorId 현재 로그인 사용자 ID
     */
    @Transactional
    public List<String> updatePpmAdminIds(List<String> adminIds, String operatorId) {
        verifyPpmAdmin(operatorId);

        List<String> cleanIds = adminIds.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // 자기 자신은 항상 유지
        if (!cleanIds.contains(operatorId)) {
            cleanIds.add(operatorId);
        }

        PsInspCodeGroup group = codeGroupRepository.findByGroupCode(GROUP_ADMIN)
                .orElseThrow(() -> new IllegalStateException("PS_INSP_ADMIN 공통코드 그룹이 없습니다."));

        // 기존 전체 삭제
        List<PsInspCodeDetail> existing = codeDetailRepository.findAllByGroupCode(GROUP_ADMIN);
        codeDetailRepository.deleteAll(existing);

        // 새로 등록
        for (int i = 0; i < cleanIds.size(); i++) {
            PsInspCodeDetail newDetail = PsInspCodeDetail.builder()
                    .groupId(group.getGroupId())
                    .code(cleanIds.get(i))
                    .codeName(cleanIds.get(i))
                    .description("PPM 기준값 수정 권한자")
                    .isActive(true)
                    .sortOrder(i + 1)
                    .build();
            codeDetailRepository.save(newDetail);
        }

        log.info("[PS-INSP-CONFIG] PPM 권한자 목록 전체 업데이트 - ids: {}, operator: {}", cleanIds, operatorId);
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
}
