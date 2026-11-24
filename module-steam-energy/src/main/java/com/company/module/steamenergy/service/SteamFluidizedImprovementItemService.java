package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemResponse;
import com.company.module.steamenergy.dto.SteamFluidizedImprovementItemSaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedImprovementItem;
import com.company.module.steamenergy.repository.SteamFluidizedImprovementItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 설비개선 항목 비즈니스 로직 — (YEAR_NO, RECORD_KEY) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedImprovementItemService {

    private final SteamFluidizedImprovementItemRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedImprovementItemResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedImprovementItemResponse::from).toList();
    }

    public SteamFluidizedImprovementItemResponse get(Long id) {
        return SteamFluidizedImprovementItemResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedImprovementItemResponse save(SteamFluidizedImprovementItemSaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedImprovementItemResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 삭제한다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedImprovementItemBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedImprovementItemSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getRecordKey()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getRecordKey());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedImprovementItem e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getRecordKey())) continue;
                e.delete(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 설비개선 항목 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 설비개선 항목 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedImprovementItem entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedImprovementItemSaveRequest r) {
        SteamFluidizedImprovementItem active = repository.findActive(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (active != null) {
            active.update(r.getCategory(), r.getItemNo(), r.getItemName(), r.getPlanCost(), r.getActualCost(), r.getStatus(), r.getRemark(), r.getSortOrder(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedImprovementItem deleted = repository.findAnyByKey(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getCategory(), r.getItemNo(), r.getItemName(), r.getPlanCost(), r.getActualCost(), r.getStatus(), r.getRemark(), r.getSortOrder(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedImprovementItem created = repository.save(SteamFluidizedImprovementItem.builder()
                .yearNo(r.getYearNo())
                .recordKey(r.getRecordKey())
                .category(r.getCategory())
                .itemNo(r.getItemNo())
                .itemName(r.getItemName())
                .planCost(r.getPlanCost())
                .actualCost(r.getActualCost())
                .status(r.getStatus())
                .remark(r.getRemark())
                .sortOrder(r.getSortOrder())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedImprovementItem> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(year);
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamFluidizedImprovementItem findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 설비개선 항목", id));
    }
}
