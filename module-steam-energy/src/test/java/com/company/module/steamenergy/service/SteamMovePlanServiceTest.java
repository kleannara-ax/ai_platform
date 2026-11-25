package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamMovePlanBoilerSaveRequest;
import com.company.module.steamenergy.dto.SteamMovePlanDayBulkRequest;
import com.company.module.steamenergy.dto.SteamMovePlanDayResponse;
import com.company.module.steamenergy.dto.SteamMovePlanDaySaveRequest;
import com.company.module.steamenergy.entity.SteamMovePlanDay;
import com.company.module.steamenergy.entity.SteamMovePlanMachine;
import com.company.module.steamenergy.repository.SteamMovePlanBoilerRepository;
import com.company.module.steamenergy.repository.SteamMovePlanDayRepository;
import com.company.module.steamenergy.repository.SteamMovePlanMachineRepository;
import com.company.module.steamenergy.service.excel.ProductionPlanWorkbookReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 운영 최적화 서비스 — 일자별 운휴 업서트, 보일러 코드 검사, 생산계획 엑셀 반영 규칙.
 * Spring 컨텍스트 없이 리포지토리를 Mockito 로 대신한다.
 */
class SteamMovePlanServiceTest {

    private static SteamMovePlanDaySaveRequest day(LocalDate date, String downPm2, String stopFluid) {
        SteamMovePlanDaySaveRequest r = new SteamMovePlanDaySaveRequest();
        r.setPlanDate(date);
        r.setDownPm2(downPm2 == null ? null : new BigDecimal(downPm2));
        r.setStopFluid(stopFluid == null ? null : new BigDecimal(stopFluid));
        return r;
    }

    @Test
    @DisplayName("일자별 운휴: 없으면 새로 만들고, 있으면 행 전체를 바꾼다 (보내지 않은 칸은 비워진다)")
    void daySaveInsertsThenUpdates() {
        SteamMovePlanDayRepository repository = mock(SteamMovePlanDayRepository.class);
        when(repository.save(any(SteamMovePlanDay.class))).thenAnswer(inv -> inv.getArgument(0));
        SteamMovePlanDayService service = new SteamMovePlanDayService(repository);
        LocalDate date = LocalDate.of(2026, 11, 13);

        when(repository.findActive(date)).thenReturn(Optional.empty());
        when(repository.findAnyByKey(date)).thenReturn(Optional.empty());
        SteamMovePlanDayResponse created = service.save(day(date, "24", null));
        assertFalse(created.getIsUpdate());
        assertEquals(new BigDecimal("24"), created.getDownPm2());

        SteamMovePlanDay existing = SteamMovePlanDay.builder().planDate(date).downPm2(new BigDecimal("24")).note("옛 비고").build();
        when(repository.findActive(date)).thenReturn(Optional.of(existing));
        SteamMovePlanDayResponse updated = service.save(day(date, null, "12"));
        assertTrue(updated.getIsUpdate());
        assertNull(updated.getDownPm2());
        assertEquals(new BigDecimal("12"), updated.getStopFluid());
        assertNull(existing.getNote());
    }

