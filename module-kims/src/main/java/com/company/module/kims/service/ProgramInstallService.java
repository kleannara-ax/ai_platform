package com.company.module.kims.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.ProgramInstallCreateRequest;
import com.company.module.kims.dto.request.ProgramInstallUpdateRequest;
import com.company.module.kims.dto.response.ProgramInstallResponse;
import com.company.module.kims.entity.ProgramInstall;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.repository.ProgramInstallRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로그램 설치 내역 비즈니스 로직.
 * <p>등록 / 목록 / 상세 / 수정 / 삭제 / 엑셀을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramInstallService {

    private final ProgramInstallRepository programInstallRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelExportService excelExportService;

    // ================================================================
    // 등록
    // ================================================================
    @Transactional
    public ProgramInstallResponse register(ProgramInstallCreateRequest req) {
        // 설치일 기본값 = 오늘
        LocalDate installedAt = (req.getInstalledAt() != null) ? req.getInstalledAt() : LocalDate.now();

        ProgramInstall entity = ProgramInstall.builder()
                .serviceRequest(findRequestOrNull(req.getRequestId()))
                .programName(req.getProgramName())
                .requesterName(req.getRequesterName())
                .department(req.getDepartment())
                .targetPc(req.getTargetPc())
                .installedBy(req.getInstalledBy())
                .installedAt(installedAt)
                .remark(req.getRemark())
                .build();

        return ProgramInstallResponse.from(programInstallRepository.save(entity));
    }

    // ================================================================
    // 목록 (검색 + 페이징)
    // ================================================================
    public PageResponse<ProgramInstallResponse> getList(String keyword, String department, String installedBy,
                                                        LocalDate from, LocalDate to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProgramInstallResponse> result = programInstallRepository
                .search(emptyToNull(keyword), emptyToNull(department), emptyToNull(installedBy), from, to, pageable)
                .map(ProgramInstallResponse::from);
        return PageResponse.of(result);
    }

    // ================================================================
    // 상세
    // ================================================================
    public ProgramInstallResponse getDetail(Long installId) {
        return ProgramInstallResponse.from(findInstall(installId));
    }

    // ================================================================
    // 수정
    // ================================================================
    @Transactional
    public ProgramInstallResponse update(Long installId, ProgramInstallUpdateRequest req) {
        ProgramInstall entity = findInstall(installId);
        entity.update(req.getProgramName(), req.getRequesterName(), req.getDepartment(),
                req.getTargetPc(), req.getInstalledBy(), req.getInstalledAt(), req.getRemark());
        return ProgramInstallResponse.from(entity);
    }

    // ================================================================
    // 삭제
    // ================================================================
    @Transactional
    public void delete(Long installId, String deletedBy) {
        findInstall(installId).delete(deletedBy);
    }

    // ================================================================
    // Excel 다운로드
    // ================================================================
    public byte[] exportExcel(String keyword, String department, String installedBy,
                              LocalDate from, LocalDate to) {
        List<ProgramInstall> list = programInstallRepository
                .search(emptyToNull(keyword), emptyToNull(department), emptyToNull(installedBy), from, to,
                        Pageable.unpaged())
                .getContent();
        return excelExportService.buildProgramInstallExcel(list);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private ProgramInstall findInstall(Long installId) {
        ProgramInstall entity = programInstallRepository.findById(installId)
                .orElseThrow(() -> new EntityNotFoundException("설치 내역을 찾을 수 없습니다. id=" + installId));
        if (entity.isDeleted()) {
            throw new EntityNotFoundException("설치 내역을 찾을 수 없습니다. id=" + installId);
        }
        return entity;
    }

    private ServiceRequest findRequestOrNull(Long requestId) {
        if (requestId == null) {
            return null;
        }
        return serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("연결할 업무 요청을 찾을 수 없습니다. id=" + requestId));
    }

    private String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
