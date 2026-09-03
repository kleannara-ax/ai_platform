package com.company.module.autodrawing.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.autodrawing.dto.DrawingProjectResponse;
import com.company.module.autodrawing.entity.DrawingProject;
import com.company.module.autodrawing.repository.DrawingProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 자동도면 프로젝트 서비스
 * 기존 JSON 파일 기반 → JPA 기반으로 전환
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrawingProjectService {

    private final DrawingProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * 팀별 프로젝트 목록 조회
     */
    public List<DrawingProjectResponse> getProjects(String teamId) {
        List<DrawingProject> projects = projectRepository.findByTeamIdOrderByUpdatedAtDesc(teamId);
        log.info("[AutoDrawing] GET projects (team: {}) → {}개", teamId, projects.size());
        return projects.stream()
                .map(DrawingProjectResponse::from)
                .toList();
    }

    /**
     * 프로젝트 저장 (전체 갱신 방식 — 기존 방식 호환)
     * 프론트엔드에서 프로젝트 목록 전체를 보내면 DB를 동기화
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveProjects(String teamId, List<Map<String, Object>> projects,
                                            boolean force, Long userId) {
        if (projects == null) {
            throw new IllegalArgumentException("projects must be an array");
        }

        List<DrawingProject> existing = projectRepository.findByTeamIdOrderByUpdatedAtDesc(teamId);
        int prevCount = existing.size();
        int newCount = projects.size();

        // 데이터 손실 방어
        boolean isSuspicious = prevCount >= 3
                && newCount < prevCount
                && (newCount == 0 || newCount <= prevCount / 2)
                && !force;

        if (isSuspicious) {
            log.warn("[AutoDrawing] ⚠️ 의심스러운 저장 거부 (team: {}): {}개 → {}개", teamId, prevCount, newCount);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "suspicious_bulk_delete");
            error.put("message", String.format(
                    "기존 %d개에서 %d개로 급감하여 저장을 차단했습니다. force:true로 다시 요청하세요.",
                    prevCount, newCount));
            error.put("prevCount", prevCount);
            error.put("newCount", newCount);
            return error;
        }

        // 기존 프로젝트 UUID → entity 맵
        Map<String, DrawingProject> existingMap = new LinkedHashMap<>();
        existing.forEach(p -> existingMap.put(p.getProjectUuid(), p));

        Set<String> incomingUuids = new HashSet<>();

        for (Map<String, Object> projData : projects) {
            String uuid = (String) projData.get("id");
            if (uuid == null) uuid = UUID.randomUUID().toString();
            incomingUuids.add(uuid);

            String name = (String) projData.getOrDefault("name", "Untitled");
            String dataJson;
            try {
                dataJson = objectMapper.writeValueAsString(projData);
            } catch (JsonProcessingException e) {
                dataJson = "{}";
            }

            DrawingProject existingProject = existingMap.get(uuid);
            if (existingProject != null) {
                existingProject.updateData(name, dataJson);
            } else {
                DrawingProject newProject = DrawingProject.builder()
                        .projectUuid(uuid)
                        .projectName(name)
                        .teamId(teamId)
                        .projectData(dataJson)
                        .createdBy(userId)
                        .build();
                projectRepository.save(newProject);
            }
        }

        // 제거된 프로젝트 삭제
        existing.stream()
                .filter(p -> !incomingUuids.contains(p.getProjectUuid()))
                .forEach(projectRepository::delete);

        log.info("[AutoDrawing] POST projects (team: {}) {}개 → {}개 저장{}",
                teamId, prevCount, newCount, force ? " (force)" : "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", newCount);
        return result;
    }

    /**
     * 단일 프로젝트 조회
     */
    public DrawingProjectResponse getProject(String projectUuid) {
        DrawingProject project = projectRepository.findByProjectUuid(projectUuid)
                .orElseThrow(() -> new EntityNotFoundException("DrawingProject", projectUuid));
        return DrawingProjectResponse.from(project);
    }
}
