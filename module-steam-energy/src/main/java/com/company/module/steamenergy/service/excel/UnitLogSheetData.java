package com.company.module.steamenergy.service.excel;

import com.company.module.steamenergy.dto.SteamFuelDailyUsageSaveRequest;
import com.company.module.steamenergy.dto.SteamLngDailyUsageSaveRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonSaveRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageSaveRequest;

import java.util.List;

/**
 * 원단위 일지 월 시트 하나를 읽은 결과 — 네 표의 저장 요청 목록.
 *
 * @param sheetName 시트 이름 (예: "3월일지")
 * @param notFound  제목을 못 찾아 건너뛴 칸 (가져오기 결과에 그대로 보여 준다)
 */
public record UnitLogSheetData(
        String sheetName,
        int year,
        int monthNo,
        List<SteamUnitDailyUsageSaveRequest> unitRows,
        List<SteamUnitDailyCommonSaveRequest> commonRows,
        List<SteamLngDailyUsageSaveRequest> lngRows,
        List<SteamFuelDailyUsageSaveRequest> fuelRows,
        List<String> notFound
) {
}
