package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteSaveRequest;
import com.company.module.steamenergy.entity.SteamEnergyMonthCompareNote;
import com.company.module.steamenergy.repository.SteamEnergyMonthCompareNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 회계비용 마감비교 비고 비즈니스 로직 — 연도 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamEnergyMonthCompareNoteService {

    private final SteamEnergyMonthCompareNoteRepository repository;

    public List<SteamEnergyMonthCompareNoteResponse> getList(int year) {
        requireYear(year);
        return repository.findByYearNoOrderByYearNoAsc(year).stream().map(SteamEnergyMonthCompareNoteResponse::from).toList();
    }

    public SteamEnergyMonthCompareNoteResponse get(Long id) {
        return SteamEnergyMonthCompareNoteResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamEnergyMonthCompareNoteResponse save(SteamEnergyMonthCompareNoteSaveRequest request) {
        Upsert result = upsert(request);
        log.info("[STEAM-ENERGY] 회계비용 마감비교 비고 {} - {}", result.updated() ? "갱신" : "신규", request.getYearNo());
        return SteamEnergyMonthCompareNoteResponse.from(result.entity(), result.updated());
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 회계비용 마감비교 비고 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamEnergyMonthCompareNote entity, boolean updated) {}

    private Upsert upsert(SteamEnergyMonthCompareNoteSaveRequest r) {
        SteamEnergyMonthCompareNote active = repository.findByYearNo(r.getYearNo()).orElse(null);
        if (active != null) {
            active.update(r.getNoteFuel(), r.getNoteDeduct(), r.getNoteActual(), r.getNoteSteamRatio(), r.getNotePlan(), r.getNoteProd(), r.getNoteProduct(), r.getNoteUnit(), r.getNoteProfit(), r.getNoteAux(), null);
            return new Upsert(active, true);
        }
        SteamEnergyMonthCompareNote deleted = repository.findAnyByKey(r.getYearNo()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getNoteFuel(), r.getNoteDeduct(), r.getNoteActual(), r.getNoteSteamRatio(), r.getNotePlan(), r.getNoteProd(), r.getNoteProduct(), r.getNoteUnit(), r.getNoteProfit(), r.getNoteAux(), null);
            return new Upsert(deleted, true);
        }
        SteamEnergyMonthCompareNote created = repository.save(SteamEnergyMonthCompareNote.builder()
                .yearNo(r.getYearNo())
                .noteFuel(r.getNoteFuel())
                .noteDeduct(r.getNoteDeduct())
                .noteActual(r.getNoteActual())
                .noteSteamRatio(r.getNoteSteamRatio())
                .notePlan(r.getNotePlan())
                .noteProd(r.getNoteProd())
                .noteProduct(r.getNoteProduct())
                .noteUnit(r.getNoteUnit())
                .noteProfit(r.getNoteProfit())
                .noteAux(r.getNoteAux())
                .build());
        return new Upsert(created, false);
    }

    private static void requireYear(Integer year) {
        if (year == null || year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
    }

    private SteamEnergyMonthCompareNote findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("회계비용 마감비교 비고", id));
    }
}
