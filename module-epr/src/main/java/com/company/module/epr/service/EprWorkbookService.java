package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.epr.dto.MasterExcelRow;
import com.company.module.epr.dto.ParsedSheet;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.SalesExcelRow;
import com.company.module.epr.dto.UploadSummary;
import com.company.module.epr.dto.WeightExcelRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 엑셀 올리기 · 내려받기 흐름을 한곳에 모은다 (컨트롤러는 이 서비스만 부른다).
 * 해석은 {@link EprExcelService}, 저장은 {@link EprMasterService} · {@link EprAnnualSalesService}, 계산은
 * {@link EprCalculationService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EprWorkbookService {

    private final EprExcelService excelService;
    private final EprMasterService masterService;
    private final EprCalculationService calculationService;
    private final EprAnnualSalesService annualSalesService;

    // ───────────────────────── 내려받기 ─────────────────────────

    public byte[] masterTemplate(boolean includeData) {
        return excelService.createMasterTemplate(includeData, includeData ? masterService.allProducts() : java.util.List.of());
    }

    public byte[] weightTemplate() {
        return excelService.createWeightTemplate(masterService.allProducts());
    }

    public byte[] salesTemplate() {
        return excelService.createSalesTemplate();
    }

    public byte[] annualReport(int reportYear) {
        return excelService.createReport(annualSalesService.result(reportYear));
    }

    // ───────────────────────── 올리기 ─────────────────────────

    @Transactional
    public UploadSummary uploadMaster(MultipartFile file, Long userId) {
        excelService.requireXlsx(file);
        ParsedSheet<MasterExcelRow> parsed;
        try (InputStream input = file.getInputStream()) {
            parsed = excelService.parseMaster(input);
        } catch (IOException exception) {
            throw unreadable();
        }
        return masterService.upsertMaster(parsed.rows(), parsed.issues(), userId);
    }

    @Transactional
    public UploadSummary uploadWeights(MultipartFile file, Long userId) {
        excelService.requireXlsx(file);
        ParsedSheet<WeightExcelRow> parsed;
        try (InputStream input = file.getInputStream()) {
            parsed = excelService.parseWeight(input);
        } catch (IOException exception) {
            throw unreadable();
        }
        return masterService.updateWeights(parsed.rows(), parsed.issues(), userId);
    }

    /** 판매수량 엑셀을 계산해 그 연도 실적으로 저장한다. 이미 저장된 연도는 replaceExisting=true 일 때만 교체한다. */
    @Transactional
    public SalesCalculationResult calculateSales(MultipartFile file, int reportYear, boolean replaceExisting, Long userId) {
        excelService.requireXlsx(file);
        annualSalesService.requireReplaceConfirmed(reportYear, replaceExisting);
        ParsedSheet<SalesExcelRow> parsed;
        try (InputStream input = file.getInputStream()) {
            parsed = excelService.parseSales(input);
        } catch (IOException exception) {
            throw unreadable();
        }
        SalesCalculationResult result = calculationService.calculate(reportYear, parsed.rows(), parsed.issues());
        annualSalesService.replaceYear(reportYear, result, replaceExisting, userId);
        return result;
    }

    private BusinessException unreadable() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "업로드한 파일을 읽지 못했습니다.");
    }
}
