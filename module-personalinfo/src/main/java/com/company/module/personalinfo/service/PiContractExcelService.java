package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.ContractSearchCondition;
import com.company.module.personalinfo.dto.ContractSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 외부위탁 현황 리스트 엑셀 다운로드 (보안팀 관리대장 양식과 같은 열 순서).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiContractExcelService {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String[] HEADERS = {
            "번호", "ISMS\n범위", "수탁업체 명", "계약명", "관련 서비스", "위탁업무 내용", "담당부서", "담당자",
            "원계약\n시작일", "원계약\n종료일", "현재 계약\n종료일", "원계약 기간 비고", "자동연장", "개인정보처리\n위탁계약서 작성",
            "재위탁 여부", "재위탁 업체 명", "계약상태", "만료까지(일)", "최근 점검일", "최근 점검결과",
            "정기\n파기확인", "정기 파기확인서\n최근 수취 기간", "정기 파기확인서\n미수취"
    };
    private static final int[] WIDTHS = {6, 7, 22, 24, 16, 40, 14, 10, 12, 12, 12, 24, 10, 10, 8, 24, 10, 10, 12, 12, 10, 16, 12};

    private final PiContractService contractService;
    private final PiAccessLogService accessLog;

    /** 계약 목록 엑셀 (사유 필수, 접근 기록 남김) */
    public PiFileStorage.DownloadFile export(ContractSearchCondition cond, String sort, String reason) {
        String r = PiAccessLogService.requireReason(reason);
        List<ContractSummaryResponse> rows = contractService.searchAll(cond, sort);
        PiFileStorage.DownloadFile file = build(rows);
        accessLog.record(com.company.module.personalinfo.entity.AccessActionType.DOWNLOAD_EXCEL, null, null, null,
                null, describe(cond), rows.size(), r);
        return file;
    }

    private static String describe(ContractSearchCondition c) {
        List<String> parts = new java.util.ArrayList<>();
        if (c.getBaseYear() != null) parts.add(c.getBaseYear() + "년");
        if (c.getYearStatus() != null && !c.getYearStatus().isBlank()) parts.add("구분=" + c.getYearStatus());
        if (c.getYearCheck() != null && !c.getYearCheck().isBlank()) parts.add("점검=" + c.getYearCheck());
        if (c.getFollowUp() != null && !c.getFollowUp().isBlank()) parts.add("확인필요=" + c.getFollowUp());
        if (c.getKeyword() != null && !c.getKeyword().isBlank()) parts.add("검색어=" + c.getKeyword());
        if (c.getStatus() != null && !c.getStatus().isBlank()) parts.add("상태=" + c.getStatus());
        if (c.getDeptName() != null && !c.getDeptName().isBlank()) parts.add("부서=" + c.getDeptName());
        return "계약 목록 엑셀" + (parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
    }

    private PiFileStorage.DownloadFile build(List<ContractSummaryResponse> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("외부위탁 현황 리스트_" + LocalDate.now().getYear());
            CellStyle title = titleStyle(wb);
            CellStyle header = headerStyle(wb);
            CellStyle body = bodyStyle(wb, HorizontalAlignment.LEFT);
            CellStyle center = bodyStyle(wb, HorizontalAlignment.CENTER);

            Row titleRow = sheet.createRow(0);
            Cell t = titleRow.createCell(0);
            t.setCellValue("개인정보 외부위탁 현황 리스트 (출력일 " + LocalDate.now().format(D) + ", " + rows.size() + "건)");
            t.setCellStyle(title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row h = sheet.createRow(2);
            h.setHeightInPoints(32);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            int r = 3;
            int no = 1;
            for (ContractSummaryResponse x : rows) {
                Row row = sheet.createRow(r++);
                int col = 0;
                set(row, col++, String.valueOf(no++), center);
                set(row, col++, x.getIsmsScopeYn(), center);
                set(row, col++, x.getVendorName(), body);
                set(row, col++, x.getContractName(), body);
                set(row, col++, x.getServiceName(), body);
                set(row, col++, x.getEntrustedTask(), body);
                set(row, col++, x.getDeptName(), body);
                set(row, col++, x.getManagerName(), center);
                set(row, col++, fmt(x.getContractStartDate()), center);
                set(row, col++, fmt(x.getContractEndDate()), center);
                set(row, col++, fmt(x.getCurrentEndDate()), center);   // 자동연장 반영 (연장 없으면 원계약 종료일과 같음)
                set(row, col++, x.getContractPeriodNote(), body);
                set(row, col++, "Y".equals(x.getAutoRenewYn()) && x.getAutoRenewPeriodMonths() != null
                        ? "Y (" + com.company.module.personalinfo.entity.PiContract.renewPeriodLabel(x.getAutoRenewPeriodMonths()) + ")"
                        : x.getAutoRenewYn(), center);
                set(row, col++, x.getContractDocYn(), center);
                set(row, col++, x.getSubcontractYn(), center);
                set(row, col++, x.getSubcontractorName(), body);
                set(row, col++, x.getStatusLabel(), center);
                set(row, col++, x.getDaysLeft() == null ? "" : String.valueOf(x.getDaysLeft()), center);
                set(row, col++, fmt(x.getLastInspectionDate()), center);
                set(row, col++, x.getLastInspectionResultLabel(), center);
                boolean pd = "Y".equals(x.getPeriodicDestructionYn());
                set(row, col++, pd ? "Y (" + x.getPeriodicDestructionCycleLabel() + ")" : "N", center);
                set(row, col++, pd ? (x.getPeriodicDestructionLastLabel() == null ? "-" : x.getPeriodicDestructionLastLabel()) : "", center);
                set(row, col, pd ? (x.getPeriodicDestructionOverdueCount() == null || x.getPeriodicDestructionOverdueCount() == 0
                        ? "없음" : x.getPeriodicDestructionOverdueCount() + "개 기간") : "", center);
            }
            sheet.createFreezePane(3, 3);
            wb.write(out);
            wb.dispose();
            String name = "개인정보_외부위탁_현황_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            return new PiFileStorage.DownloadFile(name, XLSX, out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "엑셀 파일 생성 중 오류가 발생했습니다.");
        }
    }

    private static void set(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : d.format(D);
    }

    private static CellStyle titleStyle(SXSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setFontName("맑은 고딕");
        s.setFont(f);
        return s;
    }

    private static CellStyle headerStyle(SXSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontName("맑은 고딕");
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        border(s);
        return s;
    }

    private static CellStyle bodyStyle(SXSSFWorkbook wb, HorizontalAlignment align) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontName("맑은 고딕");
        s.setFont(f);
        s.setAlignment(align);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        border(s);
        return s;
    }

    private static void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
