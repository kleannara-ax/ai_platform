package com.company.module.kims.dto.request;

import com.company.module.kims.entity.enums.IssueType;
import com.company.module.kims.entity.enums.ReceivedChannel;
import com.company.module.kims.entity.enums.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 업무 요청 등록 요청 DTO.
 */
@Getter
@Setter
public class ServiceRequestCreateRequest {

    @NotBlank(message = "요청자명은 필수입니다.")
    @Size(max = 50)
    private String requesterName;

    @Size(max = 50)
    private String department;

    @Size(max = 30)
    private String contact;

    @Size(max = 100)
    private String location;

    @NotNull(message = "요청유형은 필수입니다.")
    private RequestType requestType;

    /** 세부 불편유형 (요청유형이 PC관련 불편사항 조치일 때만 사용, 그 외 null) */
    private IssueType issueType;

    /** 요청목록 (요청유형 IP 일 때: IP변경/IP신규생성/PC변경/기타변경) */
    private com.company.module.kims.entity.enums.IpRequestKind ipKind;

    /** 변경자/생성자 (자동 반영 대상 사용자명) */
    private String changerName;

    @NotBlank(message = "요청내용은 필수입니다.")
    private String content;

    /** 긴급 여부 (미입력 시 false) */
    private boolean urgent;

    /** 접수 채널 (미입력 시 전화 PHONE) */
    private ReceivedChannel receivedChannel;
}
