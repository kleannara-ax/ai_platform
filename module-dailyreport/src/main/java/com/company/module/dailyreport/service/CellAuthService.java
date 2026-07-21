package com.company.module.dailyreport.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dailyreport.dto.CellAuthRequest;
import com.company.module.dailyreport.dto.CellAuthResponse;
import com.company.module.dailyreport.entity.CellAuth;
import com.company.module.dailyreport.repository.CellAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 셀 단위 접근 권한 CRUD 서비스 (★ Phase 4 신규)
 * - '세부공장일보 접근권한' 관리 페이지의 백엔드 로직
 * - 관리자가 사용자별 담당 셀 좌표와 입력 주기를 설정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellAuthService {

    private final CellAuthRepository cellAuthRepository;

    /**
     * 전체 셀 권한 조회 (활성만)
     */
    public List<CellAuthResponse> getAllActiveAuths() {
        return cellAuthRepository.findByIsActiveTrue().stream()
                .map(CellAuthResponse::from)
                .toList();
    }

    /**
     * 전체 셀 권한 조회 (비활성 포함, 관리자 페이지용)
     */
    public List<CellAuthResponse> getAllAuths() {
        return cellAuthRepository.findAllByOrderByTableCodeAscUserIdAsc().stream()
                .map(CellAuthResponse::from)
                .toList();
    }

    /**
     * 사용자별 셀 권한 조회
     */
    public List<CellAuthResponse> getAuthsByUser(Long userId) {
        return cellAuthRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(CellAuthResponse::from)
                .toList();
    }

    /**
     * 표 코드별 셀 권한 조회
     */
    public List<CellAuthResponse> getAuthsByTable(String tableCode) {
        return cellAuthRepository.findByTableCodeAndIsActiveTrue(tableCode).stream()
                .map(CellAuthResponse::from)
                .toList();
    }

    /**
     * 단건 조회
     */
    public CellAuthResponse getAuth(Long authId) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));
        return CellAuthResponse.from(auth);
    }

    /**
     * 셀 권한 등록
     */
    @Transactional
    public CellAuthResponse createAuth(CellAuthRequest request, Long grantedBy) {
        // 동일 사용자+표 코드 중복 확인
        cellAuthRepository.findByUserIdAndTableCode(request.getUserId(), request.getTableCode())
                .ifPresent(existing -> {
                    if (Boolean.TRUE.equals(existing.getIsActive())) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT,
                                String.format("이미 권한이 존재합니다. userId=%d, tableCode=%s",
                                        request.getUserId(), request.getTableCode()));
                    }
                    // 비활성 상태면 재활성화
                    existing.updateActive(true);
                    existing.updateAll(
                            request.getCellCoordsAsJson(),
                            request.getFreqCode(),
                            request.getFreqLabel(),
                            request.getDescription(),
                            grantedBy);
                });

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
        return CellAuthResponse.from(auth);
    }

    /**
     * 셀 권한 수정
     */
    @Transactional
    public CellAuthResponse updateAuth(Long authId, CellAuthRequest request, Long grantedBy) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));

        auth.updateAll(
                request.getCellCoordsAsJson(),
                request.getFreqCode(),
                request.getFreqLabel(),
                request.getDescription(),
                grantedBy);

        return CellAuthResponse.from(auth);
    }

    /**
     * 셀 권한 비활성화 (논리 삭제)
     */
    @Transactional
    public void deactivateAuth(Long authId) {
        CellAuth auth = cellAuthRepository.findById(authId)
                .orElseThrow(() -> new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId));
        auth.updateActive(false);
    }

    /**
     * 셀 권한 물리 삭제
     */
    @Transactional
    public void deleteAuth(Long authId) {
        if (!cellAuthRepository.existsById(authId)) {
            throw new EntityNotFoundException("셀 권한을 찾을 수 없습니다. ID: " + authId);
        }
        cellAuthRepository.deleteById(authId);
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
