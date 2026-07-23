package com.company.module.dailyreport.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dailyreport.dto.CellAuthRequest;
import com.company.module.dailyreport.dto.CellAuthResponse;
import com.company.module.dailyreport.entity.CellAuth;
import com.company.module.dailyreport.repository.CellAuthRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 셀 단위 접근 권한 CRUD 서비스 (★ Phase 4 신규)
 * - '세부공장일보 컬럼관리' 관리 페이지의 백엔드 로직
 * - 관리자가 사용자별 담당 셀 좌표와 입력 주기를 설정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellAuthService {

    private final CellAuthRepository cellAuthRepository;
    private final EntityManager entityManager;
    private final CellOwnershipSyncService cellOwnershipSyncService;

    // ─────────────────────────────────────────────
    //  아키텍처 Rule 4: core_user 참조는 EntityManager 네이티브 쿼리 사용
    //  (InspectorNameResolver 패턴 준용)
    // ─────────────────────────────────────────────

    /**
     * userId 목록에 해당하는 core_user의 USER_NAME, LOGIN_ID를 일괄 조회
     * @return Map<userId, Object[]{userName, loginId}>
     */
    private Map<Long, Object[]> resolveUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT user_id, " +
                "       COALESCE(NULLIF(TRIM(user_name), ''), login_id) AS user_name, " +
                "       login_id " +
                "FROM core_user WHERE user_id IN (:ids)")
                .setParameter("ids", userIds)
                .getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> new Object[]{ (String) row[1], (String) row[2] },
                (a, b) -> a   // 중복 시 첫 번째 유지
        ));
    }

    /**
     * CellAuth 엔티티 목록을 userName/loginId가 채워진 응답 DTO로 변환
     */
    private List<CellAuthResponse> toResponsesWithUserName(List<CellAuth> auths) {
        Set<Long> userIds = auths.stream()
                .map(CellAuth::getUserId)
                .collect(Collectors.toSet());
        Map<Long, Object[]> userMap = resolveUserNames(userIds);

        return auths.stream()
                .map(auth -> {
                    Object[] user = userMap.get(auth.getUserId());
                    if (user != null) {
                        return CellAuthResponse.from(auth, (String) user[0], (String) user[1]);
                    }
                    return CellAuthResponse.from(auth);
                })
                .toList();
    }

    /**
     * 전체 셀 권한 조회 (활성만)
     */
    public List<CellAuthResponse> getAllActiveAuths() {
        return toResponsesWithUserName(
                cellAuthRepository.findByIsActiveTrue());
    }

    /**
     * 전체 셀 권한 조회 (비활성 포함, 관리자 페이지용)
     */
    public List<CellAuthResponse> getAllAuths() {
        return toResponsesWithUserName(
                cellAuthRepository.findAllByOrderByTableCodeAscUserIdAsc());
    }

    /**
     * 사용자별 셀 권한 조회
     */
    public List<CellAuthResponse> getAuthsByUser(Long userId) {
        return toResponsesWithUserName(
                cellAuthRepository.findByUserIdAndIsActiveTrue(userId));
    }

    /**
     * 표 코드별 셀 권한 조회
     */
    public List<CellAuthResponse> getAuthsByTable(String tableCode) {
        return toResponsesWithUserName(
                cellAuthRepository.findByTableCodeAndIsActiveTrue(tableCode));
    }

    /**
     * 단건 조회
     */
    public CellAuthResponse getAuth(Long authId) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));
        return toResponsesWithUserName(List.of(auth)).get(0);
    }

    /**
     * 셀 권한 등록
     * - 성공 시 해당 tableCode의 daily_report_cell.OWNER_IDS/OWNER_NAMES 캐시를
     *   즉시 재동기화한다 (★ 하드코딩 대체: 코드 배포 없이 담당자 반영).
     */
    @Transactional
    public CellAuthResponse createAuth(CellAuthRequest request, Long grantedBy) {
        // 동일 사용자+표 코드 중복 확인
        java.util.Optional<CellAuth> existingOpt =
                cellAuthRepository.findByUserIdAndTableCode(request.getUserId(), request.getTableCode());

        CellAuthResponse response;
        if (existingOpt.isPresent()) {
            CellAuth existing = existingOpt.get();
            if (Boolean.TRUE.equals(existing.getIsActive())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("이미 권한이 존재합니다. userId=%d, tableCode=%s",
                                request.getUserId(), request.getTableCode()));
            }
            // 비활성 상태면 재활성화 후 반환 (새 레코드 INSERT 방지)
            existing.updateActive(true);
            existing.updateAll(
                    request.getUserId(),
                    request.getTableCode(),
                    request.getCellCoordsAsJson(),
                    request.getFreqCode(),
                    request.getFreqLabel(),
                    request.getDescription(),
                    grantedBy);
            response = toResponsesWithUserName(List.of(existing)).get(0);
        } else {
            // 기존 레코드가 없는 경우에만 새로 생성
            CellAuth auth = CellAuth.builder()
                    .userId(request.getUserId())
                    .tableCode(request.getTableCode())
                    .cellCoords(request.getCellCoordsAsJson())
                    .freqCode(request.getFreqCode())
                    .freqLabel(request.getFreqLabel())
                    .isActive(true)
                    .grantedBy(grantedBy)
                    .description(request.getDescription())
                    .build();

            cellAuthRepository.save(auth);
            response = toResponsesWithUserName(List.of(auth)).get(0);
        }

        cellOwnershipSyncService.syncTable(request.getTableCode());
        return response;
    }

    /**
     * 셀 권한 수정
     * - userId 또는 tableCode 변경 시 UNIQUE(USER_ID, TABLE_CODE) 제약 위반 방지
     * - 성공 시 원래 tableCode와 변경 후 tableCode 양쪽 모두 캐시를 재동기화한다
     *   (표를 바꾼 경우 이전 표에 남아있던 담당자 캐시도 정리해야 하므로).
     */
    @Transactional
    public CellAuthResponse updateAuth(Long authId, CellAuthRequest request, Long grantedBy) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));

        String previousTableCode = auth.getTableCode();

        // userId 또는 tableCode가 변경되면 대상 조합의 기존 활성 레코드 확인
        boolean userChanged = !auth.getUserId().equals(request.getUserId());
        boolean tableChanged = !auth.getTableCode().equals(request.getTableCode());
        if (userChanged || tableChanged) {
            cellAuthRepository.findByUserIdAndTableCode(request.getUserId(), request.getTableCode())
                    .ifPresent(existing -> {
                        if (!existing.getAuthId().equals(authId)) {
                            throw new BusinessException(ErrorCode.INVALID_INPUT,
                                    String.format("변경하려는 사용자+표 조합에 이미 권한이 존재합니다. userId=%d, tableCode=%s",
                                            request.getUserId(), request.getTableCode()));
                        }
                    });
        }

        auth.updateAll(
                request.getUserId(),
                request.getTableCode(),
                request.getCellCoordsAsJson(),
                request.getFreqCode(),
                request.getFreqLabel(),
                request.getDescription(),
                grantedBy);

        CellAuthResponse response = toResponsesWithUserName(List.of(auth)).get(0);

        if (tableChanged) {
            cellOwnershipSyncService.syncTable(previousTableCode);
        }
        cellOwnershipSyncService.syncTable(request.getTableCode());

        return response;
    }

    /**
     * 셀 권한 비활성화 (논리 삭제)
     * - 비활성화 즉시 해당 tableCode 캐시를 재동기화하여 OWNER_IDS에서 제거한다.
     */
    @Transactional
    public void deactivateAuth(Long authId) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));
        auth.updateActive(false);
        cellOwnershipSyncService.syncTable(auth.getTableCode());
    }

    /**
     * 셀 권한 물리 삭제
     * - 삭제 즉시 해당 tableCode 캐시를 재동기화하여 OWNER_IDS에서 제거한다.
     */
    @Transactional
    public void deleteAuth(Long authId) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));
        String tableCode = auth.getTableCode();
        cellAuthRepository.deleteById(authId);
        cellOwnershipSyncService.syncTable(tableCode);
    }

    /**
     * 사용자가 특정 표의 특정 셀 좌표에 권한이 있는지 확인
     * (CellService에서 호출)
     */
    public boolean hasCoordAccess(Long userId, String tableCode, String excelCoord) {
        return cellAuthRepository.findByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode)
                .map(auth -> auth.coversCoord(excelCoord))
                .orElse(false);
    }

    /**
     * 사용자의 특정 표 셀 권한 엔티티 조회 (CellService에서 직접 사용)
     */
    public CellAuth findAuthEntity(Long userId, String tableCode) {
        return cellAuthRepository.findByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode)
                .orElse(null);
    }
}
