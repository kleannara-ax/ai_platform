package com.company.module.autodrawing.dto;

import com.company.module.autodrawing.entity.DrawingProject;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class DrawingProjectResponse {
    private Long projectId;
    private String projectUuid;
    private String projectName;
    private String teamId;
    private String projectData;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DrawingProjectResponse from(DrawingProject project) {
        return DrawingProjectResponse.builder()
                .projectId(project.getProjectId())
                .projectUuid(project.getProjectUuid())
                .projectName(project.getProjectName())
                .teamId(project.getTeamId())
                .projectData(project.getProjectData())
                .createdBy(project.getCreatedBy())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
