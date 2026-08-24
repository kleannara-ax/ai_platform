package com.company.module.kims.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.RequestLogCreateRequest;
import com.company.module.kims.dto.request.ServiceRequestCreateRequest;
import com.company.module.kims.dto.request.StatusChangeRequest;
import com.company.module.kims.dto.response.AttachmentResponse;
import com.company.module.kims.dto.response.InternetWorkResponse;
import com.company.module.kims.dto.response.IpHistoryResponse;
import com.company.module.kims.dto.response.ProgramInstallResponse;
import com.company.module.kims.dto.response.RequestLogResponse;
import com.company.module.kims.dto.response.RequestTypeSummaryResponse;
import com.company.module.kims.dto.response.ServiceRequestDetailResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.dto.response.SupplyIssueResponse;
import com.company.module.kims.entity.RequestLog;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import com.company.module.kims.repository.InternetWorkRepository;
import com.company.module.kims.repository.IpHistoryRepository;
import com.company.module.kims.repository.ProgramInstallRepository;
import com.company.module.kims.repository.RequestAttachmentRepository;
import com.company.module.kims.repository.RequestLogRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import com.company.module.kims.repository.SupplyIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업무 요청 관련 비즈니스 로직.
 * <p>등록 / 목록 / 상세 / 상태변경 / 처리내용 기록 / 엑셀 다운로드를 담당한다.
 * <p>읽기 메서드는 클래스 레벨의 {@code @Transactional(readOnly = true)} 를 따르고,
 * 쓰기 메서드에만 별도로 {@code @Transactional} 을 선언한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceRequestService {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ServiceRequestRepository serviceRequestRepository;
    private final RequestLogRepository requestLogRepository;
    private final SupplyIssueRepository supplyIssueRepository;
    private final SupplyIssueService supplyIssueService;
    private final IpAddressService ipAddressService;
    private final IpHistoryRepository ipHistoryRepository;
    private final ProgramInstallRepository programInstallRepository;
    private final InternetWorkRepository internetWorkRepository;
    private final RequestAttachmentRepository requestAttachmentRepository;
    private final ExcelExportService excelExportService;

    // ================================================================
    // 1. 업무 요청 등록
    // ================================================================
    @Transactional
    public ServiceRequestResponse register(ServiceRequestCreateRequest request) {
        // 요청번호 자동 생성 (예: KIMS-20260602-0001)
        String requestNo = generateRequestNo();

        ServiceRequest entity = ServiceRequest.builder()
                .requestNo(requestNo)
                .requesterName(request.getRequesterName())
                .department(request.getDepartment())
                .contact(request.getContact())
                .location(request.getLocation())
                .requestType(request.getRequestType())
                .issueType(request.getIssueType())
                .ipKind(request.getIpKind())
                .changerName(request.getChangerName())
                .content(request.getContent())
                .urgent(request.isUrgent())
                .receivedChannel(request.getReceivedChannel())
                .build();

        ServiceRequest saved = serviceRequestRepository.save(entity);

        // 등록 이력 로그 기록
        requestLogRepository.save(RequestLog.forCreated(saved, request.getRequesterName()));

        return ServiceRequestResponse.from(saved);
    }

    /**
     * 오늘 날짜 기준으로 일련번호를 매겨 요청번호를 생성한다.
     * <p>형식: {@code KIMS-yyyyMMdd-0001}
     * <p>주의: 동시 등록이 매우 많은 환경에서는 번호 충돌 가능성이 있으므로,
     * 운영 단계에서는 시퀀스 테이블/유니크 제약+재시도 등을 고려한다. (MVP 단계 단순 구현)
     */
    private String generateRequestNo() {
        String prefix = "KIMS-" + LocalDate.now().format(DATE_KEY) + "-";
        long todayCount = serviceRequestRepository.countByRequestNoStartingWith(prefix);
        return prefix + String.format("%04d", todayCount + 1);
    }

    // ================================================================
    // 2. 업무 요청 목록 조회 (다중 조건 검색 + 페이징)
    // ================================================================
    public PageResponse<ServiceRequestResponse> getList(LocalDateTime from, LocalDateTime to,
                                                        RequestType requestType, RequestStatus status,
                                                        String department, String requester,
                                                        String assignee, String location,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ServiceRequestResponse> result = serviceRequestRepository
                .search(from, to, requestType, status, emptyToNull(department), emptyToNull(requester),
                        emptyToNull(assignee), emptyToNull(location), pageable)
                .map(ServiceRequestResponse::from);
        return PageResponse.of(result);
    }

    // ================================================================
    // 홈 카드: 유형별 신규(접수)/전체 건수
    // ================================================================
    public List<RequestTypeSummaryResponse> getTypeSummary() {
        Map<RequestType, Long> newMap = toTypeMap(serviceRequestRepository.countTypeByStatus(RequestStatus.RECEIVED));
        Map<RequestType, Long> totalMap = toTypeMap(serviceRequestRepository.countGroupByType());
        List<RequestTypeSummaryResponse> result = new ArrayList<>();
        for (RequestType t : RequestType.values()) {
            result.add(RequestTypeSummaryResponse.builder()
                    .type(t.name()).label(t.getLabel())
                    .newCount(newMap.getOrDefault(t, 0L))
                    .totalCount(totalMap.getOrDefault(t, 0L))
                    .build());
        }
        return result;
    }

    /** 요청이 있는 월 목록 ("yyyy-MM", 최신순). 당월이 없으면 맨 앞에 추가. */
    public List<String> getMonths() {
        List<String> months = new ArrayList<>();
        for (Object[] row : serviceRequestRepository.findDistinctYearMonths()) {
            months.add(String.format("%04d-%02d", ((Number) row[0]).intValue(), ((Number) row[1]).intValue()));
        }
        String cur = String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        if (!months.contains(cur)) {
            months.add(0, cur);
        }
        return months;
    }

    private Map<RequestType, Long> toTypeMap(List<Object[]> rows) {
        Map<RequestType, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((RequestType) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    // ================================================================
    // 3. 업무 요청 상세 조회 (로그 + 소모품 지급 내역 포함)
    // ================================================================
    public ServiceRequestDetailResponse getDetail(Long requestId) {
        ServiceRequest request = findRequest(requestId);

        List<RequestLogResponse> logs = requestLogRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(RequestLogResponse::from)
                .toList();

        List<SupplyIssueResponse> supplyIssues = supplyIssueRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(SupplyIssueResponse::from)
                .toList();

        // 관련 IP 변경 / 프로그램 설치 / 인터넷 공사 내역
        List<IpHistoryResponse> ipChanges = ipHistoryRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtDesc(requestId)
                .stream().map(IpHistoryResponse::from).toList();

        List<ProgramInstallResponse> programInstalls = programInstallRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream().map(ProgramInstallResponse::from).toList();

        List<InternetWorkResponse> internetWorks = internetWorkRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream().map(InternetWorkResponse::from).toList();

        List<AttachmentResponse> attachments = requestAttachmentRepository
                .findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream().map(AttachmentResponse::from).toList();

        return ServiceRequestDetailResponse.of(request, logs, supplyIssues,
                ipChanges, programInstalls, internetWorks, attachments);
    }

    // ================================================================
    // 4. 처리상태 변경 (상태변경 로그 자동 기록)
    // ================================================================
    @Transactional
    public ServiceRequestResponse changeStatus(Long requestId, StatusChangeRequest request) {
        ServiceRequest entity = findRequest(requestId);

        RequestStatus before = entity.getStatus();
        RequestStatus after = request.getStatus();

        // 상태 변경 (완료 시 completedAt 자동 입력은 엔티티 내부에서 처리)
        entity.changeStatus(after, request.getAssignee());

        boolean voiding = (after == RequestStatus.CANCELED || after == RequestStatus.REJECTED);
        boolean wasVoided = (before == RequestStatus.CANCELED || before == RequestStatus.REJECTED);

        // 완료로 전환 & IP 변경/생성 요청이면 자동 반영 (변경 전 스냅샷 저장)
        boolean completing = (after == RequestStatus.COMPLETED && before != RequestStatus.COMPLETED);
        if (completing) {
            applyIpRequest(entity, request);
        }

        // 취소/반려로 전환되면 자동 반영 원복 + 소모품 지급 원복
        if (voiding && !wasVoided) {
            if (entity.getAppliedSnapshot() != null) {
                ipAddressService.revertApplied(entity.getAppliedSnapshot(), request.getChangedBy());
                entity.clearAppliedSnapshot();
            }
            supplyIssueService.reverseByRequest(requestId, request.getChangedBy());
        }

        // 상태변경 이력 로그 자동 기록
        requestLogRepository.save(
                RequestLog.forStatusChange(entity, before, after, request.getChangedBy(), request.getReason()));

        return ServiceRequestResponse.from(entity);
    }

    /** 완료 처리 시 IP 변경/생성 요청을 자동 반영하고 원복용 스냅샷을 저장한다. */
    private void applyIpRequest(ServiceRequest req, StatusChangeRequest scr) {
        if (req.getRequestType() != RequestType.IP || req.getIpKind() == null) return;
        String by = scr.getChangedBy();
        Long rid = req.getRequestId();
        String snapshot = null;
        switch (req.getIpKind()) {
            case IP_CHANGE -> {
                if (scr.getTargetIpId() == null || isBlank(scr.getNewIp())) {
                    throw new com.company.core.common.exception.BusinessException(
                            com.company.core.common.exception.ErrorCode.INVALID_INPUT_VALUE,
                            "IP변경 완료: 대상 PC와 새 IP를 지정해야 합니다.");
                }
                snapshot = ipAddressService.applyIpMove(scr.getTargetIpId(), scr.getNewIp(), by, rid);
            }
            case IP_NEW -> {
                if (isBlank(scr.getNewIp())) {
                    throw new com.company.core.common.exception.BusinessException(
                            com.company.core.common.exception.ErrorCode.INVALID_INPUT_VALUE,
                            "IP신규생성 완료: 부여할 IP를 입력해야 합니다.");
                }
                snapshot = ipAddressService.applyIpNew(req.getChangerName(), scr.getNewIp(),
                        req.getDepartment(), by, rid);
            }
            case PC_CHANGE -> {
                if (scr.getTargetIpId() == null || scr.getPcFields() == null || scr.getPcFields().isEmpty()) {
                    throw new com.company.core.common.exception.BusinessException(
                            com.company.core.common.exception.ErrorCode.INVALID_INPUT_VALUE,
                            "PC변경 완료: 대상 PC와 변경할 항목을 지정해야 합니다.");
                }
                snapshot = ipAddressService.applyPcChange(scr.getTargetIpId(), scr.getPcFields(), by, rid);
            }
            case ETC -> { /* 기타 변경: 자동 반영 없음 (수기 처리) */ }
        }
        if (snapshot != null) {
            req.setAppliedSnapshot(snapshot);
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ================================================================
    // 5. 처리내용(메모) 로그 추가
    // ================================================================
    @Transactional
    public RequestLogResponse addProcessLog(Long requestId, RequestLogCreateRequest request) {
        ServiceRequest entity = findRequest(requestId);
        RequestLog log = requestLogRepository.save(
                RequestLog.forNote(entity, request.getChangedBy(), request.getContent()));
        return RequestLogResponse.from(log);
    }

    // ================================================================
    // 10. 업무 요청 목록 Excel 다운로드
    // ================================================================
    public byte[] exportExcel(LocalDateTime from, LocalDateTime to,
                              RequestType requestType, RequestStatus status,
                              String department, String requester, String assignee, String location) {
        // Pageable.unpaged() → 페이징 없이 조건에 맞는 전체 결과 조회
        List<ServiceRequest> requests = serviceRequestRepository
                .search(from, to, requestType, status, emptyToNull(department), emptyToNull(requester),
                        emptyToNull(assignee), emptyToNull(location), Pageable.unpaged())
                .getContent();
        return excelExportService.buildRequestListExcel(requests);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private ServiceRequest findRequest(Long requestId) {
        return serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("업무 요청을 찾을 수 없습니다. id=" + requestId));
    }

    /** 빈 문자열("")을 null 로 바꿔 검색 조건에서 무시되도록 한다. */
    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
