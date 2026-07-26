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
     *
     * ★★ 다중 주기 지원(2026-07): 한 사용자가 같은 표에서 서로 다른 주기(예: 매일 +
     * 매년)의 셀들을 나눠서 담당할 수 있어야 한다. 따라서 동일 사용자+표 코드에 대해
     * "이미 권한이 존재합니다" 식으로 무조건 막지 않는다. 대신:
     * 1) 같은 사용자+표 코드의 기존 활성 레코드들 중, 이번 요청과 좌표가 겹치는
     *    레코드가 있으면 충돌로 간주하여 막는다 (동일 셀을 두 번 등록하는 것은 방지).
     * 2) 좌표가 겹치지 않으면(= 다른 셀 그룹) 새 레코드를 INSERT하여 별도의
     *    주기(FREQ_CODE)를 가진 추가 담당 그룹으로 등록한다.
     * 3) 비활성 레코드는 좌표 충돌 검사에서 제외한다 (재활성화 대상이 아니라 참고만).
     */
    @Transactional
    public CellAuthResponse createAuth(CellAuthRequest request, Long grantedBy) {
        List<CellAuth> existingActiveAuths =
                cellAuthRepository.findAllByUserIdAndTableCodeAndIsActiveTrue(
                        request.getUserId(), request.getTableCode());

        // 요청한 좌표 중 하나라도 기존 활성 레코드와 겹치면 충돌로 간주
        Set<String> requestCoords = request.getCellCoords() == null ? Set.of()
                : request.getCellCoords().stream()
                        .filter(c -> c != null && !c.isBlank())
                        .map(c -> c.trim().toUpperCase())
                        .collect(Collectors.toSet());

        for (CellAuth existing : existingActiveAuths) {
            boolean overlap = existing.getCellCoordList().stream()
                    .map(String::toUpperCase)
                    .anyMatch(requestCoords::contains);
            if (overlap) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("이미 동일한 셀에 대한 권한이 존재합니다. userId=%d, tableCode=%s",
                                request.getUserId(), request.getTableCode()));
            }
        }

        // 좌표 겹침이 없으면 항상 새 레코드로 등록 (같은 사용자+표에 여러 주기 그룹 허용)
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
        CellAuthResponse response = toResponsesWithUserName(List.of(auth)).get(0);

        cellOwnershipSyncService.syncTable(request.getTableCode());
        return response;
    }

    /**
     * 셀 권한 수정
     * - userId 또는 tableCode 변경 시, 대상 조합의 다른 활성 레코드와 좌표가 겹치는지만 확인
     *   (한 사용자가 같은 표에 여러 주기 그룹을 가질 수 있으므로, 단순히 같은 조합이
     *   존재한다는 이유만으로 막지 않는다 — ★★ 다중 주기 지원, 2026-07)
     * - 성공 시 원래 tableCode와 변경 후 tableCode 양쪽 모두 캐시를 재동기화한다
     *   (표를 바꾼 경우 이전 표에 남아있던 담당자 캐시도 정리해야 하므로).
     */
    @Transactional
    public CellAuthResponse updateAuth(Long authId, CellAuthRequest request, Long grantedBy) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));

        String previousTableCode = auth.getTableCode();
        boolean tableChanged = !previousTableCode.equals(request.getTableCode());

        // 대상 사용자+표 조합의 다른 활성 레코드들과 좌표 겹침 여부만 확인 (자기 자신은 제외)
        List<CellAuth> siblingAuths = cellAuthRepository
                .findAllByUserIdAndTableCodeAndIsActiveTrue(request.getUserId(), request.getTableCode())
                .stream()
                .filter(a -> !a.getAuthId().equals(authId))
                .toList();

        if (!siblingAuths.isEmpty()) {
            Set<String> requestCoords = request.getCellCoords() == null ? Set.of()
                    : request.getCellCoords().stream()
                            .filter(c -> c != null && !c.isBlank())
                            .map(c -> c.trim().toUpperCase())
                            .collect(Collectors.toSet());

            for (CellAuth sibling : siblingAuths) {
                boolean overlap = sibling.getCellCoordList().stream()
                        .map(String::toUpperCase)
                        .anyMatch(requestCoords::contains);
                if (overlap) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            String.format("이미 동일한 셀에 대한 다른 권한이 존재합니다. userId=%d, tableCode=%s",
                                    request.getUserId(), request.getTableCode()));
                }
            }
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
     * (현재 다른 코드에서 호출되지 않는 미사용 메서드. 다중 CellAuth 지원에 맞춰
     * 좌표를 커버하는 레코드가 여러 건 중 하나라도 있으면 true를 반환하도록 갱신)
     */
    public boolean hasCoordAccess(Long userId, String tableCode, String excelCoord) {
        return cellAuthRepository.findAllByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode)
                .stream()
                .anyMatch(auth -> auth.coversCoord(excelCoord));
    }

    /**
     * 사용자의 특정 표 + 좌표에 해당하는 CellAuth 엔티티 조회
     * (현재 다른 코드에서 호출되지 않는 미사용 메서드. 다중 CellAuth 지원에 맞춰
     * 좌표를 커버하는 레코드를 찾아 반환하도록 갱신 — 좌표 없이는 어떤 그룹인지
     * 특정할 수 없으므로 excelCoord 파라미터를 추가)
     */
    public CellAuth findAuthEntity(Long userId, String tableCode, String excelCoord) {
        return cellAuthRepository.findAllByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode)
                .stream()
                .filter(auth -> auth.coversCoord(excelCoord))
                .findFirst()
                .orElse(null);
    }
}
