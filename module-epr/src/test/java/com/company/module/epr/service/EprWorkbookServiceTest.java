package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.epr.dto.ParsedSheet;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.UploadIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EprWorkbookServiceTest {

    @Mock private EprExcelService excelService;
    @Mock private EprMasterService masterService;
    @Mock private EprCalculationService calculationService;
    @Mock private EprAnnualSalesService annualSalesService;
    @InjectMocks private EprWorkbookService workbookService;

    @Test
    void salesFileWithoutUsableRowsIsNotSavedSoAnExistingYearIsNotOverwrittenWithZeros() {
        List<UploadIssue> issues = List.of(new UploadIssue(2, "FRT-A1", "EA 값은 숫자여야 합니다."));
        when(excelService.parseSales(any())).thenReturn(new ParsedSheet<>(List.of(), issues));
        when(calculationService.calculate(anyInt(), anyList(), anyList())).thenReturn(new SalesCalculationResult(
                2025, LocalDateTime.now(), 0, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), issues));

        MockMultipartFile file = new MockMultipartFile("file", "sales.xlsx", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> workbookService.calculateSales(file, 2025, true, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("저장하지 않았습니다")
                .hasMessageContaining("2행");
        verify(annualSalesService, never()).replaceYear(anyInt(), any(), anyBoolean(), any());
    }
}
