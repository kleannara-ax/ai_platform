package com.company.module.personalinfo.service;

import com.company.module.personalinfo.dto.FollowUpResponse;
import com.company.module.personalinfo.entity.ContractFileType;
import com.company.module.personalinfo.entity.InspectionResult;
import com.company.module.personalinfo.entity.InspectionType;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiContractFile;
import com.company.module.personalinfo.entity.PiInspection;
import com.company.module.personalinfo.repository.PiContractFileRepository;
import com.company.module.personalinfo.repository.PiInspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 보안팀 확인 필요 규칙 (목록·상세·요약 카드·알림·종료 처리에서 같은 규칙을 쓴다).
 * 목록 검색 조건(followUp)의 JPQL 은 {@link com.company.module.personalinfo.repository.PiContractRepository#search} 에 같은 규칙으로 들어 있다.
 */
@Component
@RequiredArgsConstructor
public class PiFollowUpRules {

    public static final String DOC_CHECKLIST = "만료 점검 체크리스트";
    public static final String DOC_PLEDGE = "정보보호 및 비밀유지 확약서";
    public static final String DOC_DESTRUCTION = "개인정보 파기 확인서";
    private static final String N = "N";

    private final PiContractFileRepository fileRepository;
    private final PiInspectionRepository inspectionRepository;

    /**
     * 파기 기한 (계약 종료 기준일 + N일, 기본 5일).
     * 표준 개인정보 보호지침 제10조 ① "보유 기간이 경과하거나 처리 목적이 달성된 경우 … 정당한 사유가 없는 한 그로부터 5일 이내에 파기".
     */
    @Value("${personalinfo.destruction-days:5}")
    private int destructionDays = 5;

    /** 파기 확인서 회신 기한 (계약 종료 기준일 + N일, 기본 30일). 파기 기한과 별도로 협력사가 확인서를 보내오는 실무 기간 */
    @Value("${personalinfo.destruction-confirm-reply-days:30}")
    private int confirmReplyDays = 30;

    /** 파기 기한 (종료 기준일 + 파기 기한 일수). 종료 기준일이 없으면 null */
    public LocalDate destructionDeadline(PiContract c) {
        LocalDate base = endBase(c);
        return base == null ? null : base.plusDays(destructionDays);
    }

    /** 파기 확인서 회신 기한 (종료 기준일 + 회신 기한 일수). 종료 기준일이 없으면 null */
    public LocalDate confirmReplyDeadline(PiContract c) {
        LocalDate base = endBase(c);
        return base == null ? null : base.plusDays(confirmReplyDays);
    }

    /** 만료 서류를 받아야 하는 계약: 종료 처리했거나, 원계약 종료일이 만료 알림 기간 안(만료임박) 또는 지남(만료) */
    public static boolean expiryDocsNeeded(PiContract c, LocalDate today) {
        if ("Y".equals(c.getTerminatedYn())) {
            return true;
        }
        if (c.getContractEndDate() == null) {
            return false;
        }
        int alertDays = c.getAlertDaysBefore() == null ? PiContract.DEFAULT_ALERT_DAYS : c.getAlertDaysBefore();
        return ChronoUnit.DAYS.between(today, c.getContractEndDate()) <= alertDays;
    }

    /** 계약 종료 기준일: 종료 처리일, 없으면 원계약 종료일 */
    public static LocalDate endBase(PiContract c) {
        return "Y".equals(c.getTerminatedYn()) && c.getTerminatedDate() != null ? c.getTerminatedDate() : c.getContractEndDate();
    }

    public FollowUpResponse of(PiContract c, LocalDate today) {
        return of(List.of(c), today, true).get(c.getContractId());
    }

    /** 여러 계약의 확인 필요 상태 (파일·점검은 한 번에 읽는다). detail=true 면 조치 지연 점검 목록까지 채움 */
    public Map<Long, FollowUpResponse> of(Collection<PiContract> contracts, LocalDate today, boolean detail) {
        Map<Long, FollowUpResponse> result = new HashMap<>();
        if (contracts.isEmpty()) {
            return result;
        }
        List<Long> ids = contracts.stream().map(PiContract::getContractId).toList();
        Map<Long, List<PiContractFile>> files = fileRepository.findByContractIdInAndDeletedYn(ids, N).stream()
                .collect(Collectors.groupingBy(PiContractFile::getContractId));
        Map<Long, List<PiInspection>> inspections = inspectionRepository.findByContractIdInAndDeletedYn(ids, N).stream()
                .collect(Collectors.groupingBy(PiInspection::getContractId));
        for (PiContract c : contracts) {
            result.put(c.getContractId(), build(c, today, files.getOrDefault(c.getContractId(), List.of()),
                    inspections.getOrDefault(c.getContractId(), List.of()), detail));
        }
        return result;
    }

    private FollowUpResponse build(PiContract c, LocalDate today, List<PiContractFile> files,
                                   List<PiInspection> inspections, boolean detail) {
        boolean checklist = inspections.stream().anyMatch(i -> i.getInspectionType() == InspectionType.TERMINATION);
        List<PiContractFile> pledges = ofType(files, ContractFileType.SECURITY_PLEDGE);
        List<PiContractFile> destructions = ofType(files, ContractFileType.DESTRUCTION_CONFIRM);
        boolean needed = expiryDocsNeeded(c, today);
        List<String> missing = new ArrayList<>();
        if (!checklist) missing.add(DOC_CHECKLIST);
        if (pledges.isEmpty()) missing.add(DOC_PLEDGE);
        if (destructions.isEmpty()) missing.add(DOC_DESTRUCTION);

        List<PiInspection> overdue = inspections.stream().filter(i -> isOverdue(i, today))
                .sorted(Comparator.comparing(PiInspection::getActionDueDate)).toList();

        boolean consentMissing = "Y".equals(c.getSubcontractYn()) && ofType(files, ContractFileType.SUBCONTRACT_CONSENT).isEmpty();

        // 파기 완료일: 가장 최근에 올린 파기 확인서 기준. 파기 기한(종료 + 5일)보다 늦으면 파기 지연
        PiContractFile latestDestruction = destructions.isEmpty() ? null : destructions.get(0);
        LocalDate destructionDate = latestDestruction == null ? null : latestDestruction.getDocumentDate();
        LocalDate base = endBase(c);
        LocalDate deadline = destructionDeadline(c);
        boolean late = destructionDate != null && deadline != null && destructionDate.isAfter(deadline);
        // 확인서 회신 기한(종료 + 30일)이 지났는데 파기 확인서를 아직 받지 못함
        LocalDate replyDeadline = confirmReplyDeadline(c);
        boolean replyOverdue = latestDestruction == null && replyDeadline != null && replyDeadline.isBefore(today);

        List<String> dateMissing = new ArrayList<>();
        if (!pledges.isEmpty() && pledges.get(0).getDocumentDate() == null) dateMissing.add(DOC_PLEDGE + " 서명일");
        if (!destructions.isEmpty() && destructionDate == null) dateMissing.add(DOC_DESTRUCTION + " 파기 완료일");

        return FollowUpResponse.builder()
                .expiryDocsNeeded(needed)
                .expiryDocsReceived(3 - missing.size())
                .missingDocs(missing)
                .expiryDocsMissing(needed && !missing.isEmpty())
                .actionOverdueCount(overdue.size())
                .overdueInspections(detail ? overdue.stream().map(i -> FollowUpResponse.OverdueInspection.builder()
                        .inspectionId(i.getInspectionId())
                        .inspectionDate(i.getInspectionDate())
                        .actionDueDate(i.getActionDueDate())
                        .overdueDays(ChronoUnit.DAYS.between(i.getActionDueDate(), today))
                        .nCount(i.getNCount())
                        .build()).toList() : null)
                .consentMissing(consentMissing)
                .destructionLate(late)
                .destructionDate(destructionDate)
                .destructionDeadline(deadline)
                .destructionDelayReason(latestDestruction == null ? null : latestDestruction.getDocumentNote())
                .confirmReplyDeadline(replyDeadline)
                .confirmReplyOverdue(replyOverdue)
                .endBaseDate(base)
                .destructionDays(destructionDays)
                .confirmReplyDays(confirmReplyDays)
                .documentDateMissing(dateMissing)
                .build();
    }

    /** 조치 지연: 결과 '조치 필요' + 조치 미완료 + 조치 예정일이 기준일보다 이름 */
    public static boolean isOverdue(PiInspection i, LocalDate today) {
        return i.getResult() == InspectionResult.NEED_ACTION && !"Y".equals(i.getActionCompletedYn())
                && i.getActionDueDate() != null && i.getActionDueDate().isBefore(today);
    }

    /** 같은 종류 파일 (최근 업로드 먼저) */
    private static List<PiContractFile> ofType(List<PiContractFile> files, ContractFileType type) {
        return files.stream().filter(f -> f.getFileType() == type)
                .sorted(Comparator.comparing(PiContractFile::getFileId).reversed()).toList();
    }

    public int getDestructionDays() {
        return destructionDays;
    }

    public int getConfirmReplyDays() {
        return confirmReplyDays;
    }
}
