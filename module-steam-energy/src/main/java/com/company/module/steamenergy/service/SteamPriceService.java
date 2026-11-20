package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamPriceApplyRequest;
import com.company.module.steamenergy.dto.SteamPriceApplyResponse;
import com.company.module.steamenergy.dto.SteamPriceResponse;
import com.company.module.steamenergy.dto.SteamPriceSaveRequest;
import com.company.module.steamenergy.entity.SteamPrice;
import com.company.module.steamenergy.repository.SteamPriceRepository;
import com.company.module.steamenergy.service.table.SteamIncineratorContractDailyAdapter;
import com.company.module.steamenergy.service.table.SteamTableFieldApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
    private final SteamLngDailyUsageService lngDailyUsageService;
    private final SteamFuelDailyUsageService fuelDailyUsageService;
    private final SteamIncineratorContractDailyAdapter incineratorContract;

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

    /**
     * 등록 단가를 그 달 일별 표에 일괄 적용한다 (단가 입력 페이지의 "단가 적용").
     *
     * <ul>
     *   <li>lng_unit → LNG 일일 사용량의 보일러·버너 단가</li>
     *   <li>fluid_steam → 유동상 단가, waste2_steam → 폐합성소각로 단가 (스팀 구매 일일 사용량)</li>
     *   <li>waste2_steam → 소각로 도급내역 2호기 운영단가·실 2호기 단가, waste1_fixed_hr / waste2_fixed_hr → 1·2호기 운휴시간당 고정비</li>
     * </ul>
     * 그 밖의 항목은 아직 전환되지 않은 화면의 단가라 hasDailyTarget=false 로 돌려준다.
     */
    @Transactional
    public SteamPriceApplyResponse applyToDaily(SteamPriceApplyRequest request) {
        SteamPrice price = priceRepository
                .findByYearNoAndMonthNoAndItemCode(request.getYearNo(), request.getMonthNo(), request.getItemCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        request.getYearNo() + "년 " + request.getMonthNo() + "월 " + request.getItemCode() + " 단가가 등록되지 않았습니다."));
        if (price.getPriceValue() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, request.getItemCode() + " 단가 값이 비어 있습니다.");
        }
        int year = request.getYearNo(), month = request.getMonthNo();
        java.math.BigDecimal value = price.getPriceValue();
        int applied;
        switch (request.getItemCode()) {
            case "lng_unit" -> applied = lngDailyUsageService.applyUnitPrice(year, month, value);
            case "fluid_steam" -> applied = fuelDailyUsageService.applyFluidUnitPrice(year, month, value);
            case "waste2_steam" -> {
                applied = fuelDailyUsageService.applyIncineratorUnitPrice(year, month, value);
                // 소각로 도급내역: 결산 2호기 운영단가 + 실 스팀량 기준 2호기 단가
                SteamTableFieldApplier.applyToEveryDay(incineratorContract, year, month, Map.of("u2Unit", value, "realUnit2", value));
            }
            case "waste1_fixed_hr" -> applied = SteamTableFieldApplier.applyToEveryDay(incineratorContract, year, month, Map.of("u1Fixed", value));
            case "waste2_fixed_hr" -> applied = SteamTableFieldApplier.applyToEveryDay(incineratorContract, year, month, Map.of("u2Fixed", value));
            default -> {
                return SteamPriceApplyResponse.of(request.getItemCode(), year, month, value, false, 0);
            }
        }
        log.info("[STEAM-ENERGY] 단가 일괄 적용 {} {}/{} → {}일", request.getItemCode(), year, month, applied);
        return SteamPriceApplyResponse.of(request.getItemCode(), year, month, value, true, applied);
    }

    private SteamPrice findOrThrow(Long id) {
        return priceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("단가", id));
    }
}
