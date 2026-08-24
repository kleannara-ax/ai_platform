package com.company.module.kims.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.InternetWorkCreateRequest;
import com.company.module.kims.dto.request.InternetWorkStatusRequest;
import com.company.module.kims.dto.request.InternetWorkUpdateRequest;
import com.company.module.kims.dto.response.InternetWorkResponse;
import com.company.module.kims.entity.InternetWork;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.InternetWorkStatus;
import com.company.module.kims.entity.enums.InternetWorkType;
import com.company.module.kims.repository.InternetWorkRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인터넷 공사 비즈니스 로직.
 * <p>등록 / 목록 / 상세 / 수정 / 상태변경 / 삭제 / 엑셀을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternetWorkService {

    private final InternetWorkRepository internetWorkRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelExportService excelExportService;

    @Transactional
    public InternetWorkResponse register(InternetWorkCreateRequest req) {
        InternetWork entity = InternetWork.builder()
                .serviceRequest(findRequestOrNull(req.getRequestId()))
                .workType(req.getWorkType())
                .requesterName(req.getRequesterName())
                .department(req.getDepartment())
                .location(req.getLocation())
                .content(req.getContent())
                .externalVendor(req.isExternalVendor())
                .vendorName(req.getVendorName())
                .hasCost(req.isHasCost())
                .cost(req.getCost())
                .assignee(req.getAssignee())
                .status(req.getStatus())
                .completedAt(req.getCompletedAt())
                .remark(req.getRemark())
                .build();
        return InternetWorkResponse.from(internetWorkRepository.save(entity));
    }

    public PageResponse<InternetWorkResponse> getList(String keyword, InternetWorkType workType,
                                                     InternetWorkStatus status, String department,
                                                     LocalDateTime from, LocalDateTime to,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InternetWorkResponse> result = internetWorkRepository
                .search(emptyToNull(keyword), workType, status, emptyToNull(department), from, to, pageable)
                .map(InternetWorkResponse::from);
        return PageResponse.of(result);
    }

    public InternetWorkResponse getDetail(Long workId) {
        return InternetWorkResponse.from(findWork(workId));
    }

    @Transactional
    public InternetWorkResponse update(Long workId, InternetWorkUpdateRequest req) {
        InternetWork entity = findWork(workId);
        entity.update(req.getWorkType(), req.getRequesterName(), req.getDepartment(), req.getLocation(),
                req.getContent(), req.isExternalVendor(), req.getVendorName(),
                req.isHasCost(), req.getCost(), req.getAssignee(), req.getRemark());
        return InternetWorkResponse.from(entity);
    }

    @Transactional
    public InternetWorkResponse changeStatus(Long workId, InternetWorkStatusRequest req) {
        InternetWork entity = findWork(workId);
        entity.changeStatus(req.getStatus(), req.getCompletedAt());
        return InternetWorkResponse.from(entity);
    }

    @Transactional
    public void delete(Long workId) {
        internetWorkRepository.delete(findWork(workId));
    }

    public byte[] exportExcel(String keyword, InternetWorkType workType, InternetWorkStatus status,
                              String department, LocalDateTime from, LocalDateTime to) {
        List<InternetWork> list = internetWorkRepository
                .search(emptyToNull(keyword), workType, status, emptyToNull(department), from, to, Pageable.unpaged())
                .getContent();
        return excelExportService.buildInternetWorkExcel(list);
    }

    // ----------------------------------------------------------------
    private InternetWork findWork(Long workId) {
        return internetWorkRepository.findById(workId)
                .orElseThrow(() -> new EntityNotFoundException("인터넷 공사 내역을 찾을 수 없습니다. id=" + workId));
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
