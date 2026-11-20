package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.steamenergy.dto.SteamPriceApplyRequest;
import com.company.module.steamenergy.dto.SteamPriceApplyResponse;
import com.company.module.steamenergy.entity.SteamLngDailyUsage;
import com.company.module.steamenergy.entity.SteamPrice;
import com.company.module.steamenergy.repository.SteamFuelDailyUsageRepository;
import com.company.module.steamenergy.repository.SteamLngDailyUsageRepository;
import com.company.module.steamenergy.repository.SteamPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 단가 입력의 "그 달 전체에 적용" — 등록 단가를 일별 표에 넣는 규칙.
 */
class SteamPriceApplyToDailyTest {

    private SteamPriceRepository priceRepository;
    private SteamLngDailyUsageRepository lngRepository;
    private SteamFuelDailyUsageRepository fuelRepository;
    private com.company.module.steamenergy.repository.SteamIncineratorContractDailyRepository contractRepository;
    private com.company.module.steamenergy.repository.SteamSrfInvoiceSummaryRepository summaryRepository;
    private SteamPriceService service;

    @BeforeEach
    void setUp() {
        priceRepository = mock(SteamPriceRepository.class);
        lngRepository = mock(SteamLngDailyUsageRepository.class);
        fuelRepository = mock(SteamFuelDailyUsageRepository.class);
        when(lngRepository.save(any(SteamLngDailyUsage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fuelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        summaryRepository = mock(com.company.module.steamenergy.repository.SteamSrfInvoiceSummaryRepository.class);
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        contractRepository = mock(com.company.module.steamenergy.repository.SteamIncineratorContractDailyRepository.class);
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new SteamPriceService(priceRepository,
                new SteamLngDailyUsageService(lngRepository), new SteamFuelDailyUsageService(fuelRepository),
                new com.company.module.steamenergy.service.table.SteamIncineratorContractDailyAdapter(
                        new SteamIncineratorContractDailyService(contractRepository)),
                new com.company.module.steamenergy.service.table.SteamSrfInvoiceSummaryAdapter(
                        new SteamSrfInvoiceSummaryService(summaryRepository)));
    }

    private static SteamPriceApplyRequest request(int year, int month, String itemCode) {
        SteamPriceApplyRequest r = new SteamPriceApplyRequest();
        r.setYearNo(year); r.setMonthNo(month); r.setItemCode(itemCode);
        return r;
    }

    private static SteamPrice price(String itemCode, String value) {
        return SteamPrice.builder().yearNo(2026).monthNo(2).itemCode(itemCode).facility("LNG").itemName(itemCode)
                .inputType("DIRECT").priceValue(value == null ? null : new BigDecimal(value)).build();
    }

    @Test
    @DisplayName("lng_unit 은 그 달 모든 날(2월 = 28일)의 보일러·버너 단가에 들어간다 — 행이 없는 날은 새로 만든다")
    void lngUnitAppliesToBothLngPrices() {
        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "lng_unit")).thenReturn(Optional.of(price("lng_unit", "1026.88")));
        SteamLngDailyUsage existing = SteamLngDailyUsage.builder().usageDate(LocalDate.of(2026, 2, 10)).boiler20t1(BigDecimal.ONE).build();
        when(lngRepository.findByUsageDate(any())).thenAnswer(inv ->
                inv.getArgument(0).equals(LocalDate.of(2026, 2, 10)) ? Optional.of(existing) : Optional.empty());
        when(lngRepository.findAnyByKey(any())).thenReturn(Optional.empty());

        SteamPriceApplyResponse result = service.applyToDaily(request(2026, 2, "lng_unit"));

        assertTrue(result.isHasDailyTarget());
        assertEquals(28, result.getAppliedDays());
        assertEquals(new BigDecimal("1026.88"), existing.getBoilerUnitPrice());
        assertEquals(new BigDecimal("1026.88"), existing.getBurnerUnitPrice());
        assertEquals(BigDecimal.ONE, existing.getBoiler20t1(), "단가 외 값은 그대로");

        ArgumentCaptor<SteamLngDailyUsage> created = ArgumentCaptor.forClass(SteamLngDailyUsage.class);
        verify(lngRepository, times(27)).save(created.capture());
        List<LocalDate> dates = new ArrayList<>();
        created.getAllValues().forEach(row -> dates.add(row.getUsageDate()));
        assertFalse(dates.contains(LocalDate.of(2026, 2, 10)), "있던 날은 새로 만들지 않는다");
        assertTrue(dates.contains(LocalDate.of(2026, 2, 28)));
    }

    @Test
    @DisplayName("아직 전환되지 않은 화면의 단가(예: fluid_fixed_hr)는 적용 대상이 없다고 알려 주기만 한다")
    void unknownItemHasNoDailyTarget() {
        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "fluid_fixed_hr")).thenReturn(Optional.of(price("fluid_fixed_hr", "11700")));

        SteamPriceApplyResponse result = service.applyToDaily(request(2026, 2, "fluid_fixed_hr"));

        assertFalse(result.isHasDailyTarget());
        assertEquals(0, result.getAppliedDays());
        verifyNoInteractions(lngRepository, fuelRepository);
    }

    @Test
    @DisplayName("등록되지 않은 단가나 빈 값은 거부한다")
    void rejectsMissingOrEmptyPrice() {
        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "fluid_steam")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.applyToDaily(request(2026, 2, "fluid_steam")));

        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "waste2_steam")).thenReturn(Optional.of(price("waste2_steam", null)));
        assertThrows(BusinessException.class, () -> service.applyToDaily(request(2026, 2, "waste2_steam")));
    }

    @Test
    @DisplayName("waste1_fixed_hr 은 소각로 도급내역 그 달 모든 날의 1호기 운휴시간당 고정비에 들어간다")
    void wasteFixedAppliesToContract() {
        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "waste1_fixed_hr"))
                .thenReturn(java.util.Optional.of(price("waste1_fixed_hr", "155000")));
        var response = service.applyToDaily(request(2026, 2, "waste1_fixed_hr"));
        org.junit.jupiter.api.Assertions.assertTrue(response.isHasDailyTarget());
        org.junit.jupiter.api.Assertions.assertEquals(28, response.getAppliedDays());
        var captor = org.mockito.ArgumentCaptor.forClass(com.company.module.steamenergy.entity.SteamIncineratorContractDaily.class);
        org.mockito.Mockito.verify(contractRepository, org.mockito.Mockito.times(28)).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getAllValues().stream()
                .allMatch(row -> new BigDecimal("155000").compareTo(row.getU1Fixed()) == 0 && row.getU2Fixed() == null));
    }

    @Test
    @DisplayName("comb_bsc 는 SRF 인보이스 Summary 그 달 행의 BSC 단가에 들어간다")
    void combBscAppliesToSummary() {
        when(priceRepository.findByYearNoAndMonthNoAndItemCode(2026, 2, "comb_bsc"))
                .thenReturn(java.util.Optional.of(price("comb_bsc", "18100")));
        var response = service.applyToDaily(request(2026, 2, "comb_bsc"));
        org.junit.jupiter.api.Assertions.assertTrue(response.isHasDailyTarget());
        var captor = org.mockito.ArgumentCaptor.forClass(com.company.module.steamenergy.entity.SteamSrfInvoiceSummary.class);
        org.mockito.Mockito.verify(summaryRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("18100").compareTo(captor.getValue().getBscRate()));
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getValue().getMonthNo());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getAscRate());
    }
}
