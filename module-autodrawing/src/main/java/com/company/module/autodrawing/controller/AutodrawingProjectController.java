package com.company.module.autodrawing.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.security.CustomUserDetails;
import com.company.module.autodrawing.dto.DrawingProjectResponse;
import com.company.module.autodrawing.dto.DrawingProjectSaveRequest;
import com.company.module.autodrawing.service.DrawingProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 자동도면 프로젝트 API 컨트롤러
 *
 * GET  /api/autodrawing/projects       — 팀별 프로젝트 목록
 * POST /api/autodrawing/projects       — 프로젝트 저장 (전체 동기화)
 * GET  /api/autodrawing/projects/{uuid} — 단일 프로젝트 조회
 */
@RestController
@RequestMapping("/api/autodrawing/projects")
@RequiredArgsConstructor
public class AutodrawingProjectController {

    private final DrawingProjectService projectService;

    /** 팀별 프로젝트 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DrawingProjectResponse>>> getProjects(
            @RequestParam(defaultValue = "default") String teamId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.getProjects(teamId)));
    }

    /** 프로젝트 저장 (전체 동기화 방식) */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveProjects(
            @RequestParam(defaultValue = "default") String teamId,
            @RequestBody DrawingProjectSaveRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> result = projectService.saveProjects(
                teamId,
                request.getProjects(),
                Boolean.TRUE.equals(request.getForce()),
                user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 단일 프로젝트 조회 */
    @GetMapping("/{projectUuid}")
    public ResponseEntity<ApiResponse<DrawingProjectResponse>> getProject(
            @PathVariable String projectUuid) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.getProject(projectUuid)));
    }
}
