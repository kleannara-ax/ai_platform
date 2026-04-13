package com.company.module.psinsp.controller;

import com.company.core.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PS 지분 검사 모듈 Health Check API
 *
 * <p>GET /ps-insp-api/health
 */
@Slf4j
@RestController
@RequestMapping("/ps-insp-api/health")
public class PsInspHealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "ok",
                "module", "module-ps-insp",
                "application", "PSPartInspection",
                "version", "7.0.0",
                "timestamp", LocalDateTime.now().toString()
        )));
    }
}
