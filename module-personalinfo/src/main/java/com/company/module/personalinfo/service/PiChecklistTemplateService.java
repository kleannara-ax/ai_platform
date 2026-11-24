package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.entity.FormTemplateType;
import com.company.module.personalinfo.entity.PiContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 보안팀 점검 체크리스트 양식 다운로드.
 *
 * <p>양식 원본은 양식 관리 화면에서 보안팀이 사용 지정한 버전이며, 없으면 모듈 jar 의
 * {@code personalinfo/checklist-template.xlsx}(기본 양식)를 쓴다.
 * 계약을 지정하면 '1. 관리체계' 시트의 회사명/수행(수탁) 업무를 미리 채워서 내려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiChecklistTemplateService {

    static final String TEMPLATE_PATH = "personalinfo/checklist-template.xlsx";
    static final String TEMPLATE_FILE_NAME = "개인정보 위수탁 협력사 점검 리스트.xlsx";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PiContractService contractService;
    private final PiFormTemplateService formTemplates;

    /** 빈 양식 (양식 관리에서 사용 중인 버전, 없으면 기본 양식) */
    public PiFileStorage.DownloadFile blankTemplate() {
        return new PiFileStorage.DownloadFile(TEMPLATE_FILE_NAME, XLSX, readTemplate());
    }

    /** 계약 정보를 미리 채운 양식 */
    public PiFileStorage.DownloadFile templateFor(Long contractId) {
        return templateFor(contractService.findActive(contractId));
    }

    /** 계약 정보를 미리 채운 양식 (계약 조회·권한 확인은 호출한 쪽에서) */
    PiFileStorage.DownloadFile templateFor(PiContract c) {
        Long contractId = c.getContractId();
        byte[] template = readTemplate();
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s1 = findSheet(wb, "관리체계");
            if (s1 != null) {
                fillRightOfLabel(s1, "회사명", c.getVendorName());
                fillRightOfLabel(s1, "수행(수탁)업무", c.getEntrustedTask());
                fillRightOfLabel(s1, "평가일", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
                fillRightOfLabel(s1, "평가자", "");
            }
            wb.write(out);
            String name = "개인정보 위수탁 점검 리스트_" + safeFileName(c.getVendorName()) + "_"
                    + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            return new PiFileStorage.DownloadFile(name, XLSX, out.toByteArray());
        } catch (IOException | RuntimeException e) {
            // 미리 채우기에 실패해도 업무가 막히지 않게 빈 양식을 내려준다
            log.warn("[personalinfo] 체크리스트 양식 미리 채우기 실패 - 빈 양식 제공. contractId={}", contractId, e);
            return new PiFileStorage.DownloadFile(TEMPLATE_FILE_NAME, XLSX, template);
        }
    }

    /** 사용 중 체크리스트 양식 원본 (양식 관리에서 등록·지정한 버전, 없으면 모듈 내장 기본 양식) */
    byte[] readTemplate() {
        return formTemplates.current(FormTemplateType.CHECKLIST).data();
    }

    static Sheet findSheet(XSSFWorkbook wb, String keyword) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (PiExcelSupport.compact(wb.getSheetName(i)).contains(keyword)) {
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    static void fillRightOfLabel(Sheet sheet, String label, String value) {
        String target = PiExcelSupport.compact(label);
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 12); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                if (PiExcelSupport.compact(PiExcelSupport.cellText(row.getCell(c))).equals(target)) {
                    Cell valueCell = row.getCell(c + 1);
                    if (valueCell == null) {
                        valueCell = row.createCell(c + 1);
                    }
                    valueCell.setCellValue(value == null ? "" : value);
                    return;
                }
            }
        }
    }

    static String safeFileName(String s) {
        if (s == null || s.isBlank()) {
            return "협력사";
        }
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
