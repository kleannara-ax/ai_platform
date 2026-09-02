package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.dto.request.CategoryCreateRequest;
import com.company.module.safety.dto.request.CategoryUpdateRequest;
import com.company.module.safety.dto.response.CategoryResponse;
import com.company.module.safety.entity.SafetyManualCategory;
import com.company.module.safety.repository.SafetyManualCategoryRepository;
import com.company.module.safety.repository.SafetyManualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 안전작업방식 매뉴얼 분류(카테고리) 관련 비즈니스 로직.
 * <p>분류는 대분류(1) &gt; 중분류(2) &gt; 소분류(3) 정확히 3단계로 고정된 자기참조 트리이다.
 * 매뉴얼은 항상 소분류(3단계)에만 등록할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyCategoryService {

    private final SafetyManualCategoryRepository categoryRepository;
    private final SafetyManualRepository manualRepository;

    // ================================================================
    // 분류 트리 조회 (화면: 분류 목록 - 대분류→중분류→소분류 드릴다운)
    // ================================================================
    public List<CategoryResponse> getTree() {
        List<SafetyManualCategory> all = categoryRepository.findAllActive();
        Map<Long, List<SafetyManualCategory>> childrenByParent = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getCategoryId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildNode(root, childrenByParent))
                .toList();
    }

    private CategoryResponse buildNode(SafetyManualCategory node, Map<Long, List<SafetyManualCategory>> childrenByParent) {
        List<SafetyManualCategory> children = childrenByParent.getOrDefault(node.getCategoryId(), List.of());
        List<CategoryResponse> childResponses = new ArrayList<>();
        for (SafetyManualCategory child : children) {
            childResponses.add(buildNode(child, childrenByParent));
        }
        return CategoryResponse.withChildren(node, childResponses);
    }

    public CategoryResponse getDetail(Long categoryId) {
        return CategoryResponse.from(findActive(categoryId));
    }

    /** 특정 부모의 하위 분류 목록 (대분류 선택 → 중분류 조회, 중분류 선택 → 소분류 조회 등 단계별 선택 UI용) */
    public List<CategoryResponse> getChildren(Long parentId) {
        List<SafetyManualCategory> children = (parentId == null)
                ? categoryRepository.findRootCategories()
                : categoryRepository.findByParentId(parentId);
        return children.stream().map(CategoryResponse::from).toList();
    }

    // ================================================================
    // 분류 등록 (관리자) — parentId 로 단계가 자동 결정된다 (null=대분류)
    // ================================================================
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request, String createdBy) {
        SafetyManualCategory parent = null;
        if (request.getParentId() != null) {
            parent = findActive(request.getParentId());
            if (parent.isMinor()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "소분류 아래에는 하위 분류를 추가할 수 없습니다. (대분류/중분류/소분류 3단계까지만 지원)");
            }
        }
        if (categoryRepository.existsByNameAndParent(request.getName(), request.getParentId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "같은 상위 분류 아래 이미 존재하는 분류명입니다. name=" + request.getName());
        }

        SafetyManualCategory entity = SafetyManualCategory.builder()
                .name(request.getName())
                .parent(parent)
                .sortOrder(request.getSortOrder())
                .createdBy(createdBy)
                .build();
        return CategoryResponse.from(categoryRepository.save(entity));
    }

    // ================================================================
    // 분류 수정 (관리자) — 이름/표시순서만 변경 (단계 이동은 지원하지 않음)
    // ================================================================
    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request, String updatedBy) {
        SafetyManualCategory entity = findActive(categoryId);
        entity.update(request.getName(), request.getSortOrder(), updatedBy);
        return CategoryResponse.from(entity);
    }

    // ================================================================
    // 분류 삭제 (관리자) - 소프트 삭제. 하위 분류/매뉴얼이 있으면 거부.
    // ================================================================
    @Transactional
    public void delete(Long categoryId, String deletedBy) {
        SafetyManualCategory entity = findActive(categoryId);

        List<SafetyManualCategory> children = categoryRepository.findByParentId(categoryId);
        if (!children.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "하위 분류가 있어 삭제할 수 없습니다. 먼저 하위 분류를 정리하세요.");
        }
        List<com.company.module.safety.entity.SafetyManual> manuals = manualRepository.findByCategoryId(categoryId);
        if (!manuals.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 분류에 속한 매뉴얼이 있어 삭제할 수 없습니다. 먼저 매뉴얼을 이동하거나 삭제하세요.");
        }
        entity.delete(deletedBy);
    }

    // ----------------------------------------------------------------
    // 내부 공통 (다른 서비스에서도 재사용)
    // ----------------------------------------------------------------

    SafetyManualCategory findActive(Long categoryId) {
        return categoryRepository.findActiveById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("분류를 찾을 수 없습니다. id=" + categoryId));
    }

    /** 매뉴얼은 항상 소분류(3단계)에만 속해야 하므로, 매뉴얼 등록/이동 시 이 메서드로 검증한다. */
    SafetyManualCategory findActiveMinor(Long categoryId) {
        SafetyManualCategory category = findActive(categoryId);
        if (!category.isMinor()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "매뉴얼은 소분류(3단계)에만 등록할 수 있습니다. 소분류를 선택하세요.");
        }
        return category;
    }
}
