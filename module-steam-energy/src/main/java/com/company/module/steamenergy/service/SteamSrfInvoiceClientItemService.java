package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceClientItemSaveRequest;
import com.company.module.steamenergy.entity.SteamSrfInvoiceClientItem;
import com.company.module.steamenergy.repository.SteamSrfInvoiceClientItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SRF 인보이스 Client Invoice 비즈니스 로직 — (YEAR_NO, MONTH_NO, SECTION, LINE_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamSrfInvoiceClientItemService {

    private final SteamSrfInvoiceClientItemRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamSrfInvoiceClientItemResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamSrfInvoiceClientItemResponse::from).toList();
    }

    public SteamSrfInvoiceClientItemResponse get(Long id) {
        return SteamSrfInvoiceClientItemResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamSrfInvoiceClientItemResponse save(SteamSrfInvoiceClientItemSaveRequest request) {
        Upsert result = upsert(request);
        return SteamSrfInvoiceClientItemResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamSrfInvoiceClientItemBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamSrfInvoiceClientItemSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getSection() + "|" + r.getLineNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getSection() + "|" + r.getLineNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamSrfInvoiceClientItem e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo() + "|" + e.getSection() + "|" + e.getLineNo())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] SRF 인보이스 Client Invoice 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] SRF 인보이스 Client Invoice 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamSrfInvoiceClientItem entity, boolean updated) {}

    private Upsert upsert(SteamSrfInvoiceClientItemSaveRequest r) {
        SteamSrfInvoiceClientItem active = repository.findActive(r.getYearNo(), r.getMonthNo(), r.getSection(), r.getLineNo()).orElse(null);
        if (active != null) {
            active.update(r.getItemLabel(), r.getUnitLabel(), r.getQty(), r.getUnitPrice(), r.getNote(), null);
            return new Upsert(active, true);
        }
        SteamSrfInvoiceClientItem deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo(), r.getSection(), r.getLineNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getItemLabel(), r.getUnitLabel(), r.getQty(), r.getUnitPrice(), r.getNote(), null);
            return new Upsert(deleted, true);
        }
        SteamSrfInvoiceClientItem created = repository.save(SteamSrfInvoiceClientItem.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .section(r.getSection())
                .lineNo(r.getLineNo())
                .itemLabel(r.getItemLabel())
                .unitLabel(r.getUnitLabel())
                .qty(r.getQty())
                .unitPrice(r.getUnitPrice())
                .note(r.getNote())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamSrfInvoiceClientItem> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(year).stream().filter(e -> month == null || month.equals(e.getMonthNo())).toList();
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamSrfInvoiceClientItem findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("SRF 인보이스 Client Invoice", id));
    }
}
