package com.company.module.safety.support;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.entity.SafetyFormType;
import com.company.module.safety.entity.SafetyManualColumn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFAnchor;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFShapeGroup;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 안전 관련 엑셀 파서 — 두 가지 서식을 읽는다.
 *
 * <p><b>1) 안전작업 매뉴얼</b> (기존, .xlsx)
 * <ul>
 *   <li>시트 1개 = 매뉴얼 1개. 머리글 행에 "공정 순서" 가 있어야 인식한다.
 *       (머리글이 항상 1행에 있지는 않아 위쪽 몇 행을 훑어서 찾는다)</li>
 *   <li>매뉴얼 제목은 <b>시트명</b>을 쓴다. 시트 안의 "공정명" 칸은 파일마다 비어 있거나
 *       여러 시트가 같은 값을 갖는 경우가 많아 제목으로 쓰기에 적합하지 않다.</li>
 *   <li>열 구성은 파일마다 다르므로 머리글 행에서 읽는다. ("No." / "공정명" 칸은 열이 아니다)</li>
 *   <li>사진은 시트에 도형으로 삽입되어 있고, 앵커의 행 위치로 어느 단계인지 판별한다.
 *       사진과 화살표/타원 같은 도형이 <b>그룹으로 묶여 있으면</b> 그룹 안까지 훑어 사진만 꺼낸다.</li>
 * </ul>
 *
 * <p><b>2) 작업 위험성 평가서</b> (신규, .xls)
 * <ul>
 *   <li>워크북에 가이드/첨부/별첨 시트가 섞여 있고, 실제 평가 시트만 골라낸다.</li>
 *   <li>머리말(작업명/부서명/작업인원/목적/개인보호구/중요위험요소 등)을 라벨-값으로 뽑는다.</li>
 *   <li>표 머리글: 작업 순서 | 발생 가능한 위험 | √ | 위험성 평가 대책 | √
 *       — "√" 칸은 체크 열로 만든다.</li>
 *   <li>사진은 없다.</li>
 * </ul>
 *
 * <p>파일 형식(.xls/.xlsx)은 {@link WorkbookFactory} 가 알아서 가른다.
 * 이 클래스는 순수 파싱만 담당하고(디스크/DB 접근 없음), 결과는 {@link ParsedSheet} 로 반환한다.
 */
public class SafetyExcelParser {

    // ── 안전작업 매뉴얼 서식 ──
    /** 매뉴얼 시트로 인식하려면 머리글 행에 이 문구가 있어야 한다. */
    private static final String HEADER_MARK_STEP = "공정 순서";
    /** 개요/범례 시트(예: "초지" 시트)의 특징적 머리글 문구 — 이게 있으면 매뉴얼이 아니라 제외한다. */
    private static final String HEADER_MARK_OVERVIEW = "공정단계";
    /** 머리글 행을 찾을 최대 행 — 위에 제목/여백 행이 붙어 있는 파일이 있어 1행만 보지 않는다. */
    private static final int WORK_HEADER_SCAN_LIMIT = 10;
    /** 정해진 문구 없이 표로 인정하려면 이름이 붙은 칸이 최소 이만큼은 있어야 한다. */
    private static final int MIN_TABLE_COLUMNS = 3;

    /** 행 번호 칸 (열 정의에는 넣지 않고 stepNo 로 쓴다) */
    private static final String HEADER_NO = "No.";
    /** 시트 안의 공정명 칸 (제목은 시트명을 쓰므로 열 정의에도 넣지 않는다) */
    private static final String HEADER_TITLE = "공정명";
    /** 사진이 들어가는 칸 */
    private static final String HEADER_PHOTO = "사진";

    /** 화면(<img>)에서 그대로 보여줄 수 있는 그림 형식만 가져온다. (wmf/emf/wdp 등은 제외) */
    private static final Set<String> WEB_IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    /** 엑셀 그림 좌표 단위. 행 높이(포인트)를 그림 좌표(EMU)로 맞출 때 쓴다. */
    private static final int EMU_PER_POINT = 12700;

    // ── 작업 위험성 평가서 서식 ──
    /** 표 머리글 첫 칸 문구 */
    private static final String RISK_HEADER_STEP = "작업 순서";
    /** 표 머리글에 함께 있어야 하는 문구 (가이드 시트 오인 방지) */
    private static final String RISK_HEADER_HAZARD = "발생 가능한 위험";
    /** 체크 열 머리글 */
    private static final String RISK_CHECK_MARK = "√";
    /** 머리글을 찾을 최대 행 (이보다 아래에 있으면 평가 시트로 보지 않는다) */
    private static final int RISK_HEADER_SCAN_LIMIT = 20;
    /** 표의 끝을 알리는 문구 */
    private static final String RISK_TABLE_END = "작업자 서명";

    /** 머리말로 뽑을 라벨 (엑셀에 이 문구가 있으면 오른쪽 첫 값 칸을 값으로 본다) */
    private static final List<String> RISK_META_LABELS = List.of(
            "부서명", "작업인원", "작업장소", "작업주기", "작업일",
            "목적", "개인보호구", "중요위험요소");

    /**
     * 사진 원본까지 모두 읽는다. (확정 업로드용)
     *
     * <p>스트림이 아니라 <b>파일</b>로 받는 것이 중요하다. POI 는 스트림을 받으면 zip 전체를
     * 메모리에 올리지만, 파일을 받으면 필요한 부분만 읽는다. 사진이 많은 90MB 짜리 파일 기준으로
     * 힙 사용량이 약 250MB → 90MB 로 줄어든다.
     */
    public List<ParsedSheet> parse(File excelFile) {
        return parse(excelFile, true);
    }

