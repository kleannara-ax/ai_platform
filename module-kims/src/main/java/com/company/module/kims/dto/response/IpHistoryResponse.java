package com.company.module.kims.dto.response;

import com.company.module.kims.entity.IpHistory;
import com.company.module.kims.entity.enums.IpChangeType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * IP 변경 이력 응답 DTO.
 */
@Getter
@Builder
public class IpHistoryResponse {

    private final Long historyId;
    private final Long ipId;
    private final String ipAddress;
    private final String userName;       // 현재 사용자
    private final String department;     // 현재 부서
    private final String device;         // 현재 장치구분
    private final IpChangeType changeType;
    private final String changeTypeLabel;
    private final String content;
    private final boolean approved;
    private final String approvalNo;
    private final String changedBy;
    private final String beforeIp;       // 정보변경(IP 이동) 시 변경 전 IP
    private final String afterIp;        // 정보변경(IP 이동) 시 변경 후 IP
    private final String snapshotIp;     // 이 이력 시점의 전체 IP (없으면 null)
    private final String beforeUser;     // 변경 전 사용자
    private final String afterUser;      // 변경 후 사용자
    // PC/장비 상세 스펙 (현재 IP 레코드 기준)
    private final String model;
    private final String serialNo;
    private final String vendor;
    private final String osVersion;
    private final String osSerial;
    private final String officeVersion;
    private final String officeSerial;
    private final String hangulVersion;
    private final String hangulSerial;
    private final String rentalCompany;   // 렌탈사명 (없으면 자산)
    private final String pcAssetNo;
    private final String monitorAssetNo;
    private final Long requestId;        // 연결된 요청(선택)
    private final LocalDateTime createdAt;

    public static IpHistoryResponse from(IpHistory e) {
        return IpHistoryResponse.builder()
                .historyId(e.getHistoryId())
                .ipId(e.getIpAddress().getIpId())
                .ipAddress(e.getIpAddress().getIpAddress())
                .userName(e.getIpAddress().getUserName())
                .department(e.getIpAddress().getDepartment())
                .device(e.getIpAddress().getDevice())
                .changeType(e.getChangeType())
                .changeTypeLabel(e.getChangeType().getLabel())
                .content(e.getContent())
                .approved(e.isApproved())
                .approvalNo(e.getApprovalNo())
                .changedBy(e.getChangedBy())
                .beforeIp(e.getBeforeIp())
                .afterIp(e.getAfterIp())
                .snapshotIp(e.getSnapshotIp())
                .beforeUser(e.getBeforeUser())
                .afterUser(e.getAfterUser())
                .model(e.getIpAddress().getModel())
                .serialNo(e.getIpAddress().getSerialNo())
                .vendor(e.getIpAddress().getVendor())
                .osVersion(e.getIpAddress().getOsVersion())
                .osSerial(e.getIpAddress().getOsSerial())
                .officeVersion(e.getIpAddress().getOfficeVersion())
                .officeSerial(e.getIpAddress().getOfficeSerial())
                .hangulVersion(e.getIpAddress().getHangulVersion())
                .hangulSerial(e.getIpAddress().getHangulSerial())
                .rentalCompany(e.getIpAddress().getRentalCompany())
                .pcAssetNo(e.getIpAddress().getPcAssetNo())
                .monitorAssetNo(e.getIpAddress().getMonitorAssetNo())
                .requestId(e.getServiceRequest() != null ? e.getServiceRequest().getRequestId() : null)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
