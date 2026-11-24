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
        // 쓸 수 있는 행이 하나도 없으면 저장하지 않는다 — 잘못 고른 파일로 기존 연도 실적이 0 으로 덮이는 것을 막는다.
        if (result.acceptedRows() == 0) {
            String firstIssue = result.issues().isEmpty() ? ""
                    : " (예: " + result.issues().get(0).rowNumber() + "행 " + result.issues().get(0).message() + ")";
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "계산할 수 있는 판매 행이 없어 저장하지 않았습니다. 확인 필요 " + result.issues().size() + "행" + firstIssue);
        }
        annualSalesService.replaceYear(reportYear, result, replaceExisting, userId);
        return result;
    }

    private BusinessException unreadable() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "업로드한 파일을 읽지 못했습니다.");
    }
}
