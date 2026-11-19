package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageSaveRequest;
import com.company.module.steamenergy.entity.SteamUnitDailyUsage;
import com.company.module.steamenergy.repository.SteamUnitDailyUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 원단위 일지 서비스 — 업서트(신규/갱신/삭제 행 되살리기)와 한 달치 일괄 저장 규칙.
 * Spring 컨텍스트 없이 리포지토리를 Mockito 로 대신한다.
 */
class SteamUnitDailyUsageServiceTest {

    private SteamUnitDailyUsageRepository repository;
    private SteamUnitDailyUsageService service;

    @BeforeEach
    void setUp() {
        repository = mock(SteamUnitDailyUsageRepository.class);
        when(repository.save(any(SteamUnitDailyUsage.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SteamUnitDailyUsageService(repository);
    }

    private static SteamUnitDailyUsageSaveRequest request(LocalDate date, String machine, String mainSteam) {
        SteamUnitDailyUsageSaveRequest r = new SteamUnitDailyUsageSaveRequest();
        r.setUsageDate(date);
        r.setMachineCode(machine);
        r.setMainSteam(mainSteam == null ? null : new BigDecimal(mainSteam));
        r.setRuntimeHours(new BigDecimal("24"));
        return r;
    }

    @Test
    @DisplayName("활성 행이 없고 삭제된 행도 없으면 새로 만든다")
    void saveInsertsWhenAbsent() {
        LocalDate date = LocalDate.of(2026, 3, 5);
        when(repository.findByUsageDateAndMachineCode(date, "PM-2")).thenReturn(Optional.empty());
        when(repository.findAnyByKey(date, "PM-2")).thenReturn(Optional.empty());

        SteamUnitDailyUsageResponse saved = service.save(request(date, "PM-2", "123.4"));

        assertFalse(saved.getIsUpdate());
        assertEquals(new BigDecimal("123.4"), saved.getMainSteam());
        verify(repository).save(any(SteamUnitDailyUsage.class));
    }

    @Test
    @DisplayName("활성 행이 있으면 그 행의 값을 통째로 바꾼다 (보내지 않은 항목은 비워진다)")
    void saveUpdatesWhenActiveExists() {
        LocalDate date = LocalDate.of(2026, 3, 5);
        SteamUnitDailyUsage existing = SteamUnitDailyUsage.builder()
                .usageDate(date).machineCode("PM-2").mainSteam(new BigDecimal("1")).note("옛 비고").build();
        when(repository.findByUsageDateAndMachineCode(date, "PM-2")).thenReturn(Optional.of(existing));

        SteamUnitDailyUsageResponse saved = service.save(request(date, "PM-2", "99"));

        assertTrue(saved.getIsUpdate());
        assertEquals(new BigDecimal("99"), existing.getMainSteam());
        assertNull(existing.getNote(), "행 전체 갱신이라 보내지 않은 비고는 비워진다");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("소프트 삭제된 행만 있으면 되살려 쓴다 — UNIQUE 키 충돌이 나지 않는다")
    void saveRevivesSoftDeletedRow() {
        LocalDate date = LocalDate.of(2026, 3, 5);
        SteamUnitDailyUsage deleted = SteamUnitDailyUsage.builder().usageDate(date).machineCode("TM-3").build();
        deleted.delete(null);
        when(repository.findByUsageDateAndMachineCode(date, "TM-3")).thenReturn(Optional.empty());
        when(repository.findAnyByKey(date, "TM-3")).thenReturn(Optional.of(deleted));

        SteamUnitDailyUsageResponse saved = service.save(request(date, "TM-3", null));

        assertTrue(saved.getIsUpdate());
        assertEquals("N", deleted.getDeletedYn());
        assertNull(deleted.getDeletedAt());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("한 달치 일괄 저장 — 목록에 없는 기존 행은 값만 비우고(clearMissing), 건수를 센다")
    void saveMonthClearsMissingRows() {
        LocalDate d1 = LocalDate.of(2026, 3, 1), d2 = LocalDate.of(2026, 3, 2);
        SteamUnitDailyUsage keep = SteamUnitDailyUsage.builder().usageDate(d1).machineCode("PM-2").mainSteam(BigDecimal.ONE).build();
        SteamUnitDailyUsage stale = SteamUnitDailyUsage.builder().usageDate(d2).machineCode("PM-2").mainSteam(BigDecimal.TEN).build();
        when(repository.findByUsageDateAndMachineCode(d1, "PM-2")).thenReturn(Optional.of(keep));
        when(repository.findByUsageDateBetweenOrderByUsageDateAscMachineCodeAsc(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(keep, stale));

        SteamUnitDailyUsageBulkRequest bulk = new SteamUnitDailyUsageBulkRequest();
        bulk.setYear(2026); bulk.setMonth(3); bulk.setClearMissing(true);
        bulk.setRows(List.of(request(d1, "PM-2", "5")));

        SteamBulkSaveResponse result = service.saveMonth(bulk);

        assertEquals(0, result.getInserted());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getCleared());
        assertEquals(new BigDecimal("5"), keep.getMainSteam());
        assertNull(stale.getMainSteam(), "파일에 없는 날은 값이 비워진다");
    }

    @Test
    @DisplayName("한 달치 일괄 저장 — 요청 기간 밖 일자는 거부한다")
    void saveMonthRejectsDateOutsidePeriod() {
        SteamUnitDailyUsageBulkRequest bulk = new SteamUnitDailyUsageBulkRequest();
        bulk.setYear(2026); bulk.setMonth(3);
        bulk.setRows(List.of(request(LocalDate.of(2026, 4, 1), "PM-2", "1")));

        assertThrows(BusinessException.class, () -> service.saveMonth(bulk));
        Mockito.verifyNoInteractions(repository);
    }
}
