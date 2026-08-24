package com.company.module.kims.service;

import com.company.module.kims.dto.response.InternetWorkResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.dto.response.ProgramInstallResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.dto.response.SettlementResponse;
import com.company.module.kims.dto.response.SettlementResponse.NameCount;
import com.company.module.kims.dto.response.SupplyIssueResponse;
import com.company.module.kims.entity.enums.IssueType;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import com.company.module.kims.repository.InternetWorkRepository;
import com.company.module.kims.repository.IpHistoryRepository;
import com.company.module.kims.repository.ProgramInstallRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import com.company.module.kims.repository.SupplyIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 월말 결산 집계 서비스.
 * <p>지정 기간의 요청/소모품/IP/프로그램/인터넷 내역과 각종 집계를 모은다. (읽기 전용)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    /** 미완료로 간주하는 상태 (접수/처리중/보류) */
    private static final List<RequestStatus> INCOMPLETE =
            List.of(RequestStatus.RECEIVED, RequestStatus.IN_PROGRESS, RequestStatus.ON_HOLD);

    private final ServiceRequestRepository serviceRequestRepository;
    private final SupplyIssueRepository supplyIssueRepository;
    private final IpHistoryRepository ipHistoryRepository;
    private final ProgramInstallRepository programInstallRepository;
    private final InternetWorkRepository internetWorkRepository;
    private final ExcelExportService excelExportService;

    public SettlementResponse getSettlement(LocalDate from, LocalDate to) {
        // 기본 기간 = 이번 달 1일 ~ 말일
        LocalDate f = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = (to != null) ? to : f.withDayOfMonth(f.lengthOfMonth());
        LocalDateTime fromDt = f.atStartOfDay();
        LocalDateTime toDt = t.atTime(23, 59, 59);

        return SettlementResponse.builder()
                .from(f).to(t)
                // 요청 집계
                .requestByType(typeCounts(serviceRequestRepository.countTypeInPeriod(fromDt, toDt)))
                .requestByIssueType(issueTypeCounts(serviceRequestRepository.countIssueTypeInPeriod(fromDt, toDt)))
                .requestByAssignee(nameCounts(serviceRequestRepository.countAssigneeInPeriod(fromDt, toDt)))
                .requestByDepartment(nameCounts(serviceRequestRepository.countDepartmentInPeriod(fromDt, toDt)))
                .incompleteRequests(serviceRequestRepository.findByStatusInOrderByCreatedAtDesc(INCOMPLETE)
                        .stream().map(ServiceRequestResponse::from).toList())
                // 소모품 수량 합계
                .supplyByItem(nameCounts(supplyIssueRepository.sumByItem(f, t)))
                .supplyByDepartment(nameCounts(supplyIssueRepository.sumByDepartment(f, t)))
                .supplyByRequester(nameCounts(supplyIssueRepository.sumByRequester(f, t)))
                .supplyByIssuedBy(nameCounts(supplyIssueRepository.sumByIssuedBy(f, t)))
                // 도메인별 기간 내역
                .supplyIssues(supplyIssueRepository.findByIssuedAtBetweenOrderByIssuedAtDesc(f, t)
                        .stream().map(SupplyIssueResponse::from).toList())
                .ipChanges(ipHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromDt, toDt)
                        .stream().map(IpHistoryResponse::from).toList())
                .unapprovedIpChanges(ipHistoryRepository.findByApprovedFalseAndCreatedAtBetweenOrderByCreatedAtDesc(fromDt, toDt)
                        .stream().map(IpHistoryResponse::from).toList())
                .programInstalls(programInstallRepository.findByInstalledAtBetweenOrderByInstalledAtDesc(f, t)
                        .stream().map(ProgramInstallResponse::from).toList())
                .internetWorks(internetWorkRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromDt, toDt)
                        .stream().map(InternetWorkResponse::from).toList())
                .build();
    }

    public byte[] exportExcel(LocalDate from, LocalDate to) {
        return excelExportService.buildSettlementExcel(getSettlement(from, to));
    }

    // ----------------------------------------------------------------
    // [name, number] 행 → NameCount 변환
    // ----------------------------------------------------------------

    private List<NameCount> nameCounts(List<Object[]> rows) {
        return rows.stream()
                .map(r -> NameCount.builder()
                        .name(r[0] == null ? "(미지정)" : String.valueOf(r[0]))
                        .value(((Number) r[1]).longValue())
                        .build())
                .toList();
    }

    /** 요청유형(Enum) 행은 라벨로 변환 */
    private List<NameCount> typeCounts(List<Object[]> rows) {
        return rows.stream()
                .map(r -> NameCount.builder()
                        .name(((RequestType) r[0]).getLabel())
                        .value(((Number) r[1]).longValue())
                        .build())
                .toList();
    }

    /** 불편유형(Enum) 행은 라벨로 변환 (건수 많은 순) */
    private List<NameCount> issueTypeCounts(List<Object[]> rows) {
        return rows.stream()
                .map(r -> NameCount.builder()
                        .name(((IssueType) r[0]).getLabel())
                        .value(((Number) r[1]).longValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .toList();
    }
}
