package com.company.module.kims.dto.response;

import com.company.module.kims.entity.IpAddress;
import com.company.module.kims.entity.enums.IpStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IP 주소 응답 DTO (목록/요약).
 */
@Getter
@Builder
public class IpAddressResponse {

    private final Long ipId;
    private final String ipAddress;
    private final String ipGroup;          // IP그룹 (예: 192.1.0, 별도관리)
    private final IpStatus status;
    private final String statusLabel;
    private final String usageType;        // 상주/임시/반납/예비
    private final String userName;
    private final String userId;           // 사용자ID
    private final String department;
    private final String location;
    private final String device;           // 장치구분
    private final boolean approved;
    private final String approvalNo;
    private final String remark;
    private final LocalDate noteDate;      // 비고작성일
    private final LocalDate reclaimedAt;   // 회수일
    private final LocalDateTime createdAt; // 생성일
    private final LocalDateTime updatedAt; // 변경일

    // PC/장비 상세 스펙
    private final String model;
    private final String serialNo;
    private final String vendor;
    private final String purchaseDate;     // 구입일
    private final String osVersion;
    private final String osSerial;
    private final String officeVersion;
    private final String officeSerial;
    private final String hangulVersion;
    private final String hangulSerial;
    private final String rentalCompany;   // 렌탈사명 (없으면 자산)
    private final String pcAssetNo;
    private final String monitorAssetNo;

    public static IpAddressResponse from(IpAddress e) {
        return IpAddressResponse.builder()
                .ipId(e.getIpId())
                .ipAddress(e.getIpAddress())
                .ipGroup(e.getIpGroup() != null ? e.getIpGroup() : groupOf(e.getIpAddress()))
                .status(e.getStatus())
                .statusLabel(e.getStatus().getLabel())
                .usageType(e.getUsageType())
                .userName(e.getUserName())
                .userId(e.getUserId())
                .department(e.getDepartment())
                .location(e.getLocation())
                .device(e.getDevice())
                .approved(e.isApproved())
                .approvalNo(e.getApprovalNo())
                .remark(e.getRemark())
                .noteDate(e.getNoteDate())
                .reclaimedAt(e.getReclaimedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .model(e.getModel())
                .serialNo(e.getSerialNo())
                .vendor(e.getVendor())
                .purchaseDate(e.getPurchaseDate())
                .osVersion(e.getOsVersion())
                .osSerial(e.getOsSerial())
                .officeVersion(e.getOfficeVersion())
                .officeSerial(e.getOfficeSerial())
                .hangulVersion(e.getHangulVersion())
                .hangulSerial(e.getHangulSerial())
                .rentalCompany(e.getRentalCompany())
                .pcAssetNo(e.getPcAssetNo())
                .monitorAssetNo(e.getMonitorAssetNo())
                .build();
    }

    /** IP 그룹(마지막 옥텟 제외) 추출. 예: 192.1.0.44 → 192.1.0 */
    private static String groupOf(String ip) {
        if (ip == null) {
            return null;
        }
        int dot = ip.lastIndexOf('.');
        return (dot > 0) ? ip.substring(0, dot) : ip;
    }
}
