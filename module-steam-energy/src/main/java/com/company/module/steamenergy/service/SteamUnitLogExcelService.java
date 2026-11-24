package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.*;
import com.company.module.steamenergy.legacy.service.OriginalWorkbookFiles;
import com.company.module.steamenergy.service.excel.SteamUnitLogWorkbookReader;
import com.company.module.steamenergy.service.excel.SteamUnitLogWorkbookWriter;
import com.company.module.steamenergy.service.excel.UnitLogSheetData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원단위 일지 누계 엑셀(월 시트 "N월일지" × 12) ↔ 네 표
 *
 * <p>가져오기: 시트마다 읽어 네 서비스의 한 달치 일괄 저장(clearMissing) 으로 넣는다 — 파일이 그 달의 기준이 된다.
 * <p>내보내기: 서식(원본 폴더의 "원단위 일지" 파일)에 연도 데이터를 채워 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SteamUnitLogExcelService {

    private static final Pattern MONTHLY_SHEET_PATTERN = Pattern.compile("^(\\d{1,2})월일지$");

    private final SteamUnitDailyUsageService unitDailyUsageService;
    private final SteamUnitDailyCommonService unitDailyCommonService;
    private final SteamLngDailyUsageService lngDailyUsageService;
    private final SteamFuelDailyUsageService fuelDailyUsageService;

    private final SteamUnitLogWorkbookReader reader = new SteamUnitLogWorkbookReader();
    private final SteamUnitLogWorkbookWriter writer = new SteamUnitLogWorkbookWriter();

    @Transactional
    public SteamUnitLogImportResponse importWorkbook(MultipartFile file, int year) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 엑셀 파일이 없습니다.");
        }
        List<String> sheets = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        int unitRows = 0, commonRows = 0, lngRows = 0, fuelRows = 0;

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index += 1) {
                Sheet sheet = workbook.getSheetAt(index);
                Matcher matcher = MONTHLY_SHEET_PATTERN.matcher(sheet.getSheetName());
                if (!matcher.matches()) continue;
                int monthNo = Integer.parseInt(matcher.group(1));
                if (monthNo < 1 || monthNo > 12) continue;

                UnitLogSheetData data = reader.read(sheet, year, monthNo);
                data.notFound().forEach(item -> notFound.add(data.sheetName() + ": " + item));

                SteamBulkSaveResponse u = unitDailyUsageService.saveMonth(bulkUnit(year, monthNo, data.unitRows()));
                SteamBulkSaveResponse c = unitDailyCommonService.saveMonth(bulkCommon(year, monthNo, data.commonRows()));
                SteamBulkSaveResponse l = lngDailyUsageService.saveMonth(bulkLng(year, monthNo, data.lngRows()));
                SteamBulkSaveResponse f = fuelDailyUsageService.saveMonth(bulkFuel(year, monthNo, data.fuelRows()));
                unitRows += u.getInserted() + u.getUpdated();
                commonRows += c.getInserted() + c.getUpdated();
                lngRows += l.getInserted() + l.getUpdated();
                fuelRows += f.getInserted() + f.getUpdated();
                sheets.add(sheet.getSheetName());
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽지 못했습니다: " + e.getMessage());
        }
        if (sheets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "'N월일지' 시트가 없습니다. 원단위 일지 누계 파일인지 확인하세요.");
        }
        log.info("[STEAM-ENERGY] 원단위 일지 엑셀 가져오기 {}년: 시트 {}개, 호기 {}건, 공통 {}건, LNG {}건, 구매 {}건, 못 찾음 {}건",
                year, sheets.size(), unitRows, commonRows, lngRows, fuelRows, notFound.size());
        return SteamUnitLogImportResponse.builder()
                .year(year).sheets(sheets)
                .unitRows(unitRows).commonRows(commonRows).lngRows(lngRows).fuelRows(fuelRows)
                .notFound(notFound)
                .build();
    }

    public byte[] exportWorkbook(int year) {
        Path templatePath;
        try {
            templatePath = OriginalWorkbookFiles.find(
                    name -> name.replace(" ", "").contains("원단위일지") && (name.endsWith(".xls") || name.endsWith(".xlsx")),
                    "원단위 일지");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "원단위 일지 서식 파일을 찾지 못했습니다: " + e.getMessage());
        }
        try (InputStream in = Files.newInputStream(templatePath);
             HSSFWorkbook workbook = new HSSFWorkbook(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setForceFormulaRecalculation(true);
            List<SteamUnitDailyUsageResponse> unitYear = unitDailyUsageService.getList(year, null, null);
            List<SteamUnitDailyCommonResponse> commonYear = unitDailyCommonService.getList(year, null);
            List<SteamLngDailyUsageResponse> lngYear = lngDailyUsageService.getList(year, null);
            List<SteamFuelDailyUsageResponse> fuelYear = fuelDailyUsageService.getList(year, null);
            for (int month = 1; month <= 12; month += 1) {
                Sheet sheet = workbook.getSheet(month + "월일지");
                if (sheet == null) continue;
                final int m = month;
                writer.fill(sheet, year, month,
                        unitYear.stream().filter(r -> r.getUsageDate().getMonthValue() == m).toList(),
                        commonYear.stream().filter(r -> r.getUsageDate().getMonthValue() == m).toList(),
                        lngYear.stream().filter(r -> r.getUsageDate().getMonthValue() == m).toList(),
                        fuelYear.stream().filter(r -> r.getUsageDate().getMonthValue() == m).toList());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "원단위 일지 엑셀을 만들지 못했습니다: " + e.getMessage());
        }
    }

    private static SteamUnitDailyUsageBulkRequest bulkUnit(int year, int month, List<SteamUnitDailyUsageSaveRequest> rows) {
        SteamUnitDailyUsageBulkRequest r = new SteamUnitDailyUsageBulkRequest();
        r.setYear(year); r.setMonth(month); r.setClearMissing(true); r.setRows(rows);
        return r;
    }

    private static SteamUnitDailyCommonBulkRequest bulkCommon(int year, int month, List<SteamUnitDailyCommonSaveRequest> rows) {
        SteamUnitDailyCommonBulkRequest r = new SteamUnitDailyCommonBulkRequest();
        r.setYear(year); r.setMonth(month); r.setClearMissing(true); r.setRows(rows);
        return r;
    }

    private static SteamLngDailyUsageBulkRequest bulkLng(int year, int month, List<SteamLngDailyUsageSaveRequest> rows) {
        SteamLngDailyUsageBulkRequest r = new SteamLngDailyUsageBulkRequest();
        r.setYear(year); r.setMonth(month); r.setClearMissing(true); r.setRows(rows);
        return r;
    }

    private static SteamFuelDailyUsageBulkRequest bulkFuel(int year, int month, List<SteamFuelDailyUsageSaveRequest> rows) {
        SteamFuelDailyUsageBulkRequest r = new SteamFuelDailyUsageBulkRequest();
        r.setYear(year); r.setMonth(month); r.setClearMissing(true); r.setRows(rows);
        return r;
    }
}
