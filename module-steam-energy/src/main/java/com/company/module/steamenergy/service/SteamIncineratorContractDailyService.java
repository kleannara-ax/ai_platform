package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailyResponse;
import com.company.module.steamenergy.dto.SteamIncineratorContractDailySaveRequest;
import com.company.module.steamenergy.entity.SteamIncineratorContractDaily;
import com.company.module.steamenergy.repository.SteamIncineratorContractDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 소각로 도급내역 비즈니스 로직 — (USAGE_DATE) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamIncineratorContractDailyService {

    private final SteamIncineratorContractDailyRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamIncineratorContractDailyResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamIncineratorContractDailyResponse::from).toList();
    }

    public SteamIncineratorContractDailyResponse get(Long id) {
        return SteamIncineratorContractDailyResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamIncineratorContractDailyResponse save(SteamIncineratorContractDailySaveRequest request) {
        Upsert result = upsert(request);
        return SteamIncineratorContractDailyResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 값을 비운다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamIncineratorContractDailyBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamIncineratorContractDailySaveRequest r : request.getRows()) {
            if (!(r.getUsageDate().getYear() == year && (month == null || r.getUsageDate().getMonthValue() == month))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getUsageDate()));
            }
            keys.add("" + r.getUsageDate());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamIncineratorContractDaily e : scope(year, month)) {
                if (keys.contains("" + e.getUsageDate())) continue;
                e.clearValues(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 소각로 도급내역 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 소각로 도급내역 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamIncineratorContractDaily entity, boolean updated) {}

    private Upsert upsert(SteamIncineratorContractDailySaveRequest r) {
        SteamIncineratorContractDaily active = repository.findActive(r.getUsageDate()).orElse(null);
        if (active != null) {
            active.update(r.getU1Steam(), r.getU1Unit(), r.getU1Runtime(), r.getU1Fixed(), r.getU2Steam(), r.getU2Unit(), r.getU2Runtime(), r.getU2Fixed(), r.getRealSteam1(), r.getRealUnit1(), r.getRealSteam2(), r.getRealUnit2(), null);
            return new Upsert(active, true);
        }
        SteamIncineratorContractDaily deleted = repository.findAnyByKey(r.getUsageDate()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getU1Steam(), r.getU1Unit(), r.getU1Runtime(), r.getU1Fixed(), r.getU2Steam(), r.getU2Unit(), r.getU2Runtime(), r.getU2Fixed(), r.getRealSteam1(), r.getRealUnit1(), r.getRealSteam2(), r.getRealUnit2(), null);
            return new Upsert(deleted, true);
        }
        SteamIncineratorContractDaily created = repository.save(SteamIncineratorContractDaily.builder()
                .usageDate(r.getUsageDate())
                .u1Steam(r.getU1Steam())
                .u1Unit(r.getU1Unit())
                .u1Runtime(r.getU1Runtime())
                .u1Fixed(r.getU1Fixed())
                .u2Steam(r.getU2Steam())
                .u2Unit(r.getU2Unit())
                .u2Runtime(r.getU2Runtime())
                .u2Fixed(r.getU2Fixed())
                .realSteam1(r.getRealSteam1())
                .realUnit1(r.getRealUnit1())
                .realSteam2(r.getRealSteam2())
                .realUnit2(r.getRealUnit2())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamIncineratorContractDaily> scope(int year, Integer month) {
        requireScope(year, month);
        return repository.findScope(LocalDate.of(year, month == null ? 1 : month, 1), month == null ? LocalDate.of(year, 12, 31) : LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
    }

    private static void requireScope(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
    }

    private SteamIncineratorContractDaily findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("소각로 도급내역", id));
    }
}
