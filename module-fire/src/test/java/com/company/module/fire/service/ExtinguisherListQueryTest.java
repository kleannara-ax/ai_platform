package com.company.module.fire.service;

import com.company.module.fire.dto.ExtinguisherResponse;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ExtinguisherListQueryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private ExtinguisherResponse row(long id, String type, LocalDate due, LocalDate inspected, Boolean faulty) {
        return ExtinguisherResponse.builder().extinguisherId(id).serialNumber("EXT-" + id)
                .extinguisherType(type).replacementDueDate(due).lastInspectionDate(inspected)
                .lastIsFaulty(faulty).build();
    }
    private ExtinguisherListQuery.Result query(List<ExtinguisherResponse> rows, String type, String status,
            String sort, String dir, int page, boolean exportAll) {
        return ExtinguisherListQuery.evaluate(rows, List.of("분말소화기", "이산화탄소소화기"),
                type, status, sort, dir, page, 200, exportAll, TODAY);
    }
    @Test void sortsEntireSyntheticInventoryBeforePaginationAndExportMatchesEveryPage() {
        // Synthetic regression fixture; not copied from production.
        var rows = IntStream.rangeClosed(1, 769).mapToObj(i -> row(i, "분말소화기", null, TODAY, false)).toList();
        var first = query(rows, null, null, "serialNumber", "desc", 0, false);
        var second = query(rows, null, null, "serialNumber", "desc", 1, false);
        assertThat(first.content().get(0).getSerialNumber()).isEqualTo("EXT-769");
        assertThat(first.content().get(199).getSerialNumber()).isEqualTo("EXT-570");
        assertThat(second.content().get(0).getSerialNumber()).isEqualTo("EXT-569");
        var collected = new ArrayList<ExtinguisherResponse>();
        for (int page = 0; page < first.totalPages(); page++)
            collected.addAll(query(rows, null, null, "serialNumber", "desc", page, false).content());
        assertThat(collected).hasSize(769).doesNotHaveDuplicates()
                .containsExactlyElementsOf(query(rows, null, null, "serialNumber", "desc", 0, true).content());
        assertThat(query(rows, null, null, "serialNumber", "asc", 0, false).content().get(1).getSerialNumber()).isEqualTo("EXT-2");
    }
    @Test void countersAreIndependentOfPageAndStatusToggle() {
        var rows = IntStream.rangeClosed(1, 769).mapToObj(i -> row(i, "분말소화기", null,
                i <= 225 ? null : TODAY, false)).toList();
        var baseline = query(rows, null, null, "serialNumber", "asc", 0, false);
        assertThat(baseline.summary().inspectCount()).isEqualTo(225);
        for (String status : List.of("", "inspect", "planned", "urgent", "")) {
            var result = query(rows, null, status, "serialNumber", "asc", 1, false);
            assertThat(result.summary()).isEqualTo(baseline.summary());
            assertThat(result.totalMatching()).isEqualTo(769);
        }
        assertThat(query(rows, null, "inspect", "serialNumber", "asc", 1, false).content()).hasSize(25);
        assertThat(query(rows, null, "inspect", "serialNumber", "asc", 0, false).totalElements()).isEqualTo(225);
    }
    @Test void dateBoundariesMatchExistingThirtyAndOneHundredEightyDayRules() {
        assertThat(ExtinguisherListQuery.matches(row(1,"분말소화기",null,TODAY.minusDays(30),false),"inspect",TODAY)).isTrue();
        assertThat(ExtinguisherListQuery.matches(row(1,"분말소화기",null,TODAY.minusDays(29),false),"inspect",TODAY)).isFalse();
        for (int days : new int[]{-1,0,30}) {
            var r = row(1,"분말소화기",TODAY.plusDays(days),TODAY,false);
            assertThat(ExtinguisherListQuery.matches(r,"urgent",TODAY)).isTrue();
            assertThat(ExtinguisherListQuery.matches(r,"planned",TODAY)).isFalse();
        }
        for (int days : new int[]{31,180})
            assertThat(ExtinguisherListQuery.matches(row(1,"분말소화기",TODAY.plusDays(days),TODAY,false),"planned",TODAY)).isTrue();
        assertThat(ExtinguisherListQuery.matches(row(1,"분말소화기",TODAY.plusDays(181),TODAY,false),"planned",TODAY)).isFalse();
    }
    @Test void exactTypeFilterAndCo2NinetyNineYearPolicyRemainCompatible() {
        var co2 = row(1,"이산화탄소소화기",TODAY.plusYears(99),TODAY,false);
        var powder = row(2,"분말소화기",TODAY.plusDays(30),null,false);
        var result = query(List.of(co2,powder),"이산화탄소소화기",null,"serialNumber","asc",0,false);
        assertThat(result.content()).containsExactly(co2);
        assertThat(result.totalMatching()).isEqualTo(1);
        assertThat(result.summary()).isEqualTo(new ExtinguisherListQuery.Counts(0,0,0));
        assertThat(ExtinguisherListQuery.matches(row(3,"이산화탄소소화기",TODAY.plusYears(99),TODAY,true),"urgent",TODAY)).isTrue();
    }
    @Test void latestInspectionFieldsCanSortAndBlanksStayLastInBothDirections() {
        var blank = row(1,"분말소화기",null,null,null);
        var old = row(2,"분말소화기",null,TODAY.minusDays(30),false);
        var recent = row(3,"분말소화기",null,TODAY,false);
        assertThat(query(List.of(blank,old,recent),null,null,"lastInspectionDate","asc",0,false).content()).containsExactly(old,recent,blank);
        assertThat(query(List.of(blank,old,recent),null,null,"lastInspectionDate","desc",0,false).content()).containsExactly(recent,old,blank);
    }
    @Test void duplicateSortValuesHaveStableIdTieBreakAndPageIsClamped() {
        var a = row(2,"분말소화기",null,null,false);
        var b = row(1,"분말소화기",null,null,false);
        assertThat(query(List.of(a,b),null,null,"extinguisherType","desc",99,false).content()).containsExactly(b,a);
        assertThat(query(List.of(a,b),null,null,"extinguisherType","desc",99,false).number()).isZero();
        assertThat(query(List.of(),null,null,"serialNumber","asc",99,false).content()).isEmpty();
    }
    @Test void snapshotCarriesServerStatusMembershipsForEveryRow() {
        var rows = List.of(row(1,"분말소화기",TODAY.plusDays(20),null,false),
                row(2,"분말소화기",TODAY.plusDays(90),TODAY,false),
                row(3,"이산화탄소소화기",TODAY.plusYears(99),TODAY,false));
        var inventory = query(rows,null,null,"serialNumber","asc",0,true);
        var snapshot = ExtinguisherListQuery.snapshot(inventory);
        assertThat(snapshot.statuses()).hasSize(3);
        assertThat(snapshot.statuses().get(1L)).containsExactly("inspect", "urgent");
        assertThat(snapshot.statuses().get(2L)).containsExactly("planned");
        assertThat(snapshot.statuses().get(3L)).isEmpty();
        assertThat(snapshot.inventory().asOfDate()).isEqualTo(TODAY);
        for (String status : List.of("inspect","planned","urgent")) {
            var fromSnapshot = inventory.content().stream()
                    .filter(r -> snapshot.statuses().get(r.getExtinguisherId()).contains(status)).toList();
            assertThat(fromSnapshot).containsExactlyElementsOf(query(rows,null,status,"serialNumber","asc",0,true).content());
        }
    }
    @Test void emptySnapshotDoesNotInventMemberships() {
        var snapshot = ExtinguisherListQuery.snapshot(query(List.of(),null,null,"serialNumber","asc",0,true));
        assertThat(snapshot.statuses()).isEmpty();
        assertThat(snapshot.inventory().content()).isEmpty();
    }
    @Test void rejectsUnsupportedStatusSortAndDirection() {
        assertThatIllegalArgumentException().isThrownBy(() -> query(List.of(),null,"bad","serialNumber","asc",0,false));
        assertThatIllegalArgumentException().isThrownBy(() -> query(List.of(),null,null,"bad","asc",0,false));
        assertThatIllegalArgumentException().isThrownBy(() -> query(List.of(),null,null,"serialNumber","bad",0,false));
    }
}
