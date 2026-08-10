package com.company.module.autodrawing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawingProjectSaveRequest {

    /** 저장할 프로젝트 목록 (전체 갱신 방식) */
    private List<Map<String, Object>> projects;

    /** 강제 덮어쓰기 여부 */
    private Boolean force;
}
