package com.company.module.dailyreport.service;

import com.company.module.dailyreport.entity.CellAuth;
import com.company.module.dailyreport.entity.DailyReportCell;
import com.company.module.dailyreport.entity.DailyReportTable;
import com.company.module.dailyreport.repository.CellAuthRepository;
import com.company.module.dailyreport.repository.DailyReportCellRepository;
import com.company.module.dailyreport.repository.DailyReportTableRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ★ 셀 담당자 캐시 동기화 서비스 (하드코딩 제거 후속 조치)
 *
 * 배경:
 * - 과거에는 {@code DefaultCellTemplate}이 실제 직원 로그인ID/이름을 자바 코드에
 *   문자열로 박아 넣어 daily_report_cell.OWNER_IDS/OWNER_NAMES를 채웠다.
 * - 이제 담당자 지정은 오직 관리자가 '세부공장일보 컬럼관리' 화면에서
 *   daily_report_cell_auth 테이블에 등록하는 방식으로만 이루어진다.
 * - 하지만 CellService.isCellEditableForUser()는 셀 단위로 정확한 입력 주기
 *   (FREQ_CODE: daily/monthly/yearly/event)를 확인해야 하는데, CellAuth 한
 *   레코드는 (USER_ID, TABLE_CODE)당 FREQ_CODE 값을 하나만 가진다. 같은
 *   사용자가 같은 표 안에서 서로 다른 주기의 셀들을 담당하는 경우가 실제로
 *   존재하므로(예: yearly 셀과 monthly 셀을 동시에 담당), CellAuth의
 *   CELL_COORDS만으로는 셀별 정확한 주기 판정을 보장할 수 없다.
 * - 따라서 OWNER_IDS/OWNER_NAMES는 "하드코딩된 값"이 아니라, CellAuth가
 *   바뀔 때마다 이 서비스가 자동으로 재계산해 넣는 "읽기 전용 캐시"로 유지한다.
 *   담당자 지정을 위해 코드를 수정하거나 재배포할 필요가 전혀 없다.
 *
 * 동작:
 * - createAuth/updateAuth/deactivateAuth/deleteAuth 이후 영향받은
 *   tableCode 하나에 대해 syncTable(tableCode)을 호출한다.
 * - 해당 tableCode를 가진 "모든 일보(날짜)"의 DATA 셀을 순회하며,
 *   각 좌표(EXCEL_COORD)를 담당하는 활성 CellAuth들을 찾아 그 사용자들의
 *   core_user.LOGIN_ID/USER_NAME으로 OWNER_IDS(공백 구분)/OWNER_NAMES(쉼표 구분)를
 *   재계산한다. 아무도 담당하지 않는 좌표는 OWNER_IDS/OWNER_NAMES를 NULL로 되돌린다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CellOwnershipSyncService {

    private final CellAuthRepository cellAuthRepository;
    private final DailyReportCellRepository cellRepository;
    private final DailyReportTableRepository tableRepository;
    private final EntityManager entityManager;

    /**
     * 특정 표 코드(TABLE_CODE)의 담당자 캐시를 CellAuth 기준으로 재계산한다.
     * - CellAuthService의 create/update/deactivate/delete 이후 호출된다.
     * - 이 표 코드를 가진 "이미 DB에 저장된 모든 일보(날짜)"의 DATA 셀이 대상.
     */
    public void syncTable(String tableCode) {
        CoordOwnerMap coordOwnerMap = buildCoordOwnerMap(tableCode);

        List<DailyReportCell> dataCells = cellRepository.findDataCellsByTableCode(tableCode);
        for (DailyReportCell cell : dataCells) {
            applyOwnerCache(cell, coordOwnerMap);
        }
    }

    /**
     * ★ 전체 표 코드에 대해 syncTable()을 일괄 실행 (마이그레이션/운영 배포 직후,
     * 또는 관리자가 '전체 재동기화'를 수동 실행할 때 사용).
     * - 기존 하드코딩 시절 데이터가 남아있는 daily_report_cell.OWNER_IDS를
     *   daily_report_cell_auth 기준으로 다시 계산해 정합성을 맞추는 용도.
     */
    public void syncAllTables() {
        for (String tableCode : tableRepository.findDistinctTableCodes()) {
            syncTable(tableCode);
        }
    }

    /**
     * ★ 신규 일보 생성 시 사용 — 아직 DB에 저장되지 않은(영속화 전) 표의
     * 메모리 상 셀 목록에 현재 활성 CellAuth 담당자를 즉시 반영한다.
     *
     * DefaultCellTemplate.populateDefaultCells()가 만든 셀은 항상
     * OWNER_IDS=NULL 상태로 시작하므로, 이 메서드를 호출하지 않으면 관리자가
     * 이미 배정해 둔 담당자가 "새로 생성되는 일보"에는 반영되지 않고 다음
     * CellAuth 변경(수정/재저장) 시점까지 비어 있게 된다.
     */
    public void applyCurrentOwnersToNewTable(DailyReportTable table) {
        CoordOwnerMap coordOwnerMap = buildCoordOwnerMap(table.getTableCode());
        for (DailyReportCell cell : table.getCells()) {
            if (!"DATA".equals(cell.getCellType())) continue;
            applyOwnerCache(cell, coordOwnerMap);
        }
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    /** 좌표별 담당자 로그인ID/이름 매핑 (private record 용도) */
    private record CoordOwnerMap(Map<String, String> coordToOwnerIds,
                                  Map<String, String> coordToOwnerNames) {
    }

    private void applyOwnerCache(DailyReportCell cell, CoordOwnerMap map) {
        String coord = cell.getExcelCoord();
        String normalized = coord == null ? null : coord.trim().toUpperCase();
        String ownerIds = normalized == null ? null : map.coordToOwnerIds().get(normalized);
        String ownerNames = normalized == null ? null : map.coordToOwnerNames().get(normalized);

        // 담당자가 없으면 캐시를 비운다 (하드코딩 잔재/스테일 데이터 방지)
        if (!Objects.equals(cell.getOwnerIds(), ownerIds)
                || !Objects.equals(cell.getOwnerNames(), ownerNames)) {
            cell.syncOwnerCache(ownerIds, ownerNames);
        }
    }

    /** 표 코드의 활성 CellAuth 전체로부터 좌표별 담당자 문자열 매핑을 구성 */
    private CoordOwnerMap buildCoordOwnerMap(String tableCode) {
        List<CellAuth> activeAuths = cellAuthRepository.findByTableCodeAndIsActiveTrue(tableCode);

        // 좌표(EXCEL_COORD) → [userId...] 매핑
        Map<String, List<Long>> coordToUserIds = new LinkedHashMap<>();
        for (CellAuth auth : activeAuths) {
            for (String coord : auth.getCellCoordList()) {
                String normalized = coord == null ? null : coord.trim().toUpperCase();
                if (normalized == null || normalized.isEmpty()) continue;
                coordToUserIds.computeIfAbsent(normalized, k -> new ArrayList<>())
                        .add(auth.getUserId());
            }
        }

        // 등장하는 모든 userId의 LOGIN_ID/USER_NAME을 일괄 조회 (Architecture Rule #4)
        Set<Long> allUserIds = coordToUserIds.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<Long, String[]> userInfo = resolveLoginIdAndName(allUserIds);

        Map<String, String> coordToOwnerIds = new LinkedHashMap<>();
        Map<String, String> coordToOwnerNames = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : coordToUserIds.entrySet()) {
            String ownerIds = entry.getValue().stream()
                    .map(id -> userInfo.getOrDefault(id, new String[]{null, null})[0])
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(" "));
            String ownerNames = entry.getValue().stream()
                    .map(id -> userInfo.getOrDefault(id, new String[]{null, null})[1])
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));

            if (!ownerIds.isBlank()) coordToOwnerIds.put(entry.getKey(), ownerIds);
            if (!ownerNames.isBlank()) coordToOwnerNames.put(entry.getKey(), ownerNames);
        }

        return new CoordOwnerMap(coordToOwnerIds, coordToOwnerNames);
    }

    /**
     * userId 목록에 해당하는 core_user의 LOGIN_ID, USER_NAME을 일괄 조회
     * - Architecture Rule #4: core 모듈 Entity를 직접 import하지 않고
     *   EntityManager native query로 core_user 테이블 조회
     * @return Map<userId, String[]{loginId, userName}>
     */
    @SuppressWarnings("unchecked")
    private Map<Long, String[]> resolveLoginIdAndName(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT user_id, login_id, " +
                        "       COALESCE(NULLIF(TRIM(user_name), ''), login_id) AS user_name " +
                        "FROM core_user WHERE user_id IN (:ids)")
                .setParameter("ids", userIds)
                .getResultList();

        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> new String[]{ (String) row[1], (String) row[2] },
                (a, b) -> a
        ));
    }
}
