package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.FormTemplateCheckResponse;
import com.company.module.personalinfo.dto.FormTemplateGroupResponse;
import com.company.module.personalinfo.dto.FormTemplateResponse;
import com.company.module.personalinfo.entity.ChecklistSheetType;
import com.company.module.personalinfo.entity.FormTemplateType;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiFormTemplate;
import com.company.module.personalinfo.repository.PiFormTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보안팀 양식 관리 (위수탁 점검 체크리스트, 정보보호 및 비밀유지 확약서, 개인정보 파기 확인서).
 *
 * <ul>
 *   <li>새 버전을 올리면 형식을 검사한다. 체크리스트는 시스템 판독기로 1·2·3번 시트를 실제로 읽어 보고,
 *       읽지 못하면 등록을 막는다(협력사 작성본 자동 판정이 깨지지 않도록).</li>
 *   <li>확약서·파기 확인서는 docx·doc·hwp·hwpx·pdf 를 받는다. docx 만 계약 정보 자동 입력이 되고,
 *       나머지 형식은 원본 그대로 내려받기·메일 첨부한다.</li>
 *   <li>종류별 사용 중 버전은 1건. 등록한 양식이 없거나 기본 양식으로 되돌리면 모듈 내장 기본 양식(버전 '기본')을 쓴다.</li>
 *   <li>이전 버전은 남겨 두고(소프트 삭제만) 언제든 다시 사용 지정할 수 있다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiFormTemplateService {

    static final String N = "N";
    static final String DEFAULT_VERSION_LABEL = "기본";
    private static final long MAX_TEMPLATE_BYTES = 20L * 1024 * 1024;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    /** docx 양식 자리표시자 → 채워지는 값 설명 */
    public static final Map<String, String> PLACEHOLDERS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("{{위탁사명}}", "당사(위탁사) 이름 — 서버 설정 personalinfo.company-name");
        m.put("{{수탁업체명}}", "수탁업체명");
        m.put("{{계약명}}", "계약명 (없으면 '-')");
        m.put("{{위탁업무}}", "위탁업무 내용");
        m.put("{{계약기간}}", "원계약 기간 (예: 2026.01.01 ~ 2026.12.31)");
        m.put("{{계약종료일}}", "계약 종료일 (종료 처리했으면 그 종료일, 아니면 원계약 종료일)");
        m.put("{{재위탁업체}}", "재위탁 업체명 (재위탁이 없으면 '없음')");
        PLACEHOLDERS = java.util.Collections.unmodifiableMap(m);
    }

    /** 체크리스트 양식이 지켜야 하는 구조 (화면 안내용) */
    public static final List<String> CHECKLIST_RULES = List.of(
            "시트 이름: '1. 관리체계', '2. 개인정보처리시스템', '3. 계약변경 만료시 체크' (번호 또는 이름으로 찾음)",
            "각 시트에 '평가 항목' 머리글과 '평가' 열 (Y / N / NA 를 적는 칸)",
            "'1. 관리체계' 시트 위쪽에 '회사명', '수행(수탁)업무', '평가일', '평가자' 라벨 (오른쪽 칸에 자동 입력)",
            "'2. 개인정보처리시스템' 시트의 '보유여부' 라벨 (미 보유면 그 시트는 집계 제외)",
            "'3. 계약변경 만료시 체크' 시트의 '현황' 열 (위탁계약 종료 확인·재위탁 확인 현황 자동 입력)");

    private final PiFormTemplateRepository repository;
    private final PiFileStorage storage;
    private final ChecklistExcelParser parser;

    private volatile Map<ChecklistSheetType, Integer> defaultChecklistCounts;

    // ─────────────────────────────────────────────────────────────
    //  양식 원본 (다른 서비스가 사용)
    // ─────────────────────────────────────────────────────────────

    /** 양식 원본 파일 */
    public record Source(FormTemplateType type, byte[] data, String ext, String contentType,
                         String versionLabel, Long formTemplateId, String originalName) {

        /** 계약 정보 자동 입력이 되는 형식인지 (체크리스트 xlsx, 확약서·파기 확인서 docx) */
        public boolean autofill() {
            return "xlsx".equals(ext) || "docx".equals(ext);
        }
    }

    /** 사용 중 양식. 등록한 양식이 없거나 파일을 읽지 못하면 기본 양식 */
    public Source current(FormTemplateType type) {
        PiFormTemplate active = repository
                .findFirstByFormTypeAndActiveYnAndDeletedYnOrderByVersionNoDesc(type, "Y", N).orElse(null);
        if (active != null) {
            try {
                return sourceOf(active);
            } catch (RuntimeException e) {
                log.error("[personalinfo] 사용 중 양식 파일을 읽지 못해 기본 양식을 씁니다. type={} version={}",
                        type, active.getVersionNo(), e);
            }
        }
        return defaultSource(type);
    }

    /** 사용 중 양식 버전 표시 (예: v2, 기본) */
    public String currentVersionLabel(FormTemplateType type) {
        return repository.findFirstByFormTypeAndActiveYnAndDeletedYnOrderByVersionNoDesc(type, "Y", N)
                .map(PiFormTemplate::getVersionLabel).orElse(DEFAULT_VERSION_LABEL);
    }

    /** 특정 버전 원본 (미리보기용) */
    public Source source(Long formTemplateId) {
        return sourceOf(find(formTemplateId));
    }

    public Source defaultSource(FormTemplateType type) {
        try (InputStream in = new ClassPathResource(type.getDefaultResource()).getInputStream()) {
            String ext = PiFileStorage.extensionOf(type.getDefaultResource());
            return new Source(type, in.readAllBytes(), ext, contentTypeOf(ext), DEFAULT_VERSION_LABEL, null,
                    type.getDefaultFileBase() + "." + ext);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, type.getLabel() + " 기본 양식 파일을 찾을 수 없습니다.");
        }
    }

    private Source sourceOf(PiFormTemplate f) {
        return new Source(f.getFormType(), storage.read(f.getStoredPath()), f.getFileExt(),
                StringUtils.hasText(f.getContentType()) ? f.getContentType() : contentTypeOf(f.getFileExt()),
                f.getVersionLabel(), f.getFormTemplateId(), f.getOriginalName());
    }

    // ─────────────────────────────────────────────────────────────
    //  양식 관리 화면
    // ─────────────────────────────────────────────────────────────

    public List<FormTemplateGroupResponse> list() {
        List<FormTemplateGroupResponse> list = new ArrayList<>();
        for (FormTemplateType t : FormTemplateType.values()) {
            List<FormTemplateResponse> versions = repository.findByFormTypeAndDeletedYnOrderByVersionNoDesc(t, N)
                    .stream().map(f -> FormTemplateResponse.from(f, null)).toList();
            FormTemplateResponse active = versions.stream().filter(FormTemplateResponse::isActive).findFirst().orElse(null);
            String defaultExt = PiFileStorage.extensionOf(t.getDefaultResource());
            list.add(FormTemplateGroupResponse.builder()
                    .formType(t.getPath())
                    .code(t.name())
                    .label(t.getLabel())
                    .description(t.getDescription())
                    .extensions(t.getExtensions())
                    .placeholders(t == FormTemplateType.CHECKLIST ? Map.of() : PLACEHOLDERS)
                    .rules(t == FormTemplateType.CHECKLIST ? CHECKLIST_RULES : List.of())
                    .currentVersionLabel(active == null ? DEFAULT_VERSION_LABEL : active.getVersionLabel())
                    .currentOriginalName(active == null ? t.getDefaultFileBase() + "." + defaultExt : active.getOriginalName())
                    .currentAutofill(active == null || active.isAutofill())
                    .usingDefault(active == null)
                    .defaultFileName(t.getDefaultFileBase() + "." + defaultExt)
                    .versions(versions)
                    .build());
        }
        return list;
    }

    /** 저장하지 않고 검사만 */
    public FormTemplateCheckResponse check(String type, MultipartFile file) {
        FormTemplateType t = FormTemplateType.of(type);
        String ext = extOf(t, file);
        return inspect(t, ext, bytesOf(file));
    }

    /** 새 버전 등록 (검사 통과 시). activate=true 면 바로 사용 중으로 지정 */
    @Transactional
    public FormTemplateResponse upload(String type, MultipartFile file, String changeNote, boolean activate,
                                       Long userId, String userName) {
        FormTemplateType t = FormTemplateType.of(type);
        String ext = extOf(t, file);
        FormTemplateCheckResponse check = inspect(t, ext, bytesOf(file));   // 오류면 BusinessException (저장 안 함)

        PiFileStorage.StoredFile stored = storage.store(file, "form-template", Set.copyOf(t.getExtensions()),
                MAX_TEMPLATE_BYTES);
        int next = repository.maxVersionNo(t) + 1;
        List<String> notes = new ArrayList<>(check.getInfo());
        notes.addAll(check.getWarnings().stream().map(w -> "[경고] " + w).toList());
        PiFormTemplate saved = repository.save(PiFormTemplate.builder()
                .formType(t)
                .versionNo(next)
                .originalName(stored.originalName())
                .storedPath(stored.relativePath())
                .contentType(stored.contentType())
                .fileExt(ext)
                .fileSize(stored.size())
                .fileHash(stored.sha256())
                .changeNote(PiExcelSupport.truncate(PiContractService.trim(changeNote), 1000))
                .validationNote(PiExcelSupport.truncate(String.join("\n", notes), 2000))
                .createdBy(userId)
                .createdByName(PiExcelSupport.truncate(userName, 100))
                .build());
        if (activate) {
            activateInternal(saved, userId, userName);
        }
        log.info("[personalinfo] 양식 등록 type={} version={} activate={} by={}", t, next, activate, userId);
        return FormTemplateResponse.from(saved, check.getWarnings());
    }

    /** 사용 중 양식으로 지정 (이전 버전으로 되돌리기 포함) */
    @Transactional
    public FormTemplateResponse activate(Long formTemplateId, Long userId, String userName) {
        PiFormTemplate f = find(formTemplateId);
        activateInternal(f, userId, userName);
        log.info("[personalinfo] 양식 사용 지정 type={} version={} by={}", f.getFormType(), f.getVersionNo(), userId);
        return FormTemplateResponse.from(f, null);
    }

    /** 기본 양식(모듈 내장)으로 되돌리기 */
    @Transactional
    public void useDefault(String type, Long userId) {
        FormTemplateType t = FormTemplateType.of(type);
        repository.findByFormTypeAndActiveYn(t, "Y").forEach(f -> f.deactivate(userId));
        log.info("[personalinfo] 기본 양식으로 되돌림 type={} by={}", t, userId);
    }

    /** 버전 삭제 (소프트). 사용 중인 버전은 삭제할 수 없다 */
    @Transactional
    public void delete(Long formTemplateId, Long userId) {
        PiFormTemplate f = find(formTemplateId);
        if (f.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "사용 중인 양식은 삭제할 수 없습니다. 다른 버전이나 기본 양식을 사용 지정한 뒤 삭제하세요.");
        }
        f.delete(userId);
    }

    /** 버전 원본 다운로드 */
    public PiFileStorage.DownloadFile download(Long formTemplateId) {
        PiFormTemplate f = find(formTemplateId);
        String base = f.getFormType().getLabel() + "_" + f.getVersionLabel();
        return new PiFileStorage.DownloadFile(base + "." + f.getFileExt(), contentTypeOf(f.getFileExt()),
                storage.read(f.getStoredPath()));
    }

    /** 기본 양식 원본 다운로드 */
    public PiFileStorage.DownloadFile downloadDefault(String type) {
        Source s = defaultSource(FormTemplateType.of(type));
        return new PiFileStorage.DownloadFile(s.type().getLabel() + "_기본." + s.ext(), s.contentType(), s.data());
    }

    private void activateInternal(PiFormTemplate target, Long userId, String userName) {
        for (PiFormTemplate f : repository.findByFormTypeAndActiveYn(target.getFormType(), "Y")) {
            if (!f.getFormTemplateId().equals(target.getFormTemplateId())) {
                f.deactivate(userId);
            }
        }
        repository.flush();
        target.activate(userId, PiExcelSupport.truncate(userName, 100));
    }

    private PiFormTemplate find(Long id) {
        return repository.findByFormTemplateIdAndDeletedYn(id, N)
                .orElseThrow(() -> new EntityNotFoundException("양식", id));
    }

    // ─────────────────────────────────────────────────────────────
    //  양식 검사
    // ─────────────────────────────────────────────────────────────

    FormTemplateCheckResponse inspect(FormTemplateType t, String ext, byte[] data) {
        return t == FormTemplateType.CHECKLIST ? inspectChecklist(data) : inspectDocument(t, ext, data);
    }

    /** 체크리스트: 시스템 판독기로 정기·만료 점검을 실제로 읽어 본다. 못 읽으면 등록 불가 */
    private FormTemplateCheckResponse inspectChecklist(byte[] data) {
        List<String> errors = new ArrayList<>();
        List<String> info = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<ChecklistSheetType, Integer> counts = new EnumMap<>(ChecklistSheetType.class);

        for (InspectionType type : List.of(InspectionType.REGULAR, InspectionType.TERMINATION)) {
            try {
                ChecklistExcelParser.ParsedChecklist p = parser.parse(new ByteArrayInputStream(data), type);
                p.items().forEach(i -> counts.merge(i.sheetType(), 1, Integer::sum));
                p.warnings().stream()
                        .filter(w -> w.contains("머리글") || w.contains("시트가 없어"))
                        .forEach(warnings::add);
            } catch (BusinessException e) {
                errors.add((type == InspectionType.REGULAR ? "정기 점검: " : "계약만료시 점검: ") + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "체크리스트 양식을 시스템이 읽을 수 없어 등록하지 않았습니다. 협력사 작성본 자동 판정에 필요한 구조를 확인하세요.\n- "
                            + String.join("\n- ", errors));
        }
        Map<ChecklistSheetType, Integer> base = defaultChecklistCounts();
        for (ChecklistSheetType s : ChecklistSheetType.values()) {
            int n = counts.getOrDefault(s, 0);
            info.add(sheetLabel(s) + " 평가 항목 " + n + "건");
            Integer d = base.get(s);
            if (d != null && d != n) {
                warnings.add(sheetLabel(s) + " 평가 항목 수가 기본 양식(" + d + "건)과 다릅니다(" + n + "건). 의도한 변경인지 확인하세요.");
            }
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet s1 = PiChecklistTemplateService.findSheet(wb, "관리체계");
            if (s1 == null) {
                warnings.add("'1. 관리체계' 시트 이름을 찾지 못해 회사명·수탁업무를 미리 채울 수 없습니다.");
            } else {
                for (String label : List.of("회사명", "수행(수탁)업무", "평가일", "평가자")) {
                    if (!hasLabel(s1, label)) {
                        warnings.add("'1. 관리체계' 시트에 '" + label + "' 라벨이 없어 그 칸은 미리 채우거나 읽지 못합니다.");
                    }
                }
            }
            Sheet s3 = PiChecklistTemplateService.findSheet(wb, "만료");
            if (s3 != null && !hasLabel(s3, "현황", 40)) {
                warnings.add("'3. 계약변경 만료시 체크' 시트에 '현황' 열이 없어 계약 종료·재위탁 현황을 미리 채우지 못합니다.");
            }
        } catch (IOException | RuntimeException e) {
            warnings.add("엑셀 구조를 자세히 확인하지 못했습니다: " + e.getClass().getSimpleName());
        }
        info.add("협력사가 이 양식으로 작성해 올리면 정기 점검·계약만료시 점검 모두 자동 판정됩니다.");
        return FormTemplateCheckResponse.builder().autofill(true).info(info).warnings(dedup(warnings)).build();
    }

    /** 확약서·파기 확인서: docx 는 자리표시자 확인, 그 밖의 형식은 원본 그대로 사용 안내 */
    private FormTemplateCheckResponse inspectDocument(FormTemplateType t, String ext, byte[] data) {
        List<String> info = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"docx".equals(ext)) {
            if ("pdf".equals(ext) && !(data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F')) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일이 아닙니다. 파일을 확인하세요.");
            }
            warnings.add(ext.toUpperCase() + " 양식은 계약 정보(수탁업체명 등)가 자동으로 채워지지 않습니다. "
                    + "내려받기와 만료 알림 메일 첨부 모두 올린 파일 그대로 나갑니다. 자동 입력이 필요하면 docx 로 올리세요.");
            info.add(t.getLabel() + " " + ext.toUpperCase() + " 양식 (원본 그대로 사용)");
            return FormTemplateCheckResponse.builder().autofill(false).info(info).warnings(warnings).build();
        }
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor x = new XWPFWordExtractor(doc)) {
            text = x.getText();
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "docx 파일을 열 수 없습니다. Word 에서 '다른 이름으로 저장 → Word 문서(.docx)'로 저장했는지 확인하세요.");
        }
        Set<String> found = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(text == null ? "" : text);
        while (m.find()) {
            String key = "{{" + m.group(1).replace(" ", "") + "}}";
            if (PLACEHOLDERS.containsKey(key)) {
                found.add(key);
            } else {
                unknown.add(m.group());
            }
        }
        if (found.isEmpty()) {
            warnings.add("자리표시자({{수탁업체명}} 등)가 없어 계약 정보가 채워지지 않고 양식 그대로 나갑니다.");
        } else {
            info.add("자동 입력 항목: " + String.join(", ", found));
        }
        if (!unknown.isEmpty()) {
            warnings.add("알 수 없는 자리표시자는 그대로 남습니다: " + String.join(", ", unknown)
                    + " (사용 가능: " + String.join(", ", PLACEHOLDERS.keySet()) + ")");
        }
        if (!found.contains("{{수탁업체명}}")) {
            warnings.add("{{수탁업체명}} 자리표시자가 없습니다. 협력사 이름이 들어가지 않습니다.");
        }
        return FormTemplateCheckResponse.builder().autofill(!found.isEmpty()).info(info).warnings(warnings).build();
    }

    private Map<ChecklistSheetType, Integer> defaultChecklistCounts() {
        Map<ChecklistSheetType, Integer> cached = defaultChecklistCounts;
        if (cached != null) {
            return cached;
        }
        Map<ChecklistSheetType, Integer> counts = new EnumMap<>(ChecklistSheetType.class);
        try {
            byte[] data = defaultSource(FormTemplateType.CHECKLIST).data();
            for (InspectionType type : List.of(InspectionType.REGULAR, InspectionType.TERMINATION)) {
                parser.parse(new ByteArrayInputStream(data), type).items()
                        .forEach(i -> counts.merge(i.sheetType(), 1, Integer::sum));
            }
        } catch (RuntimeException e) {
            log.warn("[personalinfo] 기본 체크리스트 항목 수를 세지 못했습니다.", e);
        }
        defaultChecklistCounts = counts;
        return counts;
    }

    private static boolean hasLabel(Sheet sheet, String label) {
        return hasLabel(sheet, label, 12);
    }

    private static boolean hasLabel(Sheet sheet, String label, int rows) {
        String target = PiExcelSupport.compact(label);
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), rows); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                if (PiExcelSupport.compact(PiExcelSupport.cellText(row.getCell(c))).equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sheetLabel(ChecklistSheetType s) {
        return switch (s) {
            case MANAGEMENT -> "'1. 관리체계'";
            case SYSTEM -> "'2. 개인정보처리시스템'";
            case TERMINATION -> "'3. 계약변경 만료시 체크'";
        };
    }

    private static List<String> dedup(List<String> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    private static String extOf(FormTemplateType t, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 양식 파일이 없습니다.");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "양식 파일은 20MB 이하만 올릴 수 있습니다.");
        }
        String ext = PiFileStorage.extensionOf(PiFileStorage.sanitizeOriginalName(file.getOriginalFilename()));
        if (!new HashSet<>(t.getExtensions()).contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    t.getLabel() + " 양식은 " + String.join(", ", t.getExtensions()) + " 파일만 올릴 수 있습니다.");
        }
        return ext;
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "양식 파일을 읽을 수 없습니다.");
        }
    }

    static String contentTypeOf(String ext) {
        return switch (ext == null ? "" : ext) {
            case "xlsx" -> PiExpiryFormService.XLSX;
            case "docx" -> PiExpiryFormService.DOCX;
            case "doc" -> "application/msword";
            case "pdf" -> "application/pdf";
            case "hwp" -> "application/x-hwp";
            case "hwpx" -> "application/hwp+zip";
            default -> "application/octet-stream";
        };
    }
}