    /**
     * 형식 확인(미리보기)용 — 사진의 <b>개수</b>만 세고 원본 바이트는 읽지 않는다.
     * <p>사진이 수백 장 들어 있는 파일이 많아, 미리보기까지 전부 메모리에 올리면 낭비가 크다.
     */
    public List<ParsedSheet> parseForPreview(File excelFile) {
        return parse(excelFile, false);
    }

    /** 사진 원본까지 모두 읽는다. (파일로 떨어뜨릴 수 없을 때만 — 메모리를 훨씬 많이 쓴다) */
    public List<ParsedSheet> parse(InputStream excelStream) {
        try (Workbook workbook = WorkbookFactory.create(excelStream)) {
            return parseSheets(workbook, true);
        } catch (IOException e) {
            throw readFailed(e);
        }
    }

    private List<ParsedSheet> parse(File excelFile, boolean includePhotoData) {
        try (Workbook workbook = WorkbookFactory.create(excelFile, null, true)) {
            return parseSheets(workbook, includePhotoData);
        } catch (IOException e) {
            throw readFailed(e);
        }
    }

    /**
     * 시트 하나만 읽는다. (미리보기에서 고른 시트의 표를 그대로 보여줄 때)
     *
     * @return 그런 이름의 시트가 없으면 null
     */
    public ParsedSheet parseOneSheet(File excelFile, String sheetName) {
        try (Workbook workbook = WorkbookFactory.create(excelFile, null, true)) {
            Sheet sheet = findSheet(workbook, sheetName);
            return (sheet != null) ? parseSheet(sheet, false) : null;
        } catch (IOException e) {
            throw readFailed(e);
        }
    }

    /**
     * 시트에서 사진 <b>한 장</b>의 원본만 읽는다.
     *
     * <p>미리보기에서 화면에 보이는 사진만 가져오려는 것이라, 시트의 사진을 전부 메모리에
     * 올리지 않는다. {@code index} 는 {@link ParsedPhoto#index()} 로 알려 준 값이다.
     *
     * @return 해당 번호의 사진이 없으면 null
     */
    public ParsedPhoto findPhoto(File excelFile, String sheetName, int index) {
        try (Workbook workbook = WorkbookFactory.create(excelFile, null, true)) {
            Sheet sheet = findSheet(workbook, sheetName);
            if (!(sheet instanceof XSSFSheet xssfSheet)) return null;
            XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
            if (drawing == null) return null;

            // 번호는 도형을 훑는 순서대로 매겨지므로, 같은 순서로 따라가다 그 한 장만 읽는다.
            Map<Integer, List<ParsedPhoto>> found = new LinkedHashMap<>();
            int[] seq = {0};
            long[] rowTops = rowTopOffsets(sheet, -1);
            for (XSSFShape shape : drawing.getShapes()) {
                collectPhotos(sheet, shape, null, found, seq, false, List.of(), -1, rowTops, 0, -1);
                if (seq[0] > index) break;
            }
            for (List<ParsedPhoto> photos : found.values()) {
                for (ParsedPhoto photo : photos) {
                    if (photo.index() == index) {
                        return readPhotoData(drawing, photo, index);
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw readFailed(e);
        }
    }

    /** 번호가 같은 그림을 다시 찾아 바이트만 채워 돌려준다. */
    private ParsedPhoto readPhotoData(XSSFDrawing drawing, ParsedPhoto photo, int index) {
        int[] seq = {0};
        for (XSSFShape shape : drawing.getShapes()) {
            XSSFPicture picture = pictureAt(shape, seq, index);
            if (picture != null) {
                XSSFPictureData data = picture.getPictureData();
                return new ParsedPhoto(photo.fileName(), photo.contentType(),
                        (data != null) ? uprightBytes(picture, data, webImageExtension(data)) : null,
                        photo.columnIndex(), index);
            }
        }
        return null;
    }

    /** 도형(그룹 포함)을 훑으며 번호가 {@code index} 인 그림을 찾는다. */
    private XSSFPicture pictureAt(XSSFShape shape, int[] seq, int index) {
        if (shape instanceof XSSFShapeGroup group) {
            for (XSSFShape child : group) {
                XSSFPicture hit = pictureAt(child, seq, index);
                if (hit != null) return hit;
            }
            return null;
        }
        if (!(shape instanceof XSSFPicture picture)) return null;
        XSSFPictureData data = picture.getPictureData();
        if (data == null) return null;
        if (webImageExtension(data) == null) return null;
        return (seq[0]++ == index) ? picture : null;
    }

    /**
     * 화면에 그대로 띄울 수 있는 그림이면 그 확장자를, 아니면 null 을 준다.
     *
     * <p>엑셀이 확장자를 엉뚱하게 붙여 둔 그림이 있다(예: {@code image4.tmp} 인데 알맹이는 PNG).
     * 확장자만 믿으면 멀쩡한 사진이 통째로 빠지므로, 모르는 확장자는 앞부분 몇 바이트로 확인한다.
     * 정말 못 쓰는 형식(EMF/WMF 같은 벡터 그림)만 걸러진다.
     */
    private String webImageExtension(XSSFPictureData data) {
        String ext = data.suggestFileExtension();
        if (ext != null && WEB_IMAGE_EXTENSIONS.contains(ext.toLowerCase())) {
            ext = ext.toLowerCase();
            return "jpg".equals(ext) ? "jpeg" : ext;   // MIME 타입까지 이 값으로 만든다
        }
        return sniffImageExtension(data.getData());
    }

    /** 파일 앞부분(매직 넘버)으로 그림 형식을 알아낸다. 모르면 null. */
    private String sniffImageExtension(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "png";
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) return "jpeg";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "gif";
        if (bytes[0] == 'B' && bytes[1] == 'M') return "bmp";
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "webp";
        return null;
    }

    private Sheet findSheet(Workbook workbook, String sheetName) {
        String wanted = (sheetName != null) ? sheetName.trim() : "";
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName().trim().equals(wanted)) return sheet;
        }
        return null;
    }

