package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 에너지 회계비용 5개 페이지 통합 워크북 처리.
 *
 * - 다운로드: 원본 템플릿 워크북 1개를 열어 4개 서비스가 자신의 시트만 채우고 1개 파일로 반환
 * - 업로드  : 업로드된 워크북에 대해 4개 서비스가 자신의 시트를 찾아 import. 시트 없으면 skip
 *
 * 이 서비스는 에너지 연료비 요약 (energy-fuel-summary) 페이지의 엑셀 버튼이 사용.
 */
@Service
public class EnergyAllWorkbookService {
    private static final String ORIGINAL_DIR = "원본";
    private static final String FILE_PREFIX = "6.";

    private final EnergyAccountingPlanWorkbookService eap;
    private final EnergyFuelPlanWorkbookService efp;
    private final EnergyFuelRatioWorkbookService efr;
    private final EnergyMonthCompareWorkbookService emc;
    private volatile Path cachedWorkbookPath;

    public EnergyAllWorkbookService(
            EnergyAccountingPlanWorkbookService eap,
            EnergyFuelPlanWorkbookService efp,
            EnergyFuelRatioWorkbookService efr,
            EnergyMonthCompareWorkbookService emc
    ) {
        this.eap = eap;
        this.efp = efp;
        this.efr = efr;
        this.emc = emc;
    }

    public Map<String, Object> importWorkbook(MultipartFile file, int year) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            int total = 0;
            total += eap.applyImport(workbook, year);
            total += efp.applyImport(workbook, year);
            total += efr.applyImport(workbook, year);
            total += emc.applyImport(workbook, year);
            return Map.of("year", year, "imported", total);
        }
    }

    public byte[] exportWorkbook(int year) throws IOException {
        Path workbookPath = resolveWorkbookPath();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setForceFormulaRecalculation(true);
            eap.applyExport(workbook, year);
            efp.applyExport(workbook, year);
            efr.applyExport(workbook, year);
            emc.applyExport(workbook, year);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Path resolveWorkbookPath() throws IOException {
        Path current = cachedWorkbookPath;
        if (current != null && Files.exists(current)) return current;
        try (Stream<Path> paths = Files.walk(Path.of(ORIGINAL_DIR), 1)) {
            Path resolved = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("에너지 회계비용 원본 엑셀을 찾을 수 없습니다."));
            cachedWorkbookPath = resolved;
            return resolved;
        }
    }
}
