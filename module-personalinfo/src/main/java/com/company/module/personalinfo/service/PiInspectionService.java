package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.InspectionItemResponse;
import com.company.module.personalinfo.dto.InspectionResponse;
import com.company.module.personalinfo.dto.InspectionUpdateRequest;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiInspection;
import com.company.module.personalinfo.entity.PiInspectionItem;
import com.company.module.personalinfo.repository.PiContractRepository;
import com.company.module.personalinfo.repository.PiInspectionItemRepository;
import com.company.module.personalinfo.repository.PiInspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 위수탁 점검 체크리스트 업로드/조회 서비스.
 *
 * <p>협력사가 작성한 체크리스트 엑셀을 업로드하면
 * <ol>
 *   <li>원본 파일을 저장하고(증적),</li>
 *   <li>평가 항목을 행 단위로 파싱해 pi_inspection_item 에 저장하고,</li>
 *   <li>Y/N/NA 집계로 점검 결과(적합/조치 필요/평가 미완료)를 판정하고,</li>
 *   <li>계약의 최근 점검 정보를 갱신한다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiInspectionService {

    private static final String N = "N";
    private static final long MAX_CHECKLIST_BYTES = 20L * 1024 * 1024;

    private final PiInspectionRepository inspectionRepository;
    private final PiInspectionItemRepository itemRepository;
    private final PiContractRepository contractRepository;
    private final ChecklistExcelParser parser;
    private final PiFileStorage storage;

    public List<InspectionResponse> getList(Long contractId) {
        findContract(contractId);
        return inspectionRepository.findByContractIdAndDeletedYnOrderByInspectionDateDescInspectionIdDesc(contractId, N)
                .stream().map(InspectionResponse::from).toList();
    }

    public InspectionResponse getDetail(Long inspectionId) {
        PiInspection inspection = findActive(inspectionId);
        findContract(inspection.getContractId());
        List<InspectionItemResponse> items = itemRepository
                .findByInspectionIdAndDeletedYnOrderBySortOrderAsc(inspectionId, N)
                .stream().map(InspectionItemResponse::from).toList();
        return InspectionResponse.of(inspection, items, null);
    }

    @Transactional
    public InspectionResponse upload(Long contractId, MultipartFile file, String inspectionType,
                                     LocalDate inspectionDate, String inspectorName, LocalDate actionDueDate,
                                     String remark, Long userId) {
        PiContract contract = findContract(contractId);
        InspectionType type = parseType(inspectionType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 체크리스트 파일이 없습니다.");
        }

        // 1) 먼저 파싱해서 양식 오류면 파일을 저장하지 않는다
        ChecklistExcelParser.ParsedChecklist parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = parser.parse(in, type);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크리스트 파일을 읽을 수 없습니다.");
        }

        // 2) 원본 저장
        PiFileStorage.StoredFile stored = storage.store(file, "checklist", PiFileStorage.CHECKLIST_EXTENSIONS,
                MAX_CHECKLIST_BYTES);

        List<String> warnings = new ArrayList<>(parsed.warnings());
        if (StringUtils.hasText(parsed.companyName()) && !looksPlaceholder(parsed.companyName())
                && !sameCompany(parsed.companyName(), contract.getVendorName())) {
            warnings.add(0, "체크리스트 회사명('" + parsed.companyName() + "')이 계약 수탁업체명('"
                    + contract.getVendorName() + "')과 다릅니다. 올바른 계약에 업로드했는지 확인하세요.");
        }

        LocalDate date = inspectionDate != null ? inspectionDate
                : (parsed.evaluationDate() != null ? parsed.evaluationDate() : LocalDate.now());
        String inspector = StringUtils.hasText(inspectorName) ? inspectorName.trim()
                : (looksPlaceholder(parsed.evaluator()) ? null : parsed.evaluator());

        PiInspection inspection = inspectionRepository.save(PiInspection.builder()
                .contractId(contractId)
                .inspectionType(type)
                .inspectionDate(date)
                .inspectorName(PiExcelSupport.truncate(inspector, 100))
                .sheetCompanyName(PiExcelSupport.truncate(looksPlaceholder(parsed.companyName()) ? null : parsed.companyName(), 200))
                .sheetTaskName(PiExcelSupport.truncate(parsed.taskName(), 500))
                .systemOwnedYn(parsed.systemOwnedYn())
                .systemName(PiExcelSupport.truncate(parsed.systemName(), 200))
                .totalCount(parsed.total())
                .yCount(parsed.yCount())
                .nCount(parsed.nCount())
                .naCount(parsed.naCount())
                .blankCount(parsed.blankCount())
                .result(parsed.result())
                .actionDueDate(actionDueDate)
                .originalName(stored.originalName())
                .storedPath(stored.relativePath())
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .fileHash(stored.sha256())
                .remark(PiContractService.trim(remark))
                .createdBy(userId)
                .build());

        List<PiInspectionItem> items = parsed.items().stream().map(p -> PiInspectionItem.builder()
                .inspectionId(inspection.getInspectionId())
                .sheetType(p.sheetType())
                .rowNo(p.rowNo())
                .sortOrder(p.sheetType().ordinal() * 1000 + p.sortOrder())
                .category(p.category())
                .importantYn(p.important() ? "Y" : "N")
                .question(p.question())
                .checkMethod(p.checkMethod())
                .currentStatus(p.currentStatus())
                .evaluation(p.evaluation())
                .evaluationRaw(PiExcelSupport.truncate(p.evaluationRaw(), 100))
                .actionPlan(p.actionPlan())
                .actionSchedule(p.actionSchedule())
                .confirmDate(p.confirmDate())
                .confirmerName(p.confirmerName())
                .itemRemark(p.itemRemark())
                .createdBy(userId)
                .build()).toList();
        itemRepository.saveAll(items);

        refreshLatest(contract, userId);

        return InspectionResponse.of(inspection, items.stream().map(InspectionItemResponse::from).toList(), warnings);
    }

    @Transactional
    public InspectionResponse update(Long inspectionId, InspectionUpdateRequest r, Long userId) {
        PiInspection inspection = findActive(inspectionId);
        PiContract contract = findContract(inspection.getContractId());
        inspection.updateFollowUp(r.getInspectionDate(), PiContractService.trim(r.getInspectorName()),
                r.getActionDueDate(), r.getActionCompletedYn(), r.getActionCompletedDate(),
                PiContractService.trim(r.getRemark()), userId);
        refreshLatest(contract, userId);
        return getDetail(inspectionId);
    }

    public PiFileStorage.DownloadFile download(Long inspectionId) {
        PiInspection inspection = findActive(inspectionId);
        findContract(inspection.getContractId());
        return new PiFileStorage.DownloadFile(inspection.getOriginalName(), inspection.getContentType(),
                storage.read(inspection.getStoredPath()));
    }

    /** 점검 이력 소프트 삭제 (관리자 전용). 항목도 함께 소프트 삭제하고 최근 점검 정보를 다시 계산한다. */
    @Transactional
    public void delete(Long inspectionId, Long userId) {
        PiInspection inspection = findActive(inspectionId);
        PiContract contract = findContract(inspection.getContractId());
        inspection.delete(userId);
        itemRepository.findByInspectionIdAndDeletedYnOrderBySortOrderAsc(inspectionId, N)
                .forEach(item -> item.delete(userId));
        inspectionRepository.flush();
        refreshLatest(contract, userId);
    }

    /** 계약의 최근 점검(점검일 최신) 정보를 다시 계산한다 */
    private void refreshLatest(PiContract contract, Long userId) {
        inspectionRepository.flush();
        inspectionRepository
                .findFirstByContractIdAndDeletedYnOrderByInspectionDateDescInspectionIdDesc(contract.getContractId(), N)
                .ifPresentOrElse(
                        latest -> contract.applyInspectionResult(latest.getInspectionId(), latest.getInspectionDate(),
                                latest.getResult(), userId),
                        () -> contract.clearInspectionResult(userId));
    }

    private PiInspection findActive(Long inspectionId) {
        return inspectionRepository.findByInspectionIdAndDeletedYn(inspectionId, N)
                .orElseThrow(() -> new EntityNotFoundException("위수탁 점검", inspectionId));
    }

    private PiContract findContract(Long contractId) {
        return contractRepository.findByContractIdAndDeletedYn(contractId, N)
                .orElseThrow(() -> new EntityNotFoundException("위수탁 계약", contractId));
    }

    private static InspectionType parseType(String value) {
        if (!StringUtils.hasText(value)) {
            return InspectionType.REGULAR;
        }
        try {
            return InspectionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "점검 구분 값이 올바르지 않습니다: " + value);
        }
    }

    private static boolean looksPlaceholder(String s) {
        if (!StringUtils.hasText(s)) {
            return true;
        }
        String c = PiExcelSupport.compact(s);
        return c.contains("00회사") || c.contains("OOO") || c.contains("ooo");
    }

    /** ㈜, (주), 주식회사, 공백 차이는 같은 회사로 본다 */
    static boolean sameCompany(String a, String b) {
        String x = normalizeCompany(a);
        String y = normalizeCompany(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    private static String normalizeCompany(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "")
                .replace("㈜", "").replace("(주)", "").replace("（주）", "").replace("주식회사", "")
                .toLowerCase(Locale.ROOT);
    }
}
