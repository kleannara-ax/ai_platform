package com.company.module.steamenergy.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.steamenergy.dto.SteamPriceResponse;
import com.company.module.steamenergy.dto.SteamPriceSaveRequest;
import com.company.module.steamenergy.entity.SteamPrice;
import com.company.module.steamenergy.repository.SteamPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시설별 단가 비즈니스 로직
 *
 * <p>저장은 (연도+월+항목코드) 기준 업서트(Upsert).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamPriceService {

    private final SteamPriceRepository priceRepository;

    /** 연/월 페이징 조회 (월 미지정 시 연도 전체 페이징) */
    public Page<SteamPriceResponse> getList(Integer yearNo, Integer monthNo, Pageable pageable) {
        Page<SteamPrice> page = (monthNo != null)
                ? priceRepository.findByYearNoAndMonthNoOrderByFacilityAscItemCodeAsc(yearNo, monthNo, pageable)
                : priceRepository.findByYearNoOrderByMonthNoAscFacilityAscItemCodeAsc(yearNo, pageable);
        return page.map(SteamPriceResponse::from);
    }

    /** 연도 전체 단가 (관리자 그리드 로드용, 비페이징) */
    public List<SteamPriceResponse> getYear(Integer yearNo) {
        return priceRepository.findByYearNoOrderByFacilityAscItemCodeAscMonthNoAsc(yearNo)
                .stream().map(SteamPriceResponse::from).toList();
    }

    public SteamPriceResponse get(Long id) {
        return SteamPriceResponse.from(findOrThrow(id));
    }

    /** (연도+월+항목코드) 업서트 */
    @Transactional
    public SteamPriceResponse save(SteamPriceSaveRequest request) {
        SteamPrice existing = priceRepository
                .findByYearNoAndMonthNoAndItemCode(request.getYearNo(), request.getMonthNo(), request.getItemCode())
                .orElse(null);

        if (existing != null) {
            existing.update(request.getFacility(), request.getItemName(), request.getInputType(),
                    request.getUnit(), request.getPriceValue(), request.getRemark());
            log.info("[STEAM-ENERGY] 단가 갱신 - id: {}, {}/{} {}", existing.getPriceId(),
                    request.getYearNo(), request.getMonthNo(), request.getItemCode());
            return SteamPriceResponse.from(existing, true);
        }

        SteamPrice entity = SteamPrice.builder()
                .yearNo(request.getYearNo())
                .monthNo(request.getMonthNo())
                .facility(request.getFacility())
                .itemCode(request.getItemCode())
                .itemName(request.getItemName())
                .inputType(request.getInputType())
                .unit(request.getUnit())
                .priceValue(request.getPriceValue())
                .remark(request.getRemark())
                .build();
        SteamPrice saved = priceRepository.save(entity);
        log.info("[STEAM-ENERGY] 단가 신규 - id: {}, {}/{} {}", saved.getPriceId(),
                request.getYearNo(), request.getMonthNo(), request.getItemCode());
        return SteamPriceResponse.from(saved, false);
    }

    @Transactional
    public SteamPriceResponse update(Long id, SteamPriceSaveRequest request) {
        SteamPrice entity = findOrThrow(id);
        entity.update(request.getFacility(), request.getItemName(), request.getInputType(),
                request.getUnit(), request.getPriceValue(), request.getRemark());
        return SteamPriceResponse.from(entity, true);
    }

    @Transactional
    /**
     * 소프트 삭제 — 물리 삭제하지 않고 DELETED_YN 을 'Y' 로 바꾼다.
     * 삭제자(DELETED_BY)는 core 에 CurrentUserProvider 가 생기면 채운다.
     */
    public void delete(Long id) {
        SteamPrice target = findOrThrow(id);
        target.delete(null);
        log.info("[STEAM-ENERGY] 단가 삭제(소프트) - id: {}", id);
    }

    private SteamPrice findOrThrow(Long id) {
        return priceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("단가", id));
    }
}
