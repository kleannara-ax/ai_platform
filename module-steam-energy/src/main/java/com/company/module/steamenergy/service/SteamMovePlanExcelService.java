package com.company.module.steamenergy.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.steamenergy.dto.SteamMovePlanImportResponse;
import com.company.module.steamenergy.entity.SteamMovePlanMachine;
import com.company.module.steamenergy.repository.SteamMovePlanMachineRepository;
import com.company.module.steamenergy.service.excel.ProductionPlanWorkbookReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.TreeSet;

/**
 * 운영 최적화 — 생산계획 엑셀 가져오기.
 *
 * <p>파일에 값이 있는 (연·월·호기)만 생산량·가동시간·구분을 바꾼다. 직접 넣은 원단위(UNIT_OVERRIDE)와
 * 파일에 없는 달은 건드리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamMovePlanExcelService {

    private final SteamMovePlanMachineRepository machineRepository;

    @Transactional
    public SteamMovePlanImportResponse importProductionPlan(MultipartFile file, int baseYear) {
        ProductionPlanWorkbookReader.Result result;
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            result = ProductionPlanWorkbookReader.read(workbook, baseYear);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "생산계획 엑셀을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
        if (result.rows().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "'" + result.sheetName() + "' 시트에서 제지2·제지3·화장지3~5 의 생산량이나 가동시간을 찾지 못했습니다.");
        }
        int[] counts = apply(result.rows());
        TreeSet<String> months = new TreeSet<>();
        for (ProductionPlanWorkbookReader.PlanRow row : result.rows()) {
            months.add(String.format("%04d-%02d", row.year(), row.month()));
        }
        log.info("[STEAM-ENERGY] 생산계획 엑셀 가져오기 - 시트 '{}' 달 {} 신규 {} 갱신 {}", result.sheetName(), months, counts[0], counts[1]);
        return SteamMovePlanImportResponse.of(result.sheetName(), List.copyOf(months), counts[0], counts[1]);
    }

    /** [신규, 갱신] 행 수 */
    int[] apply(List<ProductionPlanWorkbookReader.PlanRow> rows) {
        int inserted = 0, updated = 0;
        for (ProductionPlanWorkbookReader.PlanRow r : rows) {
            SteamMovePlanMachine active = machineRepository.findActive(r.year(), r.month(), r.machineCode()).orElse(null);
            if (active != null) {
                active.update(r.productionTon(), r.runtimeHours(), r.planKind(), active.getUnitOverride(), null);
                updated += 1;
                continue;
            }
            SteamMovePlanMachine deleted = machineRepository.findAnyByKey(r.year(), r.month(), r.machineCode()).orElse(null);
            if (deleted != null) {
                deleted.restore(null);
                deleted.update(r.productionTon(), r.runtimeHours(), r.planKind(), null, null);
                updated += 1;
                continue;
            }
            machineRepository.save(SteamMovePlanMachine.builder()
                    .yearNo(r.year())
                    .monthNo(r.month())
                    .machineCode(r.machineCode())
                    .productionTon(r.productionTon())
                    .runtimeHours(r.runtimeHours())
                    .planKind(r.planKind())
                    .build());
            inserted += 1;
        }
        return new int[]{inserted, updated};
    }
}
