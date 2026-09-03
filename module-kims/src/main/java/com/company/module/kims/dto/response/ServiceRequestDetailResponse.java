package com.company.module.kims.dto.response;

import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 업무 요청 상세 응답 DTO.
 * <p>요청 기본/상세 정보와 함께 처리 로그, 소모품 지급 내역을 포함한다.
 */
@Getter
@Builder
public class ServiceRequestDetailResponse {

    // ---- 기본 정보 ----
    private final Long requestId;
    private final String requestNo;
    private final String requesterName;
    private final String department;
    private final String contact;
    private final String location;

    private final RequestType requestType;
    private final String requestTypeLabel;
    private final String issueType;          // 세부 불편유형 코드 (없으면 null)
    private final String issueTypeLabel;     // 세부 불편유형 한글명 (없으면 null)
    private final String ipKind;             // 요청목록 코드 (IP_CHANGE/IP_NEW/PC_CHANGE/ETC)
    private final String ipKindLabel;        // 요청목록 한글명
    private final String changerName;        // 변경자/생성자

    private final String content;       // 상세 요청내용
    private final boolean urgent;

    private final RequestStatus status;
    private final String statusLabel;

    private final String assignee;
    private final String receivedChannel;
    private final String receivedChannelLabel;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime completedAt;

    // ---- 연관 정보 ----
    private final List<RequestLogResponse> logs;              // 처리 로그
    private final List<SupplyIssueResponse> supplyIssues;     // 소모품 지급 내역
    private final List<IpHistoryResponse> ipChanges;          // 관련 IP 변경 내역
    private final List<ProgramInstallResponse> programInstalls; // 관련 프로그램 설치 내역
    private final List<InternetWorkResponse> internetWorks;   // 관련 인터넷 공사 내역
    private final List<AttachmentResponse> attachments;       // 첨부파일

    public static ServiceRequestDetailResponse of(ServiceRequest entity,
                                                  List<RequestLogResponse> logs,
                                                  List<SupplyIssueResponse> supplyIssues,
                                                  List<IpHistoryResponse> ipChanges,
                                                  List<ProgramInstallResponse> programInstalls,
                                                  List<InternetWorkResponse> internetWorks,
                                                  List<AttachmentResponse> attachments) {
        return ServiceRequestDetailResponse.builder()
                .requestId(entity.getRequestId())
                .requestNo(entity.getRequestNo())
                .requesterName(entity.getRequesterName())
                .department(entity.getDepartment())
                .contact(entity.getContact())
                .location(entity.getLocation())
                .requestType(entity.getRequestType())
                .requestTypeLabel(entity.getRequestType().getLabel())
                .issueType(entity.getIssueType() == null ? null : entity.getIssueType().name())
                .issueTypeLabel(entity.getIssueType() == null ? null : entity.getIssueType().getLabel())
                .ipKind(entity.getIpKind() == null ? null : entity.getIpKind().name())
                .ipKindLabel(entity.getIpKind() == null ? null : entity.getIpKind().getLabel())
                .changerName(entity.getChangerName())
                .content(entity.getContent())
                .urgent(entity.isUrgent())
                .status(entity.getStatus())
                .statusLabel(entity.getStatus().getLabel())
                .assignee(entity.getAssignee())
                .receivedChannel(entity.getReceivedChannel().name())
                .receivedChannelLabel(entity.getReceivedChannel().getLabel())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .logs(logs)
                .supplyIssues(supplyIssues)
                .ipChanges(ipChanges)
                .programInstalls(programInstalls)
                .internetWorks(internetWorks)
                .attachments(attachments)
                .build();
    }
}
