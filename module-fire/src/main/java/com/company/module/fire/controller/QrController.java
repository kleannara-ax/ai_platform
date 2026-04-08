package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.QrExtItem;
import com.company.module.fire.dto.QrHydItem;
import com.company.module.fire.dto.QrPumpItem;
import com.company.module.fire.dto.QrReceiverItem;
import com.company.module.fire.entity.Building;
import com.company.module.fire.entity.Extinguisher;
import com.company.module.fire.entity.FireHydrant;
import com.company.module.fire.entity.FirePump;
import com.company.module.fire.entity.FireReceiver;
import com.company.module.fire.entity.Floor;
import com.company.module.fire.repository.BuildingRepository;
import com.company.module.fire.repository.ExtinguisherRepository;
import com.company.module.fire.repository.FireHydrantRepository;
import com.company.module.fire.repository.FirePumpRepository;
import com.company.module.fire.repository.FireReceiverRepository;
import com.company.module.fire.repository.FloorRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * QR 코드 관련 API
 * <p>
 * 기존 ASP.NET: Pages/Qr/Index 대응
 * - GET /fire-api/qr/image?type=ext&id=EXT-000001  → QR PNG 이미지 반환 (인증 불필요)
 * - GET /fire-api/qr/list                           → 소화기/소화전 목록 (인증 필요)
 * - GET /fire-api/qr/buildings                      → 건물 목록 (인증 필요)
 * - GET /fire-api/qr/floors                         → 층 목록 (인증 필요)
 * - GET /fire-api/qr/unregistered-serials           → 미등록 시리얼 생성 (인증 필요)
 */
@RestController
@RequestMapping("/fire-api/qr")
@RequiredArgsConstructor
public class QrController {

