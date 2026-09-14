package com.company.module.fire.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.fire.entity.FireSprinkler;
import com.company.module.fire.entity.FireSprinklerInspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 스프링클러 점검표 정의.
 * <ul>
 *   <li>STANDARD      : 표준 점검표 (주차타워 외 모든 건물, 신규 점검부터 적용)</li>
 *   <li>PARKING_TOWER : 주차타워 건물은 기존 점검표를 그대로 사용</li>
 *   <li>STATUS_ONLY   : 점검표 교체 이전 이력 (주차타워 제외) — 정상/비정상 결과만 표시</li>
 * </ul>
 * PC 화면과 모바일 QR 점검 화면은 이 정의를 API 로 받아 그린다.
 */
public final class SprinklerChecklist {

    public static final String TYPE_STANDARD = "STANDARD";
    public static final String TYPE_PARKING_TOWER = "PARKING_TOWER";
    public static final String TYPE_STATUS_ONLY = "STATUS_ONLY";

    private static final String PARKING_TOWER_BUILDING_NAME = "주차타워";

    public record Item(String key, String label) {
    }

    public record Group(String title, List<Item> items) {
    }

    public record CheckedItem(String itemKey, String itemLabel, String result) {
    }

    private static final List<Group> STANDARD_GROUPS = List.of(
            new Group("스프링클러 배관", List.of(
                    new Item("forklift_pipe_deform", "지게차·물류 이동 중 배관 휘어짐·찌그러짐"),
                    new Item("forklift_valve_leak", "지게차·물류 이동 중 밸브 파손·누수"),
                    new Item("forklift_pipe_finish", "지게차·물류 이동 중 배관 마감재 탈락·벌어짐"),
                    new Item("forklift_head_reflector", "지게차·물류 이동 중 헤드·반사판 탈락·변형")
            )),
            new Group("제품", List.of(
                    new Item("product_clearance_60cm", "헤드로 부터 제품 이격거리 60cm 이격거리 확보")
            ))
    );

    /** 기존 점검표 — 키는 fire_sprinkler_inspection 의 개별 상태 컬럼과 1:1 대응 */
    private static final List<Group> PARKING_TOWER_GROUPS = List.of(
            new Group("스프링클러 배관", List.of(
                    new Item("pipe_damage", "배관 파손여부 확인(휘거나 찌그러짐)"),
                    new Item("pipe_connection", "배관 연결부 상태 확인(플랜지, 나사부, 엘보 등)"),
                    new Item("pipe_support", "배관 지지대 상태 확인(고정 및 풀림 확인)"),
                    new Item("drain_valve", "드레인 벨브 누수 상태 확인(벨브 파손 및 잠금상태)"),
                    new Item("drain_pipe_sealing", "드레인 배관 실리콘 마감상태 확인")
            )),
            new Group("스프링클러 헤드 반사판", List.of(
                    new Item("head_reflector", "헤드 반사판 탈락여부 확인")
            )),
            new Group("제품", List.of(
                    new Item("product_clearance", "헤드로부터 제품 이격거리 60cm 이격거리 확보 여부")
            ))
    );

    private SprinklerChecklist() {
    }

    public static boolean isParkingTower(FireSprinkler sprinkler) {
        if (sprinkler == null || sprinkler.getBuilding() == null || sprinkler.getBuilding().getBuildingName() == null) {
            return false;
        }
        return PARKING_TOWER_BUILDING_NAME.equals(sprinkler.getBuilding().getBuildingName().replaceAll("\\s+", ""));
    }

    /** 스프링클러에 지금 점검할 때 적용되는 점검표 유형 */
    public static String currentType(FireSprinkler sprinkler) {
        return isParkingTower(sprinkler) ? TYPE_PARKING_TOWER : TYPE_STANDARD;
    }

    /** 저장된 점검 이력의 점검표 유형 (유형 미기록 이력은 교체 이전 이력으로 본다) */
    public static String effectiveType(FireSprinklerInspection inspection) {
        String stored = inspection.getChecklistType();
        if (TYPE_STANDARD.equals(stored) || TYPE_PARKING_TOWER.equals(stored) || TYPE_STATUS_ONLY.equals(stored)) {
            return stored;
        }
        return isParkingTower(inspection.getSprinkler()) ? TYPE_PARKING_TOWER : TYPE_STATUS_ONLY;
    }

    public static List<Group> groups(String type) {
        if (TYPE_STANDARD.equals(type)) {
            return STANDARD_GROUPS;
        }
        if (TYPE_PARKING_TOWER.equals(type)) {
            return PARKING_TOWER_GROUPS;
        }
        return List.of();
    }

    public static List<Item> items(String type) {
        return groups(type).stream().flatMap(group -> group.items().stream()).toList();
    }

    /**
     * 요청 결과(항목키 → 결과)를 점검표 순서·서버 정의 라벨로 정규화한다.
     * 점검표에 있는 항목은 모두 양호/불량 중 하나가 선택돼야 한다.
     */
    public static List<CheckedItem> normalize(String type, Map<String, String> resultsByKey) {
        List<Item> templateItems = items(type);
        if (templateItems.isEmpty()) {
            throw new BusinessException("적용할 스프링클러 점검표가 없습니다.");
        }
        List<CheckedItem> checked = new ArrayList<>();
        for (Item item : templateItems) {
            String raw = resultsByKey == null ? null : resultsByKey.get(item.key());
            if (raw == null || raw.isBlank()) {
                throw new BusinessException("'" + item.label() + "' 결과를 선택해 주세요.");
            }
            checked.add(new CheckedItem(item.key(), item.label(), normalizeResult(raw)));
        }
        return checked;
    }

    public static String resolveStatus(List<CheckedItem> items) {
        return items.stream().anyMatch(item -> "FAULTY".equals(item.result())) ? "FAULTY" : "NORMAL";
    }

    public static String labelOf(String type, String key) {
        return items(type).stream()
                .filter(item -> item.key().equals(key))
                .map(Item::label)
                .findFirst()
                .orElse(null);
    }

    private static String normalizeResult(String result) {
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "양호", "정상", "GOOD", "NORMAL" -> "NORMAL";
            case "불량", "비정상", "FAULTY" -> "FAULTY";
            default -> throw new BusinessException("점검 결과는 양호 또는 불량만 가능합니다.");
        };
    }
}
