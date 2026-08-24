package com.company.module.kims.dto.response;

import com.company.module.kims.entity.QrLocation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * QR 구역 응답 DTO.
 */
@Getter
@Builder
public class QrLocationResponse {

    private final Long qrId;
    private final String token;
    private final String name;
    private final String location;
    private final String department;
    private final boolean active;
    private final String remark;
    private final LocalDateTime createdAt;

    public static QrLocationResponse from(QrLocation e) {
        return QrLocationResponse.builder()
                .qrId(e.getQrId())
                .token(e.getToken())
                .name(e.getName())
                .location(e.getLocation())
                .department(e.getDepartment())
                .active(e.isActive())
                .remark(e.getRemark())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
