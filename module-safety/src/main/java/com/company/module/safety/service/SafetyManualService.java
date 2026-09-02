package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.safety.dto.request.ManualCreateRequest;
import com.company.module.safety.dto.request.ManualUpdateRequest;
import com.company.module.safety.dto.request.StepCreateRequest;
import com.company.module.safety.dto.request.StepUpdateRequest;
import com.company.module.safety.dto.response.ManualDetailResponse;
import com.company.module.safety.dto.response.ManualSummaryResponse;
import com.company.module.safety.dto.response.StepPhotoResponse;
import com.company.module.safety.dto.response.StepResponse;
import com.company.module.safety.entity.SafetyManual;
import com.company.module.safety.entity.SafetyManualCategory;
import com.company.module.safety.entity.SafetyManualStep;
import com.company.module.safety.repository.SafetyManualRepository;
import com.company.module.safety.repository.SafetyManualStepPhotoRepository;
import com.company.module.safety.repository.SafetyManualStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 안전작업방식 매뉴얼(본문) 관련 비즈니스 로직.
 * <p>매뉴얼 CRUD + 단계(순서) CRUD를 담당한다. 사진은 {@code SafetyPhotoService} 에서 별도 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyManualService {

    private final SafetyManualRepository manualRepository;
    private final SafetyManualStepRepository stepRepository;
    private final SafetyManualStepPhotoRepository photoRepository;
    private final SafetyCategoryService categoryService;

    // ================================================================
    // 매뉴얼 목록 (분류 선택 시 / 검색)
    // ================================================================
    public PageResponse<ManualSummaryResponse> getList(String keyword, Long categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ManualSummaryResponse> result = manualRepository
                .search(emptyToNull(keyword), categoryId, pageable)
                .map(ManualSummaryResponse::from);
        return PageResponse.of(result);
    }

    /** 특정 분류에 직접 속한 매뉴얼 (트리 화면에서 분류 클릭 시) */
    public List<ManualSummaryResponse> getListByCategory(Long categoryId) {
        return manualRepository.findByCategoryId(categoryId)
                .stream().map(ManualSummaryResponse::from).toList();
    }

    /**
     * 분류 하위 전체 매뉴얼.
     * <p>좌측 분류 트리에서 대분류/중분류/소분류 어느 단계를 클릭하더라도 그 아래 매뉴얼을 모두 보여주기 위한 조회이다.
     * {@code categoryId} 가 null 이면 전체 매뉴얼을 반환한다.
     */
    public List<ManualSummaryResponse> getListInSubtree(Long categoryId) {
        return manualRepository.findInCategorySubtree(categoryId)
                .stream().map(ManualSummaryResponse::from).toList();
    }

    // ================================================================
    // 매뉴얼 상세 (엑셀과 같은 레이아웃: 단계 + 단계별 사진)
    // ================================================================
    public ManualDetailResponse getDetail(Long manualId) {
        SafetyManual manual = findActive(manualId);
        List<StepResponse> steps = buildStepResponses(manualId);
        return ManualDetailResponse.from(manual, steps);
    }

    private List<StepResponse> buildStepResponses(Long manualId) {
        List<SafetyManualStep> stepEntities = stepRepository.findByManualIdOrderBySortOrder(manualId);
        Map<Long, List<StepPhotoResponse>> photosByStep = photoRepository.findByManualId(manualId)
                .stream()
                .map(p -> Map.entry(p.getStep().getStepId(), StepPhotoResponse.from(p)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        return stepEntities.stream()
                .map(s -> StepResponse.from(s, photosByStep.getOrDefault(s.getStepId(), List.of())))
                .toList();
    }

    // ================================================================
    // 매뉴얼 등록 (관리자, 화면에서 직접 추가) — 단계까지 한번에 등록
    // ================================================================
    @Transactional
    public ManualDetailResponse create(ManualCreateRequest request, String createdBy) {
        SafetyManualCategory category = categoryService.findActiveMinor(request.getCategoryId());
        if (manualRepository.existsByTitleAndCategory_CategoryId(request.getTitle(), request.getCategoryId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "같은 분류 안에 이미 존재하는 매뉴얼 제목입니다. title=" + request.getTitle());
        }

        SafetyManual manual = SafetyManual.builder()
                .category(category)
                .title(request.getTitle())
                .sortOrder(request.getSortOrder())
                .createdBy(createdBy)
                .build();
        manual = manualRepository.save(manual);

        if (request.getSteps() != null) {
            for (StepCreateRequest s : request.getSteps()) {
                stepRepository.save(SafetyManualStep.builder()
                        .manual(manual)
                        .stepNo(s.getStepNo())
                        .description(s.getDescription())
                        .hazard(s.getHazard())
                        .safetyEquipment(s.getSafetyEquipment())
                        .remark(s.getRemark())
                        .sortOrder(s.getSortOrder())
                        .createdBy(createdBy)
                        .build());
            }
        }
        return getDetail(manual.getManualId());
    }

    // ================================================================
    // 매뉴얼 기본정보 수정 (관리자)
    // ================================================================
    @Transactional
    public ManualDetailResponse update(Long manualId, ManualUpdateRequest request, String updatedBy) {
        SafetyManual manual = findActive(manualId);
        SafetyManualCategory category = categoryService.findActiveMinor(request.getCategoryId());
        manual.update(category, request.getTitle(), request.getSortOrder(), updatedBy);
        return getDetail(manualId);
    }

    // ================================================================
    // 매뉴얼 삭제 (관리자) — 소프트 삭제. 하위 단계도 함께 소프트 삭제.
    // ================================================================
    @Transactional
    public void delete(Long manualId, String deletedBy) {
        SafetyManual manual = findActive(manualId);
        stepRepository.findByManualIdOrderBySortOrder(manualId)
                .forEach(step -> step.delete(deletedBy));
        manual.delete(deletedBy);
    }

    // ================================================================
    // 단계(순서) 추가/수정/삭제 (관리자) — 매뉴얼 상세 화면에서 개별 관리
    // ================================================================
    @Transactional
    public StepResponse addStep(Long manualId, StepCreateRequest request, String createdBy) {
        SafetyManual manual = findActive(manualId);
        SafetyManualStep step = stepRepository.save(SafetyManualStep.builder()
                .manual(manual)
                .stepNo(request.getStepNo())
                .description(request.getDescription())
                .hazard(request.getHazard())
                .safetyEquipment(request.getSafetyEquipment())
                .remark(request.getRemark())
                .sortOrder(request.getSortOrder())
                .createdBy(createdBy)
                .build());
        return StepResponse.from(step, List.of());
    }

    @Transactional
    public StepResponse updateStep(Long stepId, StepUpdateRequest request, String updatedBy) {
        SafetyManualStep step = findActiveStep(stepId);
        step.update(request.getStepNo(), request.getDescription(), request.getHazard(),
                request.getSafetyEquipment(), request.getRemark(), request.getSortOrder(), updatedBy);
        List<StepPhotoResponse> photos = photoRepository.findByStepIdOrderBySortOrder(stepId)
                .stream().map(StepPhotoResponse::from).toList();
        return StepResponse.from(step, photos);
    }

    @Transactional
    public void deleteStep(Long stepId, String deletedBy) {
        SafetyManualStep step = findActiveStep(stepId);
        step.delete(deletedBy);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    SafetyManual findActive(Long manualId) {
        return manualRepository.findActiveById(manualId)
                .orElseThrow(() -> new EntityNotFoundException("매뉴얼을 찾을 수 없습니다. id=" + manualId));
    }

    private SafetyManualStep findActiveStep(Long stepId) {
        return stepRepository.findActiveById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("단계를 찾을 수 없습니다. id=" + stepId));
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
