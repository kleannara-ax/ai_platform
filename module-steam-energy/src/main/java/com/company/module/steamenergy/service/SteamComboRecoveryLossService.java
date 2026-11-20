package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossBulkRequest;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossSaveRequest;
import com.company.module.steamenergy.entity.SteamComboRecoveryLoss;
import com.company.module.steamenergy.repository.SteamComboRecoveryLossRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 복합보일러 스팀 회수 감소 비즈니스 로직 — (YEAR_NO, MONTH_NO, LINE_NO) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamComboRecoveryLossService {

    private final SteamComboRecoveryLossRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamComboRecoveryLossResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamComboRecoveryLossResponse::from).toList();
    }

    public SteamComboRecoveryLossResponse get(Long id) {
        return SteamComboRecoveryLossResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamComboRecoveryLossResponse save(SteamComboRecoveryLossSaveRequest request) {
        Upsert result = upsert(request);
        return SteamComboRecoveryLossResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 삭제한다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamComboRecoveryLossBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamComboRecoveryLossSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getLineNo()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getLineNo());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamComboRecoveryLoss e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo() + "|" + e.getLineNo())) continue;
                e.delete(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 복합보일러 스팀 회수 감소 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 복합보일러 스팀 회수 감소 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamComboRecoveryLoss entity, boolean updated) {}

    private Upsert upsert(SteamComboRecoveryLossSaveRequest r) {
        SteamComboRecoveryLoss active = repository.findActive(r.getYearNo(), r.getMonthNo(), r.getLineNo()).orElse(null);
        if (active != null) {
            active.update(r.getLossDate(), r.getLossTime(), r.getLossNote(), r.getLossSteamRate(), r.getLossBaseRate(), r.getLossMinutes(), null);
            return new Upsert(active, true);
        }
        SteamComboRecoveryLoss deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo(), r.getLineNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getLossDate(), r.getLossTime(), r.getLossNote(), r.getLossSteamRate(), r.getLossBaseRate(), r.getLossMinutes(), null);
            return new Upsert(deleted, true);
        }
        SteamComboRecoveryLoss created = repository.save(SteamComboRecoveryLoss.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .lineNo(r.getLineNo())
                .lossDate(r.getLossDate())
                .lossTime(r.getLossTime())
                .lossNote(r.getLossNote())
                .lossSteamRate(r.getLossSteamRate())
                .lossBaseRate(r.getLossBaseRate())
                .lossMinutes(r.getLossMinutes())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamComboRecoveryLoss> scope(int year, Integer month) {
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

    private SteamComboRecoveryLoss findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("복합보일러 스팀 회수 감소", id));
    }
}
