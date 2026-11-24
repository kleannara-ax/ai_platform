package com.company.module.personalinfo.service;

import com.company.module.personalinfo.entity.DestructionCycle;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.entity.PiContractFile;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정기 파기확인 (월 / 분기 / 반기) 기간 계산 규칙.
 * <ul>
 *   <li>기간 키: 월 {@code 2026-07}, 분기 {@code 2026-Q3}, 반기 {@code 2026-H2}</li>
 *   <li>수취 기한 = 기간 마지막 날 + {@code personalinfo.periodic-destruction.reply-days}(기본 20일)</li>
 *   <li>대상 기간 = 계약기간(시작일 ~ 종료 처리일, 없으면 자동연장 반영 현재 종료일)과 겹치는 기간</li>
 *   <li>관리 시작일(정기 파기확인을 체크한 날) 이후에 끝나는 기간부터 미수취를 따진다. 그 전 기간도 업로드는 할 수 있다</li>
 * </ul>
 */
@Component
public class PiPeriodicDestruction {

    private static final Pattern KEY = Pattern.compile("^(\\d{4})-(?:(0[1-9]|1[0-2])|Q([1-4])|H([12]))$");

    @Value("${personalinfo.periodic-destruction.reply-days:20}")
    private int replyDays = 20;

    public int getReplyDays() {
        return replyDays;
    }

    /** 기간 상태 */
    public enum Status {
        RECEIVED("받음"),
        OVERDUE("미수취"),
        WAITING("기한 전"),
        IN_PROGRESS("진행 중"),
        NOT_MANAGED("관리 전");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 한 기간 */
    @Getter
    @Builder
    public static class Period {
        private String key;
        private String label;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate dueDate;
        private Status status;
        /** 관리 대상(관리 시작일 이후에 끝나는 기간)인지 */
        private boolean managed;
        /** 이 기간에 받은 파기 확인서 (최근 업로드 먼저) */
        private List<PiContractFile> files;

        public String getStatusLabel() {
            return status.getLabel();
        }
    }

    // ── 기간 키 ──

    /** 날짜가 속한 기간의 시작일 */
    public static LocalDate periodStart(DestructionCycle cycle, LocalDate d) {
        int m = ((d.getMonthValue() - 1) / cycle.getMonths()) * cycle.getMonths() + 1;
        return LocalDate.of(d.getYear(), m, 1);
    }

    public static LocalDate periodEnd(DestructionCycle cycle, LocalDate start) {
        return start.plusMonths(cycle.getMonths()).minusDays(1);
    }

    public static String keyOf(DestructionCycle cycle, LocalDate start) {
        int idx = (start.getMonthValue() - 1) / cycle.getMonths() + 1;
        return switch (cycle) {
            case MONTH -> String.format("%d-%02d", start.getYear(), start.getMonthValue());
            case QUARTER -> start.getYear() + "-Q" + idx;
            case HALF -> start.getYear() + "-H" + idx;
        };
    }

    /** 기간 키 → 표시 이름 (예: 2026년 3분기). 형식이 틀리면 키 그대로 */
    public static String labelOf(String key) {
        if (key == null) {
            return null;
        }
        Matcher m = KEY.matcher(key);
        if (!m.matches()) {
            return key;
        }
        String y = m.group(1) + "년 ";
        if (m.group(2) != null) return y + Integer.parseInt(m.group(2)) + "월";
        if (m.group(3) != null) return y + m.group(3) + "분기";
        return y + ("1".equals(m.group(4)) ? "상반기" : "하반기");
    }

    /** 기간 키 → 시작일 (형식이 틀리면 null) */
    public static LocalDate startOf(String key) {
        if (key == null) {
            return null;
        }
        Matcher m = KEY.matcher(key);
        if (!m.matches()) {
            return null;
        }
        int y = Integer.parseInt(m.group(1));
        if (m.group(2) != null) return LocalDate.of(y, Integer.parseInt(m.group(2)), 1);
        if (m.group(3) != null) return LocalDate.of(y, (Integer.parseInt(m.group(3)) - 1) * 3 + 1, 1);
        return LocalDate.of(y, "1".equals(m.group(4)) ? 1 : 7, 1);
    }

    /** 키의 주기 (월/분기/반기, 형식이 틀리면 null) */
    public static DestructionCycle cycleOf(String key) {
        if (key == null) {
            return null;
        }
        Matcher m = KEY.matcher(key);
        if (!m.matches()) {
            return null;
        }
        return m.group(2) != null ? DestructionCycle.MONTH : m.group(3) != null ? DestructionCycle.QUARTER : DestructionCycle.HALF;
    }

    // ── 계약 기준 ──

    /** 계약의 정기 파기확인 대상 마지막 날 (종료 처리일, 없으면 자동연장 반영 현재 종료일, 종료일 없으면 null = 계속) */
    public static LocalDate contractLastDay(PiContract c, LocalDate today) {
        if ("Y".equals(c.getTerminatedYn()) && c.getTerminatedDate() != null) {
            return c.getTerminatedDate();
        }
        return c.getContractEndDate() == null ? null : c.currentEndDate(today);
    }

    /** 기간이 계약기간과 겹치는지 */
    public static boolean overlapsContract(PiContract c, LocalDate start, LocalDate end, LocalDate today) {
        LocalDate last = contractLastDay(c, today);
        boolean afterStart = c.getContractStartDate() == null || !end.isBefore(c.getContractStartDate());
        boolean beforeEnd = last == null || !start.isAfter(last);
        return afterStart && beforeEnd;
    }

