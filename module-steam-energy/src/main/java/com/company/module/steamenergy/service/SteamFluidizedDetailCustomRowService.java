package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailCustomRowSaveRequest;
import com.company.module.steamenergy.entity.SteamFluidizedDetailCustomRow;
import com.company.module.steamenergy.repository.SteamFluidizedDetailCustomRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유동상 세부 운영내역 (추가 행) 비즈니스 로직 — (YEAR_NO, MONTH_NO, SECTION, ROW_KEY) 업서트.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamFluidizedDetailCustomRowService {

    private final SteamFluidizedDetailCustomRowRepository repository;

    /** 한 해(month 가 있으면 그 달) 범위의 행 */
    public List<SteamFluidizedDetailCustomRowResponse> getList(int year, Integer month) {
        return scope(year, month).stream().map(SteamFluidizedDetailCustomRowResponse::from).toList();
    }

    public SteamFluidizedDetailCustomRowResponse get(Long id) {
        return SteamFluidizedDetailCustomRowResponse.from(findOrThrow(id));
    }

    @Transactional
    public SteamFluidizedDetailCustomRowResponse save(SteamFluidizedDetailCustomRowSaveRequest request) {
        Upsert result = upsert(request);
        return SteamFluidizedDetailCustomRowResponse.from(result.entity(), result.updated());
    }

    /** 범위(한 해 또는 한 달) 일괄 저장. clearMissing 이면 범위 안에서 목록에 없는 행을 삭제한다. */
    @Transactional
    public SteamBulkSaveResponse saveBulk(SteamFluidizedDetailCustomRowBulkRequest request) {
        int year = request.getYear();
        Integer month = request.getMonth();
        requireScope(year, month);
        int inserted = 0, updated = 0, cleared = 0;
        Set<String> keys = new HashSet<>();
        for (SteamFluidizedDetailCustomRowSaveRequest r : request.getRows()) {
            if (!(r.getYearNo() == year && (month == null || month.equals(r.getMonthNo())))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "요청 범위(" + year + (month == null ? "" : "-" + month) + ") 밖의 행입니다: " + ("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getSection() + "|" + r.getRowKey()));
            }
            keys.add("" + r.getYearNo() + "|" + r.getMonthNo() + "|" + r.getSection() + "|" + r.getRowKey());
            if (upsert(r).updated()) updated += 1; else inserted += 1;
        }
        if (request.isClearMissing()) {
            for (SteamFluidizedDetailCustomRow e : scope(year, month)) {
                if (keys.contains("" + e.getYearNo() + "|" + e.getMonthNo() + "|" + e.getSection() + "|" + e.getRowKey())) continue;
                e.delete(null);
                cleared += 1;
            }
        }
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (추가 행) 일괄 저장 - {}{} 신규 {} 갱신 {} 정리 {}",
                year, month == null ? "" : "-" + month, inserted, updated, cleared);
        return SteamBulkSaveResponse.of(inserted, updated, cleared);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id).delete(null);
        log.info("[STEAM-ENERGY] 유동상 세부 운영내역 (추가 행) 삭제(소프트) - id: {}", id);
    }

    private record Upsert(SteamFluidizedDetailCustomRow entity, boolean updated) {}

    private Upsert upsert(SteamFluidizedDetailCustomRowSaveRequest r) {
        SteamFluidizedDetailCustomRow active = repository.findActive(r.getYearNo(), r.getMonthNo(), r.getSection(), r.getRowKey()).orElse(null);
        if (active != null) {
            active.update(r.getLabel(), r.getInsertAfter(), r.getDay01(), r.getDay02(), r.getDay03(), r.getDay04(), r.getDay05(), r.getDay06(), r.getDay07(), r.getDay08(), r.getDay09(), r.getDay10(), r.getDay11(), r.getDay12(), r.getDay13(), r.getDay14(), r.getDay15(), r.getDay16(), r.getDay17(), r.getDay18(), r.getDay19(), r.getDay20(), r.getDay21(), r.getDay22(), r.getDay23(), r.getDay24(), r.getDay25(), r.getDay26(), r.getDay27(), r.getDay28(), r.getDay29(), r.getDay30(), r.getDay31(), null);
            return new Upsert(active, true);
        }
        SteamFluidizedDetailCustomRow deleted = repository.findAnyByKey(r.getYearNo(), r.getMonthNo(), r.getSection(), r.getRowKey()).orElse(null);
        if (deleted != null) {
            deleted.restore(null);
            deleted.update(r.getLabel(), r.getInsertAfter(), r.getDay01(), r.getDay02(), r.getDay03(), r.getDay04(), r.getDay05(), r.getDay06(), r.getDay07(), r.getDay08(), r.getDay09(), r.getDay10(), r.getDay11(), r.getDay12(), r.getDay13(), r.getDay14(), r.getDay15(), r.getDay16(), r.getDay17(), r.getDay18(), r.getDay19(), r.getDay20(), r.getDay21(), r.getDay22(), r.getDay23(), r.getDay24(), r.getDay25(), r.getDay26(), r.getDay27(), r.getDay28(), r.getDay29(), r.getDay30(), r.getDay31(), null);
            return new Upsert(deleted, true);
        }
        SteamFluidizedDetailCustomRow created = repository.save(SteamFluidizedDetailCustomRow.builder()
                .yearNo(r.getYearNo())
                .monthNo(r.getMonthNo())
                .section(r.getSection())
                .rowKey(r.getRowKey())
                .label(r.getLabel())
                .insertAfter(r.getInsertAfter())
                .day01(r.getDay01())
                .day02(r.getDay02())
                .day03(r.getDay03())
                .day04(r.getDay04())
                .day05(r.getDay05())
                .day06(r.getDay06())
                .day07(r.getDay07())
                .day08(r.getDay08())
                .day09(r.getDay09())
                .day10(r.getDay10())
                .day11(r.getDay11())
                .day12(r.getDay12())
                .day13(r.getDay13())
                .day14(r.getDay14())
                .day15(r.getDay15())
                .day16(r.getDay16())
                .day17(r.getDay17())
                .day18(r.getDay18())
                .day19(r.getDay19())
                .day20(r.getDay20())
                .day21(r.getDay21())
                .day22(r.getDay22())
                .day23(r.getDay23())
                .day24(r.getDay24())
                .day25(r.getDay25())
                .day26(r.getDay26())
                .day27(r.getDay27())
                .day28(r.getDay28())
                .day29(r.getDay29())
                .day30(r.getDay30())
                .day31(r.getDay31())
                .build());
        return new Upsert(created, false);
    }

    private List<SteamFluidizedDetailCustomRow> scope(int year, Integer month) {
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

    private SteamFluidizedDetailCustomRow findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("유동상 세부 운영내역 (추가 행)", id));
    }
}
