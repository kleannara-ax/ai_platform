package com.company.module.fire.service;

import com.company.module.fire.dto.ExtinguisherResponse;
import java.time.LocalDate;
import java.util.*;
import java.text.Collator;

/** One server-side policy for list, counters and export. Dates use Asia/Seoul. */
public final class ExtinguisherListQuery {
    private ExtinguisherListQuery() {}
    public record Counts(long inspectCount, long plannedCount, long urgentCount) {}
    public record Result(List<ExtinguisherResponse> content, long totalElements, int totalPages,
                         int number, int size, long totalMatching, Counts summary,
                         List<String> types, LocalDate asOfDate) {}

    /** Full inventory and server-evaluated status memberships for in-tab filtering. */
    public record Snapshot(Result inventory, Map<Long, List<String>> statuses) {}

    public static Snapshot snapshot(Result inventory) {
        Map<Long, List<String>> statuses = new LinkedHashMap<>();
        for (ExtinguisherResponse row : inventory.content()) {
            statuses.put(row.getExtinguisherId(), java.util.stream.Stream.of("inspect", "planned", "urgent")
                    .filter(status -> matches(row, status, inventory.asOfDate())).toList());
        }
        return new Snapshot(inventory, statuses);
    }

    static boolean matches(ExtinguisherResponse row, String status, LocalDate today) {
        boolean inspect = row.getLastInspectionDate() == null ||
                !row.getLastInspectionDate().plusDays(30).isAfter(today);
        LocalDate due = row.getReplacementDueDate();
        boolean urgent = Boolean.TRUE.equals(row.getLastIsFaulty()) ||
                (due != null && !due.isAfter(today.plusDays(30)));
        boolean planned = due != null && !due.isBefore(today) &&
                !due.isAfter(today.plusDays(180)) && !urgent;
        return switch (status) {
            case "inspect" -> inspect;
            case "planned" -> planned;
            case "urgent" -> urgent;
            default -> true;
        };
    }

    public static Result evaluate(List<ExtinguisherResponse> rows, List<String> types,
            String type, String status, String sort, String direction,
            int page, int size, boolean exportAll, LocalDate today) {
        String state = status == null ? "" : status;
        if (!Set.of("", "inspect", "planned", "urgent").contains(state))
            throw new IllegalArgumentException("지원하지 않는 상태 필터입니다.");
        String key = sort == null || sort.isBlank() ? "extinguisherId" : sort;
        if (!Set.of("extinguisherId", "rowNo", "serialNumber", "buildingName", "floorName",
                "extinguisherType", "manufactureDate", "replacementDueDate", "lastInspectionDate",
                "lastInspectorName", "lastIsFaulty", "lastFaultReason", "note").contains(key))
            throw new IllegalArgumentException("지원하지 않는 정렬 항목입니다.");
        if (!Set.of("asc", "desc").contains(direction))
            throw new IllegalArgumentException("지원하지 않는 정렬 방향입니다.");
        List<ExtinguisherResponse> base = rows.stream()
                .filter(r -> type == null || type.isBlank() || type.equals(r.getExtinguisherType())).toList();
        Counts counts = new Counts(base.stream().filter(r -> matches(r, "inspect", today)).count(),
                base.stream().filter(r -> matches(r, "planned", today)).count(),
                base.stream().filter(r -> matches(r, "urgent", today)).count());
        Collator collator = Collator.getInstance(Locale.KOREAN);
        Comparator<ExtinguisherResponse> comparator = (a, b) -> {
            String av = value(a, key), bv = value(b, key);
            // Empty values remain last in both directions.
            if (av.isBlank() != bv.isBlank()) return av.isBlank() ? 1 : -1;
            int cmp = naturalCompare(av, bv, collator);
            if (cmp != 0) return "desc".equals(direction) ? -cmp : cmp;
            return Comparator.nullsLast(Long::compareTo).compare(a.getExtinguisherId(), b.getExtinguisherId());
        };
        List<ExtinguisherResponse> filtered = base.stream().filter(r -> matches(r, state, today))
                .sorted(comparator).toList();
        int limit = Math.max(1, Math.min(size, 200));
        int pages = (filtered.size() + limit - 1) / limit;
        int number = Math.min(Math.max(0, page), Math.max(0, pages - 1));
        int start = number * limit;
        List<ExtinguisherResponse> content = exportAll ? filtered :
                filtered.subList(start, Math.min(start + limit, filtered.size()));
        return new Result(content, filtered.size(), pages, number, limit, base.size(), counts,
                types, today);
    }

    private static String value(ExtinguisherResponse r, String key) {
        Object value = switch (key) {
            case "extinguisherId" -> r.getExtinguisherId();
            case "rowNo", "serialNumber" -> r.getSerialNumber();
            case "buildingName" -> r.getBuildingName();
            case "floorName" -> r.getFloorName();
            case "extinguisherType" -> r.getExtinguisherType();
            case "manufactureDate" -> r.getManufactureDate();
            case "replacementDueDate" -> r.getReplacementDueDate();
            case "lastInspectionDate" -> r.getLastInspectionDate();
            case "lastInspectorName" -> r.getLastInspectorName();
            case "lastIsFaulty" -> r.getLastInspectionDate() == null ? null :
                    (Boolean.TRUE.equals(r.getLastIsFaulty()) ? "비정상" : "정상");
            case "lastFaultReason" -> Boolean.TRUE.equals(r.getLastIsFaulty()) ? r.getLastFaultReason() : null;
            case "note" -> r.getNote();
            default -> null;
        };
        return value == null ? "" : value.toString();
    }

    /** Natural numeric order: EXT-2 precedes EXT-10, including mixed Korean labels. */
    static int naturalCompare(String a, String b, Collator collator) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int ai = i, bj = j;
            boolean an = a.charAt(i) >= '0' && a.charAt(i) <= '9';
            boolean bn = b.charAt(j) >= '0' && b.charAt(j) <= '9';
            while (i < a.length() && (a.charAt(i) >= '0' && a.charAt(i) <= '9') == an) i++;
            while (j < b.length() && (b.charAt(j) >= '0' && b.charAt(j) <= '9') == bn) j++;
            String ap = a.substring(ai, i), bp = b.substring(bj, j);
            int cmp = an && bn ? new java.math.BigInteger(ap).compareTo(new java.math.BigInteger(bp)) :
                    collator.compare(ap.toLowerCase(Locale.ROOT), bp.toLowerCase(Locale.ROOT));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
