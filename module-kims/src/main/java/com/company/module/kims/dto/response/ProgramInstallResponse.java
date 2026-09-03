package com.company.module.kims.dto.response;

import com.company.module.kims.entity.ProgramInstall;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 프로그램 설치 내역 응답 DTO.
 */
@Getter
@Builder
public class ProgramInstallResponse {

    private final Long installId;
    private final Long requestId;     // 연결된 요청(선택)
    private final String requestNo;   // 연결된 요청번호(선택)
    private final String programName;
    private final String requesterName;
    private final String department;
    private final String targetPc;
    private final String installedBy;
    private final LocalDate installedAt;
    private final String remark;
    private final LocalDateTime createdAt;

    /**
     * Entity → Response 변환.
     * <p>연관 요청에 접근하므로 트랜잭션(Service) 내부에서 호출해야 한다.
     */
    public static ProgramInstallResponse from(ProgramInstall e) {
        boolean linked = e.getServiceRequest() != null;
        return ProgramInstallResponse.builder()
                .installId(e.getInstallId())
                .requestId(linked ? e.getServiceRequest().getRequestId() : null)
                .requestNo(linked ? e.getServiceRequest().getRequestNo() : null)
                .programName(e.getProgramName())
                .requesterName(e.getRequesterName())
                .department(e.getDepartment())
                .targetPc(e.getTargetPc())
                .installedBy(e.getInstalledBy())
                .installedAt(e.getInstalledAt())
                .remark(e.getRemark())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
