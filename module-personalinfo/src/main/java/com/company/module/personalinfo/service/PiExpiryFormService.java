package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.entity.PiContract;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 계약 만료 시 협력사(수탁사)에게서 받아야 하는 자료의 양식.
 *
 * <ul>
 *   <li>계약만료시 점검 체크리스트 (.xlsx) — 보안팀 점검 리스트 원본에서 '3.계약변경 만료시 체크' 시트를 연 상태로, 회사명·업무·계약 종료일을 채움</li>
 *   <li>정보보호 및 비밀유지 확약서 (.docx)</li>
 *   <li>개인정보 파기 확인서 (.docx)</li>
 * </ul>
 * 계약을 지정하면 수탁업체명·계약명·위탁업무·계약기간·재위탁 업체를 미리 채우고, 지정하지 않으면 빈 양식을 준다.
 * 위탁사(당사) 이름은 {@code personalinfo.company-name} 설정값을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiExpiryFormService {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String BLANK = "                ";

    /** 양식 종류 (URL 경로 값) */
    @Getter
    @RequiredArgsConstructor
    public enum FormType {
        CHECKLIST("checklist", "계약만료시 점검 체크리스트", "xlsx", XLSX, null),
        SECURITY_PLEDGE("security-pledge", "정보보호 및 비밀유지 확약서", "docx", DOCX, "personalinfo/security-pledge-template.docx"),
        DESTRUCTION_CONFIRM("destruction-confirm", "개인정보 파기 확인서", "docx", DOCX, "personalinfo/destruction-confirm-template.docx");

        private final String path;
        private final String label;
        private final String ext;
        private final String contentType;
        private final String resource;

        public static FormType of(String value) {
            String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (FormType t : values()) {
                if (t.path.equals(v) || t.name().equalsIgnoreCase(v)) {
                    return t;
                }
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "양식 종류가 올바르지 않습니다: " + value + " (checklist / security-pledge / destruction-confirm)");
        }
    }

    private final PiContractService contractService;
    private final PiChecklistTemplateService checklistTemplateService;

    @Value("${personalinfo.company-name:}")
    private String companyName;

    /** 빈 양식 */
    public PiFileStorage.DownloadFile blank(String type) {
        return build(FormType.of(type), null);
    }

    /** 계약 정보를 채운 양식 (부서 범위 확인 포함) */
    public PiFileStorage.DownloadFile forContract(Long contractId, String type) {
        return build(FormType.of(type), contractService.findActive(contractId));
    }

    /** 만료 알림 메일 첨부용 3종 (계약 정보 채움). 일부 양식을 만들지 못해도 나머지는 돌려준다. */
    public List<PiFileStorage.DownloadFile> mailAttachments(PiContract c) {
        List<PiFileStorage.DownloadFile> list = new ArrayList<>();
        for (FormType t : FormType.values()) {
            try {
                list.add(build(t, c));
            } catch (RuntimeException e) {
                log.warn("[personalinfo] 만료 알림 첨부 양식 생성 실패 contractId={} form={}", c.getContractId(), t, e);
            }
        }
        return list;
    }

    PiFileStorage.DownloadFile build(FormType type, PiContract c) {
        String vendor = c == null ? null : PiChecklistTemplateService.safeFileName(c.getVendorName());
        String name = type.getLabel() + (vendor == null ? "" : "_" + vendor
                + (type == FormType.CHECKLIST ? "_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) : ""))
                + "." + type.getExt();
        byte[] data = type == FormType.CHECKLIST ? checklist(c) : document(type, c);
        return new PiFileStorage.DownloadFile(name, type.getContentType(), data);
    }

    // ───────── 체크리스트 (.xlsx) ─────────

    private byte[] checklist(PiContract c) {
        byte[] template = checklistTemplateService.readTemplate();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (c != null) {
                Sheet s1 = PiChecklistTemplateService.findSheet(wb, "관리체계");
                if (s1 != null) {
                    PiChecklistTemplateService.fillRightOfLabel(s1, "회사명", c.getVendorName());
                    PiChecklistTemplateService.fillRightOfLabel(s1, "수행(수탁)업무", c.getEntrustedTask());
                    PiChecklistTemplateService.fillRightOfLabel(s1, "평가일", "");
                    PiChecklistTemplateService.fillRightOfLabel(s1, "평가자", "");
                }
            }
            Sheet s3 = PiChecklistTemplateService.findSheet(wb, "만료");
            if (s3 != null) {
                if (StringUtils.hasText(companyName)) {
                    replaceInSheet(s3, "ooo㈜", companyName.trim());
                    replaceInSheet(s3, "ooo", companyName.trim());
                }
                if (c != null) {
                    fillStatus(s3, "위탁계약종료확인", "수탁업체: " + nz(c.getVendorName())
                            + (StringUtils.hasText(c.getContractName()) ? " / " + c.getContractName() : "")
                            + "\n계약기간: " + period(c));
                    fillStatus(s3, "재위탁확인", "Y".equals(c.getSubcontractYn())
                            ? "재위탁 업체: " + nz(c.getSubcontractorName()) : "재위탁 없음");
                }
                // 파일을 열면 '3.계약변경 만료시 체크' 시트가 바로 보이도록
                int idx = wb.getSheetIndex(s3);
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    wb.getSheetAt(i).setSelected(i == idx);
                }
                wb.setActiveSheet(idx);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "만료 점검 체크리스트 양식을 만들지 못했습니다.");
        }
    }

    /** 평가 항목(label) 행의 '현황' 칸에 값 채우기 */
    private static void fillStatus(Sheet sheet, String label, String value) {
        String target = PiExcelSupport.compact(label);
        int statusCol = -1;
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 40); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int col = 0; col < Math.max(row.getLastCellNum(), 0); col++) {
                String text = PiExcelSupport.compact(PiExcelSupport.cellText(row.getCell(col)));
                if (statusCol < 0 && text.equals("현황")) {
                    statusCol = col;
                } else if (statusCol >= 0 && text.equals(target)) {
                    Cell cell = row.getCell(statusCol);
                    if (cell == null) {
                        cell = row.createCell(statusCol);
                    }
                    cell.setCellValue(value);
                    return;
                }
            }
        }
    }

    private static void replaceInSheet(Sheet sheet, String from, String to) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains(from)) {
                    cell.setCellValue(cell.getStringCellValue().replace(from, to));
                }
            }
        }
    }

    // ───────── 확약서 / 파기 확인서 (.docx) ─────────

    private byte[] document(FormType type, PiContract c) {
        Map<String, String> values = placeholders(c);
        try (InputStream in = new ClassPathResource(type.getResource()).getInputStream();
             XWPFDocument doc = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getParagraphs().forEach(p -> fill(p, values));
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cell.getParagraphs().forEach(p -> fill(p, values));
                    }
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, type.getLabel() + " 양식 파일을 찾을 수 없습니다.");
        }
    }

    private Map<String, String> placeholders(PiContract c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("{{위탁사명}}", StringUtils.hasText(companyName) ? companyName.trim() : (c == null ? BLANK : "위탁사"));
        m.put("{{수탁업체명}}", c == null ? BLANK : nz(c.getVendorName()));
        m.put("{{계약명}}", c == null ? "" : dash(c.getContractName()));
        m.put("{{위탁업무}}", c == null ? "" : dash(c.getEntrustedTask()));
        m.put("{{계약기간}}", c == null ? "" : period(c));
        m.put("{{계약종료일}}", c == null ? "" : fmt(c.getTerminatedDate() != null ? c.getTerminatedDate() : c.getContractEndDate()));
        m.put("{{재위탁업체}}", c == null ? "" : ("Y".equals(c.getSubcontractYn()) ? dash(c.getSubcontractorName()) : "없음"));
        return m;
    }

    /** 문단 안 자리표시 값 바꾸기: 글자 묶음(run) 단위로 먼저, 여러 묶음에 걸친 경우 문단 단위로 */
    private static void fill(XWPFParagraph p, Map<String, String> values) {
        String text = p.getText();
        if (text == null || !text.contains("{{")) {
            return;
        }
        for (XWPFRun r : p.getRuns()) {
            String t = r.getText(0);
            if (t != null && t.contains("{{")) {
                r.setText(apply(t, values), 0);
            }
        }
        if (p.getText().contains("{{") && !p.getRuns().isEmpty()) {
            String merged = apply(p.getText(), values);
            List<XWPFRun> runs = p.getRuns();
            runs.get(0).setText(merged, 0);
            for (int i = runs.size() - 1; i > 0; i--) {
                p.removeRun(i);
            }
        }
    }

    private static String apply(String s, Map<String, String> values) {
        String out = s;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static String period(PiContract c) {
        return fmt(c.getContractStartDate()) + " ~ " + fmt(c.getContractEndDate());
    }

    private static String fmt(LocalDate d) {
        return d == null ? "-" : d.format(D);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String dash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }
}
