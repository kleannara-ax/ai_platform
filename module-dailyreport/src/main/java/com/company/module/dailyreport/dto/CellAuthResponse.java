package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.CellAuth;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 셀 접근 권한 응답 DTO (★ Phase 4 신규)
 */
@Getter
@Builder
public class CellAuthResponse {

    private Long authId;
    private Long userId;
    private String userName;       // core_user JOIN으로 채움
    private String loginId;        // core_user JOIN으로 채움
    private String tableCode;
    private List<String> cellCoords;
    private String freqCode;
    private String freqLabel;
    private Boolean isActive;
    private Long grantedBy;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity → DTO 변환 (userName/loginId 없는 버전)
     */
    public static CellAuthResponse from(CellAuth entity) {
        return CellAuthResponse.builder()
                .authId(entity.getAuthId())
                .userId(entity.getUserId())
                .tableCode(entity.getTableCode())
                .cellCoords(entity.getCellCoordList())
                .freqCode(entity.getFreqCode())
                .freqLabel(entity.getFreqLabel())
                .isActive(entity.getIsActive())
                .grantedBy(entity.getGrantedBy())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Entity → DTO 변환 (userName/loginId 포함)
     */
    public static CellAuthResponse from(CellAuth entity, String userName, String loginId) {
        return CellAuthResponse.builder()
                .authId(entity.getAuthId())
                .userId(entity.getUserId())
                .userName(userName)
                .loginId(loginId)
                .tableCode(entity.getTableCode())
                .cellCoords(entity.getCellCoordList())
                .freqCode(entity.getFreqCode())
                .freqLabel(entity.getFreqLabel())
                .isActive(entity.getIsActive())
                .grantedBy(entity.getGrantedBy())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
