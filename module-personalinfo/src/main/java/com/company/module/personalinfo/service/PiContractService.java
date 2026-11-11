package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.AlertLogResponse;
import com.company.module.personalinfo.dto.ContactHistoryResponse;
import com.company.module.personalinfo.dto.ContractDetailResponse;
import com.company.module.personalinfo.dto.ContractFlagUpdateRequest;
import com.company.module.personalinfo.dto.ContractFileResponse;
import com.company.module.personalinfo.dto.ContractSaveRequest;
import com.company.module.personalinfo.dto.ContractSearchCondition;
import com.company.module.personalinfo.dto.ContractSummaryResponse;
import com.company.module.personalinfo.dto.ContractTerminateRequest;
import com.company.module.personalinfo.dto.DashboardResponse;
import com.company.module.personalinfo.dto.InspectionResponse;
import com.company.module.personalinfo.entity.ContactChangeType;
import com.company.module.personalinfo.entity.ContractStatus;
import com.company.module.personalinfo.entity.PiContactHistory;
import com.company.module.personalinfo.entity.InspectionResult;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiInspection;
import com.company.module.personalinfo.repository.PiAlertLogRepository;
import com.company.module.personalinfo.repository.PiContactHistoryRepository;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import com.company.module.personalinfo.repository.PiContractRepository;
import com.company.module.personalinfo.repository.PiInspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 위수탁 계약 등록/조회/수정/삭제(소프트) 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PiContractService {

    private static final String N = "N";
    private static final int MAX_PAGE_SIZE = 500;
    /** 정렬 허용 필드 (화면 정렬 파라미터 → 엔티티 필드) */
    private static final Map<String, String> SORTABLE = Map.of(
            "contractEndDate", "contractEndDate",
            "vendorName", "vendorName",
            "serviceName", "serviceName",
            "deptName", "deptName",
            "lastInspectionDate", "lastInspectionDate",
            "contractId", "contractId",
            "createdAt", "createdAt");
    /** NULL 이 있을 수 있는 정렬 필드 (NULL 은 항상 뒤로) */
    private static final Set<String> NULLABLE_SORT = Set.of("contractEndDate", "serviceName", "deptName", "lastInspectionDate");

    private final PiContractRepository contractRepository;
    private final PiContractFileRepository fileRepository;
    private final PiInspectionRepository inspectionRepository;
    private final PiAlertLogRepository alertLogRepository;
    private final PiContactHistoryRepository contactHistoryRepository;

    public Page<ContractSummaryResponse> search(ContractSearchCondition cond, int page, int size, String sort) {
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), toSort(sort));
        Integer by = cond.getBaseYear();
        if (by != null && (by < 1990 || by > 2100)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "기준 연도가 올바르지 않습니다: " + by);
        }
        int year = by == null ? today.getYear() : by;
        String yearStatus = by == null ? "" : upperOrEmpty(cond.getYearStatus(), Set.of("ACTIVE", "ENDED"));
        String yearCheck = by == null ? "" : upperOrEmpty(cond.getYearCheck(),
                Set.of("INSPECTED", "NOT_INSPECTED", "TERM_UPLOADED", "TERM_MISSING"));
        Page<PiContract> result = contractRepository.search(
                nz(cond.getKeyword()), nz(cond.getDeptName()), nz(cond.getManagerName()),
                upperOrEmpty(cond.getIsmsScopeYn(), Set.of("Y", "N")),
                upperOrEmpty(cond.getInspectionResult(), Set.of("PASS", "NEED_ACTION", "INCOMPLETE", "NONE")),
                cond.getEndDateFrom(), cond.getEndDateTo(),
                upperOrEmpty(cond.getStatus(), Set.of("ACTIVE", "EXPIRING", "EXPIRED", "TERMINATED", "NO_END_DATE")),
                today,
                by == null ? 0 : 1, year, PiYearScope.yearStart(year), PiYearScope.yearEnd(year),
                yearStatus, yearCheck, InspectionType.REGULAR, InspectionType.TERMINATION,
                pageable);
        Set<Long> termChecked = termCheckedIds(result.getContent().stream().map(PiContract::getContractId).toList());
        return result.map(c -> ContractSummaryResponse.from(c, today, termChecked.contains(c.getContractId())));
    }

    /** 계약종결 체크(계약변경·만료 점검)를 업로드한 계약 ID */
    private Set<Long> termCheckedIds(List<Long> contractIds) {
        if (contractIds.isEmpty()) {
            return Set.of();
        }
        return inspectionRepository.findByContractIdInAndInspectionTypeAndDeletedYn(contractIds, InspectionType.TERMINATION, N)
                .stream().map(PiInspection::getContractId).collect(Collectors.toSet());
    }

    public ContractDetailResponse getDetail(Long contractId) {
        PiContract c = findActive(contractId);
        Long latestDocId = fileRepository
                .findFirstByContractIdAndFileTypeAndDeletedYnAndSignedDateIsNotNullOrderBySignedDateDescFileIdDesc(
                        contractId, com.company.module.personalinfo.entity.ContractFileType.CONTRACT, N)
                .map(com.company.module.personalinfo.entity.PiContractFile::getFileId).orElse(null);
        List<ContractFileResponse> files = fileRepository.findByContractIdAndDeletedYnOrderByFileIdDesc(contractId, N)
                .stream().map(f -> ContractFileResponse.from(f, latestDocId)).toList();
        List<InspectionResponse> inspections = inspectionRepository
                .findByContractIdAndDeletedYnOrderByInspectionDateDescInspectionIdDesc(contractId, N)
                .stream().map(InspectionResponse::from).toList();
        List<AlertLogResponse> alerts = alertLogRepository.findTop20ByContractIdAndDeletedYnOrderByAlertIdDesc(contractId, N)
                .stream().map(AlertLogResponse::from).toList();
        return ContractDetailResponse.of(c, LocalDate.now(), files, inspections, alerts);
    }

    @Transactional
    public ContractDetailResponse create(ContractSaveRequest r, Long userId) {
        return create(r, userId, null);
    }

    @Transactional
    public ContractDetailResponse create(ContractSaveRequest r, Long userId, String userName) {
        validate(r);
        PiContract contract = PiContract.builder()
                .ismsScopeYn(r.getIsmsScopeYn())
                .vendorName(trim(r.getVendorName()))
                .serviceName(trim(r.getServiceName()))
                .entrustedTask(trim(r.getEntrustedTask()))
                .deptName(trim(r.getDeptName()))
                .managerName(trim(r.getManagerName()))
                .managerEmail(trim(r.getManagerEmail()))
                .vendorContactName(trim(r.getVendorContactName()))
                .vendorContactEmail(trim(r.getVendorContactEmail()))
                .vendorContactPhone(trim(r.getVendorContactPhone()))
                .contractStartDate(r.getContractStartDate())
                .contractEndDate(r.getContractEndDate())
                .contractPeriodNote(trim(r.getContractPeriodNote()))
                .autoRenewYn(r.getAutoRenewYn())
                .autoRenewNote(trim(r.getAutoRenewNote()))
                .contractDocYn(r.getContractDocYn())
                .contractSignedDate(r.getContractSignedDate())
                .subcontractYn(r.getSubcontractYn())
                .subcontractorName(trim(r.getSubcontractorName()))
                .subcontractTask(trim(r.getSubcontractTask()))
                .subcontractConsentYn(emptyToNull(r.getSubcontractConsentYn()))
                .alertYn(r.getAlertYn())
                .alertDaysBefore(r.getAlertDaysBefore())
                .alertExtraEmails(trim(r.getAlertExtraEmails()))
                .remark(trim(r.getRemark()))
                .createdBy(userId)
                .build();
        PiContract saved = contractRepository.save(contract);
        if (hasAnyContact(saved)) {
            contactHistoryRepository.save(PiContactHistory.snapshot(saved, ContactChangeType.CREATE, null, userName, userId));
        }
        return ContractDetailResponse.of(saved, LocalDate.now(), List.of(), List.of(), List.of());
    }

    @Transactional
    public ContractDetailResponse update(Long contractId, ContractSaveRequest r, Long userId) {
        return update(contractId, r, userId, null);
    }

    @Transactional
    public ContractDetailResponse update(Long contractId, ContractSaveRequest r, Long userId, String userName) {
        validate(r);
        PiContract c = findActive(contractId);
        String[] before = contactValues(c);
        c.update(r.getIsmsScopeYn(), trim(r.getVendorName()), trim(r.getServiceName()), trim(r.getEntrustedTask()),
                trim(r.getDeptName()), trim(r.getManagerName()), trim(r.getManagerEmail()),
                trim(r.getVendorContactName()), trim(r.getVendorContactEmail()), trim(r.getVendorContactPhone()),
                r.getContractStartDate(), r.getContractEndDate(), trim(r.getContractPeriodNote()),
                r.getAutoRenewYn(), trim(r.getAutoRenewNote()), r.getContractDocYn(), r.getContractSignedDate(),
                r.getSubcontractYn(), trim(r.getSubcontractorName()), trim(r.getSubcontractTask()),
                emptyToNull(r.getSubcontractConsentYn()),
                r.getAlertYn(), r.getAlertDaysBefore(), trim(r.getAlertExtraEmails()), trim(r.getRemark()),
                userId);
        recordContactChange(c, before, userName, userId);
        return getDetail(contractId);
    }

    /**
     * 협력사(업체) 담당자 변경 이력.
     * 같은 업체(㈜/(주)/공백 차이 무시)의 모든 계약 이력을 최신순으로 돌려준다. 삭제된 계약은 제외.
     */
    public List<ContactHistoryResponse> getContactHistory(Long contractId, boolean vendorWide) {
        PiContract c = findActive(contractId);
        List<Long> ids;
        if (vendorWide) {
            String key = PiVendorNames.key(c.getVendorName());
            ids = contractRepository.findByDeletedYnOrderByContractIdAsc(N).stream()
                    .filter(x -> PiVendorNames.key(x.getVendorName()).equals(key))
                    .map(PiContract::getContractId).toList();
        } else {
            ids = List.of(contractId);
        }
        // 위탁사 담당자 항목이 바뀌지 않은 '담당자 변경' 건(예: 이전 버전에서 수탁사 담당자만 바뀌어 남은 이력)은 보여주지 않는다
        return contactHistoryRepository.findByContractIdInAndDeletedYnOrderByCreatedAtDescHistoryIdDesc(ids, N)
                .stream()
                .filter(h -> h.getChangeType() != ContactChangeType.UPDATE
                        || !ContactHistoryResponse.changedTrackedFields(h).isEmpty())
                .map(h -> ContactHistoryResponse.of(h, contractId)).toList();
    }

    /** 이력 대상: 위탁사(당사) 담당부서·담당자·이메일. 수탁사 담당자·연락처는 대상 아님 */
    private static final String[] CONTACT_FIELDS = {
            PiContactHistory.F_DEPT, PiContactHistory.F_MANAGER, PiContactHistory.F_MANAGER_EMAIL};

    private static String[] contactValues(PiContract c) {
        return new String[]{c.getDeptName(), c.getManagerName(), c.getManagerEmail()};
    }

    private static boolean hasAnyContact(PiContract c) {
        for (String v : contactValues(c)) {
            if (StringUtils.hasText(v)) {
                return true;
            }
        }
        return false;
    }

    /** 위탁사 담당자 항목이 하나라도 바뀌면 변경 후 스냅샷(+계약기간) 1건 기록 (대소문자·앞뒤 공백만 다른 경우는 변경 아님) */
    private void recordContactChange(PiContract c, String[] before, String userName, Long userId) {
        String[] after = contactValues(c);
        List<String> changed = new java.util.ArrayList<>();
        for (int i = 0; i < CONTACT_FIELDS.length; i++) {
            if (!normalize(before[i]).equalsIgnoreCase(normalize(after[i]))) {
                changed.add(CONTACT_FIELDS[i]);
            }
        }
        if (!changed.isEmpty()) {
            contactHistoryRepository.save(PiContactHistory.snapshot(c, ContactChangeType.UPDATE,
                    String.join(",", changed), userName, userId));
        }
    }

    private static String normalize(String v) {
        return v == null ? "" : v.trim();
    }

    /**
     * 목록 화면에서 ISMS 인증 여부 / 재위탁 여부를 바로 바꾼다.
     * 보낸 항목만 바꾸며, 계약의 다른 항목은 건드리지 않는다.
     */
    @Transactional
    public ContractSummaryResponse updateFlags(Long contractId, ContractFlagUpdateRequest r, Long userId) {
        if (r == null || (r.getIsmsScopeYn() == null && r.getSubcontractYn() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "변경할 항목(ismsScopeYn 또는 subcontractYn)을 보내세요.");
        }
        PiContract c = findActive(contractId);
        if (r.getIsmsScopeYn() != null) {
            c.changeIsmsScope(r.getIsmsScopeYn(), userId);
        }
        if (r.getSubcontractYn() != null) {
            c.changeSubcontract(r.getSubcontractYn(), userId);
        }
        return ContractSummaryResponse.from(c, LocalDate.now());
    }

    @Transactional
    public ContractDetailResponse terminate(Long contractId, ContractTerminateRequest r, Long userId) {
        PiContract c = findActive(contractId);
        c.terminate(r == null ? null : r.getTerminatedDate(), r == null ? null : trim(r.getReason()), userId);
        return getDetail(contractId);
    }

    @Transactional
    public ContractDetailResponse reopen(Long contractId, Long userId) {
        PiContract c = findActive(contractId);
        c.reopen(userId);
        return getDetail(contractId);
    }

    /** 소프트 삭제 (관리자 전용). 첨부파일/점검 이력은 계약을 통해서만 조회되므로 함께 숨겨진다. */
    @Transactional
    public void delete(Long contractId, Long userId) {
        findActive(contractId).delete(userId);
    }

    public DashboardResponse dashboard() {
        return dashboard(null);
    }

    /**
     * 목록 상단 요약. 기준 연도(baseYear, 없으면 올해) 기준으로
     * 전체 = 원계약 기간이 그 연도에 걸친 계약, 계약중 / 종료(그 연도 안에 종료 처리·만료) 로 나눈다.
     * 계약중은 그 연도 수탁사 정기 점검 완료 건수, 종료는 계약종결 체크(계약변경·만료 점검) 업로드 여부를 함께 센다.
     */
    public DashboardResponse dashboard(Integer baseYear) {
        LocalDate today = LocalDate.now();
        if (baseYear != null && (baseYear < 1990 || baseYear > 2100)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "기준 연도가 올바르지 않습니다: " + baseYear);
        }
        int by = baseYear == null ? today.getYear() : baseYear;
        int year = today.getYear();
        List<PiContract> all = contractRepository.findByDeletedYnOrderByContractIdAsc(N);
        Map<ContractStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(c -> ContractStatus.of(c, today), Collectors.counting()));
        Set<Long> inspectedThisYear = inspectionRepository.findByInspectionYearAndDeletedYn(year, N).stream()
                .map(PiInspection::getContractId).collect(Collectors.toSet());
        long notInspected = all.stream()
                .filter(c -> !"Y".equals(c.getTerminatedYn()))
                .filter(c -> !inspectedThisYear.contains(c.getContractId()))
                .count();
        List<ContractSummaryResponse> expiringSoon = all.stream()
                .filter(c -> {
                    ContractStatus s = ContractStatus.of(c, today);
                    return s == ContractStatus.EXPIRING || s == ContractStatus.EXPIRED;
                })
                .filter(c -> c.getContractEndDate() != null && !c.getContractEndDate().isBefore(today.minusDays(30)))
                .sorted(Comparator.comparing(PiContract::getContractEndDate))
                .limit(10)
                .map(c -> ContractSummaryResponse.from(c, today))
                .toList();
        // ── 기준 연도 요약 ──
        List<PiContract> inYear = all.stream().filter(c -> PiYearScope.inYear(c, by)).toList();
        List<PiContract> ended = inYear.stream().filter(c -> PiYearScope.endedInYear(c, by, today)).toList();
        List<PiContract> activeInYear = inYear.stream().filter(c -> !PiYearScope.endedInYear(c, by, today)).toList();
        Set<Long> regularInspected = inspectionRepository.findByInspectionYearAndDeletedYn(by, N).stream()
                .filter(i -> i.getInspectionType() == InspectionType.REGULAR)
                .map(PiInspection::getContractId).collect(Collectors.toSet());
        Set<Long> termChecked = inspectionRepository.findByInspectionTypeAndDeletedYn(InspectionType.TERMINATION, N).stream()
                .map(PiInspection::getContractId).collect(Collectors.toSet());
        long endedChecked = ended.stream().filter(c -> termChecked.contains(c.getContractId())).count();
        // 선택 가능한 연도: 계약 시작·종료 연도 범위 + 올해 (최근 연도부터)
        java.util.TreeSet<Integer> years = new java.util.TreeSet<>(Comparator.reverseOrder());
        years.add(year);
        years.add(by);
        all.forEach(c -> {
            if (c.getContractStartDate() != null) years.add(c.getContractStartDate().getYear());
            if (c.getContractEndDate() != null && c.getContractEndDate().getYear() <= year + 1) years.add(c.getContractEndDate().getYear());
        });
        int minY = years.last();
        int maxY = years.first();
        List<Integer> available = new ArrayList<>();
        for (int y = maxY; y >= Math.max(minY, maxY - 15); y--) available.add(y);

        return DashboardResponse.builder()
                .baseYear(by)
                .availableYears(available)
                .yearTotalCount(inYear.size())
                .yearActiveCount(activeInYear.size())
                .yearActiveInspectedCount(activeInYear.stream().filter(c -> regularInspected.contains(c.getContractId())).count())
                .yearEndedCount(ended.size())
                .yearEndedTermCheckCount(endedChecked)
                .yearEndedTermMissingCount(ended.size() - endedChecked)
                .year(year)
                .totalCount(all.size())
                .activeCount(byStatus.getOrDefault(ContractStatus.ACTIVE, 0L))
                .expiringCount(byStatus.getOrDefault(ContractStatus.EXPIRING, 0L))
                .expiredCount(byStatus.getOrDefault(ContractStatus.EXPIRED, 0L))
                .terminatedCount(byStatus.getOrDefault(ContractStatus.TERMINATED, 0L))
                .noEndDateCount(byStatus.getOrDefault(ContractStatus.NO_END_DATE, 0L))
                .ismsScopeCount(all.stream().filter(c -> "Y".equals(c.getIsmsScopeYn())).count())
                .subcontractCount(all.stream().filter(c -> "Y".equals(c.getSubcontractYn())).count())
                .inspectedThisYearCount(all.stream().filter(c -> inspectedThisYear.contains(c.getContractId())).count())
                .needActionCount(all.stream()
                        .filter(c -> InspectionResult.NEED_ACTION.name().equals(c.getLastInspectionResult())).count())
                .notInspectedThisYearCount(notInspected)
                .expiringSoon(expiringSoon)
                .build();
    }

    /** 엑셀 다운로드용 전체 목록 (검색 조건 적용, 최대 5,000건) */
    public List<ContractSummaryResponse> searchAll(ContractSearchCondition cond, String sort) {
        List<ContractSummaryResponse> rows = new ArrayList<>();
        for (int p = 0; p < 10; p++) {
            Page<ContractSummaryResponse> page = search(cond, p, MAX_PAGE_SIZE, sort);
            rows.addAll(page.getContent());
            if (page.isLast()) {
                break;
            }
        }
        return rows;
    }

    PiContract findActive(Long contractId) {
        return contractRepository.findByContractIdAndDeletedYn(contractId, N)
                .orElseThrow(() -> new EntityNotFoundException("위수탁 계약", contractId));
    }

    private void validate(ContractSaveRequest r) {
        if (r.getContractStartDate() != null && r.getContractEndDate() != null
                && r.getContractEndDate().isBefore(r.getContractStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "계약 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if ("Y".equals(r.getAlertYn()) && r.getContractEndDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "만료 알림을 사용하려면 계약 종료일을 입력하세요. (종료일이 없으면 알림 사용을 N 으로 설정)");
        }
        if ("Y".equals(r.getSubcontractYn()) && !StringUtils.hasText(r.getSubcontractorName())
                && !StringUtils.hasText(r.getSubcontractTask())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "재위탁이 있으면 재위탁 업체명 또는 재위탁 업무를 입력하세요.");
        }
    }

    /**
     * 정렬 조건. MariaDB 는 NULLS LAST 를 지원하지 않으므로(Hibernate 가 무시)
     * 날짜 정렬 시 "값 없음" 을 뒤로 보내는 보조 정렬을 JPQL 함수로 앞에 둔다.
     */
    private static Sort toSort(String sort) {
        String field = "contractEndDate";
        boolean desc = false;
        if (StringUtils.hasText(sort)) {
            String[] parts = sort.split(",");
            String mapped = SORTABLE.get(parts[0].trim());
            if (mapped != null) {
                field = mapped;
                desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
            }
        }
        Sort.Order main = desc ? Sort.Order.desc(field) : Sort.Order.asc(field);
        Sort result = Sort.by(main);
        if (NULLABLE_SORT.contains(field)) {
            // CASE WHEN field IS NULL THEN 1 ELSE 0 END ASC 와 같은 효과 (JpaSort.unsafe)
            result = JpaSort.unsafe(Sort.Direction.ASC, "(case when c." + field + " is null then 1 else 0 end)").and(result);
        }
        return result.and(Sort.by(Sort.Order.desc("contractId")));
    }

    private static String upperOrEmpty(String v, Set<String> allowed) {
        if (!StringUtils.hasText(v)) {
            return "";
        }
        String u = v.trim().toUpperCase();
        if (!allowed.contains(u)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "검색 조건 값이 올바르지 않습니다: " + v);
        }
        return u;
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    static String trim(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String emptyToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