    @Test
    @DisplayName("일자별 운휴: 지운 행은 되살려 쓴다 (UNIQUE 키 충돌 방지)")
    void daySaveRestoresDeletedRow() {
        SteamMovePlanDayRepository repository = mock(SteamMovePlanDayRepository.class);
        SteamMovePlanDayService service = new SteamMovePlanDayService(repository);
        LocalDate date = LocalDate.of(2026, 11, 14);
        SteamMovePlanDay deleted = SteamMovePlanDay.builder().planDate(date).downPm3(new BigDecimal("8")).build();
        deleted.delete(null);
        when(repository.findActive(date)).thenReturn(Optional.empty());
        when(repository.findAnyByKey(date)).thenReturn(Optional.of(deleted));

        SteamMovePlanDayResponse saved = service.save(day(date, "24", null));

        assertTrue(saved.getIsUpdate());
        assertEquals("N", deleted.getDeletedYn());
        assertEquals(new BigDecimal("24"), deleted.getDownPm2());
        assertNull(deleted.getDownPm3());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("일자별 운휴 일괄 저장: 범위 밖 날짜는 거부하고, clearMissing 이면 목록에 없는 날을 비운다")
    void dayBulkChecksScopeAndClears() {
        SteamMovePlanDayRepository repository = mock(SteamMovePlanDayRepository.class);
        when(repository.save(any(SteamMovePlanDay.class))).thenAnswer(inv -> inv.getArgument(0));
        SteamMovePlanDayService service = new SteamMovePlanDayService(repository);
        when(repository.findActive(any())).thenReturn(Optional.empty());
        when(repository.findAnyByKey(any())).thenReturn(Optional.empty());
        SteamMovePlanDay stale = SteamMovePlanDay.builder().planDate(LocalDate.of(2026, 11, 20)).downPm2(new BigDecimal("24")).build();
        when(repository.findScope(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(List.of(stale));

        SteamMovePlanDayBulkRequest outside = new SteamMovePlanDayBulkRequest();
        outside.setYear(2026);
        outside.setMonth(11);
        outside.setRows(List.of(day(LocalDate.of(2026, 12, 1), "24", null)));
        assertThrows(BusinessException.class, () -> service.saveBulk(outside));

        SteamMovePlanDayBulkRequest request = new SteamMovePlanDayBulkRequest();
        request.setYear(2026);
        request.setMonth(11);
        request.setClearMissing(true);
        request.setRows(List.of(day(LocalDate.of(2026, 11, 13), "24", null)));
        SteamBulkSaveResponse result = service.saveBulk(request);
        assertEquals(1, result.getInserted());
        assertEquals(1, result.getCleared());
        assertNull(stale.getDownPm2());
    }

    @Test
    @DisplayName("보일러 기준값: 정해진 보일러 코드만 받는다")
    void boilerRejectsUnknownCode() {
        SteamMovePlanBoilerService service = new SteamMovePlanBoilerService(mock(SteamMovePlanBoilerRepository.class));
        SteamMovePlanBoilerSaveRequest request = new SteamMovePlanBoilerSaveRequest();
        request.setYearNo(2026);
        request.setMonthNo(11);
        request.setBoilerCode("LNG60");
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(request));
        assertTrue(e.getMessage().contains("LNG60"));
    }

    @Test
    @DisplayName("생산계획 엑셀 반영: 생산량·가동시간만 바꾸고 직접 넣은 원단위는 남긴다")
    void importKeepsUnitOverride() {
        SteamMovePlanMachineRepository repository = mock(SteamMovePlanMachineRepository.class);
        when(repository.save(any(SteamMovePlanMachine.class))).thenAnswer(inv -> inv.getArgument(0));
        SteamMovePlanExcelService service = new SteamMovePlanExcelService(repository);
        SteamMovePlanMachine existing = SteamMovePlanMachine.builder()
                .yearNo(2026).monthNo(11).machineCode("PM-2")
                .productionTon(new BigDecimal("4000")).unitOverride(new BigDecimal("2.21")).build();
        when(repository.findActive(2026, 11, "PM-2")).thenReturn(Optional.of(existing));
        when(repository.findActive(2026, 11, "PM-3")).thenReturn(Optional.empty());
        when(repository.findAnyByKey(2026, 11, "PM-3")).thenReturn(Optional.empty());

        int[] counts = service.apply(List.of(
                new ProductionPlanWorkbookReader.PlanRow(2026, 11, "PM-2", new BigDecimal("4349.0095"), new BigDecimal("333"), "예상"),
                new ProductionPlanWorkbookReader.PlanRow(2026, 11, "PM-3", new BigDecimal("26989.8073"), new BigDecimal("675"), "예상")));

        assertArrayEquals(new int[]{1, 1}, counts);
        assertEquals(new BigDecimal("4349.0095"), existing.getProductionTon());
        assertEquals(new BigDecimal("333"), existing.getRuntimeHours());
        assertEquals("예상", existing.getPlanKind());
        assertEquals(new BigDecimal("2.21"), existing.getUnitOverride());
        verify(repository, times(1)).save(any(SteamMovePlanMachine.class));
    }
}
