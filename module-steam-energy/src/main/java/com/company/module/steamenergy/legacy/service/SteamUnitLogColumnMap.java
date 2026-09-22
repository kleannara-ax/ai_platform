package com.company.module.steamenergy.legacy.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 원단위 일지의 <b>DB 항목 ↔ 원본 머리글</b> 매핑표.
 *
 * <p>예전에는 열 번호를 코드에 박아 뒀다({@code main_steam=1, coater_steam=2 …}).
 * 원본에 열이 하나 추가되면 그 오른쪽 값이 전부 다른 항목으로 저장되므로,
 * 이제 <b>설비 이름 + 항목 이름</b>으로 열을 찾는다. 머리글 글자는 원본에 적힌 그대로 적는다.
 *
 * <p>설비마다 있는 항목이 다르다 — PM-2/PM-3 은 Main·Coater 를 나눠 적고,
 * TM-3~5 는 스팀량 하나로 적는다.
 */
public final class SteamUnitLogColumnMap {

    /** 설비별 (DB 항목 → 머리글 항목명) */
    private static final Map<String, Map<String, String>> BY_EQUIPMENT = Map.of(
            "PM-2", ordered(
                    "main_steam", "Main 스팀량",
                    "coater_steam", "Coater 스팀량",
                    "production", "생산량(kg)",
                    "production_type", "생산지종"),
            "PM-3", ordered(
                    "main_steam", "Main 스팀량",
                    "coater_steam", "Coater 스팀량",
                    "ventilation_steam", "환기시스템 스팀량",
                    "production", "생산량(kg)",
                    "production_type", "생산지종"),
            "TM-3", ordered(
                    "steam", "스팀량(톤)",
                    "production", "생산량(kg)"),
            "TM-4", ordered(
                    "steam", "스팀량(톤)",
                    "production", "생산량(kg)"),
            "TM-5", ordered(
                    "steam", "스팀량(톤)",
                    "production", "생산량(kg)")
    );

    /** 설비 밑에 딸린 항목 — 디스퍼져는 PM-3 칸에 있다. */
    private static final String DISPERSER_EQUIPMENT = "PM-3";
    private static final String DISPERSER_ITEM = "디스퍼져 스팀량";
    /** 설비 없이 맨 윗줄에만 이름이 있는 열. */
    private static final String TOC_LABEL = "TOC 스팀량  (환경관리)";
    private static final String PICA_LABEL = "PICA-121Vent";

    /** 설비의 항목별 열 번호. 머리글에서 못 찾은 항목은 담지 않는다(→ 호출부가 건너뛴다). */
    public static Map<String, Integer> columns(HeaderColumnIndex index, String equipment) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        BY_EQUIPMENT.getOrDefault(equipment, Map.of()).forEach((field, header) -> {
            Integer col = index.column(equipment, header);
            if (col != null) columns.put(field, col);
        });
        return columns;
    }

    /** 설비에 속하지 않는 항목들(디스퍼져 총량·TOC·PICA-121). */
    public static Map<String, Integer> rawColumns(HeaderColumnIndex index) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        Integer disperser = index.column(DISPERSER_EQUIPMENT, DISPERSER_ITEM);
        if (disperser != null) columns.put("disperser_total", disperser);
        Integer toc = index.columnByTopLabel(TOC_LABEL);
        if (toc != null) columns.put("toc_steam", toc);
        Integer pica = index.columnByTopLabel(PICA_LABEL);
        if (pica != null) columns.put("pica121_vent", pica);
        return columns;
    }

    /** 다운로드에서 비울 열 목록 — 찾은 열만 모은다. */
    @SafeVarargs
    public static int[] allColumns(Map<String, Integer>... maps) {
        return List.of(maps).stream()
                .flatMap(map -> map.values().stream())
                .mapToInt(Integer::intValue)
                .distinct()
                .toArray();
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private SteamUnitLogColumnMap() {
    }
}
