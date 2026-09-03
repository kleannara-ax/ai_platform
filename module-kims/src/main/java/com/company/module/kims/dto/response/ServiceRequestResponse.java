package com.company.module.kims.dto.response;

import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.entity.enums.RequestStatus;
import com.company.module.kims.entity.enums.RequestType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 업무 요청 목록/요약 응답 DTO.
 */
@Getter
@Builder
public class ServiceRequestResponse {

    private final Long requestId;
    private final String requestNo;
    private final String requesterName;
    private final String department;
    private final String location;

    private final RequestType requestType;
    private final String requestTypeLabel;  // 요청유형 한글명
    private final String issueType;          // 세부 불편유형 코드 (없으면 null)
    private final String issueTypeLabel;     // 세부 불편유형 한글명 (없으면 null)

    private final RequestStatus status;
    private final String statusLabel;        // 처리상태 한글명

    private final String assignee;
    private final boolean urgent;
    private final String receivedChannel;      // 접수채널 코드 (PHONE/MANUAL/QR)
    private final String receivedChannelLabel; // 접수채널 한글명
    private final String contentSummary;      // 요청내용 요약 (목록 표시용)
    private final LocalDateTime createdAt;    // 요청일
    private final LocalDateTime completedAt;  // 완료일

    private static final int SUMMARY_LEN = 50;

    /** Entity → Response 변환 */
    public static ServiceRequestResponse from(ServiceRequest entity) {
        return ServiceRequestResponse.builder()
                .requestId(entity.getRequestId())
                .requestNo(entity.getRequestNo())
                .requesterName(entity.getRequesterName())
                .department(entity.getDepartment())
                .location(entity.getLocation())
                .requestType(entity.getRequestType())
                .requestTypeLabel(entity.getRequestType().getLabel())
                .issueType(entity.getIssueType() == null ? null : entity.getIssueType().name())
                .issueTypeLabel(entity.getIssueType() == null ? null : entity.getIssueType().getLabel())
                .status(entity.getStatus())
                .statusLabel(entity.getStatus().getLabel())
                .assignee(entity.getAssignee())
                .urgent(entity.isUrgent())
                .receivedChannel(entity.getReceivedChannel().name())
                .receivedChannelLabel(entity.getReceivedChannel().getLabel())
                .contentSummary(summarize(entity.getContent()))
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    /** 긴 요청내용을 목록용으로 짧게 자른다. */
    private static String summarize(String content) {
        if (content == null) {
            return null;
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SUMMARY_LEN ? oneLine : oneLine.substring(0, SUMMARY_LEN) + "…";
    }
}
