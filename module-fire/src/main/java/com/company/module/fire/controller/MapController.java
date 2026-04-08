package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.ExtinguisherRepository;
import com.company.module.fire.repository.FireHydrantRepository;
import com.company.module.fire.repository.FloorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fire-api/maps")
@RequiredArgsConstructor
public class MapController {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;

    @GetMapping("/floor-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFloorData(
            @RequestParam Long buildingId,
            @RequestParam Long floorId) {

        if (buildingId == null || buildingId <= 0 || floorId == null || floorId <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid buildingId/floorId"));
        }

        Building building = buildingRepository.findById(buildingId).orElse(null);
        Floor floor = floorRepository.findById(floorId).orElse(null);
        if (building == null || floor == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Building/Floor not found"));
        }

        String buildingName = building.getBuildingName() == null ? "" : building.getBuildingName();
        String floorName = floor.getFloorName() == null ? "" : floor.getFloorName();

        List<Map<String, Object>> extinguishers = new ArrayList<>();
        for (Extinguisher e : extinguisherRepository.findForMap(buildingId, floorId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("extinguisherId", e.getExtinguisherId());
            row.put("x", e.getX());
            row.put("y", e.getY());
            extinguishers.add(row);
        }

        List<Map<String, Object>> hydrants = new ArrayList<>();
        for (FireHydrant h : fireHydrantRepository.findForMap("Indoor", buildingId, floorId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hydrantId", h.getHydrantId());
            row.put("hydrantType", h.getHydrantType());
            row.put("x", h.getX());
            row.put("y", h.getY());
            hydrants.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("buildingName", buildingName);
        result.put("floorId", floorId);
        result.put("floorName", floorName);
        result.put("planImagePath", resolvePlanImagePath(buildingName, floorName));
        result.put("extinguishers", extinguishers);
        result.put("hydrants", hydrants);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String resolvePlanImagePath(String buildingName, String floorName) {
        String building = normalize(buildingName);
        String floor = normalizeFloor(floorName);

        if (containsAny(building, "\uBCF5\uC9C0\uAD00", "bokji")) {
            if (isBasement(floor)) return "/images/bokji_B1.png";
            if (floor.contains("3")) return "/images/bokji_3F.png";
            if (floor.contains("2")) return "/images/bokji_2F.png";
            if (floor.contains("1")) return "/images/bokji_1F.png";
        }
        if (containsAny(building, "\uAD00\uB9AC\uB3D9", "gwanri")) {
            if (floor.contains("2")) return "/images/gwanri_2F.PNG";
            if (floor.contains("1")) return "/images/gwanri_1F.png";
        }
        if (containsAny(building, "\uC625\uC678", "outdoor")) {
            return "/images/drone_photo.JPG";
        }
        if (containsAny(building, "\uC81C\uC9C01,2\uD638\uAE30", "\uC81C\uC9C012", "jeji12", "jeji2")) {
            if (floor.contains("2")) return "/images/jeji1,2_2F.PNG";
            if (floor.contains("1")) return "/images/jeji1,2_1F.PNG";
        }
        if (containsAny(building, "\uC81C\uC9C03\uD638\uAE30", "jeji3")) {
            if (floor.contains("2")) return "/images/jeji3_2F.PNG";
            if (floor.contains("1")) return "/images/jeji3_1F.PNG";
        }
        if (containsAny(building, "\uC2EC\uBA74\uD384\uD37C", "palpa", "pulper")) {
            if (floor.contains("2")) return "/images/palpa_2F.PNG";
            if (floor.contains("1")) return "/images/palpa_1F.PNG";
        }
        if (containsAny(building, "\uD328\uB4DC\uB3D9", "pad")) {
            if (floor.contains("2")) return "/images/pad_2F.PNG";
            if (floor.contains("1")) return "/images/pad_1F.PNG";
        }
        if (containsAny(building, "\uD654\uC7A5\uC9C0 3,6\uD638\uAE30", "\uD654\uC7A5\uC9C03,6\uD638\uAE30", "tissue36", "tissue13")) {
            if (floor.contains("2")) return "/images/tissue1,3_2F.PNG";
            if (floor.contains("1")) return "/images/tissue1,3_1F.PNG";
        }
        if (containsAny(building, "\uD654\uC7A5\uC9C0 4,5\uD638\uAE30", "\uD654\uC7A5\uC9C04,5\uD638\uAE30", "tissue45")) {
            if (isBasement(floor)) return "/images/tissue4,5_B1.PNG";
            if (floor.contains("3")) return "/images/tissue4,5_3F.PNG";
            if (floor.contains("2")) return "/images/tissue4,5_2F.PNG";
            if (floor.contains("1")) return "/images/tissue4,5_1F.PNG";
        }
        if (containsAny(building, "\uAE30\uC800\uADC0\uB3D9", "diaper")) {
            return "/images/diaper_1F.png";
        }
        return "";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replace(" ", "");
    }

    private String normalizeFloor(String floorName) {
        return normalize(floorName)
                .replace("(", "")
                .replace(")", "");
    }

    private boolean isBasement(String floor) {
        return floor.contains("b1") || floor.contains("\uC9C0\uD558");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate.toLowerCase().replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }
}
