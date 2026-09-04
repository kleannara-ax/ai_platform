package com.company.module.dailyreport.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.dailyreport.dto.*;
import com.company.module.dailyreport.entity.*;
import com.company.module.dailyreport.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 세부공장일보 핵심 비즈니스 로직
 * - 4개 표: 주요 생산 지표 현황 / 제지 재공품 및 야적현황 / 에너지 원단위 / 보일러 운영 현황
 * - 특이사항 + 이미지 첨부
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final DailyReportTableRepository tableRepository;
    private final DailyReportCellRepository cellRepository;
    private final DailyReportRemarkRepository remarkRepository;
    private final DailyReportImageRepository imageRepository;
    private final CellOwnershipSyncService cellOwnershipSyncService;
    private final CellAuthRepository cellAuthRepository;
    private final DailyBatchJobRepository batchJobRepository;
    private final EntityManager entityManager;

    /** ★★ "저장" 버튼이 활성화되는 구간(2026-08, 시간대 방식으로 변경) — 이 구간
     *  [SAVE_WINDOW_START, SAVE_WINDOW_END) 안에서만 "저장" 버튼 + daily_batchjob을
     *  전혀 참조하지 않는 기존 흐름이 유지된다. 이 구간을 벗어난 나머지 모든
     *  시간(자정을 넘나드는 새벽 포함)은 "수정" 버튼 + daily_batchjob 재업로드
     *  요청 흐름이 활성화된다. */
    private static final java.time.LocalTime SAVE_WINDOW_START_TIME = java.time.LocalTime.of(5, 0, 0);
    /** ★★ 저장 구간 종료 시각(배타적, exclusive) — 08:09:59는 "저장", 08:10:00부터는 "수정"
     *  (2026-08 8:05→8:10으로 조정) */
    private static final java.time.LocalTime SAVE_WINDOW_END_TIME = java.time.LocalTime.of(8, 10, 0);

    /** ★★ 특이사항(사업부별 5행) 전용 가상 표 코드 — daily_report_cell_auth의
     *  TABLE_CODE로도 그대로 사용되어 셀과 동일한 방식으로 담당자를 배정한다. */
    public static final String SPECIAL_NOTE_TABLE_CODE = "TBL_SPECIAL_NOTE";

    /** ★★ 사고 통계 페이지(표5~8) 하단에 추가되는 특이사항 표 2종 전용 가상 표 코드
     *  (2026-09 신규) — TBL_SPECIAL_NOTE와 완전히 독립된 별도의 줄바꿈/글자수
     *  예산 및 CellAuth 담당자 배정을 갖는다. */
    public static final String SAFETY_AMOUNT_NOTE_TABLE_CODE = "TBL_SAFETY_AMOUNT_NOTE";
    public static final String SAFETY_TREND_NOTE_TABLE_CODE = "TBL_SAFETY_TREND_NOTE";

    /** 사업부 코드 → 한글 라벨, 화면에 표시할 고정 5행 순서 그대로 */
    public static final Map<String, String> SPECIAL_NOTE_CATEGORIES = new LinkedHashMap<>();
    static {
        SPECIAL_NOTE_CATEGORIES.put("PAPER", "제지");
        SPECIAL_NOTE_CATEGORIES.put("TISSUE", "화장지");
        SPECIAL_NOTE_CATEGORIES.put("PAD", "패드");
        SPECIAL_NOTE_CATEGORIES.put("SAFETY", "사고/안전사고");
        SPECIAL_NOTE_CATEGORIES.put("ETC", "기타");
    }

    /** ★★ 사고 금액 특이사항표(TBL_SAFETY_AMOUNT_NOTE) 카테고리 — 제지/화장지/패드 3행
     *  (2026-09 신규). TBL_SPECIAL_NOTE의 PAPER/TISSUE/PAD와 같은 코드값을 재사용하지만,
     *  모든 조회/검증/이어받기 로직이 TABLE_CODE로 먼저 스코프되므로 값이 서로 섞이지 않는다. */
    public static final Map<String, String> SAFETY_AMOUNT_NOTE_CATEGORIES = new LinkedHashMap<>();
    static {
        SAFETY_AMOUNT_NOTE_CATEGORIES.put("PAPER", "제지");
        SAFETY_AMOUNT_NOTE_CATEGORIES.put("TISSUE", "화장지");
        SAFETY_AMOUNT_NOTE_CATEGORIES.put("PAD", "패드");
    }

    /** ★★ 안전사고 발생추이 특이사항표(TBL_SAFETY_TREND_NOTE) 카테고리 — 분할 없는 단일 행
     *  (2026-09 신규). */
    public static final Map<String, String> SAFETY_TREND_NOTE_CATEGORIES = new LinkedHashMap<>();
    static {
        SAFETY_TREND_NOTE_CATEGORIES.put("ALL", "전체");
    }

    /**
     * ★★ 특이사항류(TBL_SPECIAL_NOTE 및 사고 통계 특이사항표) 분량 제한 스펙을
     *  표코드별로 묶어 관리한다 (2026-09 일반화). 각 표는 독립적인 카테고리 그룹과
     *  줄바꿈/글자수 "공유 총량"(같은 표 안의 행들끼리만 합산)을 가지며, 서로의
     *  예산에 전혀 영향을 주지 않는다.
     */
    private record SpecialNoteSpec(Map<String, String> categories, int maxTotalNewlines,
                                    int maxTotalChars, int maxLineLength) {
    }

    /** ★★ 특이사항 분량 제한 (2026-07 추가, 2026-08 줄바꿈 21→17 조정, 2026-08 17→15 재조정,
     *  2026-09 15→13 재조정)
     *  - 줄바꿈/전체 글자수는 같은 표 안의 사업부 행 전체를 합산한 "공유 총량"이다
     *    (한 사업부가 많이 쓰면 다른 사업부가 쓸 수 있는 여유가 줄어든다).
     *  - 한 줄(개행으로 구분되는 한 문단) 글자수는 각 행 자신만의 독립된 제한이다.
     *  - TBL_SPECIAL_NOTE(5행)의 줄바꿈 총량은 원래 분리되기 전 하나의 자유서술 칸
     *    기준 21회였으나, 5개 사업부(제지/화장지/패드/사고안전사고/기타) 행으로 나뉘며
     *    행 사이 구분선이 4곳(5개 항목 사이 간격) 생겨 그만큼 공간을 차지하므로 21에서
     *    4를 뺀 17을 사용했다가, 이후 15로 재조정되었으나, 5개 행이 모두 채워지는
     *    최악의 경우(구분선 4 + 각 행 기본 1줄×5) 22줄 포맷을 2줄 초과하는 문제가
     *    확인되어 13으로 재조정했다.
     *    (22줄 = 줄바꿈 수 + 5개 행 기본 1줄 + 구분선 4곳 → 최대 안전 줄바꿈 수 = 22-5-4=13)
     *  - 사고 금액 특이사항표(TBL_SAFETY_AMOUNT_NOTE, 3행)/안전사고 발생추이
     *    특이사항표(TBL_SAFETY_TREND_NOTE, 1행)는 각각 사용자가 직접 지정한
     *    줄바꿈 한도(9회/6회)를 기준으로, 동일한 (줄바꿈+행수)×67자 공식으로
     *    글자수 한도를 산출했다: (9+3)×67=804자, (6+1)×67=469자.
     *  프론트(index.html/safety-stats.html)에서도 동일한 상수로 실시간 검증을 하지만,
     *  프론트 검증은 우회 가능하므로(직접 API 호출 등) 여기 서버 측에서 반드시 재검증한다. */
    private static final int SPECIAL_NOTE_MAX_LINE_LENGTH = 67;
    private static final Map<String, SpecialNoteSpec> SPECIAL_NOTE_SPECS = new LinkedHashMap<>();
    static {
        SPECIAL_NOTE_SPECS.put(SPECIAL_NOTE_TABLE_CODE,
                new SpecialNoteSpec(SPECIAL_NOTE_CATEGORIES, 13, 1206, SPECIAL_NOTE_MAX_LINE_LENGTH));
        SPECIAL_NOTE_SPECS.put(SAFETY_AMOUNT_NOTE_TABLE_CODE,
                new SpecialNoteSpec(SAFETY_AMOUNT_NOTE_CATEGORIES, 9, 804, SPECIAL_NOTE_MAX_LINE_LENGTH));
        SPECIAL_NOTE_SPECS.put(SAFETY_TREND_NOTE_TABLE_CODE,
                new SpecialNoteSpec(SAFETY_TREND_NOTE_CATEGORIES, 6, 469, SPECIAL_NOTE_MAX_LINE_LENGTH));
    }

    /** tableCode에 해당하는 분량 제한 스펙을 반환한다 (등록되지 않은 코드면 예외). */
    private SpecialNoteSpec specialNoteSpec(String tableCode) {
        SpecialNoteSpec spec = SPECIAL_NOTE_SPECS.get(tableCode);
        if (spec == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "올바르지 않은 특이사항 표 코드입니다: " + tableCode);
        }
        return spec;
    }

    // ─────────────────────────────────────────────
    // 일보 CRUD
    // ─────────────────────────────────────────────

    /**
     * 일보 목록 조회 (기간·상태 필터 + 페이징)
     */
    public Page<DailyReportResponse> getReportList(LocalDate startDate, LocalDate endDate,
                                                    String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportRepository.findByConditions(startDate, endDate, status, pageable)
                .map(DailyReportResponse::from);
    }

    /**
     * 일보 상세 조회 (표 + 셀 + 특이사항 + 이미지 포함)
     */
    public DailyReportResponse getReport(Long reportId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));
        return DailyReportResponse.fromWithDetails(report);
    }

    /**
     * 날짜별 일보 조회 — 해당 날짜에 일보가 없으면 자동 생성 (DRAFT 상태)
     *
     * 세부공장일보는 매일 입력하는 문서이므로, 사용자가 해당 날짜 페이지를
     * 열었을 때 빈 일보가 자동으로 준비되어야 한다.
     */
    @Transactional
    public DailyReportResponse getReportByDate(LocalDate reportDate, Long userId) {
        return reportRepository.findByReportDate(reportDate)
                .map(report -> {
                    // 기존 일보가 있지만 셀이 비어 있는 경우 기본 셀 보충
                    // (이전 버전에서 테이블만 생성하고 셀을 누락한 데이터 보완)
                    boolean cellsMissing = report.getTables().stream()
                            .anyMatch(t -> t.getCells().isEmpty());
                    // ★★ 이 기능 배포 이전에 생성된 일보처럼 표 자체가 통째로 누락된
                    // 경우(예: 안전사고 표4개 신규 추가)도 함께 보충 대상인지 미리 확인
                    Set<String> existingCodes = report.getTables().stream()
                            .map(DailyReportTable::getTableCode).collect(Collectors.toSet());
                    boolean tablesMissing = java.util.Arrays.stream(TABLE_DEFINITIONS)
                            .anyMatch(def -> !existingCodes.contains(def[0]));
                    if (cellsMissing || tablesMissing) {
                        // ★ 값 이어받기: 보충되는 표에도 직전 일보의 값을 초기값으로 반영
                        Map<String, String> previousValues = findPreviousCellValues(reportDate);
                        if (cellsMissing) {
                            for (DailyReportTable table : report.getTables()) {
                                if (table.getCells().isEmpty()) {
                                    DefaultCellTemplate.populateDefaultCells(table, reportDate, historicalValueLookup(), historicalYearlyValueLookup());
                                    applyCarriedOverValues(table, previousValues);
                                    // ★ 하드코딩 제거: 생성 즉시 현재 활성 CellAuth 담당자를 반영
                                    cellOwnershipSyncService.applyCurrentOwnersToNewTable(table);
                                }
                            }
                        }
                        if (tablesMissing) {
                            addMissingTablesToExistingReport(report, previousValues);
                        }
                    }
                    // ★★ 2026-08 추가(2026-09 사고 통계 특이사항표 2종 포함으로 확장):
                    // 이 기능 배포 이전에 만들어진 일보 등, 특이사항류 표의 행이
                    // 누락된 경우 보충한다 (기존에 이미 입력된 행은 건드리지 않음)
                    ensureAllDefaultRemarks(report, reportDate);
                    return DailyReportResponse.fromWithDetails(report);
                })
                .orElseGet(() -> {
                    // 자동 생성: 해당 날짜 일보 + 4개 기본 표 + 기본 셀
                    DailyReport report = DailyReport.builder()
                            .reportDate(reportDate)
                            .title(reportDate + " 세부공장일보")
                            .status("DRAFT")
                            .createdBy(userId)
                            .build();
                    // ★ 값 이어받기: 직전 일보의 DATA 셀 값을 새 표의 초기값으로 반영
                    Map<String, String> previousValues = findPreviousCellValues(reportDate);
                    createDefaultTables(report, previousValues);
                    // ★★ 2026-08 추가(2026-09 사고 통계 특이사항표 2종 포함으로 확장):
                    // 특이사항류도 셀과 동일하게 직전 일보의 사업부별 내용을 이어받아
                    // 행을 미리 만들어둔다
                    ensureAllDefaultRemarks(report, reportDate);
                    reportRepository.save(report);
                    return DailyReportResponse.fromWithDetails(report);
                });
    }

    /**
     * ★★ 2026-09 신규 — 특이사항류 표 3종(TBL_SPECIAL_NOTE, TBL_SAFETY_AMOUNT_NOTE,
     * TBL_SAFETY_TREND_NOTE) 전체에 대해 {@link #ensureDefaultRemarks}를 반복 적용한다.
     * 각 표는 독립된 tableCode+카테고리 그룹을 가지므로 서로의 행/이어받기 값에
     * 전혀 영향을 주지 않는다.
     */
    private void ensureAllDefaultRemarks(DailyReport report, LocalDate reportDate) {
        for (Map.Entry<String, SpecialNoteSpec> entry : SPECIAL_NOTE_SPECS.entrySet()) {
            String tableCode = entry.getKey();
            Map<String, String> categories = entry.getValue().categories();
            boolean missing = categories.keySet().stream()
                    .anyMatch(cat -> report.getRemarks().stream()
                            .noneMatch(r -> tableCode.equals(r.getTableCode()) && cat.equals(r.getCategory())));
            if (missing) {
                Map<String, String> previousValues = findPreviousRemarkValues(reportDate, tableCode);
                ensureDefaultRemarks(report, tableCode, categories, previousValues);
            }
        }
    }

    /**
     * 일보 생성 (표 4개 기본 구조 자동 생성)
     */
    @Transactional
    public DailyReportResponse createReport(DailyReportRequest request, Long userId) {
        // 중복 날짜 검증
        if (reportRepository.existsByReportDate(request.getReportDate())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "해당 날짜의 일보가 이미 존재합니다: " + request.getReportDate());
        }

        String title = request.getTitle() != null
                ? request.getTitle()
                : request.getReportDate() + " 세부공장일보";

        DailyReport report = DailyReport.builder()
                .reportDate(request.getReportDate())
                .title(title)
                .status("DRAFT")
                .createdBy(userId)
                .build();

        // 4개 기본 표 구조 생성 (★ 값 이어받기: 직전 일보의 DATA 셀 값을 초기값으로 반영)
        Map<String, String> previousValues = findPreviousCellValues(request.getReportDate());
        createDefaultTables(report, previousValues);

        // ★★ 2026-08 추가(2026-09 사고 통계 특이사항표 2종 포함으로 확장): 특이사항류도
        // 셀과 동일하게 직전 일보의 사업부별 내용을 이어받아 행을 미리 만들어둔다
        ensureAllDefaultRemarks(report, request.getReportDate());

        reportRepository.save(report);
        return DailyReportResponse.fromWithDetails(report);
    }

    /**
     * 일보 상태 변경 (DRAFT → SUBMITTED → CONFIRMED)
     */
    @Transactional
    public DailyReportResponse updateReportStatus(Long reportId, String status, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateStatusTransition(report.getStatus(), status);
        report.updateStatus(status, userId);

        // CONFIRMED 상태 → 전체 셀 잠금
        if ("CONFIRMED".equals(status)) {
            cellRepository.lockAllCellsByReportId(reportId);
        }

        return DailyReportResponse.from(report);
    }

    /**
     * 일보 삭제 (DRAFT 상태만 삭제 가능)
     */
    @Transactional
    public void deleteReport(Long reportId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        if (!"DRAFT".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "DRAFT 상태의 일보만 삭제할 수 있습니다. 현재 상태: " + report.getStatus());
        }

        reportRepository.delete(report);
    }

    /**
     * ★★ 롤링(월 이동) 헤더/읽기전용 셀 일괄 재계산 (2026-07 추가)
     *
     * 배경: 표1~4의 월 헤더/과거 컬럼(HEADER, READONLY 타입 셀)은 일보가
     * "처음 생성되는 시점"에 {@link DefaultCellTemplate#populateDefaultCells}로
     * 한 번 계산되어 DB에 고정 저장된다. 이후 그 일보를 다시 열람할 때는
     * (셀이 이미 존재하는 한) 절대 재계산하지 않는다. 따라서 롤링 계산 로직이
     * 수정/배포된 뒤에도, 그 배포 "이전"에 미리 생성해 둔 미래 날짜 일보들은
     * 예전 로직으로 계산된 값이 DB에 그대로 남아 화면에 표시된다 — 코드 재배포
     * 만으로는 기존 레코드가 갱신되지 않는다 (신규 생성되는 일보부터만 반영).
     *
     * 이 메서드는 그 문제를 해결하기 위해, 지정한 날짜 범위의 모든 일보에 대해
     * DefaultCellTemplate을 "메모리상 임시 표"에 다시 실행해 최신 로직으로
     * HEADER/READONLY 셀 값을 계산한 뒤, 좌표(EXCEL_COORD) 기준으로 매칭되는
     * 기존 DB 셀의 cellValue만 덮어쓴다.
     *
     * ★ 안전장치: DATA 타입 셀(사용자가 실제로 입력한 값)은 이 메서드가 절대
     *   조회·수정하지 않는다 — 임시 표에서 나온 DATA 셀은 애초에 무시하고,
     *   DB 쪽 매칭 대상도 HEADER/READONLY로만 필터링한다.
     * ★ 표 구조(행/열 수, 좌표 배치)가 바뀌지 않았다는 전제하에 동작한다 —
     *   좌표가 안 맞으면(신규 좌표가 옛 표에 없거나 그 반대) 해당 셀은 그냥
     *   건너뛴다(추가/삭제하지 않고 값 갱신만 수행).
     *
     * @param startDate 갱신 대상 시작일(포함, null이면 하한 없음)
     * @param endDate   갱신 대상 종료일(포함, null이면 상한 없음)
     * @return 실제로 값이 변경된 셀 개수 (검증/로그용)
     */
    @Transactional
    public int refreshRollingHeaders(LocalDate startDate, LocalDate endDate) {
        List<DailyReport> targets = reportRepository.findByReportDateRangeOrAll(startDate, endDate);
        int updatedCount = 0;

        for (DailyReport report : targets) {
            for (DailyReportTable table : report.getTables()) {
                updatedCount += refreshTableRollingHeaders(table, report.getReportDate());
            }
        }
        return updatedCount;
    }

    /**
     * ★★ 롤링 헤더 재계산 — 표 1개 단위 (2026-08 추가, {@link #refreshRollingHeaders}의
     * 내부 로직을 추출하여 재사용 가능하게 분리).
     *
     * {@link #refreshRollingHeaders}(전체 배치, 관리자 수동 실행용)뿐 아니라
     * {@link CellService#saveCells}의 "저장 시 자동 재계산"에서도 호출된다 —
     * 실측(라이브 입력) 컬럼이 저장될 때마다, 이미 만들어져 있는 미래 일보 중
     * 이 저장으로 실제 영향받는 표들만 개별적으로 즉시 재계산하는 데 사용되어,
     * 전체 배치처럼 모든 일보×모든 표를 매번 훑지 않고도 항상 최신 상태를
     * 유지할 수 있게 한다.
     *
     * @param table 재계산 대상 표 엔티티 (영속 상태 — 변경 시 트랜잭션 커밋 시 자동 반영)
     * @param reportDate 이 표가 속한 일보의 날짜 (롤링 월 계산 기준)
     * @return 실제로 값이 변경된 셀 개수
     */
    @Transactional
    public int refreshTableRollingHeaders(DailyReportTable table, LocalDate reportDate) {
        // 최신 로직으로 헤더/읽기전용 셀을 다시 계산할 "메모리 전용" 임시 표
        // (영속화하지 않음 — DB에는 절대 저장되지 않고 값 비교용으로만 사용)
        DailyReportTable freshTable = DailyReportTable.builder()
                .tableCode(table.getTableCode())
                .tableName(table.getTableName())
                .sortOrder(table.getSortOrder())
                .rowCount(table.getRowCount())
                .colCount(table.getColCount())
                .build();
        DefaultCellTemplate.populateDefaultCells(freshTable, reportDate, historicalValueLookup(), historicalYearlyValueLookup());

        // 좌표(EXCEL_COORD) → 새로 계산된 값, HEADER/READONLY만 대상
        Map<String, String> freshValuesByCoord = new LinkedHashMap<>();
        for (DailyReportCell freshCell : freshTable.getCells()) {
            if ("HEADER".equals(freshCell.getCellType()) || "READONLY".equals(freshCell.getCellType())) {
                if (freshCell.getExcelCoord() != null) {
                    freshValuesByCoord.put(freshCell.getExcelCoord(), freshCell.getCellValue());
                }
            }
        }

        int updatedCount = 0;
        for (DailyReportCell dbCell : table.getCells()) {
            // ★ DATA 셀은 절대 건드리지 않는다 (사용자 실입력값 보호)
            if (!"HEADER".equals(dbCell.getCellType()) && !"READONLY".equals(dbCell.getCellType())) {
                continue;
            }
            String coord = dbCell.getExcelCoord();
            if (coord == null || !freshValuesByCoord.containsKey(coord)) {
                continue; // 좌표 불일치(표 구조 변경 등) — 안전하게 건너뜀
            }
            String newValue = freshValuesByCoord.get(coord);
            if (!Objects.equals(dbCell.getCellValue(), newValue)) {
                dbCell.carryOverValue(newValue); // 값만 교체, 편집자/시각 기록 안 건드림
                updatedCount++;
            }
        }
        return updatedCount;
    }

    // ─────────────────────────────────────────────
    // 표별 셀 데이터 조회
    // ─────────────────────────────────────────────

    /**
     * 특정 표의 셀 데이터 조회 (사용자별 편집 가능 여부 미포함)
     */
    public ReportTableResponse getTableData(Long reportId, String tableCode) {
        DailyReportTable table = tableRepository
                .findByDailyReport_ReportIdAndTableCode(reportId, tableCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "표를 찾을 수 없습니다. reportId=" + reportId + ", tableCode=" + tableCode));
        return ReportTableResponse.from(table);
    }

    // ─────────────────────────────────────────────
    // 특이사항 CRUD (★★ 2026-07 개편: 사업부별 5행 + CellAuth 기반 담당자 배정)
    // ─────────────────────────────────────────────

    /**
     * 특이사항 목록 조회 (사용자 미지정 — 편집 가능 여부 없이 원본만 반환)
     */
    public List<RemarkResponse> getRemarks(Long reportId) {
        return remarkRepository.findByDailyReport_ReportIdOrderBySortOrderAsc(reportId)
                .stream()
                .map(RemarkResponse::from)
                .toList();
    }

    /**
     * 특이사항 목록 조회 (사용자 기준, 기존 TBL_SPECIAL_NOTE 전용 — 하위 호환 유지)
     */
    public List<RemarkResponse> getRemarksForUser(Long reportId, Long userId) {
        return getRemarksForUser(reportId, userId, SPECIAL_NOTE_TABLE_CODE);
    }

    /**
     * ★★ 2026-09 일반화 — 특이사항류 표(tableCode)별 목록 조회 (사용자 기준) — 각 행에 대해:
     * - editable: 이 사용자가 이 행을 편집할 수 있는지 (CellAuth 기반, 셀과 동일 원칙:
     *   담당자가 배정되지 않은 행은 아무도 편집 불가)
     * - ownerNames: 이 행의 담당자 이름 (쉼표 구분)
     * - savedByName: 마지막으로 저장(작성/수정)한 사람의 이름
     *
     * ★ tableCode로 CellAuth 조회 + 응답 필터링을 모두 스코프하므로, 사고 통계
     * 특이사항표가 TBL_SPECIAL_NOTE와 같은 카테고리 코드(PAPER/TISSUE/PAD)를
     * 재사용해도 담당자 배정/저장 데이터가 서로 섞이지 않는다.
     */
    public List<RemarkResponse> getRemarksForUser(Long reportId, Long userId, String tableCode) {
        Map<String, String> categories = specialNoteSpec(tableCode).categories();

        List<DailyReportRemark> remarks =
                remarkRepository.findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(reportId, tableCode);

        // 이 표(tableCode)의 활성 CellAuth 전체 조회 → 카테고리코드별 담당자 매핑
        List<CellAuth> auths =
                cellAuthRepository.findByTableCodeAndIsActiveTrue(tableCode);
        Map<String, List<Long>> categoryToUserIds = new LinkedHashMap<>();
        for (CellAuth auth : auths) {
            for (String coord : auth.getCellCoordList()) {
                String normalized = coord == null ? null : coord.trim().toUpperCase();
                if (normalized == null || normalized.isEmpty()) continue;
                categoryToUserIds.computeIfAbsent(normalized, k -> new ArrayList<>()).add(auth.getUserId());
            }
        }

        // 등장하는 모든 userId(담당자 + 작성자 + 수정자)의 이름을 일괄 조회
        Set<Long> allUserIds = new LinkedHashSet<>();
        categoryToUserIds.values().forEach(allUserIds::addAll);
        for (DailyReportRemark r : remarks) {
            if (r.getCreatedBy() != null) allUserIds.add(r.getCreatedBy());
            if (r.getUpdatedBy() != null) allUserIds.add(r.getUpdatedBy());
        }
        Map<Long, String> userNameMap = resolveUserNames(allUserIds);

        Map<String, DailyReportRemark> byCategory = remarks.stream()
                .collect(Collectors.toMap(DailyReportRemark::getCategory, r -> r,
                        (a, b) -> a, LinkedHashMap::new));

        List<RemarkResponse> result = new ArrayList<>();
        for (Map.Entry<String, String> catEntry : categories.entrySet()) {
            String category = catEntry.getKey();
            List<Long> ownerIds = categoryToUserIds.getOrDefault(category, List.of());
            String ownerNames = ownerIds.stream()
                    .map(userNameMap::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            boolean editable = userId != null && ownerIds.contains(userId);

            DailyReportRemark existing = byCategory.get(category);
            RemarkResponse.RemarkResponseBuilder builder = RemarkResponse.builder()
                    .tableCode(tableCode)
                    .category(category)
                    .editable(editable)
                    .ownerNames(ownerNames.isBlank() ? null : ownerNames);

            if (existing != null) {
                Long lastEditorId = existing.getUpdatedBy() != null ? existing.getUpdatedBy() : existing.getCreatedBy();
                LocalDateTime savedAt = existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt();
                builder.remarkId(existing.getRemarkId())
                        .content(existing.getContent())
                        .sortOrder(existing.getSortOrder())
                        .createdBy(existing.getCreatedBy())
                        .updatedBy(existing.getUpdatedBy())
                        .createdAt(existing.getCreatedAt())
                        .updatedAt(existing.getUpdatedAt())
                        .savedByName(userNameMap.get(lastEditorId))
                        .savedAt(savedAt);
            } else {
                builder.content("");
            }
            result.add(builder.build());
        }

        return result;
    }

    /**
     * 특이사항 추가 (사업부 1행 신규 작성)
     * - CellAuth 기반 권한 검증: 이 사용자가 이 표(tableCode)의 이 카테고리(category)에
     *   배정된 담당자여야 한다 (셀과 동일 원칙 — 담당자 미배정 행은 아무도 편집 불가).
     * - ★★ 2026-08: 이 기능 배포 이후에는 일보 생성 시 각 특이사항류 표의 행이 항상
     *   미리 만들어져 있으므로(ensureAllDefaultRemarks), 이 메서드는 그 이전에
     *   만들어진 레거시 일보에 행이 누락된 경우에만 실제로 호출된다. 정상적으로
     *   새 값이 저장되었으므로 값 전파도 함께 수행한다.
     * - ★★ 2026-09: request.tableCode가 비어 있으면(기존 프론트 호환) TBL_SPECIAL_NOTE로 간주한다.
     */
    @Transactional
    public RemarkResponse addRemark(Long reportId, RemarkRequest request, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        String tableCode = request.getTableCode() != null && !request.getTableCode().isBlank()
                ? request.getTableCode() : SPECIAL_NOTE_TABLE_CODE;

        validateReportEditable(report);
        validateRemarkEditableDate(report, tableCode);
        validateSpecialNoteCategory(tableCode, request.getCategory());
        validateRemarkOwnership(tableCode, request.getCategory(), userId);
        validateSpecialNoteLimits(reportId, tableCode, request.getCategory(), request.getContent());

        int sortOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : (int) remarkRepository.findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(reportId, tableCode).size() + 1;

        DailyReportRemark remark = DailyReportRemark.builder()
                .tableCode(tableCode)
                .category(request.getCategory())
                .content(request.getContent())
                .sortOrder(sortOrder)
                .createdBy(userId)
                .build();

        report.addRemark(remark);
        remarkRepository.save(remark);

        // ★★ 값 전파: 이 저장으로 미래에 이미 만들어져 있는 일보의 이어받기
        // 특이사항 값도 최신화
        propagateRemarkForward(report.getReportDate(), tableCode, request.getCategory(), request.getContent());

        return RemarkResponse.from(remark);
    }

    /**
     * 특이사항 수정
     * - updatedBy를 기록하여 "누가 마지막으로 저장했는지" 추적한다.
     * - createdBy가 null(이어받기 상태, 아직 아무도 직접 입력한 적 없음)이면
     *   이번이 최초로 사람이 손대는 시점이므로 작성자로도 기록한다
     *   ({@link DailyReportRemark#updateContent}).
     *
     * ★★ 값 전파 안전장치(2026-08): 프론트가 "저장" 클릭 시 편집 가능한 특이사항
     * 행을 전부 다시 전송하므로(변경 여부와 무관), 실제로 내용/카테고리가 바뀌지
     * 않았다면 여기서 그대로 종료한다 — 그렇지 않으면 사용자가 건드리지 않은
     * (이어받기 상태인) 행까지 매 저장마다 "touched"로 바뀌어 값 전파가
     * 무력화된다(셀은 dirtyCoords로 변경된 셀만 전송하므로 이 문제가 없지만,
     * 특이사항은 매번 전체를 재전송하는 구조라 서버에서 별도로 방어해야 한다).
     */
    @Transactional
    public RemarkResponse updateRemark(Long remarkId, RemarkRequest request, Long userId) {
        DailyReportRemark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new EntityNotFoundException("특이사항을 찾을 수 없습니다. ID: " + remarkId));

        String tableCode = remark.getTableCode();

        validateReportEditable(remark.getDailyReport());
        validateRemarkEditableDate(remark.getDailyReport(), tableCode);
        validateRemarkOwnership(tableCode, remark.getCategory(), userId);

        boolean unchanged = Objects.equals(remark.getContent(), request.getContent())
                && Objects.equals(remark.getCategory(), request.getCategory());
        if (unchanged) {
            return RemarkResponse.from(remark);
        }

        validateSpecialNoteLimits(remark.getDailyReport().getReportId(), remark.getRemarkId(),
                tableCode, remark.getCategory(), request.getContent());
        remark.updateContent(request.getContent(), request.getCategory(), userId);

        // ★★ 값 전파: 이 저장으로 미래에 이미 만들어져 있는 일보의 이어받기
        // 특이사항 값도 최신화
        propagateRemarkForward(remark.getDailyReport().getReportDate(), tableCode, remark.getCategory(), request.getContent());

        return RemarkResponse.from(remark);
    }

    /**
     * 특이사항 삭제
     */
    @Transactional
    public void deleteRemark(Long remarkId, Long userId) {
        DailyReportRemark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new EntityNotFoundException("특이사항을 찾을 수 없습니다. ID: " + remarkId));

        validateReportEditable(remark.getDailyReport());
        validateRemarkEditableDate(remark.getDailyReport(), remark.getTableCode());
        validateRemarkOwnership(remark.getTableCode(), remark.getCategory(), userId);
        remarkRepository.delete(remark);
    }

    /**
     * ★★ 2026-09 일반화: tableCode에 등록된 카테고리 목록 중 하나인지 검증
     */
    private void validateSpecialNoteCategory(String tableCode, String category) {
        Map<String, String> categories = specialNoteSpec(tableCode).categories();
        if (category == null || !categories.containsKey(category)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "올바르지 않은 구분입니다: " + category);
        }
    }

    /**
     * ★★ 특이사항 분량 제한 검증 (신규 작성) — tableCode 스펙(줄바꿈/글자수 한도)을
     * 사용해 같은 표 안의 행 전체(자신 포함) 합계가 한도를 넘지 않는지 확인한다.
     * 다른 표(tableCode가 다른)의 내용은 전혀 합산 대상이 아니다.
     */
    private void validateSpecialNoteLimits(Long reportId, String tableCode, String category, String content) {
        validateSpecialNoteLimits(reportId, null, tableCode, category, content);
    }

    /**
     * ★★ 특이사항 분량 제한 검증 (수정) — excludeRemarkId: 수정 대상 자신의 기존
     * 레코드는 합산에서 제외하고 새 content로 교체하여 계산한다.
     */
    private void validateSpecialNoteLimits(Long reportId, Long excludeRemarkId, String tableCode,
                                            String category, String content) {
        SpecialNoteSpec spec = specialNoteSpec(tableCode);
        String safeContent = content == null ? "" : content;

        // 1) 한 줄(개행 기준) 길이 제한 — 자신이 입력한 내용만 검사
        for (String line : safeContent.split("\n", -1)) {
            if (line.length() > spec.maxLineLength()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        String.format("한 줄에 입력 가능한 최대 글자수는 %d자입니다. %d/%d",
                                spec.maxLineLength(), line.length(), spec.maxLineLength()));
            }
        }

        // 2) 같은 표(tableCode)의 행 전체 합산 — 다른 행의 기존 저장 내용 + 이번에 저장하려는 내용
        List<DailyReportRemark> remarks =
                remarkRepository.findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(reportId, tableCode);

        int totalNewlines = 0;
        int totalChars = 0;
        for (DailyReportRemark r : remarks) {
            if (excludeRemarkId != null && excludeRemarkId.equals(r.getRemarkId())) continue;
            String cat = r.getCategory();
            if (category != null && category.equals(cat)) {
                // 같은 카테고리(자기 자신 행) 기존 내용은 새 content로 대체되므로 건너뛴다
                continue;
            }
            String c = r.getContent() == null ? "" : r.getContent();
            totalNewlines += countNewlines(c);
            totalChars += c.length();
        }
        // 자기 자신(이번에 저장할 content)을 합산
        totalNewlines += countNewlines(safeContent);
        totalChars += safeContent.length();

        if (totalNewlines > spec.maxTotalNewlines()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    String.format("줄바꿈 %d회를 모두 사용했습니다.", spec.maxTotalNewlines()));
        }
        if (totalChars > spec.maxTotalChars()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    String.format("글자수 %d자 이상 기재할 수 없습니다.", spec.maxTotalChars()));
        }
    }

    private int countNewlines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * ★★ 셀과 동일한 원칙: 이 표(tableCode)의 이 카테고리(category) 행에 대해
     * 활성 CellAuth로 배정된 담당자만 편집 가능. 담당자가 아예 배정되지 않은
     * 행은 아무도 편집할 수 없다.
     */
    private void validateRemarkOwnership(String tableCode, String category, Long userId) {
        List<CellAuth> auths = cellAuthRepository
                .findAllByUserIdAndTableCodeAndIsActiveTrue(userId, tableCode);
        boolean isOwner = auths.stream().anyMatch(auth -> auth.coversCoord(category));
        if (!isOwner) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "해당 특이사항 항목에 대한 편집 권한이 없습니다: " + category);
        }
    }

    /**
     * userId 목록에 해당하는 core_user.USER_NAME(없으면 LOGIN_ID)을 일괄 조회
     * - Architecture Rule #4: core 모듈 Entity를 직접 import하지 않고 native query 사용
     * - ★ package-private(2026-08): 같은 패키지의 CellService가 셀 hover
     *   "최종 저장자" 표시 기능에서 재사용하기 위해 접근 범위를 열어둠
     *   (private → default). 외부 모듈에는 노출되지 않는다.
     */
    @SuppressWarnings("unchecked")
    Map<Long, String> resolveUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            // ★★ 버그 수정(2026-09) — Map.of()(불변 맵)는 get(null)을 호출하면
            // (내부적으로 Objects.requireNonNull(key)를 먼저 실행하기 때문에)
            // 무조건 NullPointerException을 던진다. 담당자가 아직 배정되지 않고
            // 아무도 저장한 적 없는 이어받기 상태의 특이사항 행(createdBy/updatedBy
            // 모두 null)에 대해 getRemarksForUser()가 userNameMap.get(lastEditorId)
            // (lastEditorId가 null일 수 있음)를 호출하면서 이 문제가 발생했다
            // (TBL_SAFETY_AMOUNT_NOTE/TBL_SAFETY_TREND_NOTE처럼 신규 생성되어
            // 아직 아무도 손대지 않은 표에서 재현됨). HashMap은 null 키 조회 시
            // 안전하게 null을 반환하므로 이를 대신 사용한다.
            return new HashMap<>();
        }
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT user_id, COALESCE(NULLIF(TRIM(user_name), ''), login_id) AS user_name " +
                        "FROM core_user WHERE user_id IN (:ids)")
                .setParameter("ids", userIds)
                .getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> (String) row[1],
                (a, b) -> a
        ));
    }

    // ─────────────────────────────────────────────
    // 이미지 관리
    // ─────────────────────────────────────────────

    /**
     * 이미지 목록 조회
     */
    public List<ImageResponse> getImages(Long reportId) {
        return imageRepository.findByDailyReport_ReportIdOrderBySortOrderAsc(reportId)
                .stream()
                .map(ImageResponse::from)
                .toList();
    }

    /**
     * 일보에 등록된 이미지 개수 (업로드 개수 제한 검증용)
     */
    public long countImages(Long reportId) {
        return imageRepository.countByDailyReport_ReportId(reportId);
    }

    /**
     * ★ 다운로드 전용 — reportId + imageId가 모두 일치하는 이미지 메타 조회
     * (다른 일보의 이미지를 imageId만으로 조회하는 것을 방지)
     */
    public ImageResponse getImageForDownload(Long reportId, Long imageId) {
        DailyReportImage image = imageRepository
                .findByImageIdAndDailyReport_ReportId(imageId, reportId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));
        return ImageResponse.from(image);
    }

    /** 일보당 최대 첨부 이미지 개수 (프론트엔드 MAX_UPLOAD_IMAGES와 동일하게 서버에서도 강제) */
    private static final int MAX_IMAGES_PER_REPORT = 8;

    /**
     * 이미지 메타 저장
     * - ★ 실제 파일 바이너리는 ImageController가 로컬 디스크에 먼저 저장한 뒤,
     *   그 결과(storedPath 등)를 파라미터로 넘겨 이 메서드가 DB 메타 레코드만 생성한다.
     */
    @Transactional
    public ImageResponse addImage(Long reportId, String originalName, String storedPath,
                                  Long fileSize, String contentType, String description,
                                  String tableCode, Long userId) {
        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        validateReportEditable(report);
        validateImageEditableDate(report);

        long currentCount = imageRepository.countByDailyReport_ReportId(reportId);
        if (currentCount >= MAX_IMAGES_PER_REPORT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    String.format("이미지는 일보당 최대 %d장까지 등록할 수 있습니다.", MAX_IMAGES_PER_REPORT));
        }

        int sortOrder = (int) currentCount + 1;

        DailyReportImage image = DailyReportImage.builder()
                .originalName(originalName)
                .storedPath(storedPath)
                .fileSize(fileSize)
                .contentType(contentType)
                .description(description)
                .tableCode(tableCode)
                .sortOrder(sortOrder)
                .uploadedBy(userId)
                .build();

        report.addImage(image);
        imageRepository.save(image);
        return ImageResponse.from(image);
    }

    /**
     * 이미지 설명 수정
     */
    @Transactional
    public ImageResponse updateImageDescription(Long imageId, String description) {
        DailyReportImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));

        validateReportEditable(image.getDailyReport());
        validateImageEditableDate(image.getDailyReport());
        image.updateDescription(description);
        return ImageResponse.from(image);
    }

    /**
     * 이미지 삭제
     * - DB 메타 레코드를 삭제하고, 실제 파일도 함께 지울 수 있도록 storedPath를 반환한다.
     *   (물리 파일 삭제는 ImageController가 이 값을 받아 처리 — 파일시스템 접근은
     *   컨트롤러/전용 헬퍼에서만 하도록 서비스 계층 책임을 분리)
     */
    @Transactional
    public String deleteImage(Long imageId) {
        DailyReportImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("이미지를 찾을 수 없습니다. ID: " + imageId));

        validateReportEditable(image.getDailyReport());
        validateImageEditableDate(image.getDailyReport());
        String storedPath = image.getStoredPath();
        imageRepository.delete(image);
        return storedPath;
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼 메서드
    // ─────────────────────────────────────────────

    /**
     * 8개 기본 표 정의 (표코드/표명/행수/열수) — sortOrder는 배열 순서(index+1)로 결정된다.
     * HTML 원본 기준:
     *   1. 주요 생산 지표 현황 — 10행 15열
     *   2. 제지 재공품 및 야적현황 — 10행 13열
     *   3. 에너지 원단위 — 8행 6열
     *   4. 보일러 운영 현황 — 7행 8열
     * PPT(일일현황보고) 기준 — "세부공장일보 사고 통계" 메뉴 전용 4개 표:
     *   5. 안전사고 발생건수 — 10행 18열
     *   6. 안전사고 손실금액 — 10행 18열
     *   7. 안전사고 연도별 추이 — 14행 12열
     *   8. 안전사고 월별 추이 — 14행 19열
     */
    private static final String[][] TABLE_DEFINITIONS = {
            {"TBL_PRODUCTION_INDEX",       "주요 생산 지표 현황",       "10", "15"},
            {"TBL_INVENTORY",              "제지 재공품 및 야적현황",   "10", "13"},
            {"TBL_ENERGY",                 "에너지 원단위",             "8",  "6"},
            {"TBL_BOILER",                 "보일러 운영 현황",          "7",  "8"},
            {"TBL_SAFETY_INCIDENT_COUNT",  "안전사고 발생건수",         "10", "18"},
            {"TBL_SAFETY_INCIDENT_AMOUNT", "안전사고 손실금액",         "10", "18"},
            {"TBL_SAFETY_YEARLY_TREND",    "안전사고 연도별 추이",      "14", "12"},
            {"TBL_SAFETY_MONTHLY_TREND",   "안전사고 월별 추이",        "14", "19"},
    };

    /** 8개 기본 표 구조 생성 (신규 일보 자동 생성 시) */
    private void createDefaultTables(DailyReport report, Map<String, String> previousValues) {
        for (int i = 0; i < TABLE_DEFINITIONS.length; i++) {
            DailyReportTable table = buildDefaultTable(TABLE_DEFINITIONS[i], i + 1);
            report.addTable(table);
            populateAndSyncNewTable(table, report.getReportDate(), previousValues);
        }
    }

    /**
     * ★★ 기존 일보(이 기능 배포 이전에 이미 생성되어 있던 report row)에 표 자체가
     *   통째로 누락되어 있으면(예: 신규 표4개 추가 배포 이전에 만들어진 과거/미래 일보)
     *   보충 생성한다. {@code cellsMissing}(표는 있는데 셀만 빈 경우) 케이스와 달리
     *   이 케이스는 report.getTables()에 해당 tableCode 자체가 없는 경우를 다룬다.
     *
     * @return 실제로 표가 추가되었으면 true (호출측에서 저장 필요 여부 판단용)
     */
    private boolean addMissingTablesToExistingReport(DailyReport report, Map<String, String> previousValues) {
        Set<String> existingCodes = report.getTables().stream()
                .map(DailyReportTable::getTableCode)
                .collect(Collectors.toSet());
        boolean added = false;
        for (int i = 0; i < TABLE_DEFINITIONS.length; i++) {
            String code = TABLE_DEFINITIONS[i][0];
            if (existingCodes.contains(code)) {
                continue;
            }
            DailyReportTable table = buildDefaultTable(TABLE_DEFINITIONS[i], i + 1);
            report.addTable(table);
            populateAndSyncNewTable(table, report.getReportDate(), previousValues);
            added = true;
        }
        return added;
    }

    private DailyReportTable buildDefaultTable(String[] def, int sortOrder) {
        return DailyReportTable.builder()
                .tableCode(def[0])
                .tableName(def[1])
                .sortOrder(sortOrder)
                .rowCount(Integer.parseInt(def[2]))
                .colCount(Integer.parseInt(def[3]))
                .build();
    }

    /** 표 1개에 기본 셀 생성 + 값 이어받기 + 담당자(OWNER) 동기화를 공통 수행 */
    private void populateAndSyncNewTable(DailyReportTable table, LocalDate reportDate,
                                          Map<String, String> previousValues) {
        // 기본 셀(HEADER + READONLY + DATA) 생성 — 프론트엔드 표 렌더링에 필수
        // ★ 담당자(OWNER_IDS/OWNER_NAMES)는 하드코딩하지 않음 — 항상 NULL로 시작
        // ★ 표1/표2 "진짜 값 롤링" — 과거 달의 월말 실측값을 DB에서 조회하는
        //   콜백(historicalValueLookup)을 넘긴다 (커트오프 이전 달은 템플릿
        //   내부에서 실측 조회를 시도하지 않고 하드코딩 샘플로 대체하므로
        //   이 콜백은 호출되지 않음)
        DefaultCellTemplate.populateDefaultCells(table, reportDate, historicalValueLookup(), historicalYearlyValueLookup());
        // ★ 값 이어받기: 직전 일보에 입력되어 있던 DATA 셀 값을 새 표의 초기값으로 반영
        applyCarriedOverValues(table, previousValues);
        // ★ 생성 즉시 현재 활성 CellAuth 담당자를 반영 (코드 수정/재배포 불필요)
        cellOwnershipSyncService.applyCurrentOwnersToNewTable(table);
    }

    /**
     * ★ 표1/표2 롤링 과거 컬럼의 실측(월말 대표값=누적값) 조회 콜백 생성.
     *
     * - {@link DefaultCellTemplate}이 커트오프(2026-07-22) 이후 달에 대해서만
     *   이 콜백을 호출한다 — 커트오프 이전 달은 이 메서드에 도달하지 않는다.
     * - "그 달의 월말 대표값"은 해당 연월 범위 내에서 가장 최근 날짜에 기록된
     *   실측(라이브 DATA 컬럼) 값으로 정의한다(사용자 확인: 월말값=그 달의
     *   누적/대표값). 조회 시작일은 커트오프 날짜로 하한을 두어, 월초가
     *   커트오프보다 이르더라도 커트오프 이전 데이터는 절대 사용하지 않는다.
     */
    private DefaultCellTemplate.HistoricalValueLookup historicalValueLookup() {
        return (tableCode, rowIndex, liveColIndex, targetMonth) -> {
            LocalDate monthStart = targetMonth.atDay(1);
            LocalDate monthEnd = targetMonth.atEndOfMonth();
            LocalDate rangeStart = monthStart.isBefore(DefaultCellTemplate.FEATURE_CUTOFF_DATE)
                    ? DefaultCellTemplate.FEATURE_CUTOFF_DATE
                    : monthStart;
            if (rangeStart.isAfter(monthEnd)) {
                return null; // 이론상 도달 불가 (호출측이 커트오프 이전 달을 걸러냄)
            }
            List<DailyReportCell> candidates = cellRepository.findMonthlyValueCandidates(
                    tableCode, rowIndex, liveColIndex, rangeStart, monthEnd);
            return candidates.isEmpty() ? null : candidates.get(0).getCellValue();
        };
    }

    /**
     * ★ 표7(안전사고 연도별 추이) 롤링 과거 컬럼의 실측(연말 대표값=연간 누적값)
     *   조회 콜백 생성.
     *
     * - 표5/6과 마찬가지로 신규 기능이라 FEATURE_CUTOFF_DATE 커트오프를 두지 않고
     *   항상 조회를 시도한다.
     * - "그 연도의 대표값"은 해당 연도(1/1~12/31) 범위 내에서 가장 최근 날짜에
     *   기록된 실측(라이브 DATA 컬럼) 값으로 정의한다 (표1/표2의 월말 대표값과
     *   동일한 원리를 연 단위로 적용).
     */
    private DefaultCellTemplate.HistoricalYearlyValueLookup historicalYearlyValueLookup() {
        return (tableCode, rowIndex, liveColIndex, targetYear) -> {
            List<DailyReportCell> candidates = cellRepository.findYearlyValueCandidates(
                    tableCode, rowIndex, liveColIndex, targetYear);
            return candidates.isEmpty() ? null : candidates.get(0).getCellValue();
        };
    }

    /**
     * ★ 값 이어받기(carry-over) — 주어진 날짜 이전(과거)의 가장 최근 일보에서
     *   모든 표의 DATA 셀 값을 (tableCode + excelCoord) 키로 모아 반환한다.
     *
     * - HEADER/READONLY 셀은 대상이 아니며, DefaultCellTemplate이 자체적으로
     *   고정값을 채우므로 여기서는 조회조차 하지 않는다 (findEditableCellsByTableId는
     *   cellType='DATA' 조건이 있어 자동으로 제외됨 — 단, 이 메서드는 표 단위가
     *   아니라 일보 전체 단위 조회이므로 findDataCellsByTableCode가 아닌
     *   report.getTables() 순회로 직접 필터링한다).
     * - 빈 값("")이나 null인 셀은 이어받을 필요가 없으므로 맵에서 제외한다.
     *
     * @return key = "tableCode:excelCoord", value = 직전 일보에 입력된 값
     */
    private Map<String, String> findPreviousCellValues(LocalDate reportDate) {
        Optional<DailyReport> previous = reportRepository
                .findTopByReportDateLessThanOrderByReportDateDesc(reportDate);
        if (previous.isEmpty()) {
            return Map.of();
        }

        return previous.get().getTables().stream()
                .flatMap(table -> table.getCells().stream()
                        .filter(cell -> "DATA".equals(cell.getCellType()))
                        .filter(cell -> cell.getCellValue() != null && !cell.getCellValue().isBlank())
                        .filter(cell -> cell.getExcelCoord() != null)
                        .map(cell -> Map.entry(
                                carryOverKey(table.getTableCode(), cell.getExcelCoord()),
                                cell.getCellValue())))
                // 동일 키가 중복될 경우(이론상 발생하지 않아야 하나 방어적으로) 나중 값 우선
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (existing, replacement) -> replacement));
    }

    /**
     * ★ 값 이어받기 — 새로 생성된 표의 DATA 셀 중, previousValues 맵에 대응하는
     *   값이 있는 셀에 한해 초기값을 채워 넣는다.
     *
     * - DefaultCellTemplate이 생성한 직후의 셀은 항상 cellValue=""(빈 값)이므로,
     *   "이미 값이 있는 셀을 덮어쓸 위험"은 이 호출 시점에는 없다. 그래도 향후
     *   호출 순서가 바뀔 가능성에 대비해 방어적으로 빈 값인 셀만 채운다.
     * - HEADER/READONLY 셀은 cellType이 DATA가 아니므로 자동으로 건너뛴다.
     * - 수정자(LAST_EDITOR_ID)/수정일시(LAST_EDITED_AT)는 절대 건드리지 않는다
     *   (carryOverValue()가 값만 변경) — 오늘 아직 누구도 입력하지 않았다는
     *   사실은 그대로 유지된다.
     */
    private void applyCarriedOverValues(DailyReportTable table, Map<String, String> previousValues) {
        if (previousValues.isEmpty()) {
            return;
        }
        for (DailyReportCell cell : table.getCells()) {
            if (!"DATA".equals(cell.getCellType())) {
                continue;
            }
            if (cell.getCellValue() != null && !cell.getCellValue().isBlank()) {
                continue; // 이미 값이 있으면 덮어쓰지 않음
            }
            String key = carryOverKey(table.getTableCode(), cell.getExcelCoord());
            String previousValue = previousValues.get(key);
            if (previousValue != null) {
                cell.carryOverValue(previousValue);
            }
        }
    }

    private String carryOverKey(String tableCode, String excelCoord) {
        return tableCode + ":" + excelCoord;
    }

    // ─────────────────────────────────────────────
    // 특이사항 값 이어받기 / 값 전파 (2026-08 추가, 셀과 동일한 2단계 원리)
    // ─────────────────────────────────────────────

    /**
     * ★ 값 이어받기(carry-over) — 주어진 날짜 이전(과거)의 가장 최근 일보에서
     *   사업부(category)별 특이사항 내용을 모아 반환한다. {@link #findPreviousCellValues}와
     *   동일한 원리이며, 셀과 마찬가지로 "누가 입력했는지(사람/이어받기)"와 무관하게
     *   비어있지 않은 내용은 모두 이어받기 후보가 된다.
     *
     * ★★ 2026-09: tableCode로 스코프 — 사고 통계 특이사항표(TBL_SAFETY_AMOUNT_NOTE 등)가
     * TBL_SPECIAL_NOTE와 카테고리 코드(PAPER/TISSUE/PAD)를 재사용하므로, tableCode
     * 없이 조회하면 서로 다른 표의 값이 뒤섞인다.
     *
     * @return key = 사업부/카테고리 코드, value = 직전 일보에 입력된 내용
     */
    private Map<String, String> findPreviousRemarkValues(LocalDate reportDate, String tableCode) {
        Optional<DailyReport> previous = reportRepository
                .findTopByReportDateLessThanOrderByReportDateDesc(reportDate);
        if (previous.isEmpty()) {
            return Map.of();
        }
        return remarkRepository.findByDailyReport_ReportIdAndTableCodeOrderBySortOrderAsc(
                        previous.get().getReportId(), tableCode)
                .stream()
                .filter(r -> r.getContent() != null && !r.getContent().isBlank())
                .collect(Collectors.toMap(DailyReportRemark::getCategory, DailyReportRemark::getContent,
                        (existing, replacement) -> replacement));
    }

    /**
     * ★ 값 이어받기 — 셀의 {@link #createDefaultTables}/{@link #applyCarriedOverValues}와
     *   동일한 원리를 특이사항 5개 사업부 행에 적용한다. 이미 존재하는 카테고리 행은
     *   건드리지 않고(기존 입력 보존), 아직 없는 카테고리 행만 새로 만들어 직전 일보의
     *   내용을 초기값으로 반영한다. CREATED_BY는 null로 두어 "이어받기 상태, 아직 사람이
     *   직접 입력한 적 없음"을 표시한다(셀의 LAST_EDITOR_ID null과 동일한 의미).
     *
     * - 신규 일보 생성 시(모든 카테고리 누락) 5개 행이 전부 새로 만들어진다.
     * - 이 기능 배포 이전에 만들어진 기존 일보를 다시 열었을 때는 누락된 카테고리만
     *   보충한다(이미 사람이 입력해 둔 다른 카테고리 행은 그대로 유지).
     */
    private void ensureDefaultRemarks(DailyReport report, String tableCode, Map<String, String> categories,
                                       Map<String, String> previousValues) {
        Set<String> existingCategories = report.getRemarks().stream()
                .filter(r -> tableCode.equals(r.getTableCode()))
                .map(DailyReportRemark::getCategory)
                .collect(Collectors.toSet());
        int sortOrder = report.getRemarks().size();
        for (String category : categories.keySet()) {
            if (existingCategories.contains(category)) {
                continue;
            }
            sortOrder++;
            DailyReportRemark remark = DailyReportRemark.builder()
                    .tableCode(tableCode)
                    .category(category)
                    .content(previousValues.getOrDefault(category, ""))
                    .sortOrder(sortOrder)
                    .createdBy(null) // ★ 이어받기 상태 — 아직 사람이 직접 입력한 적 없음
                    .build();
            report.addRemark(remark);
        }
    }

    /**
     * ★★ 2026-09: tableCode별 SpecialNoteSpec.categories() 정의 순서 기준 카테고리
     * 코드의 고정 정렬 순서(1부터 시작). tableCode마다 카테고리 집합이 다르므로
     * (예: TBL_SAFETY_TREND_NOTE는 ALL 하나뿐) 반드시 tableCode를 받아야 한다.
     */
    private int sortOrderOfCategory(String tableCode, String category) {
        Map<String, String> categories = specialNoteSpec(tableCode).categories();
        int i = 1;
        for (String c : categories.keySet()) {
            if (c.equals(category)) {
                return i;
            }
            i++;
        }
        return categories.size() + 1; // 이론상 도달 불가 (category는 항상 유효한 값)
    }

    /**
     * ★★ 값 전파(forward propagation, 2026-08 추가) — {@link CellService#propagateValueForward}와
     * 완전히 동일한 원리를 특이사항에 적용한다. 특이사항을 저장할 때마다 그 날짜
     * "다음"에 이미 존재하는 일보들을 날짜 순서대로 순회하며, 동일 사업부(category)
     * 행이 아직 사람이 직접 입력한 적 없는(CREATED_BY가 null인, 즉 이어받기 상태
     * 그대로인) 상태라면 방금 저장한 내용으로 갱신하고 계속 다음 날짜로 전파한다.
     *
     * 어느 미래 일보에서든 그 행에 사람이 이미 직접 내용을 입력해 둔 경우
     * (CREATED_BY != null)라면 — 예: 특정 날짜에 특이사항을 미리 입력해 둔 경우 —
     * 그 값은 의도적인 사전 입력/오버라이드이므로 그 시점에서 전파를 멈추고 절대
     * 덮어쓰지 않는다.
     *
     * ※ 이 기능 배포 이전에 만들어진 미래 일보는 해당 사업부 행 자체가 없을 수
     *   있으므로, 없으면 이어받기 상태의 새 행을 만들어 전파를 계속한다.
     * ※ {@link DailyReportRemark#carryOverContent}를 사용하므로 CREATED_BY/UPDATED_BY는
     *   변경되지 않는다 — 여전히 "이어받은 값일 뿐 아직 아무도 직접 입력하지 않았다"는
     *   상태가 그대로 유지된다.
     * ※ 무한 루프/과도한 조회 방지를 위해 최대 366일(약 1년)까지만 전파한다.
     */
    private void propagateRemarkForward(LocalDate fromDate, String tableCode, String category, String newContent) {
        LocalDate cursor = fromDate;
        for (int hop = 0; hop < 366; hop++) {
            DailyReport nextReport = reportRepository
                    .findTopByReportDateGreaterThanOrderByReportDateAsc(cursor)
                    .orElse(null);
            if (nextReport == null) {
                break; // 더 이상 미래에 생성된 일보가 없음
            }
            cursor = nextReport.getReportDate();

            DailyReportRemark nextRemark = remarkRepository
                    .findByDailyReport_ReportIdAndTableCodeAndCategory(nextReport.getReportId(), tableCode, category)
                    .orElse(null);

            if (nextRemark == null) {
                // 이 기능 배포 이전에 만들어진 미래 일보 등, 해당 사업부 행이 아직 없는
                // 경우 — 이어받기 상태의 새 행을 만들어 전파를 계속한다.
                DailyReportRemark created = DailyReportRemark.builder()
                        .tableCode(tableCode)
                        .category(category)
                        .content(newContent)
                        .sortOrder(sortOrderOfCategory(tableCode, category))
                        .createdBy(null)
                        .build();
                nextReport.addRemark(created);
                remarkRepository.save(created);
                continue; // 이 행도 여전히 "이어받기 상태" — 계속 다음 날짜로 전파
            }

            if (nextRemark.getCreatedBy() != null) {
                break; // 이미 사람이 직접 입력해 둔 값 — 의도적 오버라이드이므로 전파 중단
            }

            if (!Objects.equals(nextRemark.getContent(), newContent)) {
                nextRemark.carryOverContent(newContent);
            }
            // 이 행은 여전히 "이어받기 상태" — 계속 다음 날짜로 전파
        }
    }

    /**
     * 상태 전환 유효성 검증
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        boolean valid = switch (currentStatus) {
            case "DRAFT" -> "SUBMITTED".equals(newStatus);
            case "SUBMITTED" -> "CONFIRMED".equals(newStatus) || "DRAFT".equals(newStatus);
            case "CONFIRMED" -> false; // 확정 후 변경 불가
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "잘못된 상태 전환입니다: " + currentStatus + " → " + newStatus);
        }
    }

    /**
     * 일보가 편집 가능 상태인지 검증 (CONFIRMED이면 수정 불가)
     */
    private void validateReportEditable(DailyReport report) {
        if ("CONFIRMED".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "확정된 일보는 수정할 수 없습니다.");
        }
    }

    /**
     * ★★ 이미지 첨부(업로드/삭제/설명수정)는 "어제 이후(어제/오늘/미래 전체)" 날짜의
     * 일보에서만 허용한다 — 셀 편집 가능 기간(CellService.isCellEditableForUser)과
     * 동일한 기준(2026-08: 미래 전역 편집 허용으로 확장). 그 이전(어제보다 이전)의
     * 과거 일보의 이미지는 화면에는 계속 표시되지만(조회+다운로드는 항상 가능)
     * 추가/삭제/설명수정은 서버에서 거부한다.
     */
    private void validateImageEditableDate(DailyReport report) {
        if (!isEditableReportDate(report.getReportDate())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "이미지는 어제 이후(어제/오늘/미래) 날짜의 일보에서만 등록/삭제할 수 있습니다. 그 이전 과거 날짜는 다운로드만 가능합니다.");
        }
    }

    /**
     * ★★★★★★ 사고통계 페이지(표9/10 특이사항) 표코드 — 2026-09 "과거/현재/미래
     * 전부 수정 가능" 정책 적용 대상. TBL_SPECIAL_NOTE(표1~4 특이사항)는
     * 기존 날짜 제한 정책을 그대로 유지한다.
     */
    private static final Set<String> SAFETY_STATS_NOTE_TABLE_CODES = Set.of(
            SAFETY_AMOUNT_NOTE_TABLE_CODE,
            SAFETY_TREND_NOTE_TABLE_CODE
    );

    /**
     * ★★ 특이사항(등록/수정/삭제)도 셀·이미지와 동일하게 "어제 이후(어제/오늘/미래
     * 전체)" 날짜의 일보에서만 허용한다. 그 이전 과거 일보의 특이사항은 조회는
     * 항상 가능하지만 등록/수정/삭제는 서버에서 거부한다.
     * (하위 호환 오버로드 — TBL_SPECIAL_NOTE 등 일반 특이사항 전용, 날짜 제한 적용)
     */
    private void validateRemarkEditableDate(DailyReport report) {
        if (!isEditableReportDate(report.getReportDate())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "특이사항은 어제 이후(어제/오늘/미래) 날짜의 일보에서만 등록/수정할 수 있습니다.");
        }
    }

    /**
     * ★★★★★★ 2026-09: tableCode를 받아 사고통계 특이사항표(표9/10,
     * SAFETY_STATS_NOTE_TABLE_CODES)인 경우에는 날짜 제한을 완전히 건너뛴다
     * (과거/현재/미래 전부 수정 가능). 그 외(TBL_SPECIAL_NOTE 등)는 기존
     * 날짜 제한 정책을 그대로 적용한다.
     */
    private void validateRemarkEditableDate(DailyReport report, String tableCode) {
        if (SAFETY_STATS_NOTE_TABLE_CODES.contains(tableCode)) {
            return;
        }
        validateRemarkEditableDate(report);
    }

    /**
     * 어제 이후(어제/오늘/미래 전체) + 매월 말일 날짜인지 판정
     * (CellService.isCellEditableForUser와 동일한 기준 — 어제보다 이전의
     * 과거는 그 달의 말일이 아닌 한 차단하고, 미래는 제한하지 않는다)
     */
    private boolean isEditableReportDate(LocalDate date) {
        if (date == null) return false;
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (!date.isBefore(yesterday)) {
            return true;
        }
        return isLastDayOfMonth(date);
    }

    /**
     * ★ 매월 말일 판정 (2026-08 추가) — CellService.isLastDayOfMonth와 동일한
     * 기준으로 독립 계산한다.
     */
    private boolean isLastDayOfMonth(LocalDate date) {
        return date != null && date.getDayOfMonth() == date.lengthOfMonth();
    }

    // ─────────────────────────────────────────────
    // ★★ 게시판(공장일보/세부공장일보) 재업로드 요청 (2026-08 신규)
    // ─────────────────────────────────────────────

    /**
     * "수정" 흐름(저장 구간 05:00:00~08:09:59 이외의 모든 시간)에서 값을 저장했을 때,
     * 사용자가 선택한 게시판 구분에 따라 daily_batchjob에 재업로드 요청 행을 1건
     * INSERT한다.
     *
     * ★★ 서버 측 시간 재검증(2026-08): 프론트엔드가 저장 구간 여부를 자체 판단해
     * "저장" 버튼일 때는 이 API를 호출조차 하지 않지만, 클라이언트 시계 조작/오차로
     * 인해 저장 구간(05:00:00~08:09:59) 중에 이 API가 호출되는 것을 서버에서도
     * 한 번 더 차단한다 — 저장 구간에는 daily_batchjob을 절대 참조/적재하지 않아야
     * 하기 때문이다.
     *
     * ※ 이 메서드는 요청 등록만 담당하며, 실제 게시판 갱신은 별도 PC에서
     *   동작하는 배치 시스템이 이 테이블을 5초 주기로 폴링하여 처리한다.
     *   CREATE_YN/RESULT_VALUE 등 처리 결과 필드는 그 배치 시스템만 갱신하므로
     *   이 메서드는 항상 CREATE_YN='N'(대기 상태)으로만 INSERT한다.
     *
     * @param reportId  대상 일보 ID (BATCH_DATE 산출용)
     * @param batchType "1"(공장일보) / "2"(세부공장일보) / "3"(모두)
     * @param userId    요청자 (core_user FK)
     */
    @Transactional
    public void requestBatchJob(Long reportId, String batchType, Long userId) {
        if (isWithinSaveWindow()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "저장 시간대(05:00~08:09:59)에는 게시판 재업로드 요청을 등록할 수 없습니다.");
        }

        DailyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("일보를 찾을 수 없습니다. ID: " + reportId));

        // ★ 2026-08: BATCH_DATE는 daily_report.REPORT_DATE와 동일한 DATE 타입으로
        //   통일되어 있으므로, 문자열 변환 없이 report.getReportDate()(LocalDate)를 그대로 사용한다.
        DailyBatchJob job = DailyBatchJob.builder()
                .batchDate(report.getReportDate())
                .batchType(batchType)
                .createdBy(userId)
                .build();
        batchJobRepository.save(job);
    }

    /**
     * ★★ 현재 서버 시각(Asia/Seoul)이 "저장" 구간(05:00:00~08:09:59, 시작 포함/종료
     * 배타) 안에 있는지 여부. true면 "저장" 버튼 + 기존 흐름(daily_batchjob 미참조),
     * false면 "수정" 버튼 + daily_batchjob 재업로드 요청 흐름이 활성화된다.
     *
     * 자정을 넘나드는 새벽 시간(00:00:00~04:59:59)도 저장 구간 밖이므로 "수정"으로
     * 판정된다 — 하루를 "저장 구간"과 "그 외 나머지 전체 시간(수정 구간)" 두 구간으로
     * 나누는 것이며, "그 외 나머지"가 자정을 사이에 두고 이어져 있을 뿐이다.
     */
    private boolean isWithinSaveWindow() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return !now.isBefore(SAVE_WINDOW_START_TIME) && now.isBefore(SAVE_WINDOW_END_TIME);
    }

    /**
     * ★★ 현재 서버 시각이 "수정" 흐름(=저장 구간 밖)인지 여부. 프론트엔드가 별도로
     * 자체 시각을 판단해 버튼 라벨을 전환하지만, 참고/검증용으로 서버에도 동일 기준을
     * 노출한다.
     */
    public boolean isAfterBatchJobCutoff() {
        return !isWithinSaveWindow();
    }
}
