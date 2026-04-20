package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 소방시설 대시보드 통계 API
 */
@RestController
@RequestMapping("/fire-api/dashboard")
@RequiredArgsConstructor
public class FireDashboardController {

    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final FirePumpRepository firePumpRepository;
    private final FireReceiverRepository fireReceiverRepository;
    private final BuildingRepository buildingRepository;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long extCount = extinguisherRepository.count();
        long hydCount = fireHydrantRepository.count();
        long pumpCount = firePumpRepository.count();
        long recvCount = fireReceiverRepository.count();

        stats.put("extinguisherCount", extCount);
        stats.put("hydrantCount", hydCount);
        stats.put("pumpCount", pumpCount);
        stats.put("receiverCount", recvCount);
        stats.put("totalEquipment", extCount + hydCount + pumpCount + recvCount);
        stats.put("buildingCount", buildingRepository.count());

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