    private List<ParsedSheet> parseSheets(Workbook workbook, boolean includePhotoData) {
        List<ParsedSheet> result = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            result.add(parseSheet(workbook.getSheetAt(i), includePhotoData));
        }
        return result;
    }

    /** 표준상 RuntimeException/IllegalArgumentException 을 직접 던지지 않고 core 예외를 쓴다. */
    private BusinessException readFailed(IOException e) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
    }

    /** 서식을 판별해 해당 파서로 넘긴다. 어느 쪽도 아니면 사유를 담아 제외한다. */
    private ParsedSheet parseSheet(Sheet sheet, boolean includePhotoData) {
        String sheetName = sheet.getSheetName();
        if (sheet.getLastRowNum() < 0 || sheet.getPhysicalNumberOfRows() == 0) {
            return ParsedSheet.rejected(sheetName, "빈 시트입니다.");
        }

        int riskHeaderRow = findRiskHeaderRow(sheet);
        if (riskHeaderRow >= 0) {
            return parseRiskAssessment(sheet, riskHeaderRow);
        }
        return parseWorkMethod(sheet, includePhotoData);
    }

    // ================================================================
    // 서식 1 — 안전작업 매뉴얼
    // ================================================================
    private ParsedSheet parseWorkMethod(Sheet sheet, boolean includePhotoData) {
        String sheetName = sheet.getSheetName();

        int headerRowIdx = -1;
        int overviewRowIdx = -1;
        int scanLimit = Math.min(sheet.getLastRowNum(), WORK_HEADER_SCAN_LIMIT);
        for (int rowIdx = 0; rowIdx <= scanLimit; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            String joined = squeeze(joinRow(row));
            boolean hasStepMark = joined.contains(squeeze(HEADER_MARK_STEP));
            boolean hasOverviewMark = joined.contains(squeeze(HEADER_MARK_OVERVIEW));
            if (hasStepMark && !hasOverviewMark) {
                headerRowIdx = rowIdx;
                break;
            }
            if (hasOverviewMark && overviewRowIdx < 0) {
                overviewRowIdx = rowIdx;
            }
        }

        // "공정 순서" 라는 문구가 확실한 신호이긴 하지만, 표 자체는 열 구성이 자유롭다.
        // 문구가 없어도 표처럼 생겼으면 받아 준다 — 다만 확신이 없으므로 기본 선택은 하지 않고,
        // 화면에서 사람이 미리보기로 확인한 뒤 고르게 한다.
        boolean confident = headerRowIdx >= 0;
        if (!confident) {
            if (overviewRowIdx >= 0) {
                return ParsedSheet.rejected(sheetName, "개요/범례 시트로 추정되어 매뉴얼 대상에서 제외됩니다.");
            }
            headerRowIdx = findTableHeaderRow(sheet, scanLimit);
            if (headerRowIdx < 0) {
                return ParsedSheet.rejected(sheetName,
                        "표로 읽을 만한 머리글을 찾지 못했습니다. "
                        + "안전작업 매뉴얼은 머리글 행이, 작업 위험성 평가서는 '작업 순서'와 '발생 가능한 위험' 이 필요합니다.");
            }
        }
        Row headerRow = sheet.getRow(headerRowIdx);

        // 열은 머리글 행에서 읽는다 — 파일마다 열 구성이 다르다.
        // (예: 어떤 파일은 "안전 보호구" 가 있고 어떤 파일은 없다. 고정 인덱스로 읽으면 값이 밀린다.)
        List<ParsedColumn> columns = new ArrayList<>();
        List<Integer> sourceColumnIndexes = new ArrayList<>();
        int noColumnIndex = -1;
        int photoColumnPos = -1;   // 사진 전용 열이 columns 목록의 몇 번째인지
        for (int colIdx = 0; colIdx < headerRow.getLastCellNum(); colIdx++) {
            String label = flatten(cellText(headerRow.getCell(colIdx)));
            if (label.isBlank()) continue;
            if (isNoLabel(label)) {                       // 번호 칸은 열이 아니라 stepNo 로 쓴다
                if (noColumnIndex < 0) noColumnIndex = colIdx;
                continue;
            }
            if (squeeze(label).equals(squeeze(HEADER_TITLE))) continue;   // 공정명 칸은 열이 아니다

            if (label.contains(HEADER_PHOTO)) {
                if (photoColumnPos < 0) photoColumnPos = columns.size();
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_PHOTO, 150));
            } else {
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_TEXT, 260));
            }
            sourceColumnIndexes.add(colIdx);
        }
        if (columns.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "표의 열 머리글을 읽을 수 없습니다.");
        }

        Map<Integer, List<ParsedPhoto>> photosByRow = extractPhotosByRow(
                sheet, includePhotoData, sourceColumnIndexes, photoColumnPos,
                headerRowIdx + 1, sheet.getLastRowNum());
        List<ParsedRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        int order = 1;
        for (int rowIdx = headerRowIdx + 1; rowIdx <= lastRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            List<ParsedPhoto> photos = photosByRow.getOrDefault(rowIdx, List.of());
            if (row == null) {
                continue;   // 값이 하나도 없는 행 — 사진만 있는 행은 아래에서 걸러진다
            }

            Integer stepNo = (noColumnIndex >= 0) ? cellInt(row.getCell(noColumnIndex)) : null;
            if (stepNo == null && noColumnIndex >= 0) {
                stepNo = mergedNoAbove(sheet, rowIdx, noColumnIndex);
            }

            List<ParsedCell> cells = new ArrayList<>();
            boolean hasContent = !photos.isEmpty();
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).type().equals(SafetyManualColumn.TYPE_PHOTO)) {
                    cells.add(ParsedCell.empty());   // 사진은 값이 아니라 photos 로 들어간다
                    continue;
                }
                String text = tidyText(cellText(row.getCell(sourceColumnIndexes.get(i))));
                if (!text.isBlank()) hasContent = true;
                cells.add(ParsedCell.text(text));
            }

            // 번호만 찍혀 있고 내용이 하나도 없는 행(빈 양식)은 가져오지 않는다
            if (!hasContent) continue;

            rows.add(new ParsedRow(stepNo != null ? stepNo : order, order, cells, photos));
            order++;
        }

        if (rows.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "내용이 채워진 행이 없습니다. (빈 양식 시트로 보입니다)");
        }
        // 제목은 시트명을 쓴다 — 시트 안의 "공정명" 은 비어 있거나(예: 가공4·5호기 파일)
        // 서로 다른 시트가 같은 값을 갖는 경우(예: "손잡이 테이프 교체 작업")가 많아
        // 제목 중복으로 뒤 시트가 통째로 건너뛰어졌다.
        ParsedSheet parsed = ParsedSheet.accepted(sheetName, SafetyFormType.WORK_METHOD,
                flatten(sheetName), List.of(), columns, rows);
        if (!confident) {
            parsed.markNeedsReview("머리글을 자동으로 판단했습니다. 미리보기로 내용을 확인한 뒤 선택하세요.");
        }
        return parsed;
    }

    /**
     * 정해진 문구가 없어도 "표의 머리글"로 보이는 행을 찾는다.
     *
     * <p>이 모듈의 표는 열 구성이 자유롭기 때문에(열 정의가 데이터다) 엑셀도 같은 수준으로
     * 받아 줘야 한다. 위쪽 몇 행 중 <b>이름이 붙은 칸이 가장 많은 행</b>을 머리글로 보고,
     * 그 아래에 내용이 있는 행이 실제로 있어야 인정한다.
     *
     * @return 머리글 행 번호. 표로 볼 수 없으면 -1
     */
    private int findTableHeaderRow(Sheet sheet, int scanLimit) {
        int bestRow = -1;
        int bestLabels = 0;
        for (int rowIdx = 0; rowIdx <= scanLimit; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            int labels = 0;
            for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++) {
                String label = flatten(cellText(row.getCell(colIdx)));
                // 머리글은 짧은 이름이다. 문장이 들어 있으면 데이터 행으로 본다.
                if (!label.isBlank() && label.length() <= 40) labels++;
            }
            if (labels > bestLabels) {
                bestLabels = labels;
                bestRow = rowIdx;
            }
        }
        if (bestRow < 0 || bestLabels < MIN_TABLE_COLUMNS) return -1;
        return hasContentBelow(sheet, bestRow) ? bestRow : -1;
    }

    /** 머리글 아래에 내용이 채워진 행이 하나라도 있는지 */
    private boolean hasContentBelow(Sheet sheet, int headerRowIdx) {
        for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null && !joinRow(row).isBlank()) return true;
        }
        return false;
    }

    /** "No." / "NO" / "번호" 처럼 행 번호를 뜻하는 머리글인지. */
    private boolean isNoLabel(String label) {
        String squeezed = squeeze(label).replace(".", "");
        return squeezed.equalsIgnoreCase("NO") || squeezed.equals("번호");
    }

    // ================================================================
    // 서식 2 — 작업 위험성 평가서
    // ================================================================

    /** "작업 순서"와 "발생 가능한 위험"이 같은 행에 있는 표 머리글 행을 찾는다. 없으면 -1. */
    private int findRiskHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), RISK_HEADER_SCAN_LIMIT);
        for (int rowIdx = 0; rowIdx <= limit; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            String joined = joinRow(row);
            if (joined.contains(RISK_HEADER_STEP) && joined.contains(RISK_HEADER_HAZARD)) {
                return rowIdx;
            }
        }
        return -1;
    }

    private ParsedSheet parseRiskAssessment(Sheet sheet, int headerRowIdx) {
        String sheetName = sheet.getSheetName();
        Row headerRow = sheet.getRow(headerRowIdx);

        // 머리글에서 열 정의와 각 열이 실제로 놓인 엑셀 열 번호를 함께 뽑는다.
        List<ParsedColumn> columns = new ArrayList<>();
        List<Integer> sourceColumnIndexes = new ArrayList<>();
        int checkSeq = 0;
        for (int colIdx = 0; colIdx <= headerRow.getLastCellNum(); colIdx++) {
            String label = flatten(cellText(headerRow.getCell(colIdx)));
            if (label.isBlank()) continue;

            if (RISK_CHECK_MARK.equals(label)) {
                checkSeq++;
                columns.add(new ParsedColumn("확인" + checkSeq, SafetyManualColumn.TYPE_CHECK, 60));
            } else {
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_TEXT, 300));
            }
            sourceColumnIndexes.add(colIdx);
        }
        if (columns.size() < 2) {
            return ParsedSheet.rejected(sheetName, "위험성 평가 표의 머리글을 읽을 수 없습니다.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        int order = 1;
        for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            if (joinRow(row).contains(RISK_TABLE_END)) break;

            List<ParsedCell> cells = new ArrayList<>();
            boolean hasText = false;
            for (int i = 0; i < columns.size(); i++) {
                String raw = cellText(row.getCell(sourceColumnIndexes.get(i)));
                if (SafetyManualColumn.TYPE_CHECK.equals(columns.get(i).type())) {
                    cells.add(ParsedCell.check(isChecked(raw)));
                } else {
                    String text = tidyText(raw);
                    if (!text.isBlank()) hasText = true;
                    cells.add(ParsedCell.text(text));
                }
            }
            if (!hasText) continue;   // 빈 행/병합 잔여 행은 건너뛴다

            rows.add(new ParsedRow(leadingNumber(cells, order), order, cells, List.of()));
            order++;
        }

        if (rows.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "인식 가능한 작업 순서 행이 없습니다.");
        }

        List<ParsedMeta> meta = extractRiskMeta(sheet, headerRowIdx);
        String title = extractRiskTitle(sheet, headerRowIdx, sheetName);
        return ParsedSheet.accepted(sheetName, SafetyFormType.RISK_ASSESSMENT, title, meta, columns, rows);
    }

    /** 머리글 위쪽 행에서 "라벨 → 오른쪽 첫 값" 형태로 머리말 항목을 모은다. */
    private List<ParsedMeta> extractRiskMeta(Sheet sheet, int headerRowIdx) {
        List<ParsedMeta> meta = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < headerRowIdx; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
                String label = flatten(cellText(row.getCell(colIdx)));
                if (label.isBlank()) continue;

                String matched = RISK_META_LABELS.stream()
                        .filter(known -> label.replace(" ", "").startsWith(known.replace(" ", "")))
                        .findFirst().orElse(null);
                if (matched == null) continue;

                String value = firstValueToRight(row, colIdx);
                if (!value.isBlank()) {
                    meta.add(new ParsedMeta(matched, value));
                }
            }
        }
        return meta;
    }

    /** 라벨 칸 오른쪽에서 처음 만나는 값 (병합 때문에 몇 칸 건너뛰어 있을 수 있다) */
    private String firstValueToRight(Row row, int labelColIdx) {
        for (int colIdx = labelColIdx + 1; colIdx <= row.getLastCellNum(); colIdx++) {
            String text = flatten(cellText(row.getCell(colIdx)));
            if (text.isBlank()) continue;
            // 다음 라벨을 값으로 잘못 잡지 않도록 거른다
            boolean isAnotherLabel = RISK_META_LABELS.stream()
                    .anyMatch(known -> text.replace(" ", "").startsWith(known.replace(" ", "")));
            return isAnotherLabel ? "" : text;
        }
        return "";
    }

    /** 제목은 "작업명" 값 우선, 없으면 시트명을 쓴다. */
    private String extractRiskTitle(Sheet sheet, int headerRowIdx, String sheetName) {
        for (int rowIdx = 0; rowIdx < headerRowIdx; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
                if ("작업명".equals(flatten(cellText(row.getCell(colIdx))))) {
                    String value = firstValueToRight(row, colIdx);
                    if (!value.isBlank()) return value;
                }
            }
        }
        return sheetName.trim();
    }

    /** "□" 는 미체크, "■/☑/√/V/O" 계열은 체크로 본다. */
    private boolean isChecked(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.isEmpty() || "□".equals(text)) return false;
        return text.contains("■") || text.contains("☑") || text.contains("✔")
                || text.contains("√") || text.equalsIgnoreCase("V") || text.equalsIgnoreCase("O");
    }

    /**
     * 세로로 병합된 번호 칸의 값을 아래 행으로 끌어온다.
     *
     * <p>엑셀은 병합된 칸의 값을 첫 행에만 담는다. 그래서 "1" 하나가 여섯 줄을 덮고 있어도
     * 둘째 줄부터는 번호 칸이 비어 있고, 예전에는 그 자리에 순번을 새로 매겨
     * 1, 2, 3, 4, 5, 6, <b>2</b>, 8, <b>3</b>, 10 처럼 뒤로 돌아가는 번호가 나왔다.
     *
     * @return 병합 안이 아니거나 병합 첫 칸에 번호가 없으면 null
     */
    private Integer mergedNoAbove(Sheet sheet, int rowIdx, int noColumnIndex) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstColumn() <= noColumnIndex && noColumnIndex <= region.getLastColumn()
                    && region.getFirstRow() < rowIdx && rowIdx <= region.getLastRow()) {
                Row first = sheet.getRow(region.getFirstRow());
                return (first != null) ? cellInt(first.getCell(noColumnIndex)) : null;
            }
        }
        return null;
    }

    /** 첫 텍스트 칸 앞의 "1." 같은 번호를 단계 번호로 쓴다. 없으면 순번을 쓴다. */
    private int leadingNumber(List<ParsedCell> cells, int fallback) {
        for (ParsedCell cell : cells) {
            String text = (cell.text() != null) ? cell.text().trim() : "";
            if (text.isEmpty()) continue;
            int dot = text.indexOf('.');
            if (dot > 0 && dot <= 3) {
                try {
                    return Integer.parseInt(text.substring(0, dot).trim());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
        return fallback;
    }

    // ================================================================
    // 공통 유틸
    // ================================================================

    /**
     * 시트에 삽입된 그림을 앵커의 행 번호(0-based) 기준으로 묶는다.
     * <p>.xls(HSSF)에는 이 서식의 사진이 없으므로 .xlsx(XSSF)일 때만 훑는다.
     *
     * @param includeData false 면 개수만 세고 원본 바이트는 읽지 않는다 (미리보기용)
     */
    private Map<Integer, List<ParsedPhoto>> extractPhotosByRow(Sheet sheet, boolean includeData,
                                                               List<Integer> sourceColumnIndexes,
                                                               int photoColumnPos,
                                                               int firstDataRow, int lastDataRow) {
        Map<Integer, List<ParsedPhoto>> result = new LinkedHashMap<>();
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return result;
        }
        XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
        if (drawing == null) return result;

        int[] seq = {0};
        long[] rowTops = rowTopOffsets(sheet, lastDataRow);
        for (XSSFShape shape : drawing.getShapes()) {
            collectPhotos(sheet, shape, null, result, seq, includeData,
                    sourceColumnIndexes, photoColumnPos, rowTops, firstDataRow, lastDataRow);
        }
        return result;
    }

    /**
     * 0행부터 각 행의 위쪽 끝까지의 세로 거리(EMU) 표.
     *
     * <p>행 높이가 제각각이라 행 번호만으로는 그림이 실제로 놓인 자리를 알 수 없다.
     * 이 표가 있어야 앵커의 세로 좌표를 눈에 보이는 행으로 되돌릴 수 있다.
     */
    private long[] rowTopOffsets(Sheet sheet, int lastDataRow) {
        int size = Math.max(lastDataRow, sheet.getLastRowNum()) + 3;
        long[] tops = new long[size + 1];
        for (int r = 0; r < size; r++) {
            Row line = sheet.getRow(r);
            float points = (line != null) ? line.getHeightInPoints() : sheet.getDefaultRowHeightInPoints();
            tops[r + 1] = tops[r] + Math.round(points * (double) EMU_PER_POINT);
        }
        return tops;
    }

    /** 앵커의 (행 번호, 행 안에서의 세로 오프셋)을 시트 맨 위에서부터의 거리(EMU)로 바꾼다. */
    private long verticalEmu(long[] rowTops, int row, int dy) {
        int r = Math.max(0, Math.min(row, rowTops.length - 1));
        return rowTops[r] + Math.max(0, dy);
    }

    /**
     * 그림이 놓인 행. 앵커의 <b>세로 가운데가 실제로 걸린 행</b>을 쓴다.
     *
     * <p>행 번호만으로 가운데를 잡으면(= {@code (row1 + row2) / 2}) 두 행에 걸친 그림이 늘 윗행으로 간다.
     * 1행 바닥에서 시작해 2행을 가득 채운 그림도 1행에 붙어, 사진이 한 칸씩 위로 밀려 보였다.
     * 행 높이와 앵커 오프셋으로 실제 좌표를 재면 엑셀에서 눈으로 보는 자리와 같아진다.
     *
     * <p>머리글 위/표 아래로 삐져나온 그림은 버리지 않고 가장 가까운 데이터 행으로 당긴다.
     * 예전에는 머리글 행(0행)에서 시작하는 그림이 통째로 사라졌다.
     */
    private int anchorRowOf(XSSFClientAnchor anchor, long[] rowTops, int firstDataRow, int lastDataRow) {
        int top = Math.max(0, anchor.getRow1());
        int bottom = Math.max(anchor.getRow2(), top);
        long topEmu = verticalEmu(rowTops, top, anchor.getDy1());
        // oneCellAnchor 는 아래쪽 표시가 없어 row2/dy2 가 0 이다. 그때는 위쪽만 보고 정한다.
        long bottomEmu = Math.max(topEmu, verticalEmu(rowTops, bottom, anchor.getDy2()));
        long center = (topEmu + bottomEmu) / 2;

        int row = top;
        for (int r = 0; r + 1 < rowTops.length; r++) {
            if (rowTops[r] <= center) {
                row = r;
            } else {
                break;
            }
        }
        if (row < firstDataRow) return firstDataRow;
        if (lastDataRow >= firstDataRow && row > lastDataRow) return lastDataRow;
        return row;
    }

    /**
     * 그림이 걸쳐 있는 엑셀 열 범위를 보고 표의 어느 열에 넣을지 정한다.
     *
     * <p>그림은 칸에 딱 맞게 놓이지 않고 옆 칸까지 걸쳐 있는 경우가 많다
     * (예: "공정명"부터 "사진"까지 걸친 그림). 그래서 걸친 범위 안에
     * <b>사진 열이 있으면 사진 열을 먼저</b> 고르고, 없을 때만 다른 열을 본다.
     * 비고 칸에만 놓인 그림은 범위에 사진 열이 없으므로 비고 열로 간다.
     *
     * @return {@link ParsedSheet#getColumns()} 기준 인덱스. 못 정하면 -1
     */
    private int resolvePhotoColumn(int firstCol, int lastCol,
                                   List<Integer> sourceColumnIndexes, int photoColumnPos) {
        if (photoColumnPos >= 0) {
            int photoSourceCol = sourceColumnIndexes.get(photoColumnPos);
            if (photoSourceCol >= firstCol && photoSourceCol <= lastCol) {
                return photoColumnPos;
            }
        }
        for (int pos = 0; pos < sourceColumnIndexes.size(); pos++) {
            int sourceCol = sourceColumnIndexes.get(pos);
            if (sourceCol >= firstCol && sourceCol <= lastCol) {
                return pos;
            }
        }
        // 표의 열과 하나도 겹치지 않는 그림(No./공정명 칸에만 걸쳐 있는 경우)은
        // 가장 가까운 열에 붙인다. 사진 전용 열이 없는 시트도 있어서, 여기서 -1 로 두면
        // 갈 곳이 없어 화면에 아예 나오지 않는다.
        int nearest = -1;
        int nearestGap = Integer.MAX_VALUE;
        for (int pos = 0; pos < sourceColumnIndexes.size(); pos++) {
            int sourceCol = sourceColumnIndexes.get(pos);
            int gap = (sourceCol < firstCol) ? firstCol - sourceCol : sourceCol - lastCol;
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest = pos;
            }
        }
        return nearest;
    }

    /**
     * 그림이면 담고, 그룹(사진 + 화살표/타원 등이 묶인 도형)이면 그 안까지 들어가 그림만 꺼낸다.
     *
     * <p>그룹 안의 그림은 자기 앵커가 없으므로({@code getAnchor()} 가 null) 바깥 그룹의 앵커 행을 쓴다.
     * 그룹에 함께 묶인 도형(화살표·타원·설명상자)은 벡터 도형이라 그림 파일로 뽑을 수 없어
     * 사진만 저장된다 — 표시는 원본 엑셀보다 단순해진다.
     *
     * @param inheritedRow 바깥 그룹에서 물려받은 행 번호 (최상위 도형이면 -1)
     */
    private void collectPhotos(Sheet sheet, XSSFShape shape, XSSFClientAnchor inherited,
                               Map<Integer, List<ParsedPhoto>> result, int[] seq, boolean includeData,
                               List<Integer> sourceColumnIndexes, int photoColumnPos,
                               long[] rowTops, int firstDataRow, int lastDataRow) {
        if (shape instanceof XSSFShapeGroup group) {
            XSSFClientAnchor groupAnchor = clientAnchor(group);
            if (groupAnchor == null) groupAnchor = inherited;
            for (XSSFShape child : group) {
                collectPhotos(sheet, child, groupAnchor, result, seq, includeData,
                        sourceColumnIndexes, photoColumnPos, rowTops, firstDataRow, lastDataRow);
            }
            return;
        }
        if (!(shape instanceof XSSFPicture picture)) return;

        XSSFClientAnchor anchor = clientAnchor(picture);
        if (anchor == null) anchor = inherited;   // 그룹 안의 그림은 자기 앵커가 없다 — 그룹 것을 쓴다
        if (anchor == null) return;
        if (anchor.getRow1() < 0) return;
        int rowIdx = anchorRowOf(anchor, rowTops, firstDataRow, lastDataRow);
        int columnPos = resolvePhotoColumn(anchor.getCol1(), anchor.getCol2(),
                sourceColumnIndexes, photoColumnPos);

        XSSFPictureData pictureData = picture.getPictureData();
        if (pictureData == null) return;
        // EMF/WMF/WDP 등 웹에서 바로 표시할 수 없는 포맷은 건너뛴다.
        String ext = webImageExtension(pictureData);
        if (ext == null) {
            return;
        }
        int seqNo = seq[0]++;
        String fileName = "sheet_" + sheet.getSheetName().replaceAll("[^a-zA-Z0-9가-힣]", "_")
                + "_row" + rowIdx + "_" + seqNo + "." + ext;

        result.computeIfAbsent(rowIdx, k -> new ArrayList<>())
                .add(new ParsedPhoto(fileName, "image/" + ext,
                        includeData ? uprightBytes(picture, pictureData, ext) : null, columnPos, seqNo));
    }

    /**
     * 엑셀에서 돌려 놓은 그림은 돌린 상태로 저장한다.
     *
     * <p>엑셀은 원본 그림을 그대로 두고 도형에 회전값만 걸어 두기 때문에, 원본 바이트를 그대로
     * 쓰면 화면에서 눕혀져 보인다. 세로로 찍은 사진을 엑셀에서 세워 놓은 경우가 특히 그렇다.
     * 90도 단위 회전만 다룬다(그 외 각도는 원본 그대로 둔다).
     */
    private byte[] uprightBytes(XSSFPicture picture, XSSFPictureData pictureData, String ext) {
        byte[] data = pictureData.getData();
        int degrees = rotationDegrees(picture);
        if (degrees == 0 || data == null || data.length == 0) return data;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) return data;
            boolean quarter = (degrees == 90 || degrees == 270);
            int w = quarter ? src.getHeight() : src.getWidth();
            int h = quarter ? src.getWidth() : src.getHeight();
            BufferedImage out = new BufferedImage(w, h,
                    src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.translate(w / 2.0, h / 2.0);
            g.rotate(Math.toRadians(degrees));
            g.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
            g.drawImage(src, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            String format = "png".equalsIgnoreCase(ext) ? "png" : "jpg";
            if (!ImageIO.write(out, format, buffer)) return data;
            return buffer.toByteArray();
        } catch (Exception e) {
            return data;   // 못 돌리면 원본 그대로 — 사진이 사라지는 것보다 낫다
        }
    }

    /** 도형에 걸린 회전각을 90도 단위로 정규화한다. 90의 배수가 아니면 0. */
    private int rotationDegrees(XSSFPicture picture) {
        try {
            var shapeProperties = picture.getCTPicture().getSpPr();
            if (shapeProperties == null || !shapeProperties.isSetXfrm()) return 0;
            var xfrm = shapeProperties.getXfrm();
            if (!xfrm.isSetRot()) return 0;
            long degrees = Math.floorMod(xfrm.getRot() / 60000L, 360L);   // 1/60000 도 단위
            return (degrees % 90 == 0) ? (int) degrees : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 시트 좌표에 붙은 앵커. 그룹 안의 자식 도형은 자기 앵커가 없어 null 이다. */
    private XSSFClientAnchor clientAnchor(XSSFShape shape) {
        XSSFAnchor anchor = shape.getAnchor();
        return (anchor instanceof XSSFClientAnchor clientAnchor) ? clientAnchor : null;
    }

    private String joinRow(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
            String text = cellText(row.getCell(colIdx));
            if (!text.isBlank()) sb.append(text).append(' ');
        }
        return sb.toString();
    }

    /** 줄바꿈/연속 공백을 단일 공백으로 정리한다. */
    private String flatten(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    /**
     * 셀 안의 과한 공백을 정리한다. (표에 저장되는 본문 값 전용)
     *
     * <p>엑셀에서 칸 너비에 맞추려고 {@code "지필연결시 2인1조 작업                    "} 처럼
     * 공백을 여러 개 넣어 둔 곳이 많다. 화면은 표 폭이 달라서 그대로 두면 문장 중간이 뚝 벌어져 보인다.
     * 줄바꿈은 작성자가 의도한 것이라 살리고, <b>한 줄 안의 연속 공백만</b> 하나로 줄인다.
     * 앞뒤 빈 줄은 버리고, 중간의 연속 빈 줄은 한 줄까지만 남긴다.
     *
     * <p>일반 공백뿐 아니라 탭·줄바꿈 없는 공백(U+00A0)·전각 공백(U+3000)도 함께 다룬다
     * — 한글 엑셀에서 자주 섞여 들어온다.
     */
    private String tidyText(String raw) {
        if (raw == null) return "";
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean pendingBlankLine = false;
        for (String line : lines) {
            String tidy = line.replaceAll("[ \\t\\u00A0\\u3000]+", " ").trim();
            if (tidy.isEmpty()) {
                pendingBlankLine = (sb.length() > 0);
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
                if (pendingBlankLine) sb.append('\n');
            }
            pendingBlankLine = false;
            sb.append(tidy);
        }
        return sb.toString();
    }

    /**
     * 공백을 모두 지운다. 머리글 비교 전용 —
     * 같은 양식이라도 파일마다 "공정 순서 / 공정순서 / 공정  순서" 처럼 띄어쓰기가 제각각이다.
     */
    private String squeeze(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", "");
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                type = cell.getCachedFormulaResultType();   // 수식은 엑셀이 저장해 둔 결과값을 쓴다
            }
            switch (type) {
                case NUMERIC: {
                    double v = cell.getNumericCellValue();
                    return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
                }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case STRING: {
                    String s = (cell.getCellType() == CellType.FORMULA)
                            ? cell.getStringCellValue() : cell.getRichStringCellValue().getString();
                    return (s != null) ? s : "";
                }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private Integer cellInt(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            String s = cell.getStringCellValue();
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // 결과 모델 (순수 데이터, DB/디스크 의존 없음)
    // ================================================================

    /** 시트 1개의 파싱 결과 */
    public static final class ParsedSheet {
        private final String sheetName;
        private final boolean recognized;
        private String reason;
        /** 정해진 문구로 확실히 가려낸 시트인지. false 면 화면에서 기본 선택하지 않는다. */
        private boolean confident = true;
        private final SafetyFormType formType;
        private final String title;
        private final List<ParsedMeta> meta;
        private final List<ParsedColumn> columns;
        private final List<ParsedRow> rows;

        private ParsedSheet(String sheetName, boolean recognized, String reason, SafetyFormType formType,
                            String title, List<ParsedMeta> meta, List<ParsedColumn> columns, List<ParsedRow> rows) {
            this.sheetName = sheetName;
            this.recognized = recognized;
            this.reason = reason;
            this.formType = formType;
            this.title = title;
            this.meta = meta;
            this.columns = columns;
            this.rows = rows;
        }

        static ParsedSheet accepted(String sheetName, SafetyFormType formType, String title,
                                    List<ParsedMeta> meta, List<ParsedColumn> columns, List<ParsedRow> rows) {
            return new ParsedSheet(sheetName, true, null, formType, title, meta, columns, rows);
        }

        static ParsedSheet rejected(String sheetName, String reason) {
            return new ParsedSheet(sheetName, false, reason, null, null, List.of(), List.of(), List.of());
        }

        /** 머리글을 추정으로 잡았을 때 — 인식은 하되 사람이 확인하도록 표시한다. */
        void markNeedsReview(String note) {
            this.confident = false;
            this.reason = note;
        }

        public String getSheetName() { return sheetName; }
        public boolean isRecognized() { return recognized; }
        public boolean isConfident() { return confident; }
        public String getReason() { return reason; }
        public SafetyFormType getFormType() { return formType; }
        public String getTitle() { return title; }
        public List<ParsedMeta> getMeta() { return meta; }
        public List<ParsedColumn> getColumns() { return columns; }
        public List<ParsedRow> getRows() { return rows; }

        public int getPhotoCount() {
            return rows.stream().mapToInt(r -> r.photos().size()).sum();
        }

        /** 미리보기에 보여줄 요약 줄 (앞쪽 텍스트 열을 이어 붙인다) */
        public List<String> previewLines(int limit) {
            List<String> lines = new ArrayList<>();
            for (ParsedRow row : rows) {
                if (lines.size() >= limit) break;
                StringBuilder sb = new StringBuilder().append(row.stepNo()).append(". ");
                for (ParsedCell cell : row.cells()) {
                    if (cell.text() != null && !cell.text().isBlank()) {
                        sb.append(cell.text().replaceAll("\\s+", " ").trim());
                        break;
                    }
                }
                String line = sb.toString();
                lines.add(line.length() > 40 ? line.substring(0, 40) + "..." : line);
            }
            return lines;
        }
    }

    /** 표의 열 1개 */
    public record ParsedColumn(String label, String type, int widthWeight) {
    }

    /** 머리말 항목 1개 */
    public record ParsedMeta(String label, String value) {
    }

    /** 행(단계) 1개 — cells 는 columns 와 같은 순서로 1:1 대응한다 */
    public record ParsedRow(int stepNo, int sortOrder, List<ParsedCell> cells, List<ParsedPhoto> photos) {
    }

    /** 셀 1개 — 텍스트 열이면 text, 체크 열이면 checked 를 쓴다 */
    public record ParsedCell(String text, boolean checked) {
        static ParsedCell text(String value) { return new ParsedCell(value, false); }
        static ParsedCell check(boolean value) { return new ParsedCell(null, value); }
        static ParsedCell empty() { return new ParsedCell(null, false); }
    }

    /**
     * 사진 1장의 원본 바이트 (아직 디스크에 저장되지 않은 상태)
     *
     * @param columnIndex 이 사진이 놓인 열의 인덱스({@link ParsedSheet#getColumns()} 기준).
     *                    사진 전용 열이면 그 열, 비고처럼 글과 함께 있는 칸이면 그 열,
     *                    어느 열인지 못 정하면 -1 (화면에서 기본 사진 열로 간다)
     */
    public record ParsedPhoto(String fileName, String contentType, byte[] data,
                              int columnIndex, int index) {
    }
}