    /** 관리 시작일 (없으면 오늘 = 아직 관리 기간 없음) */
    private static LocalDate managedFrom(PiContract c, LocalDate today) {
        return c.getPeriodicDestructionFrom() == null ? today : c.getPeriodicDestructionFrom();
    }

    /**
     * 기준 연도의 기간 목록 (계약기간과 겹치고 시작일이 오늘 이전인 기간, 최근 기간 먼저).
     * @param files 계약의 파기 확인서 (정기 파기 확인서만 기간으로 매칭)
     */
    public List<Period> periodsOfYear(PiContract c, int year, List<PiContractFile> files, LocalDate today) {
        DestructionCycle cycle = c.destructionCycle();
        List<Period> out = new ArrayList<>();
        for (LocalDate s = LocalDate.of(year, 1, 1); s.getYear() == year; s = s.plusMonths(cycle.getMonths())) {
            LocalDate e = periodEnd(cycle, s);
            if (s.isAfter(today) || !overlapsContract(c, s, e, today)) {
                continue;
            }
            out.add(period(c, cycle, s, files, today));
        }
        out.sort(Comparator.comparing(Period::getStartDate).reversed());
        return out;
    }

    /** 관리 대상인데 수취 기한이 지나도록 확인서를 받지 못한 기간 (오래된 기간 먼저) */
    public List<Period> overdue(PiContract c, List<PiContractFile> files, LocalDate today) {
        if (!c.isPeriodicDestruction()) {
            return List.of();
        }
        DestructionCycle cycle = c.destructionCycle();
        List<Period> out = new ArrayList<>();
        LocalDate from = managedFrom(c, today);
        for (LocalDate s = periodStart(cycle, from); !s.isAfter(today); s = s.plusMonths(cycle.getMonths())) {
            LocalDate e = periodEnd(cycle, s);
            if (!overlapsContract(c, s, e, today)) {
                continue;
            }
            Period p = period(c, cycle, s, files, today);
            if (p.getStatus() == Status.OVERDUE) {
                out.add(p);
            }
        }
        return out;
    }

    /** 관리 대상이고 기간이 끝났는데 아직 기한 안이라 확인서를 기다리는 기간 (요청 메일 대상) */
    public List<Period> waiting(PiContract c, List<PiContractFile> files, LocalDate today) {
        if (!c.isPeriodicDestruction()) {
            return List.of();
        }
        DestructionCycle cycle = c.destructionCycle();
        List<Period> out = new ArrayList<>();
        LocalDate from = managedFrom(c, today);
        // 기한 안(기간 종료 + 기한 일수)인 기간만 보면 되므로 최근 두 기간 전부터 확인
        LocalDate s = periodStart(cycle, today.minusDays(replyDays)).minusMonths(cycle.getMonths());
        if (s.isBefore(periodStart(cycle, from))) {
            s = periodStart(cycle, from);
        }
        for (; !s.isAfter(today); s = s.plusMonths(cycle.getMonths())) {
            LocalDate e = periodEnd(cycle, s);
            if (!overlapsContract(c, s, e, today)) {
                continue;
            }
            Period p = period(c, cycle, s, files, today);
            if (p.getStatus() == Status.WAITING) {
                out.add(p);
            }
        }
        return out;
    }

    /** 업로드 때 고를 수 있는 기간인지 (형식, 계약의 주기, 계약기간과 겹침, 시작일이 오늘 이전) */
    public static String validatePeriod(PiContract c, String key, LocalDate today) {
        LocalDate s = startOf(key);
        if (s == null) {
            return "대상 기간 형식이 올바르지 않습니다. (예: 2026-07, 2026-Q3, 2026-H2)";
        }
        if (cycleOf(key) != c.destructionCycle()) {
            return "이 계약의 정기 파기확인 주기(" + c.destructionCycle().getLabel() + ")와 맞지 않는 기간입니다: " + labelOf(key);
        }
        if (s.isAfter(today)) {
            return "아직 시작하지 않은 기간에는 올릴 수 없습니다: " + labelOf(key);
        }
        if (!overlapsContract(c, s, periodEnd(c.destructionCycle(), s), today)) {
            return "계약기간 밖의 기간입니다: " + labelOf(key);
        }
        return null;
    }

    private Period period(PiContract c, DestructionCycle cycle, LocalDate s, List<PiContractFile> files, LocalDate today) {
        LocalDate e = periodEnd(cycle, s);
        String key = keyOf(cycle, s);
        LocalDate due = e.plusDays(replyDays);
        List<PiContractFile> got = files.stream().filter(f -> key.equals(f.getDestructionPeriod()))
                .sorted(Comparator.comparing(PiContractFile::getFileId).reversed()).toList();
        boolean managed = c.isPeriodicDestruction() && !e.isBefore(managedFrom(c, today));
        Status st;
        if (!got.isEmpty()) st = Status.RECEIVED;
        else if (!e.isBefore(today)) st = Status.IN_PROGRESS;
        else if (!managed) st = Status.NOT_MANAGED;
        else if (!due.isBefore(today)) st = Status.WAITING;
        else st = Status.OVERDUE;
        return Period.builder().key(key).label(labelOf(key)).startDate(s).endDate(e).dueDate(due)
                .status(st).managed(managed).files(got).build();
    }
}
