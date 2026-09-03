package com.company.module.kims.entity;

/**
 * ip_address 한 행의 가변 상태 스냅샷 (요청 완료 시 저장 → 취소 시 원복용).
 * 날짜/열거형은 문자열로 보관해 JSON 직렬화를 단순화한다.
 */
public record IpRowSnapshot(
        Long ipId,
        String status,
        String userName,
        String department,
        String location,
        String device,
        boolean approved,
        String approvalNo,
        String remark,
        String noteDate,
        String reclaimedAt,
        String model,
        String serialNo,
        String vendor,
        String purchaseDate,
        String osVersion,
        String osSerial,
        String officeVersion,
        String officeSerial,
        String hangulVersion,
        String hangulSerial,
        String rentalCompany,
        String pcAssetNo,
        String monitorAssetNo
) {}
