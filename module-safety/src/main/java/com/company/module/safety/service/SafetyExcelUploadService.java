package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.dto.request.ExcelSheetAssignRequest;
import com.company.module.safety.dto.response.ExcelImportResultResponse;
import com.company.module.safety.dto.response.ExcelSheetPreviewResponse;
import com.company.module.safety.dto.response.ManualSummaryResponse;
import com.company.module.safety.entity.SafetyManual;
import com.company.module.safety.entity.SafetyManualCategory;
import com.company.module.safety.entity.SafetyManualStep;
import com.company.module.safety.repository.SafetyManualRepository;
import com.company.module.safety.repository.SafetyManualStepRepository;
import com.company.module.safety.support.SafetyExcelParser;
import com.company.module.safety.support.SafetyExcelParser.ParsedPhoto;
import com.company.module.safety.support.SafetyExcelParser.ParsedSheet;
import com.company.module.safety.support.SafetyExcelParser.ParsedStep;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 안전작업 매뉴얼 엑셀 일괄업로드 오케스트레이션.
 *
 * <p>사용자 요구사항에 따라 <b>2단계</b>로 동작한다.
 * <ol>
 *   <li>1단계 {@link #preview}: 업로드된 워크북의 시트별 형식을 확인만 하고 DB 에는 아무것도 쓰지 않는다.
 *       (개요/범례 시트 — 예: "초지" — 는 여기서 자동으로 인식 실패(recognized=false) 처리되어 제외된다)</li>
 *   <li>2단계 {@link #confirmImport}: 사용자가 화면에서 확인 후 선택한 시트만 실제로 매뉴얼로 저장한다.
 *       시트마다 등록할 분류를 따로 지정할 수 있으므로 시트→분류 매핑을 받는다.
 *       (파일은 서버에 임시 저장하지 않으므로 2단계 호출 시 파일을 함께 다시 전송받는다)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyExcelUploadService {

    private static final int PREVIEW_LINE_LIMIT = 5;

    private final SafetyManualRepository manualRepository;
    private final SafetyManualStepRepository stepRepository;
    private final SafetyCategoryService categoryService;
    private final SafetyPhotoService photoService;

    @Value("${safety.excel.max-sheets-per-upload:100}")
    private int maxSheetsPerUpload;

    private final SafetyExcelParser parser = new SafetyExcelParser();

    // ================================================================
    // 1단계: 형식 확인 / 미리보기 (DB 변경 없음)
    // ================================================================
    public List<ExcelSheetPreviewResponse> preview(MultipartFile file) {
        List<ParsedSheet> sheets = parseWorkbook(file);
        if (sheets.size() > maxSheetsPerUpload) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "시트 개수가 너무 많습니다. (" + sheets.size() + "개, 최대 " + maxSheetsPerUpload + "개)");
        }

        List<ExcelSheetPreviewResponse> result = new ArrayList<>();
        for (ParsedSheet sheet : sheets) {
            result.add(toPreview(sheet));
        }
        return result;
    }

    private ExcelSheetPreviewResponse toPreview(ParsedSheet sheet) {
        List<String> previewLines = new ArrayList<>();
        if (sheet.isRecognized()) {
            for (ParsedStep step : sheet.getSteps()) {
                if (previewLines.size() >= PREVIEW_LINE_LIMIT) break;
                previewLines.add(step.stepNo() + ". " + truncate(step.description()));
            }
        }
        return ExcelSheetPreviewResponse.builder()
                .sheetName(sheet.getSheetName())
                .recognized(sheet.isRecognized())
                .reason(sheet.getReason())
                .stepCount(sheet.getSteps().size())
                .photoCount(sheet.getPhotoCount())
                .detectedTitle(sheet.getTitle())
                .selected(sheet.isRecognized())
                .stepPreviewLines(previewLines)
                .build();
    }

    // ================================================================
    // 2단계: 확정 업로드 (선택된 시트만 실제 저장)
    // ================================================================
    @Transactional
    public ExcelImportResultResponse confirmImport(MultipartFile file,
                                                    List<ExcelSheetAssignRequest> assignments, String createdBy) {
        Map<String, Long> categoryBySheet = toCategoryBySheet(assignments);
        List<ParsedSheet> sheets = parseWorkbook(file);
        String sourceFileName = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "upload.xlsx";

        // 같은 분류를 여러 시트가 함께 쓰는 경우가 흔하므로 분류 조회 결과와 정렬순서를 분류별로 들고 간다.
        Map<Long, SafetyManualCategory> categoryCache = new HashMap<>();
        Map<Long, Integer> nextSortOrder = new HashMap<>();

        List<ManualSummaryResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (ParsedSheet sheet : sheets) {
            Long categoryId = categoryBySheet.get(sheet.getSheetName());
            if (categoryId == null) {
                continue; // 사용자가 선택하지 않은 시트는 건너뜀
            }
            if (!sheet.isRecognized()) {
                skipped.add(sheet.getSheetName() + " - " + sheet.getReason());
                continue;
            }

            SafetyManualCategory category = categoryCache.get(categoryId);
            if (category == null) {
                category = categoryService.findActiveMinor(categoryId);
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
                    .sourceFileName(sourceFileName)
                    .sourceSheetName(sheet.getSheetName())
                    .sortOrder(sortOrder)
                    .createdBy(createdBy)
                    .build());

            for (ParsedStep parsedStep : sheet.getSteps()) {
                SafetyManualStep step = stepRepository.save(SafetyManualStep.builder()
                        .manual(manual)
                        .stepNo(parsedStep.stepNo())
                        .description(parsedStep.description())
                        .hazard(parsedStep.hazard())
                        .safetyEquipment(parsedStep.safetyEquipment())
                        .remark(parsedStep.remark())
                        .sortOrder(parsedStep.sortOrder())
                        .createdBy(createdBy)
                        .build());

                for (ParsedPhoto photo : parsedStep.photos()) {
                    photoService.saveParsedPhoto(step, photo, createdBy);
                }
            }
            created.add(ManualSummaryResponse.from(manual));
        }

        return ExcelImportResultResponse.builder()
                .importedCount(created.size())
                .manuals(created)
                .skipped(skipped)
                .build();
    }

    /** 시트→분류 매핑으로 정리한다. 같은 시트가 중복으로 오면 마지막 지정을 쓴다. */
    private Map<String, Long> toCategoryBySheet(List<ExcelSheetAssignRequest> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "가져올 시트를 하나 이상 선택하세요.");
        }
        Map<String, Long> categoryBySheet = new LinkedHashMap<>();
        for (ExcelSheetAssignRequest assignment : assignments) {
            String sheetName = (assignment.getSheetName() != null) ? assignment.getSheetName().trim() : "";
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

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private List<ParsedSheet> parseWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 엑셀 파일이 없습니다.");
        }
        try {
            return parser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 40 ? flat.substring(0, 40) + "..." : flat;
    }
}
