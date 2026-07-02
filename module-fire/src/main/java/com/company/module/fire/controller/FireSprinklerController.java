package com.company.module.fire.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.fire.dto.EquipmentInspectionRequest;
import com.company.module.fire.dto.EquipmentInspectionUpdateRequest;
import com.company.module.fire.dto.FireSprinklerResponse;
import com.company.module.fire.dto.FireSprinklerSaveRequest;
import com.company.module.fire.service.FireSprinklerService;
import com.company.module.fire.service.InspectorNameResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/fire-api/sprinklers")
@RequiredArgsConstructor
public class FireSprinklerController {

    private final FireSprinklerService fireSprinklerService;
    private final InspectorNameResolver inspectorNameResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FireSprinklerResponse>>> getList(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.getSprinklers(buildingId, floorId, q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.getSprinklerDetail(id)));
    }

    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> save(@Valid @RequestBody FireSprinklerSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.save(request)));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> inspect(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionRequest request,
            Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.inspect(
                id,
                request,
                inspectorNameResolver.resolveUserId(username),
                inspectorNameResolver.resolveDisplayName(username))));
    }

    @PatchMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> updateInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.updateInspection(id, inspectionId, request)));
    }

    @PostMapping("/{id}/inspections")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> addInspection(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentInspectionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.addInspection(id, request)));
    }

    @DeleteMapping("/{id}/inspections/{inspectionId}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<FireSprinklerResponse>> deleteInspection(
            @PathVariable Long id,
            @PathVariable Long inspectionId) {
        return ResponseEntity.ok(ApiResponse.success(fireSprinklerService.deleteInspection(id, inspectionId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fireSprinklerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{id}/inspections/export")
    public ResponseEntity<byte[]> exportInspections(
            @PathVariable Long id,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireSprinklerService.exportInspectionWorkbook(id, from, to);
        String filename = "sprinkler-inspections-" + id + "-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/inspections/export-all")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'FIRE_SPRINKLER')")
    public ResponseEntity<byte[]> exportAllInspections(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = fireSprinklerService.exportAllInspectionWorkbook(from, to);
        String filename = "sprinkler-inspections-all-" + from + "-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
