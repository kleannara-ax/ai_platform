package com.company.module.dailyreport.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.dailyreport.dto.CellPermissionRequest;
import com.company.module.dailyreport.dto.CellPermissionResponse;
import com.company.module.dailyreport.entity.CellPermission;
import com.company.module.dailyreport.repository.CellPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 셀 편집 권한 관리 서비스
 * - ADMIN이 사용자별 편집 가능 셀 범위를 설정
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellPermissionService {

    private final CellPermissionRepository permissionRepository;

    /**
     * 사용자별 권한 목록 조회
     */
    public List<CellPermissionResponse> getPermissionsByUser(Long userId) {
        return permissionRepository.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(CellPermissionResponse::from)
                .toList();
    }

    /**
     * 표 코드별 전체 권한 조회
     */
    public List<CellPermissionResponse> getPermissionsByTable(String tableCode) {
        return permissionRepository.findByTableCodeAndIsActiveTrue(tableCode)
                .stream()
                .map(CellPermissionResponse::from)
                .toList();
    }

    /**
     * 권한 등록
     */
    @Transactional
    public CellPermissionResponse createPermission(CellPermissionRequest request) {
        CellPermission permission = CellPermission.builder()
                .userId(request.getUserId())
                .tableCode(request.getTableCode())
                .rowStart(request.getRowStart())
                .rowEnd(request.getRowEnd())
                .colStart(request.getColStart())
                .colEnd(request.getColEnd())
                .inputCycle(request.getInputCycle())
                .isActive(true)
                .build();

        permissionRepository.save(permission);
        return CellPermissionResponse.from(permission);
    }

    /**
     * 권한 수정
     */
    @Transactional
    public CellPermissionResponse updatePermission(Long permissionId, CellPermissionRequest request) {
        CellPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "권한을 찾을 수 없습니다. ID: " + permissionId));

        permission.updateRange(request.getRowStart(), request.getRowEnd(),
                request.getColStart(), request.getColEnd());
        permission.updateInputCycle(request.getInputCycle());

        return CellPermissionResponse.from(permission);
    }

    /**
     * 권한 비활성화 (논리 삭제)
     */
    @Transactional
    public void deactivatePermission(Long permissionId) {
        CellPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "권한을 찾을 수 없습니다. ID: " + permissionId));
        permission.updateActive(false);
    }

    /**
     * 권한 물리 삭제
     */
    @Transactional
    public void deletePermission(Long permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new EntityNotFoundException("권한을 찾을 수 없습니다. ID: " + permissionId);
        }
        permissionRepository.deleteById(permissionId);
    }
}
