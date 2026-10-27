package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.InspectionResponse;
import com.company.module.personalinfo.dto.VendorChecklistPreviewResponse;
import com.company.module.personalinfo.dto.VendorChecklistUploadResponse;
import com.company.module.personalinfo.dto.VendorSummaryResponse;
import com.company.module.personalinfo.entity.ContractStatus;
import com.company.module.personalinfo.entity.InspectionResult;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiInspection;
import com.company.module.personalinfo.repository.PiContractRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 계약 업체별 점검 체크리스트 업로드.
 *
 * <p>같은 수탁업체가 여러 서비스(예: 자사몰/임직원몰 배송)로 계약이 나뉘어 있어도,
 * 협력사는 체크리스트를 업체 단위로 1부 작성한다. 이 서비스는 체크리스트 1건을
 * 해당 업체의 선택한 계약 전체에 점검 이력으로 등록한다.
 * <ul>
 *   <li>업체 묶음 기준: 업체명에서 ㈜/(주)/주식회사/공백/대소문자를 뺀 값이 같으면 같은 업체</li>
 *   <li>원본 파일은 1번만 저장하고, 계약별 점검 이력이 같은 파일과 묶음 ID(UPLOAD_GROUP_ID)를 공유한다</li>
 *   <li>등록은 한 트랜잭션: 계약 하나라도 실패하면 전체를 되돌린다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiVendorChecklistService {

    private static final String N = "N";
    private static final long MAX_CHECKLIST_BYTES = 20L * 1024 * 1024;
    private static final int MAX_CONTRACTS_PER_UPLOAD = 100;

    private final PiContractRepository contractRepository;
    private final PiInspectionRepository inspectionRepository;
    private final PiInspectionService inspectionService;
    private final ChecklistExcelParser parser;
    private final PiFileStorage storage;

    /** 업체 목록 (계약 업체명 기준 묶음). keyword 는 업체명 부분 검색 */
    public List<VendorSummaryResponse> getVendors(String keyword, boolean includeTerminated) {
        LocalDate today = LocalDate.now();
        String kw = PiVendorNames.key(keyword);
        Map<String, List<PiContract>> groups = groupByVendor(contractRepository.findByDeletedYnOrderByContractIdAsc(N));
        Set<Long> inspectedThisYear = inspectionRepository.findByInspectionYearAndDeletedYn(today.getYear(), N).stream()
                .map(PiInspection::getContractId).collect(Collectors.toSet());
        return groups.entrySet().stream()
                .filter(e -> kw.isEmpty() || e.getKey().contains(kw))
                .map(e -> summarize(e.getKey(), e.getValue(), today, inspectedThisYear, includeTerminated))
                .filter(v -> v.getContractCount() > 0)
                .sorted(Comparator.comparing(VendorSummaryResponse::getVendorName))
                .toList();
    }

    /**
     * 업로드 전 미리보기: 파일을 저장하지 않고 체크리스트를 읽어 집계 결과와
     * 체크리스트 회사명으로 찾은 업체 후보를 돌려준다. (업체 자동 선택용)
     */
    public VendorChecklistPreviewResponse preview(MultipartFile file, String inspectionType) {
        InspectionType type = PiInspectionService.parseType(inspectionType);
        ChecklistExcelParser.ParsedChecklist parsed = parse(file, type);
        String company = PiInspectionService.looksPlaceholder(parsed.companyName()) ? null : parsed.companyName();

        List<VendorSummaryResponse> matched = List.of();
        if (StringUtils.hasText(company)) {
            List<VendorSummaryResponse> all = getVendors(null, true);
            List<VendorSummaryResponse> exact = all.stream()
                    .filter(v -> PiVendorNames.same(v.getVendorName(), company)).toList();
            List<VendorSummaryResponse> similar = all.stream()
                    .filter(v -> !exact.contains(v) && PiVendorNames.similar(v.getVendorName(), company)).toList();
            matched = new ArrayList<>(exact);
            matched.addAll(similar);
        }
        List<String> warnings = new ArrayList<>(parsed.warnings());
        if (!StringUtils.hasText(company)) {
            warnings.add(0, "체크리스트에 회사명이 없어 업체를 자동으로 찾지 못했습니다. 업체를 직접 선택하세요.");
        } else if (matched.isEmpty()) {
            warnings.add(0, "체크리스트 회사명('" + company + "')과 같은 업체의 계약이 없습니다. 업체를 직접 선택하거나 계약을 먼저 등록하세요.");
        }
        return VendorChecklistPreviewResponse.builder()
                .sheetCompanyName(company)
                .sheetTaskName(parsed.taskName())
                .evaluationDate(parsed.evaluationDate())
                .evaluator(PiInspectionService.looksPlaceholder(parsed.evaluator()) ? null : parsed.evaluator())
                .systemOwnedYn(parsed.systemOwnedYn())
                .totalCount(parsed.total())
                .yCount(parsed.yCount())
                .nCount(parsed.nCount())
                .naCount(parsed.naCount())
                .blankCount(parsed.blankCount())
                .result(parsed.result().name())
                .resultLabel(parsed.result().getLabel())
                .warnings(warnings)
                .matchedVendors(matched)
                .build();
    }

    /**
     * 업체별 업로드: 체크리스트 1건을 업체의 선택 계약 전체에 점검 이력으로 등록한다.
     *
     * @param vendorKey   업체 그룹 키 (업체 목록의 vendorKey)
     * @param contractIds 등록할 계약 ID. 비우면 업체의 미종료 계약 전체
     */
    @Transactional
    public VendorChecklistUploadResponse upload(String vendorKey, List<Long> contractIds, MultipartFile file,
                                                String inspectionType, LocalDate inspectionDate, String inspectorName,
                                                LocalDate actionDueDate, String remark, Long userId) {
        if (!StringUtils.hasText(vendorKey)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업체를 선택하세요.");
        }
        String key = PiVendorNames.key(vendorKey);
        List<PiContract> vendorContracts = contractRepository.findByDeletedYnOrderByContractIdAsc(N).stream()
                .filter(c -> PiVendorNames.key(c.getVendorName()).equals(key))
                .toList();
        if (vendorContracts.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "선택한 업체의 계약을 찾을 수 없습니다.");
        }

        List<PiContract> targets;
        List<Long> ids = contractIds == null ? List.of()
                : contractIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            targets = vendorContracts.stream().filter(c -> !"Y".equals(c.getTerminatedYn())).toList();
            if (targets.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "업체의 계약이 모두 종료 상태입니다. 등록할 계약을 직접 선택하세요.");
            }
        } else {
            Map<Long, PiContract> byId = vendorContracts.stream()
                    .collect(Collectors.toMap(PiContract::getContractId, c -> c));
            List<Long> foreign = ids.stream().filter(id -> !byId.containsKey(id)).toList();
            if (!foreign.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "선택한 업체의 계약이 아닌 항목이 있습니다: " + foreign);
            }
            targets = ids.stream().map(byId::get).toList();
        }
        if (targets.size() > MAX_CONTRACTS_PER_UPLOAD) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "한 번에 최대 " + MAX_CONTRACTS_PER_UPLOAD + "건의 계약에 등록할 수 있습니다.");
        }

        InspectionType type = PiInspectionService.parseType(inspectionType);
        ChecklistExcelParser.ParsedChecklist parsed = parse(file, type);
        PiFileStorage.StoredFile stored = storage.store(file, "checklist", PiFileStorage.CHECKLIST_EXTENSIONS,
                MAX_CHECKLIST_BYTES);
        String groupId = UUID.randomUUID().toString();

        List<InspectionResponse> created = new ArrayList<>();
        for (PiContract c : targets) {
            PiInspectionService.Registered reg = inspectionService.register(c, type, parsed, stored,
                    inspectionDate, inspectorName, actionDueDate, remark, groupId, userId);
            created.add(InspectionResponse.withContract(reg.inspection(), c.getVendorName(), c.getServiceName()));
        }

        Set<String> warnings = new LinkedHashSet<>();
        inspectionService.companyMismatchWarning(parsed, targets.get(0)).ifPresent(warnings::add);
        warnings.addAll(parsed.warnings());

        return VendorChecklistUploadResponse.builder()
                .uploadGroupId(groupId)
                .vendorName(targets.get(0).getVendorName())
                .result(parsed.result().name())
                .resultLabel(parsed.result().getLabel())
                .registeredCount(created.size())
                .inspections(created)
                .warnings(new ArrayList<>(warnings))
                .build();
    }

    // ─────────────────────────────────────────────────────────────

    private ChecklistExcelParser.ParsedChecklist parse(MultipartFile file, InspectionType type) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 체크리스트 파일이 없습니다.");
        }
        if (!"xlsx".equals(PiFileStorage.extensionOf(PiFileStorage.sanitizeOriginalName(file.getOriginalFilename())))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크리스트는 .xlsx 파일만 업로드할 수 있습니다.");
        }
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in, type);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크리스트 파일을 읽을 수 없습니다.");
        }
    }

    private static Map<String, List<PiContract>> groupByVendor(List<PiContract> contracts) {
        Map<String, List<PiContract>> groups = new LinkedHashMap<>();
        for (PiContract c : contracts) {
            String k = PiVendorNames.key(c.getVendorName());
            if (!k.isEmpty()) {
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(c);
            }
        }
        return groups;
    }

    private static VendorSummaryResponse summarize(String key, List<PiContract> all, LocalDate today,
                                                   Set<Long> inspectedThisYear, boolean includeTerminated) {
        List<PiContract> list = includeTerminated ? all
                : all.stream().filter(c -> !"Y".equals(c.getTerminatedYn())).toList();
        PiContract latest = list.stream()
                .filter(c -> c.getLastInspectionDate() != null)
                .max(Comparator.comparing(PiContract::getLastInspectionDate))
                .orElse(null);
        List<VendorSummaryResponse.VendorContractItem> items = list.stream().map(c -> {
            ContractStatus st = ContractStatus.of(c, today);
            return VendorSummaryResponse.VendorContractItem.builder()
                    .contractId(c.getContractId())
                    .vendorName(c.getVendorName())
                    .serviceName(c.getServiceName())
                    .entrustedTask(c.getEntrustedTask())
                    .deptName(c.getDeptName())
                    .managerName(c.getManagerName())
                    .contractEndDate(c.getContractEndDate())
                    .status(st.name())
                    .statusLabel(st.getLabel())
                    .lastInspectionDate(c.getLastInspectionDate())
                    .lastInspectionResult(c.getLastInspectionResult())
                    .lastInspectionResultLabel(resultLabel(c.getLastInspectionResult()))
                    .build();
        }).toList();
        return VendorSummaryResponse.builder()
                .vendorKey(key)
                .vendorName(all.get(0).getVendorName())
                .contractCount(list.size())
                .activeContractCount((int) list.stream().filter(c -> !"Y".equals(c.getTerminatedYn())).count())
                .lastInspectionDate(latest == null ? null : latest.getLastInspectionDate())
                .lastInspectionResult(latest == null ? null : latest.getLastInspectionResult())
                .lastInspectionResultLabel(resultLabel(latest == null ? null : latest.getLastInspectionResult()))
                .notInspectedThisYearCount((int) list.stream()
                        .filter(c -> !"Y".equals(c.getTerminatedYn()) && !inspectedThisYear.contains(c.getContractId()))
                        .count())
                .contracts(items)
                .build();
    }

    private static String resultLabel(String r) {
        if (r == null) {
            return "미점검";
        }
        try {
            return InspectionResult.valueOf(r).getLabel();
        } catch (IllegalArgumentException e) {
            return r;
        }
    }
}
