package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.dto.request.ExcelSheetAssignRequest;
import com.company.module.safety.dto.response.ExcelImportResultResponse;
import com.company.module.safety.dto.response.ExcelSheetPreviewResponse;
import com.company.module.safety.dto.response.ManualSummaryResponse;
import com.company.module.safety.entity.SafetyManual;
import com.company.module.safety.entity.SafetyManualCategory;
import com.company.module.safety.entity.SafetyManualColumn;
import com.company.module.safety.entity.SafetyManualMeta;
import com.company.module.safety.entity.SafetyManualStep;
import com.company.module.safety.entity.SafetyManualStepValue;
import com.company.module.safety.repository.SafetyManualColumnRepository;
import com.company.module.safety.repository.SafetyManualMetaRepository;
import com.company.module.safety.repository.SafetyManualRepository;
import com.company.module.safety.repository.SafetyManualStepRepository;
import com.company.module.safety.repository.SafetyManualStepValueRepository;
import com.company.module.safety.support.SafetyExcelParser;
import com.company.module.safety.support.SafetyExcelParser.ParsedCell;
import com.company.module.safety.support.SafetyExcelParser.ParsedColumn;
import com.company.module.safety.support.SafetyExcelParser.ParsedMeta;
import com.company.module.safety.support.SafetyExcelParser.ParsedPhoto;
import com.company.module.safety.support.SafetyExcelParser.ParsedRow;
import com.company.module.safety.support.SafetyExcelParser.ParsedSheet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 안전 엑셀 일괄업로드 오케스트레이션 (안전작업 매뉴얼 / 작업 위험성 평가서 공통).
 *
 * <p>사용자 요구사항에 따라 <b>2단계</b>로 동작한다.
 * <ol>
 *   <li>1단계 {@link #preview}: 시트별 형식만 확인하고 DB 에는 아무것도 쓰지 않는다.</li>
 *   <li>2단계 {@link #confirmImport}: 시트마다 지정한 분류에 실제로 저장한다.
 *       (파일은 서버에 임시 저장하지 않으므로 2단계 호출 시 파일을 함께 다시 전송받는다)</li>
 * </ol>
 *
 * <p>표의 열 구성은 서식마다 다르므로 파서가 돌려준 열 정의를 매뉴얼마다 그대로 만들어 두고,
 * 각 칸 값을 {@link SafetyManualStepValue} 로 넣는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyExcelUploadService {

    private static final int PREVIEW_LINE_LIMIT = 5;

    private final SafetyManualRepository manualRepository;
    private final SafetyManualStepRepository stepRepository;
    private final SafetyManualColumnRepository columnRepository;
    private final SafetyManualStepValueRepository valueRepository;
    private final SafetyManualMetaRepository metaRepository;
    private final SafetyCategoryService categoryService;
    private final SafetyPhotoService photoService;

    @Value("${safety.excel.max-sheets-per-upload:100}")
    private int maxSheetsPerUpload;

    private final SafetyExcelParser parser = new SafetyExcelParser();

    // ================================================================
    // 1단계: 형식 확인 / 미리보기 (DB 변경 없음)
    // ================================================================
    /** 파일을 통째로 받아 형식만 확인한다. (작은 파일용 — 큰 파일은 분할 업로드 후 아래 메서드를 쓴다) */
    public List<ExcelSheetPreviewResponse> preview(MultipartFile file) {
        return toPreviews(parseWorkbook(file, false));
    }

    /** 분할 업로드로 서버에 이미 올라온 파일의 형식을 확인한다. */
    public List<ExcelSheetPreviewResponse> preview(Path excelFile) {
        return toPreviews(parseStaged(excelFile, false));
    }

    private List<ExcelSheetPreviewResponse> toPreviews(List<ParsedSheet> sheets) {
        if (sheets.size() > maxSheetsPerUpload) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "시트 개수가 너무 많습니다. (" + sheets.size() + "개, 최대 " + maxSheetsPerUpload + "개)");
        }
        return sheets.stream().map(this::toPreview).toList();
    }

    private ExcelSheetPreviewResponse toPreview(ParsedSheet sheet) {
        return ExcelSheetPreviewResponse.builder()
                .sheetName(sheet.getSheetName())
                .recognized(sheet.isRecognized())
                .reason(sheet.getReason())
                .formType(sheet.isRecognized() ? sheet.getFormType().name() : null)
                .formTypeName(sheet.isRecognized() ? sheet.getFormType().displayName() : null)
                .stepCount(sheet.getRows().size())
                .photoCount(sheet.getPhotoCount())
                .detectedTitle(sheet.getTitle())
                .selected(sheet.isRecognized())
                .stepPreviewLines(sheet.isRecognized() ? sheet.previewLines(PREVIEW_LINE_LIMIT) : List.of())
                .build();
    }

    // ================================================================
    // 2단계: 확정 업로드 (선택된 시트만 실제 저장)
    // ================================================================
    @Transactional
    public ExcelImportResultResponse confirmImport(MultipartFile file,
                                                    List<ExcelSheetAssignRequest> assignments, String createdBy) {
        Map<String, Long> categoryBySheet = toCategoryBySheet(assignments);
        String sourceFileName = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "upload.xlsx";
        return importSheets(parseWorkbook(file, true), sourceFileName, categoryBySheet, createdBy);
    }

    /** 분할 업로드로 서버에 이미 올라온 파일을 확정 저장한다. (파일을 다시 받지 않는다) */
    @Transactional
    public ExcelImportResultResponse confirmImport(Path excelFile, String sourceFileName,
                                                    List<ExcelSheetAssignRequest> assignments, String createdBy) {
        Map<String, Long> categoryBySheet = toCategoryBySheet(assignments);
        return importSheets(parseStaged(excelFile, true), sourceFileName, categoryBySheet, createdBy);
    }

    private ExcelImportResultResponse importSheets(List<ParsedSheet> sheets, String sourceFileName,
                                                    Map<String, Long> categoryBySheet, String createdBy) {
        // 같은 분류를 여러 시트가 함께 쓰는 경우가 흔하므로 분류 조회 결과와 정렬순서를 분류별로 들고 간다.
        Map<Long, SafetyManualCategory> categoryCache = new HashMap<>();
        Map<Long, Integer> nextSortOrder = new HashMap<>();

        List<ManualSummaryResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (ParsedSheet sheet : sheets) {
            Long categoryId = categoryBySheet.get(sheetKey(sheet.getSheetName()));
            if (categoryId == null) {
                continue; // 사용자가 선택하지 않은 시트는 건너뜀
            }
            if (!sheet.isRecognized()) {
                skipped.add(sheet.getSheetName() + " - " + sheet.getReason());
                continue;
            }

            SafetyManualCategory category = categoryCache.get(categoryId);
            if (category == null) {
                category = categoryService.findActiveLeaf(categoryId);
                categoryCache.put(categoryId, category);
            }

            String title = sheet.getTitle();
            if (title == null || title.isBlank()) {
                title = sheet.getSheetName();
            }
            if (manualRepository.existsByTitleAndCategory_CategoryId(title, categoryId)) {
                skipped.add(sheet.getSheetName() + " - '" + category.getName()
                        + "' 분류에 이미 존재하는 매뉴얼 제목(" + title + ")입니다.");
                continue;
            }

            int sortOrder = nextSortOrder.getOrDefault(categoryId, 0);
            nextSortOrder.put(categoryId, sortOrder + 1);

            SafetyManual manual = manualRepository.save(SafetyManual.builder()
                    .category(category)
                    .title(title)
                    .formType(sheet.getFormType())
                    .sourceFileName(sourceFileName)
                    .sourceSheetName(sheet.getSheetName())
                    .sortOrder(sortOrder)
                    .createdBy(createdBy)
                    .build());

            saveMeta(manual, sheet.getMeta(), createdBy);
            List<SafetyManualColumn> columns = saveColumns(manual, sheet.getColumns(), createdBy);
            saveRows(manual, columns, sheet.getRows(), createdBy);

            created.add(ManualSummaryResponse.from(manual));
        }

        return ExcelImportResultResponse.builder()
                .importedCount(created.size())
                .manuals(created)
                .skipped(skipped)
                .build();
    }

    private void saveMeta(SafetyManual manual, List<ParsedMeta> parsedMeta, String createdBy) {
        int order = 1;
        for (ParsedMeta meta : parsedMeta) {
            metaRepository.save(SafetyManualMeta.builder()
                    .manual(manual)
                    .label(meta.label())
                    .valueText(meta.value())
                    .sortOrder(order++)
                    .createdBy(createdBy)
                    .build());
        }
    }

    /** 파서가 돌려준 열 정의를 그대로 만든다. 반환 순서는 파서의 열 순서와 같아 셀과 1:1 대응한다. */
    private List<SafetyManualColumn> saveColumns(SafetyManual manual, List<ParsedColumn> parsedColumns,
                                                  String createdBy) {
        List<SafetyManualColumn> columns = new ArrayList<>();
        int order = 1;
        for (ParsedColumn parsed : parsedColumns) {
            columns.add(columnRepository.save(SafetyManualColumn.builder()
                    .manual(manual)
                    .label(parsed.label())
                    .columnType(parsed.type())
                    .sortOrder(order++)
                    .widthWeight(parsed.widthWeight())
                    .createdBy(createdBy)
                    .build()));
        }
        return columns;
    }

    private void saveRows(SafetyManual manual, List<SafetyManualColumn> columns,
                          List<ParsedRow> rows, String createdBy) {
        for (ParsedRow row : rows) {
            SafetyManualStep step = stepRepository.save(SafetyManualStep.builder()
                    .manual(manual)
                    .stepNo(row.stepNo())
                    .sortOrder(row.sortOrder())
                    .createdBy(createdBy)
                    .build());

            for (int i = 0; i < columns.size() && i < row.cells().size(); i++) {
                SafetyManualColumn column = columns.get(i);
                if (column.isPhoto()) {
                    continue;   // 사진 열은 값 대신 사진 테이블로 들어간다
                }
                ParsedCell cell = row.cells().get(i);
                boolean hasText = cell.text() != null && !cell.text().isBlank();
                if (!column.isCheck() && !hasText) {
                    continue;   // 빈 텍스트 칸은 굳이 행을 만들지 않는다
                }
                valueRepository.save(SafetyManualStepValue.builder()
                        .step(step)
                        .column(column)
                        .textValue(cell.text())
                        .checked(cell.checked())
                        .createdBy(createdBy)
                        .build());
            }

            for (ParsedPhoto photo : row.photos()) {
                if (photo.data() == null || photo.data().length == 0) {
                    continue;   // 미리보기 파싱 결과(사진 개수만 센 것)로는 저장하지 않는다
                }
                // 사진이 놓여 있던 칸을 그대로 살린다 (비고 칸에 있던 사진은 비고 칸에)
                SafetyManualColumn photoColumn = (photo.columnIndex() >= 0
                        && photo.columnIndex() < columns.size()) ? columns.get(photo.columnIndex()) : null;
                photoService.saveParsedPhoto(step, photoColumn, photo, createdBy);
            }
        }
    }

    /** 시트→분류 매핑으로 정리한다. 같은 시트가 중복으로 오면 마지막 지정을 쓴다. */
    private Map<String, Long> toCategoryBySheet(List<ExcelSheetAssignRequest> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "가져올 시트를 하나 이상 선택하세요.");
        }
        Map<String, Long> categoryBySheet = new LinkedHashMap<>();
        for (ExcelSheetAssignRequest assignment : assignments) {
            String sheetName = sheetKey(assignment.getSheetName());
            if (sheetName.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시트명이 비어 있는 항목이 있습니다.");
            }
            if (assignment.getCategoryId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "'" + sheetName + "' 시트의 등록 분류를 선택하세요.");
            }
            categoryBySheet.put(sheetName, assignment.getCategoryId());
        }
        return categoryBySheet;
    }

    /**
     * 시트명 대조용 키. 앞뒤 공백을 떼고 맞춘다.
     * <p>실제 파일 중에 시트명이 " Core MC원지교체" 처럼 앞에 공백이 붙은 것이 있어,
     * 한쪽만 trim 하면 지정한 시트를 못 찾고 조용히 건너뛰게 된다.
     */
    private String sheetKey(String sheetName) {
        return (sheetName != null) ? sheetName.trim() : "";
    }

    /** 분할 업로드로 이미 디스크에 있는 파일을 파싱한다. (임시 파일로 옮기는 단계가 필요 없다) */
    private List<ParsedSheet> parseStaged(Path excelFile, boolean includePhotoData) {
        try {
            return includePhotoData
                    ? parser.parse(excelFile.toFile())
                    : parser.parseForPreview(excelFile.toFile());
        } catch (IllegalArgumentException e) {
            throw poiFormatError(e);
        }
    }

    /** POI 내부에서 올라오는 형식 오류를 업무 예외로 바꿔 준다. */
    private BusinessException poiFormatError(IllegalArgumentException e) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    /**
     * 업로드된 파일을 임시 파일로 떨어뜨린 뒤 파싱한다.
     *
     * <p>스트림째로 POI 에 넘기면 zip 전체가 힙에 올라간다. 이 모듈이 다루는 매뉴얼 파일은
     * 사진 때문에 50~90MB 인 것이 흔해서, 스트림으로 읽으면 힙 250MB 이상을 잡아먹고
     * 운영 설정({@code -Xmx384m})에서 OutOfMemoryError 로 업로드가 실패한다.
     *
     * @param includePhotoData 확정 저장이면 true. 미리보기는 사진 개수만 필요해 원본을 읽지 않는다.
     */
    private List<ParsedSheet> parseWorkbook(MultipartFile file, boolean includePhotoData) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 엑셀 파일이 없습니다.");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("safety-excel-", ".xlsx");
            file.transferTo(temp);
            return includePhotoData ? parser.parse(temp.toFile()) : parser.parseForPreview(temp.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw poiFormatError(e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 임시 파일이 남아도 업로드 자체는 성공이므로 무시한다 (OS 가 정리)
                }
            }
        }
    }
}
