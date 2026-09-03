package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.safety.dto.request.ColumnSaveRequest;
import com.company.module.safety.dto.request.ManualCreateRequest;
import com.company.module.safety.dto.request.ManualUpdateRequest;
import com.company.module.safety.dto.request.StepCreateRequest;
import com.company.module.safety.dto.request.StepUpdateRequest;
import com.company.module.safety.dto.response.ColumnResponse;
import com.company.module.safety.dto.response.ManualDetailResponse;
import com.company.module.safety.dto.response.ManualSummaryResponse;
import com.company.module.safety.dto.response.MetaResponse;
import com.company.module.safety.dto.response.StepPhotoResponse;
import com.company.module.safety.dto.response.StepResponse;
import com.company.module.safety.dto.response.StepValueResponse;
import com.company.module.safety.entity.SafetyFormType;
import com.company.module.safety.entity.SafetyManual;
import com.company.module.safety.entity.SafetyManualCategory;
import com.company.module.safety.entity.SafetyManualColumn;
import com.company.module.safety.entity.SafetyManualStep;
import com.company.module.safety.entity.SafetyManualStepValue;
import com.company.module.safety.repository.SafetyManualColumnRepository;
import com.company.module.safety.repository.SafetyManualMetaRepository;
import com.company.module.safety.repository.SafetyManualRepository;
import com.company.module.safety.repository.SafetyManualStepPhotoRepository;
import com.company.module.safety.repository.SafetyManualStepRepository;
import com.company.module.safety.repository.SafetyManualStepValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 안전작업 매뉴얼(본문) 관련 비즈니스 로직.
 *
 * <p>표의 열 구성이 매뉴얼마다 다르므로(서식/사용자 정의), 상세는 열 정의 + 행 + 행x열 값을
 * 함께 돌려준다. 사진은 {@code SafetyPhotoService} 에서 별도 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyManualService {

    /** 한 매뉴얼당 보여줄 발췌 최대 개수 */
    private static final int SNIPPET_LIMIT = 3;
    /** 발췌에서 키워드 앞뒤로 남길 글자 수 */
    private static final int SNIPPET_CONTEXT = 30;

    /** 직접 등록한 매뉴얼이 처음 갖는 열 구성 (서식별 기본값) */
    private static final List<ColumnSeed> WORK_METHOD_SEED = List.of(
            new ColumnSeed("공정 순서(사진)", SafetyManualColumn.TYPE_PHOTO, 150),
            new ColumnSeed("공정 순서(설명)", SafetyManualColumn.TYPE_TEXT, 270),
            new ColumnSeed("위험요인", SafetyManualColumn.TYPE_TEXT, 400),
            new ColumnSeed("안전 보호구", SafetyManualColumn.TYPE_TEXT, 150),
            new ColumnSeed("비고", SafetyManualColumn.TYPE_TEXT, 120));

    private static final List<ColumnSeed> RISK_ASSESSMENT_SEED = List.of(
            new ColumnSeed("작업 순서", SafetyManualColumn.TYPE_TEXT, 300),
            new ColumnSeed("발생 가능한 위험", SafetyManualColumn.TYPE_TEXT, 320),
            new ColumnSeed("확인1", SafetyManualColumn.TYPE_CHECK, 60),
            new ColumnSeed("위험성 평가 대책", SafetyManualColumn.TYPE_TEXT, 320),
            new ColumnSeed("확인2", SafetyManualColumn.TYPE_CHECK, 60));

    private final SafetyManualRepository manualRepository;
    private final SafetyManualStepRepository stepRepository;
    private final SafetyManualStepPhotoRepository photoRepository;
    private final SafetyManualColumnRepository columnRepository;
    private final SafetyManualStepValueRepository valueRepository;
    private final SafetyManualMetaRepository metaRepository;
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
     * <p>좌측 분류 트리에서 어느 단계를 클릭하더라도 그 아래 매뉴얼을 모두 보여주기 위한 조회이다.
     * {@code categoryId} 가 null 이면 전체 매뉴얼을 반환한다.
     */
    public List<ManualSummaryResponse> getListInSubtree(Long categoryId) {
        return manualRepository.findInCategorySubtree(categoryId)
                .stream().map(ManualSummaryResponse::from).toList();
    }

    /**
     * 분류 하위에서 <b>표 안의 글</b>로 매뉴얼을 찾는다 (제목 검색과 별개).
     * <p>왜 걸렸는지 알 수 있도록 매칭된 부분의 짧은 발췌를 함께 담아 준다.
     */
    public List<ManualSummaryResponse> searchByContent(Long categoryId, String keyword) {
        String needle = emptyToNull(keyword);
        if (needle == null) {
            return getListInSubtree(categoryId);
        }

        // 열 값에서 걸린 셀을 매뉴얼별로 모은다 (매뉴얼 순서는 조회 순서를 유지한다).
        Map<Long, List<SafetyManualStepValue>> hitsByManual = new LinkedHashMap<>();
        for (SafetyManualStepValue value : valueRepository.searchByText(categoryId, needle)) {
            hitsByManual.computeIfAbsent(value.getStep().getManual().getManualId(), k -> new ArrayList<>())
                    .add(value);
        }
        if (hitsByManual.isEmpty()) {
            return List.of();
        }

        List<ManualSummaryResponse> result = new ArrayList<>();
        for (Map.Entry<Long, List<SafetyManualStepValue>> entry : hitsByManual.entrySet()) {
            SafetyManual manual = manualRepository.findActiveById(entry.getKey()).orElse(null);
            if (manual == null) continue;
            List<SafetyManualStepValue> hits = entry.getValue();
            List<String> snippets = hits.stream()
                    .limit(SNIPPET_LIMIT)
                    .map(hit -> buildSnippet(hit, needle))
                    .filter(text -> !text.isBlank())
                    .toList();
            result.add(ManualSummaryResponse.withMatches(manual, hits.size(), snippets));
        }
        return result;
    }

    /** 걸린 셀을 "N행 열이름: ...앞뒤 문맥..." 형태로 짧게 잘라낸다. */
    private String buildSnippet(SafetyManualStepValue value, String keyword) {
        String raw = value.getTextValue();
        if (raw == null) {
            return "";
        }
        // Java 15+ 에서 "\s" 는 문자열 이스케이프(공백)라 정규식으로 넘기려면 반드시 두 번 escape 해야 한다.
        String flat = raw.replaceAll("\\s+", " ").trim();
        int at = flat.toLowerCase().indexOf(keyword.toLowerCase());
        if (at < 0) {
            return "";
        }
        int from = Math.max(0, at - SNIPPET_CONTEXT);
        int to = Math.min(flat.length(), at + keyword.length() + SNIPPET_CONTEXT);
        String excerpt = (from > 0 ? "..." : "") + flat.substring(from, to) + (to < flat.length() ? "..." : "");
        return value.getStep().getStepNo() + "행 " + value.getColumn().getLabel() + ": " + excerpt;
    }

    // ================================================================
    // 매뉴얼 상세 (열 정의 + 행 + 행x열 값)
    // ================================================================
    public ManualDetailResponse getDetail(Long manualId) {
        SafetyManual manual = findActive(manualId);
        List<SafetyManualColumn> columns = columnRepository.findByManualId(manualId);

        List<MetaResponse> meta = metaRepository.findByManualId(manualId)
                .stream().map(MetaResponse::from).toList();
        List<ColumnResponse> columnResponses = columns.stream().map(ColumnResponse::from).toList();
        return ManualDetailResponse.from(manual, meta, columnResponses, buildStepResponses(manualId));
    }

    private List<StepResponse> buildStepResponses(Long manualId) {
        List<SafetyManualStep> steps = stepRepository.findByManualIdOrderBySortOrder(manualId);

        Map<Long, List<StepPhotoResponse>> photosByStep = photoRepository.findByManualId(manualId)
                .stream()
                .map(p -> Map.entry(p.getStep().getStepId(), StepPhotoResponse.from(p)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        Map<Long, List<StepValueResponse>> valuesByStep = valueRepository.findByManualId(manualId)
                .stream()
                .map(v -> Map.entry(v.getStep().getStepId(), StepValueResponse.from(v)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        return steps.stream()
                .map(s -> StepResponse.from(s,
                        valuesByStep.getOrDefault(s.getStepId(), List.of()),
                        photosByStep.getOrDefault(s.getStepId(), List.of())))
                .toList();
    }

    // ================================================================
    // 매뉴얼 등록 (관리자, 화면에서 직접 추가) — 서식 기본 열까지 만들어 준다
    // ================================================================
    @Transactional
    public ManualDetailResponse create(ManualCreateRequest request, String createdBy) {
        SafetyManualCategory category = categoryService.findActiveLeaf(request.getCategoryId());
        if (manualRepository.existsByTitleAndCategory_CategoryId(request.getTitle(), request.getCategoryId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "같은 분류 안에 이미 존재하는 매뉴얼 제목입니다. title=" + request.getTitle());
        }

        SafetyFormType formType = SafetyFormType.of(request.getFormType());
        SafetyManual manual = manualRepository.save(SafetyManual.builder()
                .category(category)
                .title(request.getTitle())
                .formType(formType)
                .sortOrder(request.getSortOrder())
                .createdBy(createdBy)
                .build());

        List<ColumnSeed> seed = (formType == SafetyFormType.RISK_ASSESSMENT)
                ? RISK_ASSESSMENT_SEED : WORK_METHOD_SEED;
        int order = 1;
        for (ColumnSeed column : seed) {
            columnRepository.save(SafetyManualColumn.builder()
                    .manual(manual)
                    .label(column.label())
                    .columnType(column.type())
                    .sortOrder(order++)
                    .widthWeight(column.widthWeight())
                    .createdBy(createdBy)
                    .build());
        }
        return getDetail(manual.getManualId());
    }

    // ================================================================
    // 매뉴얼 기본정보 수정 (관리자)
    // ================================================================
    @Transactional
    public ManualDetailResponse update(Long manualId, ManualUpdateRequest request, String updatedBy) {
        SafetyManual manual = findActive(manualId);
        SafetyManualCategory category = categoryService.findActiveLeaf(request.getCategoryId());
        manual.update(category, request.getTitle(), request.getSortOrder(), updatedBy);
        return getDetail(manualId);
    }

    /**
     * 매뉴얼을 다른 분류로 옮긴다 (화면에서 목록의 매뉴얼을 분류로 끌어다 놓는 경우).
     * <p>제목·정렬순서는 건드리지 않고 분류만 바꾼다. 대상은 중분류여야 한다.
     */
    @Transactional
    public ManualSummaryResponse moveToCategory(Long manualId, Long categoryId, String updatedBy) {
        SafetyManual manual = findActive(manualId);
        SafetyManualCategory target = categoryService.findActiveLeaf(categoryId);
        if (manual.getCategory() != null && target.getCategoryId().equals(manual.getCategory().getCategoryId())) {
            return ManualSummaryResponse.from(manual);   // 같은 분류면 그대로 둔다
        }
        if (manualRepository.existsByTitleAndCategory_CategoryId(manual.getTitle(), categoryId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "'" + target.getName() + "' 분류에 같은 제목의 매뉴얼이 이미 있습니다. title=" + manual.getTitle());
        }
        manual.changeCategory(target, updatedBy);
        return ManualSummaryResponse.from(manual);
    }

    // ================================================================
    // 매뉴얼 삭제 (관리자) — 소프트 삭제. 하위 행도 함께 소프트 삭제.
    // ================================================================
    @Transactional
    public void delete(Long manualId, String deletedBy) {
        SafetyManual manual = findActive(manualId);
        stepRepository.findByManualIdOrderBySortOrder(manualId)
                .forEach(step -> step.delete(deletedBy));
        manual.delete(deletedBy);
    }

    // ================================================================
    // 표의 열 관리 (관리자) — 이름/유형/순서/폭, 체크 열 추가
    // ================================================================
    @Transactional
    public List<ColumnResponse> addColumn(Long manualId, ColumnSaveRequest request, String createdBy) {
        SafetyManual manual = findActive(manualId);
        int nextOrder = columnRepository.findMaxSortOrder(manualId) + 1;
        columnRepository.save(SafetyManualColumn.builder()
                .manual(manual)
                .label(request.getLabel())
                .columnType(request.getColumnType())
                .sortOrder(request.getSortOrder() > 0 ? request.getSortOrder() : nextOrder)
                .widthWeight(request.getWidthWeight())
                .createdBy(createdBy)
                .build());
        return getColumns(manualId);
    }

    @Transactional
    public List<ColumnResponse> updateColumn(Long columnId, ColumnSaveRequest request, String updatedBy) {
        SafetyManualColumn column = findActiveColumn(columnId);
        column.update(request.getLabel(), request.getColumnType(),
                request.getSortOrder(), request.getWidthWeight(), updatedBy);
        return getColumns(column.getManual().getManualId());
    }

    /** 열 삭제 — 그 열에 들어 있던 값도 함께 소프트 삭제한다. */
    @Transactional
    public List<ColumnResponse> deleteColumn(Long columnId, String deletedBy) {
        SafetyManualColumn column = findActiveColumn(columnId);
        Long manualId = column.getManual().getManualId();
        valueRepository.findByColumnId(columnId).forEach(value -> value.delete(deletedBy));
        column.delete(deletedBy);
        return getColumns(manualId);
    }

    /** 열 순서 일괄 변경 — 화면에서 넘긴 순서대로 1부터 다시 매긴다. */
    @Transactional
    public List<ColumnResponse> reorderColumns(Long manualId, List<Long> columnIds, String updatedBy) {
        if (columnIds == null || columnIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정렬할 열 목록이 비어 있습니다.");
        }
        int order = 1;
        for (Long columnId : columnIds) {
            SafetyManualColumn column = findActiveColumn(columnId);
            if (!column.getManual().getManualId().equals(manualId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "다른 매뉴얼의 열은 정렬할 수 없습니다. columnId=" + columnId);
            }
            column.changeSortOrder(order++, updatedBy);
        }
        return getColumns(manualId);
    }

    public List<ColumnResponse> getColumns(Long manualId) {
        return columnRepository.findByManualId(manualId).stream().map(ColumnResponse::from).toList();
    }

    // ================================================================
    // 행(단계) 추가/수정/삭제 + 칸 값 저장
    // ================================================================
    @Transactional
    public StepResponse addStep(Long manualId, StepCreateRequest request, String createdBy) {
        SafetyManual manual = findActive(manualId);
        SafetyManualStep step = stepRepository.save(SafetyManualStep.builder()
                .manual(manual)
                .stepNo(request.getStepNo())
                .sortOrder(request.getSortOrder())
                .createdBy(createdBy)
                .build());
        applyValues(step, request.getValues(), createdBy);
        return toStepResponse(step);
    }

    @Transactional
    public StepResponse updateStep(Long stepId, StepUpdateRequest request, String updatedBy) {
        SafetyManualStep step = findActiveStep(stepId);
        step.updatePosition(request.getStepNo(), request.getSortOrder(), updatedBy);
        applyValues(step, request.getValues(), updatedBy);
        return toStepResponse(step);
    }

    /** 체크 열 하나만 켜고 끈다 (표에서 체크버튼을 누르는 경우). */
    @Transactional
    public StepResponse toggleCheck(Long stepId, Long columnId, boolean checked, String updatedBy) {
        SafetyManualStep step = findActiveStep(stepId);
        SafetyManualColumn column = findActiveColumn(columnId);
        if (!column.isCheck()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크 열이 아닙니다. columnId=" + columnId);
        }
        valueRepository.findByStepAndColumn(stepId, columnId)
                .ifPresentOrElse(
                        value -> value.updateChecked(checked, updatedBy),
                        () -> valueRepository.save(SafetyManualStepValue.builder()
                                .step(step).column(column).checked(checked).createdBy(updatedBy).build()));
        return toStepResponse(step);
    }

    /** 화면에서 넘어온 칸 값들을 열 기준으로 저장한다 (없으면 만들고, 있으면 고친다). */
    private void applyValues(SafetyManualStep step, List<StepCreateRequest.CellValue> values, String actor) {
        if (values == null) {
            return;
        }
        for (StepCreateRequest.CellValue cell : values) {
            if (cell.getColumnId() == null) continue;
            SafetyManualColumn column = findActiveColumn(cell.getColumnId());
            valueRepository.findByStepAndColumn(step.getStepId(), column.getColumnId())
                    .ifPresentOrElse(existing -> {
                        if (column.isCheck()) existing.updateChecked(cell.isChecked(), actor);
                        else existing.updateText(cell.getText(), actor);
                    }, () -> valueRepository.save(SafetyManualStepValue.builder()
                            .step(step)
                            .column(column)
                            .textValue(column.isCheck() ? null : cell.getText())
                            .checked(column.isCheck() && cell.isChecked())
                            .createdBy(actor)
                            .build()));
        }
    }

    @Transactional
    public void deleteStep(Long stepId, String deletedBy) {
        SafetyManualStep step = findActiveStep(stepId);
        valueRepository.findByStepId(stepId).forEach(value -> value.delete(deletedBy));
        step.delete(deletedBy);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private StepResponse toStepResponse(SafetyManualStep step) {
        List<StepValueResponse> values = valueRepository.findByStepId(step.getStepId())
                .stream().map(StepValueResponse::from).toList();
        List<StepPhotoResponse> photos = photoRepository.findByStepIdOrderBySortOrder(step.getStepId())
                .stream().map(StepPhotoResponse::from).toList();
        return StepResponse.from(step, values, photos);
    }

    SafetyManual findActive(Long manualId) {
        return manualRepository.findActiveById(manualId)
                .orElseThrow(() -> new EntityNotFoundException("매뉴얼을 찾을 수 없습니다. id=" + manualId));
    }

    private SafetyManualStep findActiveStep(Long stepId) {
        return stepRepository.findActiveById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("단계를 찾을 수 없습니다. id=" + stepId));
    }

    private SafetyManualColumn findActiveColumn(Long columnId) {
        return columnRepository.findActiveById(columnId)
                .orElseThrow(() -> new EntityNotFoundException("열을 찾을 수 없습니다. id=" + columnId));
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 서식별 기본 열 정의 */
    private record ColumnSeed(String label, String type, int widthWeight) {
    }
}
