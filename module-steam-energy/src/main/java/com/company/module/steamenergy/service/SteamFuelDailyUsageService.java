package com.company.module.steamenergy.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamDailyPeriod;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageSaveRequest;
import com.company.module.steamenergy.entity.SteamFuelDailyUsage;
import com.company.module.steamenergy.repository.SteamFuelDailyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 유동상·소각로·복합·외부 일일 스팀 구매량 비즈니스 로직 — 일자 업서트.
 *
 * <p>유동상 스팀량(fluidSteamTon)은 유동상 운전일지·세부 운영내역도 쓰므로 그 값만 바꾸는 메서드를 따로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFuelDailyUsageService {

    private final SteamFuelDailyUsageRepository repository;

    public List<SteamFuelDailyUsageResponse> getList(int year, Integer month) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        return repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())
                .stream().map(SteamFuelDailyUsageResponse::from).toList();
    }

    /** 다른 서비스(유동상 세부 운영내역 등)가 한 달치 유동상 스팀량을 읽을 때 — 일자 → 톤 */
    public Map<LocalDate, BigDecimal> fluidSteamTonByDate(int year, int month) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        return repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to()).stream()
                .filter(row -> row.getFluidSteamTon() != null)
                .collect(Collectors.toMap(SteamFuelDailyUsage::getUsageDate, SteamFuelDailyUsage::getFluidSteamTon,
                        (a, b) -> b, java.util.TreeMap::new));
    }

    public SteamFuelDailyUsageResponse get(Long id) {
        return SteamFuelDailyUsageResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFuelDailyUsageResponse save(SteamFuelDailyUsageSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 스팀 구매 일일 사용량 {} - {}", result.updated() ? "갱신" : "신규", request.getUsageDate());
        return SteamFuelDailyUsageResponse.from(result.entity(), result.updated());
    }

    @Transactional
    public SteamBulkSaveResponse saveMonth(SteamFuelDailyUsageBulkRequest request) {
        SteamDailyPeriod period = SteamDailyPeriod.of(request.getYear(), request.getMonth());
        int inserted = 0, updated = 0, cleared = 0;
        Set<LocalDate> dates = new HashSet<>();
        for (SteamFuelDailyUsageSaveRequest row : request.getRows()) {
            period.requireContains(row.getUsageDate());
            dates.add(row.getUsageDate());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFuelDailyUsage existing : repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())) {
                if (dates.contains(existing.getUsageDate())) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    /** 유동상 스팀량(송기량)만 저장 — 유동상 운전일지·엑셀 가져오기에서. null 이면 비운다. */
    @Transactional
    public SteamFuelDailyUsageResponse saveFluidSteamTon(LocalDate date, BigDecimal fluidSteamTon) {
        SteamFuelDailyUsage row = findOrCreate(date);
        row.updateFluidSteamTon(fluidSteamTon, null);
        return SteamFuelDailyUsageResponse.from(row, true);
    }

    /** 단가 입력의 유동상 단가(fluid_steam)를 그 달 모든 날에 넣는다. 적용한 날 수를 돌려준다. */
    @Transactional
    public int applyFluidUnitPrice(int year, int month, BigDecimal unitPrice) {
        return applyToMonth(year, month, row -> { row.updateFluidUnitPrice(unitPrice, null); return 1; }, "유동상 단가", unitPrice);
    }

    /** 단가 입력의 폐합성소각로 단가(waste2_steam)를 그 달 모든 날에 넣는다. */
    @Transactional
    public int applyIncineratorUnitPrice(int year, int month, BigDecimal unitPrice) {
        return applyToMonth(year, month, row -> { row.updateIncineratorUnitPrice(unitPrice, null); return 1; }, "폐합성소각로 단가", unitPrice);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 스팀 구매 일일 사용량 삭제(소프트) - id: {}", id);
    }

    private int applyToMonth(int year, int month, Function<SteamFuelDailyUsage, Integer> change, String what, BigDecimal value) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        int applied = 0;
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            applied += change.apply(findOrCreate(date));
        }
        log.info("[STEAM-ENERGY] {} 일괄 적용 {}-{}: {} ({}일)", what, year, month, value, applied);
        return applied;
    }

    private record Upsert(SteamFuelDailyUsage entity, boolean updated) {}

    private Upsert upsert(SteamFuelDailyUsageSaveRequest r) {
        SteamFuelDailyUsage active = repository.findByUsageDate(r.getUsageDate()).orElse(null);
        if (active != null) {
            apply(active, r);
            return new Upsert(active, true);
        }
        SteamFuelDailyUsage deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            apply(deleted, r);
            return new Upsert(deleted, true);
        }
        SteamFuelDailyUsage created = repository.save(SteamFuelDailyUsage.builder()
                .usageDate(r.getUsageDate())
                .fluidSteamTon(r.getFluidSteamTon()).fluidUnitPrice(r.getFluidUnitPrice())
                .incineratorSteamTon(r.getIncineratorSteamTon()).incineratorUnitPrice(r.getIncineratorUnitPrice())
                .combinedSteamTon(r.getCombinedSteamTon()).combinedUnitPrice(r.getCombinedUnitPrice()).combinedDeduction(r.getCombinedDeduction())
                .extBoilerSteamTon(r.getExtBoilerSteamTon()).extBoilerUnitPrice(r.getExtBoilerUnitPrice()).extBoilerDeduction(r.getExtBoilerDeduction())
                .build());
        return new Upsert(created, false);
    }

    private SteamFuelDailyUsage findOrCreate(LocalDate date) {
        SteamFuelDailyUsage active = repository.findByUsageDate(date).orElse(null);
        if (active != null) return active;
        SteamFuelDailyUsage deleted = repository.findAnyByKey(date).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            return deleted;
        }
        return repository.save(SteamFuelDailyUsage.builder().usageDate(date).build());
    }

    private static void apply(SteamFuelDailyUsage e, SteamFuelDailyUsageSaveRequest r) {
        e.update(r.getFluidSteamTon(), r.getFluidUnitPrice(),
                r.getIncineratorSteamTon(), r.getIncineratorUnitPrice(),
                r.getCombinedSteamTon(), r.getCombinedUnitPrice(), r.getCombinedDeduction(),
                r.getExtBoilerSteamTon(), r.getExtBoilerUnitPrice(), r.getExtBoilerDeduction(), null);
    }

    private SteamFuelDailyUsage findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("스팀 구매 일일 사용량", id));
    }
}
