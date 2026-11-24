package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Entry;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogCellMap.Header;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 유동상 운전일지 시트에서 <b>헤더 글자로 칸 위치를 찾는</b> 색인.
 *
 * <p>이 시트는 표가 아니라 양식(form)이고, 한 장에 섹션이 좌우 두 줄기로 배치된다.
 * 그래서 칸 하나를 <b>(섹션, 행 라벨, 열 라벨)</b> 로 찾는다.
 *
 * <ol>
 *   <li><b>섹션 블록</b> — "3. 약품 사용현황 (KG)" 처럼 번호로 시작하는 제목 셀을 모두 찾아,
 *       같은 열의 다음 제목 전까지를 행 범위, 오른쪽 다음 제목 열 전까지를 열 범위로 잡는다.
 *       좌측(A열)과 우측(P열) 섹션이 같은 행을 써도 서로 섞이지 않는다.</li>
 *   <li><b>행</b> — 블록의 라벨 열(제목이 있던 열)에서 행 라벨을 찾는다.</li>
 *   <li><b>열</b> — 그 행 <b>바로 위쪽</b>에서 열 라벨이 적힌 헤더 행을 찾아 그 열을 쓴다.
 *       (4. 소각재처럼 한 섹션에 헤더 행이 둘이어도 가까운 쪽을 쓴다)</li>
 * </ol>
 *
 * <p>못 찾으면 {@link Entry#ref()} 좌표로 폴백하고 경고를 남긴다. 찾았는데 좌표와 다르면
 * "양식이 바뀌었다"는 안내를 남긴다. 어느 쪽이든 예전(좌표 전용)보다 나빠지지 않는다.
 */
public final class FluidizedDailyLogSheetIndex {

    /** 시트의 (행, 열) 글자를 읽어 주는 함수. POI 읽기 방식은 호출부가 정한다. */
    @FunctionalInterface
    public interface CellText {
        /** @param row 1-based 행, @param col 1-based 열 */
        String at(int row, int col);
    }

    /**
     * @param row      1-based 행
     * @param col      1-based 열
     * @param byHeader 헤더 글자로 찾았으면 true, 좌표 폴백이면 false
     * @param moved    헤더로 찾은 자리가 매핑표 좌표와 다르면 true
     */
    public record Resolved(int row, int col, boolean byHeader, boolean moved) {}

    /** 섹션 블록 — 제목 셀 위치와 그 아래 차지하는 범위. */
    private record Block(int labelCol, int firstRow, int lastRow, int lastCol) {}

    private static final int MAX_ROW = 60;
    private static final int MAX_COL = 40;

    private final CellText text;
    private final Map<String, Block> blocks;
    private final List<String> notFound = new ArrayList<>();
    private final List<String> moved = new ArrayList<>();

    private FluidizedDailyLogSheetIndex(CellText text, Map<String, Block> blocks) {
        this.text = text;
        this.blocks = blocks;
    }

    public static FluidizedDailyLogSheetIndex build(Sheet sheet, CellText text) {
        int lastRow = Math.min(sheet.getLastRowNum() + 1, MAX_ROW);

        // 1) 번호로 시작하는 제목 셀을 모두 찾는다 — 예: "3. 약품 사용현황 (KG)"
        record Title(String key, int row, int col) {}
        List<Title> titles = new ArrayList<>();
        for (int row = 1; row <= lastRow; row += 1) {
            for (int col = 1; col <= MAX_COL; col += 1) {
                String value = text.at(row, col);
                if (isSectionTitle(value)) titles.add(new Title(normalize(value), row, col));
            }
        }

        // 2) 제목마다 블록 범위를 정한다
        //    행: 같은 열의 다음 제목 직전까지 / 열: 오른쪽 다음 제목 열 직전까지
        Map<String, Block> blocks = new LinkedHashMap<>();
        for (Title title : titles) {
            int blockLastRow = lastRow;
            for (Title other : titles) {
                if (other.col() == title.col() && other.row() > title.row()) {
                    blockLastRow = Math.min(blockLastRow, other.row() - 1);
                }
            }
            int blockLastCol = MAX_COL;
            for (Title other : titles) {
                if (other.col() > title.col()) blockLastCol = Math.min(blockLastCol, other.col() - 1);
            }
            blocks.putIfAbsent(title.key(), new Block(title.col(), title.row(), blockLastRow, blockLastCol));
        }
        return new FluidizedDailyLogSheetIndex(text, blocks);
    }

    /** 칸 위치를 정한다. 헤더로 못 찾으면 매핑표 좌표로 폴백한다. */
    public Resolved resolve(Entry entry) {
        int declaredRow = FluidizedDailyLogCellMap.parseRow(entry.ref());
        int declaredCol = FluidizedDailyLogCellMap.parseCol(entry.ref());

        Header header = entry.header();
        if (header == null) return new Resolved(declaredRow, declaredCol, false, false);

        Resolved found = locate(header.section(), header.rowLabel(), header.colLabel(),
                declaredRow, declaredCol, entry.label());
        return found;
    }

    /** 전력 사용량(전일/금일 지침) 처럼 매핑표 밖에서 쓰는 칸도 같은 방식으로 찾는다. */
    public Resolved resolve(String section, String rowLabel, String colLabel, String ref, String label) {
        return locate(section, rowLabel, colLabel,
                FluidizedDailyLogCellMap.parseRow(ref), FluidizedDailyLogCellMap.parseCol(ref), label);
    }

    private Resolved locate(String section, String rowLabel, String colLabel,
                            int declaredRow, int declaredCol, String label) {
        Block block = findBlock(section);
        if (block == null) {
            notFound.add(label + "(섹션 '" + section + "')");
            return new Resolved(declaredRow, declaredCol, false, false);
        }

        Integer row = findRow(block, rowLabel);
        if (row == null) {
            notFound.add(label + "(행 '" + rowLabel + "')");
            return new Resolved(declaredRow, declaredCol, false, false);
        }

        Integer col = findCol(block, row, colLabel);
        if (col == null) {
            notFound.add(label + "(열 '" + colLabel + "')");
            return new Resolved(declaredRow, declaredCol, false, false);
        }

        boolean shifted = row != declaredRow || col != declaredCol;
        if (shifted) {
            moved.add(label + "(" + ref(declaredRow, declaredCol) + "→" + ref(row, col) + ")");
        }
        return new Resolved(row, col, true, shifted);
    }

    /** 제목은 앞부분만 맞으면 된다 — "3. 약품" 으로 "3. 약품 사용현황 (KG)" 을 찾는다. */
    private Block findBlock(String section) {
        String key = normalize(section);
        for (Map.Entry<String, Block> candidate : blocks.entrySet()) {
            if (candidate.getKey().startsWith(key)) return candidate.getValue();
        }
        return null;
    }

    private Integer findRow(Block block, String rowLabel) {
        String key = normalize(rowLabel);
        for (int row = block.firstRow(); row <= block.lastRow(); row += 1) {
            if (normalize(text.at(row, block.labelCol())).equals(key)) return row;
        }
        return null;
    }

    /** 항목 행 바로 위쪽에서 열 라벨이 있는 헤더 행을 찾는다(가까운 쪽 우선). */
    private Integer findCol(Block block, int itemRow, String colLabel) {
        String key = normalize(colLabel);
        for (int row = itemRow - 1; row >= block.firstRow(); row -= 1) {
            for (int col = block.labelCol(); col <= block.lastCol(); col += 1) {
                if (normalize(text.at(row, col)).equals(key)) return col;
            }
        }
        return null;
    }

    /** 헤더 글자를 못 찾아 좌표로 읽은 항목들. */
    public List<String> notFound() {
        return notFound;
    }

    /** 매핑표 좌표와 다른 자리에서 찾은 항목들 — 원본 양식이 바뀌었다는 뜻. */
    public List<String> moved() {
        return moved;
    }

    /** 섹션을 하나도 못 찾았으면 양식이 많이 달라진 것이다. */
    public boolean hasSections() {
        return !blocks.isEmpty();
    }

    private static boolean isSectionTitle(String value) {
        if (value == null || value.isBlank()) return false;
        String trimmed = value.trim();
        // "1." ~ "9." 로 시작하고 뒤에 글자가 오는 셀 — "3.5" 같은 숫자는 제목이 아니다
        return trimmed.length() > 2
                && Character.isDigit(trimmed.charAt(0))
                && trimmed.charAt(1) == '.'
                && !Character.isDigit(trimmed.charAt(2));
    }

    private static String ref(int row, int col) {
        StringBuilder name = new StringBuilder();
        int remaining = col;
        while (remaining > 0) {
            int digit = (remaining - 1) % 26;
            name.insert(0, (char) ('A' + digit));
            remaining = (remaining - 1) / 26;
        }
        return name + String.valueOf(row);
    }

    /**
     * 표기 차이를 흡수한다 — 공백·줄바꿈·전각/반각·{@code # · - _} ·괄호를 지우고 소문자로.
     * (예: "소 금" = "소금", "\n비산재\n" = "비산재")
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.replaceAll("[\\s\\u00A0]+", "");
        value = value.replaceAll("[#·・\\-_()\\[\\]（）]", "");
        return value.toLowerCase(Locale.ROOT);
    }
}
