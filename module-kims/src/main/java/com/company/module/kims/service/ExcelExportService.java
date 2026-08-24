package com.company.module.kims.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.kims.dto.response.InternetWorkResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.dto.response.ProgramInstallResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.dto.response.SettlementResponse;
import com.company.module.kims.dto.response.SettlementResponse.NameCount;
import com.company.module.kims.dto.response.SupplyIssueResponse;
import com.company.module.kims.entity.InternetWork;
import com.company.module.kims.entity.IpAddress;
import com.company.module.kims.entity.ProgramInstall;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.SupplyIssue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Apache POI 를 사용해 Excel(.xlsx) 파일을 byte 배열로 생성하는 헬퍼 서비스.
 * <p>DB 접근은 하지 않으며, 전달받은 엔티티 목록을 표로 변환만 한다.
 * (연관 엔티티 접근이 있으므로 호출 측은 트랜잭션 안에서 호출해야 한다.)
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 업무 요청 목록 Excel 생성.
     */
    public byte[] buildRequestListExcel(List<ServiceRequest> requests) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("업무요청목록");
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {
                    "요청번호", "요청일", "요청자", "부서", "위치",
                    "요청유형", "처리상태", "담당자", "긴급", "접수채널", "완료일"
            };
            writeHeader(sheet, headers, headerStyle);

            int rowIdx = 1;
            for (ServiceRequest r : requests) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                setCell(row, c++, r.getRequestNo());
                setCell(row, c++, r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_TIME) : "");
                setCell(row, c++, r.getRequesterName());
                setCell(row, c++, r.getDepartment());
                setCell(row, c++, r.getLocation());
                setCell(row, c++, r.getRequestType().getLabel());
                setCell(row, c++, r.getStatus().getLabel());
                setCell(row, c++, r.getAssignee());
                setCell(row, c++, r.isUrgent() ? "Y" : "N");
                setCell(row, c++, r.getReceivedChannel().getLabel());
                setCell(row, c, r.getCompletedAt() != null ? r.getCompletedAt().format(DATE_TIME) : "");
            }

            autoSize(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "업무요청 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 소모품 지급 내역 Excel 생성.
     */
    public byte[] buildSupplyIssueExcel(List<SupplyIssue> issues) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("소모품지급내역");
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {
                    "지급일", "요청번호", "품목명", "지급수량",
                    "지급대상자", "부서", "지급담당자"
            };
            writeHeader(sheet, headers, headerStyle);

            int rowIdx = 1;
            for (SupplyIssue s : issues) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                setCell(row, c++, s.getIssuedAt() != null ? s.getIssuedAt().format(DATE) : "");
                setCell(row, c++, s.getServiceRequest().getRequestNo());
                setCell(row, c++, s.getInventoryItem().getItemName());
                setCell(row, c++, String.valueOf(s.getQuantity()));
                setCell(row, c++, s.getReceiverName());
                setCell(row, c++, s.getDepartment());
                setCell(row, c, s.getIssuedBy());
            }

            autoSize(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "소모품지급 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * IP 목록 Excel 생성.
     */
    public byte[] buildIpListExcel(List<IpAddress> ips) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("IP목록");
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 청주공장 IP 대역 관리대장 양식
            String[] headers = {
                    "IP그룹", "IP", "부서", "장치구분", "사용자", "상태",
                    "품의여부", "품의번호", "비고", "비고작성일"
            };
            writeHeader(sheet, headers, headerStyle);

            int rowIdx = 1;
            for (IpAddress ip : ips) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                setCell(row, c++, ipGroupOf(ip.getIpAddress()));
                setCell(row, c++, ip.getIpAddress());
                setCell(row, c++, ip.getDepartment());
                setCell(row, c++, ip.getDevice());
                setCell(row, c++, ip.getUserName());
                setCell(row, c++, ip.getStatus().getLabel());
                setCell(row, c++, ip.isApproved() ? "Y" : "N");
                setCell(row, c++, ip.getApprovalNo());
                setCell(row, c++, ip.getRemark());
                setCell(row, c, ip.getNoteDate() != null ? ip.getNoteDate().format(DATE) : "");
            }

            autoSize(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "IP목록 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 프로그램 설치 내역 Excel 생성.
     */
    public byte[] buildProgramInstallExcel(List<ProgramInstall> installs) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("프로그램설치내역");
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {
                    "설치일", "프로그램명", "요청자", "부서", "대상PC", "설치담당자", "요청번호", "비고"
            };
            writeHeader(sheet, headers, headerStyle);

            int rowIdx = 1;
            for (ProgramInstall p : installs) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                setCell(row, c++, p.getInstalledAt() != null ? p.getInstalledAt().format(DATE) : "");
                setCell(row, c++, p.getProgramName());
                setCell(row, c++, p.getRequesterName());
                setCell(row, c++, p.getDepartment());
                setCell(row, c++, p.getTargetPc());
                setCell(row, c++, p.getInstalledBy());
                setCell(row, c++, p.getServiceRequest() != null ? p.getServiceRequest().getRequestNo() : "");
                setCell(row, c, p.getRemark());
            }

            autoSize(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "프로그램설치 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 인터넷 공사 내역 Excel 생성.
     */
    public byte[] buildInternetWorkExcel(List<InternetWork> works) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("인터넷공사내역");
            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {
                    "접수일", "공사유형", "상태", "요청자", "부서", "위치", "공사내용",
                    "외부업체", "업체명", "공사비발생", "공사비", "담당자", "완료일", "요청번호", "비고"
            };
            writeHeader(sheet, headers, headerStyle);

            int rowIdx = 1;
            for (InternetWork w : works) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                setCell(row, c++, w.getCreatedAt() != null ? w.getCreatedAt().format(DATE_TIME) : "");
                setCell(row, c++, w.getWorkType().getLabel());
                setCell(row, c++, w.getStatus().getLabel());
                setCell(row, c++, w.getRequesterName());
                setCell(row, c++, w.getDepartment());
                setCell(row, c++, w.getLocation());
                setCell(row, c++, w.getContent());
                setCell(row, c++, w.isExternalVendor() ? "Y" : "N");
                setCell(row, c++, w.getVendorName());
                setCell(row, c++, w.isHasCost() ? "Y" : "N");
                setCell(row, c++, w.getCost() != null ? String.valueOf(w.getCost()) : "");
                setCell(row, c++, w.getAssignee());
                setCell(row, c++, w.getCompletedAt() != null ? w.getCompletedAt().format(DATE) : "");
                setCell(row, c++, w.getServiceRequest() != null ? w.getServiceRequest().getRequestNo() : "");
                setCell(row, c, w.getRemark());
            }

            autoSize(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "인터넷공사 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 월말 결산 Excel 생성 (여러 시트).
     * <p>시트: 집계요약 / 소모품지급 / IP변경 / 프로그램설치 / 인터넷공사 / 미완료요청
     */
    public byte[] buildSettlementExcel(SettlementResponse s) {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle head = createHeaderStyle(wb);

            // 1) 집계 요약
            Sheet sum = wb.createSheet("집계요약");
            int[] r = {0};
            writeTitle(sum, r, "결산 기간: " + s.getFrom() + " ~ " + s.getTo());
            writeCountBlock(sum, r, head, "업무유형별 처리 건수", s.getRequestByType(), "유형", "건수");
            writeCountBlock(sum, r, head, "불편유형별 처리 건수", s.getRequestByIssueType(), "불편유형", "건수");
            writeCountBlock(sum, r, head, "담당자별 처리 건수", s.getRequestByAssignee(), "담당자", "건수");
            writeCountBlock(sum, r, head, "부서별 요청 건수", s.getRequestByDepartment(), "부서", "건수");
            writeCountBlock(sum, r, head, "소모품 품목별 지급수량", s.getSupplyByItem(), "품목", "수량");
            writeCountBlock(sum, r, head, "소모품 부서별 지급수량", s.getSupplyByDepartment(), "부서", "수량");
            writeCountBlock(sum, r, head, "소모품 요청자별 지급수량", s.getSupplyByRequester(), "요청자", "수량");
            writeCountBlock(sum, r, head, "소모품 담당자별 지급수량", s.getSupplyByIssuedBy(), "담당자", "수량");
            for (int i = 0; i < 2; i++) sum.autoSizeColumn(i);

            // 2) 소모품 지급
            Sheet sup = wb.createSheet("소모품지급");
            writeHeader(sup, new String[]{"지급일", "품목", "수량", "대상자", "부서", "담당자", "요청번호"}, head);
            int ri = 1;
            for (SupplyIssueResponse i : s.getSupplyIssues()) {
                Row row = sup.createRow(ri++); int c = 0;
                setCell(row, c++, str(i.getIssuedAt())); setCell(row, c++, i.getItemName());
                setCell(row, c++, String.valueOf(i.getQuantity())); setCell(row, c++, i.getReceiverName());
                setCell(row, c++, i.getDepartment()); setCell(row, c++, i.getIssuedBy()); setCell(row, c, i.getRequestNo());
            }
            autoSize(sup, 7);

            // 3) IP 변경
            Sheet ip = wb.createSheet("IP변경");
            writeHeader(ip, new String[]{"일시", "IP", "변경유형", "품의여부", "품의번호", "변경자"}, head);
            ri = 1;
            for (IpHistoryResponse h : s.getIpChanges()) {
                Row row = ip.createRow(ri++); int c = 0;
                setCell(row, c++, str(h.getCreatedAt())); setCell(row, c++, h.getIpAddress());
                setCell(row, c++, h.getChangeTypeLabel()); setCell(row, c++, h.isApproved() ? "Y" : "N");
                setCell(row, c++, h.getApprovalNo()); setCell(row, c, h.getChangedBy());
            }
            autoSize(ip, 6);

            // 4) 프로그램 설치
            Sheet pi = wb.createSheet("프로그램설치");
            writeHeader(pi, new String[]{"설치일", "프로그램", "대상PC", "요청자", "부서", "담당자"}, head);
            ri = 1;
            for (ProgramInstallResponse p : s.getProgramInstalls()) {
                Row row = pi.createRow(ri++); int c = 0;
                setCell(row, c++, str(p.getInstalledAt())); setCell(row, c++, p.getProgramName());
                setCell(row, c++, p.getTargetPc()); setCell(row, c++, p.getRequesterName());
                setCell(row, c++, p.getDepartment()); setCell(row, c, p.getInstalledBy());
            }
            autoSize(pi, 6);

            // 5) 인터넷 공사
            Sheet iw = wb.createSheet("인터넷공사");
            writeHeader(iw, new String[]{"공사유형", "상태", "위치", "외부업체", "공사비", "담당자", "완료일"}, head);
            ri = 1;
            for (InternetWorkResponse w : s.getInternetWorks()) {
                Row row = iw.createRow(ri++); int c = 0;
                setCell(row, c++, w.getWorkTypeLabel()); setCell(row, c++, w.getStatusLabel());
                setCell(row, c++, w.getLocation()); setCell(row, c++, w.isExternalVendor() ? "Y" : "N");
                setCell(row, c++, w.getCost() != null ? String.valueOf(w.getCost()) : "");
                setCell(row, c++, w.getAssignee()); setCell(row, c, str(w.getCompletedAt()));
            }
            autoSize(iw, 7);

            // 6) 미완료 요청
            Sheet inc = wb.createSheet("미완료요청");
            writeHeader(inc, new String[]{"요청번호", "요청일", "요청자", "부서", "유형", "상태", "담당자"}, head);
            ri = 1;
            for (ServiceRequestResponse q : s.getIncompleteRequests()) {
                Row row = inc.createRow(ri++); int c = 0;
                setCell(row, c++, q.getRequestNo()); setCell(row, c++, str(q.getCreatedAt()));
                setCell(row, c++, q.getRequesterName()); setCell(row, c++, q.getDepartment());
                setCell(row, c++, q.getRequestTypeLabel()); setCell(row, c++, q.getStatusLabel()); setCell(row, c, q.getAssignee());
            }
            autoSize(inc, 7);

            return toByteArray(wb);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "월말결산 Excel 생성 중 오류가 발생했습니다.");
        }
    }

    private void writeTitle(Sheet sheet, int[] rowIdx, String title) {
        Row row = sheet.createRow(rowIdx[0]++);
        row.createCell(0).setCellValue(title);
        rowIdx[0]++; // 한 줄 띄움
    }

    private void writeCountBlock(Sheet sheet, int[] rowIdx, CellStyle head, String title,
                                 List<NameCount> data, String nameHeader, String valueHeader) {
        Row t = sheet.createRow(rowIdx[0]++);
        Cell tc = t.createCell(0); tc.setCellValue("[" + title + "]"); tc.setCellStyle(head);
        Row h = sheet.createRow(rowIdx[0]++);
        Cell h0 = h.createCell(0); h0.setCellValue(nameHeader); h0.setCellStyle(head);
        Cell h1 = h.createCell(1); h1.setCellValue(valueHeader); h1.setCellStyle(head);
        if (data == null || data.isEmpty()) {
            sheet.createRow(rowIdx[0]++).createCell(0).setCellValue("(없음)");
        } else {
            for (NameCount nc : data) {
                Row row = sheet.createRow(rowIdx[0]++);
                row.createCell(0).setCellValue(nc.getName() != null ? nc.getName() : "(미지정)");
                row.createCell(1).setCellValue(nc.getValue());
            }
        }
        rowIdx[0]++; // 블록 간 한 줄 띄움
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** IP 그룹(마지막 옥텟 제외). 예: 192.1.0.44 → 192.1.0 */
    private String ipGroupOf(String ip) {
        if (ip == null) {
            return "";
        }
        int dot = ip.lastIndexOf('.');
        return (dot > 0) ? ip.substring(0, dot) : ip;
    }

    // ----------------------------------------------------------------
    // 내부 헬퍼
    // ----------------------------------------------------------------

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void setCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value != null ? value : "");
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toByteArray(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
