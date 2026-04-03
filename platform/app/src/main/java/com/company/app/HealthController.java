package com.company.app;

import com.company.core.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health Check API
 * Apache Reverse Proxy에서 상태 확인 용도로 사용
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "application", "platform"
        );
        return ResponseEntity.ok(ApiResponse.success(healthInfo));
    }
}
