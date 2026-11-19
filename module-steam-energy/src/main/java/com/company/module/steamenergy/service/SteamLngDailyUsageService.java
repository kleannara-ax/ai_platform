package com.company.module.steamenergy.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamDailyPeriod;
import com.company.module.steamenergy.dto.SteamLngDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamLngDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamLngDailyUsageSaveRequest;
import com.company.module.steamenergy.entity.SteamLngDailyUsage;
import com.company.module.steamenergy.repository.SteamLngDailyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 일별 LNG 연료 사용량 비즈니스 로직 — 일자 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamLngDailyUsageService {

    private final SteamLngDailyUsageRepository repository;

    public List<SteamLngDailyUsageResponse> getList(int year, Integer month) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        return repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())
                .stream().map(SteamLngDailyUsageResponse::from).toList();
    }

    public SteamLngDailyUsageResponse get(Long id) {
        return SteamLngDailyUsageResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamLngDailyUsageResponse save(SteamLngDailyUsageSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] LNG 일일 사용량 {} - {}", result.updated() ? "갱신" : "신규", request.getUsageDate());
        return SteamLngDailyUsageResponse.from(result.entity(), result.updated());
    }

    @Transactional
    public SteamBulkSaveResponse saveMonth(SteamLngDailyUsageBulkRequest request) {
        SteamDailyPeriod period = SteamDailyPeriod.of(request.getYear(), request.getMonth());
        int inserted = 0, updated = 0, cleared = 0;
        Set<LocalDate> dates = new HashSet<>();
        for (SteamLngDailyUsageSaveRequest row : request.getRows()) {
            period.requireContains(row.getUsageDate());
            dates.add(row.getUsageDate());
            if (upsert(row).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamLngDailyUsage existing : repository.findByUsageDateBetweenOrderByUsageDateAsc(period.from(), period.to())) {
                if (dates.contains(existing.getUsageDate())) continue;
                existing.clearValues(null);
                cleared += 1;
            }
        }
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    /**
     * 단가 입력의 LNG 단가(lng_unit)를 그 달 모든 날의 보일러·버너 단가에 넣는다.
     * 행이 없는 날은 단가만 든 행을 만든다. 적용한 날 수를 돌려준다.
     */
    @Transactional
    public int applyUnitPrice(int year, int month, BigDecimal unitPrice) {
        SteamDailyPeriod period = SteamDailyPeriod.of(year, month);
        int applied = 0;
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            SteamLngDailyUsage row = findOrCreate(date);
            row.updateUnitPrices(unitPrice, unitPrice, null);
            applied += 1;
        }
        log.info("[STEAM-ENERGY] LNG 단가 일괄 적용 {}-{}: {} ({}일)", year, month, unitPrice, applied);
        return applied;
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] LNG 일일 사용량 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamLngDailyUsage entity, boolean updated) {}

    private Upsert upsert(SteamLngDailyUsageSaveRequest r) {
        SteamLngDailyUsage active = repository.findByUsageDate(r.getUsageDate()).orElse(null);
        if (active != null) {
            apply(active, r);
            return new Upsert(active, true);
        }
        SteamLngDailyUsage deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            apply(deleted, r);
            return new Upsert(deleted, true);
        }
        SteamLngDailyUsage created = repository.save(SteamLngDailyUsage.builder()
                .usageDate(r.getUsageDate())
                .boiler20t1(r.getBoiler20t1()).boiler20t2(r.getBoiler20t2())
                .boiler50t(r.getBoiler50t()).boiler60t(r.getBoiler60t())
                .boilerCombined(r.getBoilerCombined()).boilerNewIncinerator(r.getBoilerNewIncinerator())
                .boilerUnitPrice(r.getBoilerUnitPrice())
                .burnerPm3(r.getBurnerPm3()).burnerTm3(r.getBurnerTm3()).burnerTm4(r.getBurnerTm4()).burnerTm5(r.getBurnerTm5())
                .burnerUnitPrice(r.getBurnerUnitPrice())
                .mixPm2(r.getMixPm2()).mixPm3(r.getMixPm3()).mixTm3(r.getMixTm3()).mixTm4(r.getMixTm4()).mixTm5(r.getMixTm5())
                .build());
        return new Upsert(created, false);
    }

    /** 활성 행 → 삭제된 행 되살리기 → 새 행 순으로 찾는다 (단가 일괄 적용용). */
    private SteamLngDailyUsage findOrCreate(LocalDate date) {
        SteamLngDailyUsage active = repository.findByUsageDate(date).orElse(null);
        if (active != null) return active;
        SteamLngDailyUsage deleted = repository.findAnyByKey(date).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            return deleted;
        }
        return repository.save(SteamLngDailyUsage.builder().usageDate(date).build());
    }

    private static void apply(SteamLngDailyUsage e, SteamLngDailyUsageSaveRequest r) {
        e.update(r.getBoiler20t1(), r.getBoiler20t2(), r.getBoiler50t(), r.getBoiler60t(),
                r.getBoilerCombined(), r.getBoilerNewIncinerator(), r.getBoilerUnitPrice(),
                r.getBurnerPm3(), r.getBurnerTm3(), r.getBurnerTm4(), r.getBurnerTm5(), r.getBurnerUnitPrice(),
                r.getMixPm2(), r.getMixPm3(), r.getMixTm3(), r.getMixTm4(), r.getMixTm5(), null);
    }

    private SteamLngDailyUsage findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("LNG 일일 사용량", id));
    }
}