    private final ExtinguisherRepository extinguisherRepository;
    private final FireHydrantRepository fireHydrantRepository;
    private final FireReceiverRepository fireReceiverRepository;
    private final FirePumpRepository firePumpRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    /**
     * QR 이미지 생성 (PNG)
     * GET /fire-api/qr/image?type=ext&id={qrKey}
     * 인증 없이 접근 가능 (SecurityConfig에서 permitAll 설정 필요)
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> getQrImage(
            @RequestParam String type,
            @RequestParam String id,
            HttpServletRequest request) throws WriterException, IOException {

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443
                        ? ":" + request.getServerPort() : "");

        String url;
        if ("hyd".equalsIgnoreCase(type)) {
            url = baseUrl + "/minspection/hydrants/" + id;
        } else if ("receiver".equalsIgnoreCase(type) || "rcv".equalsIgnoreCase(type)) {
            url = baseUrl + "/minspection/receivers/" + id;
        } else if ("pump".equalsIgnoreCase(type) || "pmp".equalsIgnoreCase(type)) {
            url = baseUrl + "/minspection/pumps/" + id;
        } else {
            url = baseUrl + "/minspection/extinguishers/" + id;
        }

        byte[] png = generateQrPng(url, 240);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(png);
    }

    /**
     * 소화기 + 소화전 목록 조회
     * GET /fire-api/qr/list?buildingId=1&floorId=2
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId) {

        Long bId = (buildingId != null && buildingId > 0) ? buildingId : null;
        Long fId = (floorId != null && floorId > 0) ? floorId : null;
        String buildingNameFilter = bId == null ? null : buildingRepository.findById(bId)
                .map(Building::getBuildingName)
                .map(String::trim)
                .orElse(null);

        List<Extinguisher> extList;
        List<FireHydrant> hydList;
        List<FireReceiver> receiverList;
        List<FirePump> pumpList;

        if (bId == null && fId == null) {
            extList = extinguisherRepository.findAll();
            hydList = fireHydrantRepository.findAll();
            receiverList = fireReceiverRepository.findAll();
            pumpList = firePumpRepository.findAll();
        } else if (bId != null && fId != null) {
            extList = extinguisherRepository.findByBuilding_BuildingIdAndFloor_FloorId(bId, fId);
            hydList = fireHydrantRepository.findByBuilding_BuildingIdAndFloor_FloorId(bId, fId);
            receiverList = fireReceiverRepository.findAll().stream()
                    .filter(item -> buildingNameMatches(item.getBuildingName(), buildingNameFilter))
                    .filter(item -> item.getFloor() != null
                            && fId.equals(item.getFloor().getFloorId()))
                    .collect(Collectors.toList());
            pumpList = firePumpRepository.findAll().stream()
                    .filter(item -> buildingNameMatches(item.getBuildingName(), buildingNameFilter))
                    .filter(item -> item.getFloor() != null
                            && fId.equals(item.getFloor().getFloorId()))
                    .collect(Collectors.toList());
        } else if (bId != null) {
            extList = extinguisherRepository.findByBuilding_BuildingId(bId);
            hydList = fireHydrantRepository.findByBuilding_BuildingId(bId);
            receiverList = fireReceiverRepository.findAll().stream()
                    .filter(item -> buildingNameMatches(item.getBuildingName(), buildingNameFilter))
                    .collect(Collectors.toList());
            pumpList = firePumpRepository.findAll().stream()
                    .filter(item -> buildingNameMatches(item.getBuildingName(), buildingNameFilter))
                    .collect(Collectors.toList());
        } else {
            extList = extinguisherRepository.findByFloor_FloorId(fId);
            hydList = fireHydrantRepository.findByFloor_FloorId(fId);
            receiverList = fireReceiverRepository.findAll().stream()
                    .filter(item -> item.getFloor() != null && fId.equals(item.getFloor().getFloorId()))
                    .collect(Collectors.toList());
            pumpList = firePumpRepository.findAll().stream()
                    .filter(item -> item.getFloor() != null && fId.equals(item.getFloor().getFloorId()))
                    .collect(Collectors.toList());
        }

        List<QrExtItem> extItems = extList.stream()
                .sorted(Comparator.comparingLong(Extinguisher::getExtinguisherId).reversed())
                .map(e -> new QrExtItem(
                        e.getExtinguisherId(),
                        e.getSerialNumber(),
                        e.getNoteKey(),
                        e.getBuilding() != null ? e.getBuilding().getBuildingName() : "-",
                        e.getFloor() != null ? e.getFloor().getFloorName() : "-",
                        e.getExtinguisherType(),
                        e.getManufactureDate() != null ? e.getManufactureDate().toString() : "-"))
                .collect(Collectors.toList());

        List<QrHydItem> hydItems = hydList.stream()
                .sorted(Comparator.comparingLong(FireHydrant::getHydrantId).reversed())
                .map(h -> new QrHydItem(
                        h.getHydrantId(),
                        h.getSerialNumber(),
                        h.getQrKey(),
                        h.getBuilding() != null ? h.getBuilding().getBuildingName() : "-",
                        h.getFloor() != null ? h.getFloor().getFloorName() : "-",
                        h.getHydrantType(),
                        h.getOperationType(),
                        h.getLocationDescription()))
                .collect(Collectors.toList());

        List<QrReceiverItem> receiverItems = receiverList.stream()
                .sorted(Comparator.comparingLong(FireReceiver::getReceiverId).reversed())
                .map(r -> new QrReceiverItem(
                        r.getReceiverId(),
                        r.getQrKey(),
                        r.getBuildingName(),
                        r.getFloor() != null ? r.getFloor().getFloorName() : "-"))
                .collect(Collectors.toList());

        List<QrPumpItem> pumpItems = pumpList.stream()
                .sorted(Comparator.comparingLong(FirePump::getPumpId).reversed())
                .map(p -> new QrPumpItem(
                        p.getPumpId(),
                        p.getQrKey(),
                        p.getBuildingName(),
                        p.getFloor() != null ? p.getFloor().getFloorName() : "-"))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extinguishers", extItems);
        result.put("hydrants", hydItems);
        result.put("receivers", receiverItems);
        result.put("pumps", pumpItems);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 건물 목록
     * GET /fire-api/qr/buildings
     */
    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings() {
        List<Building> buildings = buildingRepository.findAllByOrderByBuildingNameAsc();
        List<Map<String, Object>> list = buildings.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("buildingId", b.getBuildingId());
            m.put("buildingName", b.getBuildingName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * 층 목록
     * GET /fire-api/qr/floors
     */
    @GetMapping("/floors")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFloors() {
        List<Floor> floors = floorRepository.findAllByOrderBySortOrderAscFloorNameAsc();
        List<Map<String, Object>> list = floors.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("floorId", f.getFloorId());
            m.put("floorName", f.getFloorName());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * 미등록 시리얼 목록 생성
     * GET /fire-api/qr/unregistered-serials?extCount=5&hydCount=3
     */
    @GetMapping("/unregistered-serials")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnregisteredSerials(
            @RequestParam(defaultValue = "0") int extCount,
            @RequestParam(defaultValue = "0") int hydCount,
            @RequestParam(defaultValue = "0") int receiverCount,
            @RequestParam(defaultValue = "0") int pumpCount) {

        extCount = Math.max(0, Math.min(extCount, 500));
        hydCount = Math.max(0, Math.min(hydCount, 500));
        receiverCount = Math.max(0, Math.min(receiverCount, 500));
        pumpCount = Math.max(0, Math.min(pumpCount, 500));

        List<String> extSerials = Collections.emptyList();
        List<String> hydSerials = Collections.emptyList();
        List<String> receiverSerials = Collections.emptyList();
        List<String> pumpSerials = Collections.emptyList();

        if (extCount > 0) {
            extSerials = generateQrKeys(extCount, true);
        }
        if (hydCount > 0) {
            hydSerials = generateQrKeys(hydCount, false);
        }
        if (receiverCount > 0) {
            receiverSerials = generateQrKeysByType(receiverCount, "receiver");
        }
        if (pumpCount > 0) {
            pumpSerials = generateQrKeysByType(pumpCount, "pump");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unregisteredExtSerials", extSerials);
        result.put("unregisteredHydSerials", hydSerials);
        result.put("unregisteredReceiverSerials", receiverSerials);
        result.put("unregisteredPumpSerials", pumpSerials);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ===== Helper Methods =====

    private List<String> generateQrKeys(int count, boolean extinguisher) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            String key = UUID.randomUUID().toString().replace("-", "");
            boolean exists = extinguisher
                    ? extinguisherRepository.existsByNoteKey(key)
                    : fireHydrantRepository.existsByQrKey(key);
            if (!exists && !result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private List<String> generateQrKeysByType(int count, String type) {
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            String key = UUID.randomUUID().toString().replace("-", "");
            boolean exists;
            if ("receiver".equalsIgnoreCase(type)) {
                exists = fireReceiverRepository.findByQrKey(key).isPresent();
            } else {
                exists = firePumpRepository.findByQrKey(key).isPresent();
            }
            if (!exists && !result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private boolean buildingNameMatches(String source, String target) {
        String left = source == null ? "" : source.trim();
        String right = target == null ? "" : target.trim();
        if (right.isBlank()) {
            return true;
        }
        return left.equalsIgnoreCase(right);
    }

    private byte[] generateQrPng(String payload, int size) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, size, size, hints);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }
}
