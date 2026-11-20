package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamIncident;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 사고·정비 이력 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncidentResponse {

    private Long incidentId;
    private Integer yearNo;
    private String recordKey;
    private String facility;
    private String kind;
    private String occurDate;
    private String reason;
    private String actionTaken;
    private String downtimeHours;
    private String lossAmount;
    private String note;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamIncidentResponse from(SteamIncident e) {
        return from(e, null);
    }

    public static SteamIncidentResponse from(SteamIncident e, Boolean isUpdate) {
        return SteamIncidentResponse.builder()
                .incidentId(e.getIncidentId())
                .yearNo(e.getYearNo())
                .recordKey(e.getRecordKey())
                .facility(e.getFacility())
                .kind(e.getKind())
                .occurDate(e.getOccurDate())
                .reason(e.getReason())
                .actionTaken(e.getActionTaken())
                .downtimeHours(e.getDowntimeHours())
                .lossAmount(e.getLossAmount())
                .note(e.getNote())
                .sortOrder(e.getSortOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
