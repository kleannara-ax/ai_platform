package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamIncidentBulkRequest;
import com.company.module.steamenergy.dto.SteamIncidentResponse;
import com.company.module.steamenergy.dto.SteamIncidentSaveRequest;
import com.company.module.steamenergy.entity.SteamIncident;
import com.company.module.steamenergy.repository.SteamIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 사고·정비 이력 비즈니스 로직 — (YEAR_NO, RECORD_KEY) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamIncidentService {

    private final SteamIncidentRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamIncidentResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamIncidentResponse::from).toList();
    }

    public SteamIncidentResponse get(Long id) {
        return SteamIncidentResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamIncidentResponse save(SteamIncidentSaveRequest request) {
        Upsert result = upsert(request);
        return SteamIncidentResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 삭제한다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamIncidentBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamIncidentSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getRecordKey()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getRecordKey());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamIncident e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getRecordKey())) continue;
                e.delete(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 사고·정비 이력 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 사고·정비 이력 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamIncident entity, boolean updated) {}

    private Upsert upsert(SteamIncidentSaveRequest r) {
        SteamIncident active = repository.findActive(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (active != null) {
            active.update(r.getFacility(), r.getKind(), r.getOccurDate(), r.getReason(), r.getActionTaken(), r.getDowntimeHours(), r.getLossAmount(), r.getNote(), r.getSortOrder(), null);
            return new Upsert(active, true);
        }
        SteamIncident deleted = repository.findAnyByKey(r.getYearNo(), r.getRecordKey()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getFacility(), r.getKind(), r.getOccurDate(), r.getReason(), r.getActionTaken(), r.getDowntimeHours(), r.getLossAmount(), r.getNote(), r.getSortOrder(), null);
            return new Upsert(deleted, true);
        }
        SteamIncident created = repository.save(SteamIncident.builder()
                .yearNo(r.getYearNo())
                .recordKey(r.getRecordKey())
                .facility(r.getFacility())
                .kind(r.getKind())
                .occurDate(r.getOccurDate())
                .reason(r.getReason())
                .actionTaken(r.getActionTaken())
                .downtimeHours(r.getDowntimeHours())
                .lossAmount(r.getLossAmount())
                .note(r.getNote())
                .sortOrder(r.getSortOrder())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamIncident> scope(int year, Integer month) {
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

    private SteamIncident findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("사고·정비 이력", id));
    }
}
