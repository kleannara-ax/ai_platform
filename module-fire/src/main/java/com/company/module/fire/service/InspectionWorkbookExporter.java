package com.company.module.fire.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 점검 이력 XLSX 내보내기용 경량 OOXML 생성기.
 * Apache POI 의존성 없이 XLSX 표와 등록 사진을 함께 포함한다.
 */
final class InspectionWorkbookExporter {

    private static final DateTimeFormatter DATE_DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private InspectionWorkbookExporter() {
    }

    record ItemColumn(String key, String header) {
    }

    record ImageFile(byte[] bytes, String extension, String contentType) {
    }

    record RowData(String sectionTitle,
                   LocalDate inspectionDate,
                   LocalTime inspectionTime,
                   String inspectorName,
                   Map<String, String> itemResults,
                   String imagePath,
                   String note) {
    }

    static byte[] export(String sheetName,
                         List<ItemColumn> itemColumns,
                         List<RowData> rows,
                         Function<String, Optional<ImageFile>> imageResolver) {
        try {
            WorkbookModel model = buildModel(itemColumns, rows, imageResolver);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                writeEntry(zip, "[Content_Types].xml", contentTypes(model));
                writeEntry(zip, "_rels/.rels", rootRels());
                writeEntry(zip, "xl/workbook.xml", workbookXml(sheetName));
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                writeEntry(zip, "xl/styles.xml", stylesXml());
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(model));
                if (!model.images.isEmpty()) {
                    writeEntry(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRels());
                    writeEntry(zip, "xl/drawings/drawing1.xml", drawingXml(model.images));
                    writeEntry(zip, "xl/drawings/_rels/drawing1.xml.rels", drawingRels(model.images));
                    for (int i = 0; i < model.images.size(); i++) {
                        EmbeddedImage image = model.images.get(i);
                        writeEntry(zip, "xl/media/image" + (i + 1) + "." + image.extension, image.bytes);
                    }
                }
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("점검 보고서 XLSX 생성에 실패했습니다.", ex);
        }
    }

    static Optional<ImageFile> loadImage(String imagePath, Path baseDir) {
        String filename = extractFilename(imagePath);
        if (filename == null) {
            return Optional.empty();
        }
        try {
            Path base = baseDir.toAbsolutePath().normalize();
            Path file = base.resolve(filename).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                return Optional.empty();
            }
            String ext = extension(filename);
            if (!List.of("png", "jpg", "jpeg").contains(ext)) {
                return Optional.empty();
            }
            String contentType = "png".equals(ext) ? "image/png" : "image/jpeg";
            return Optional.of(new ImageFile(Files.readAllBytes(file), ext, contentType));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static WorkbookModel buildModel(List<ItemColumn> itemColumns,
                                            List<RowData> rows,
                                            Function<String, Optional<ImageFile>> imageResolver) {
        WorkbookModel model = new WorkbookModel();
        List<String> headers = new ArrayList<>();
        headers.add("TIMESTAMP");
        headers.add("점검날짜");
        headers.add("점검자");
        itemColumns.forEach(c -> headers.add(c.header()));
        headers.add("점검사진");
        headers.add("비고");
        model.totalColumns = headers.size();
        model.photoColumn = headers.size() - 1;

        Map<String, List<RowData>> grouped = new LinkedHashMap<>();
        for (RowData row : rows) {
            String title = text(row.sectionTitle()).isBlank() ? "점검 내역" : text(row.sectionTitle());
            grouped.computeIfAbsent(title, k -> new ArrayList<>()).add(row);
        }
        if (grouped.isEmpty()) {
            grouped.put("점검 내역", List.of());
        }

        int rowIndex = 1;
        for (Map.Entry<String, List<RowData>> entry : grouped.entrySet()) {
            model.rows.add(SheetRow.title(rowIndex, entry.getKey(), model.totalColumns));
            model.merges.add("A" + rowIndex + ":" + colName(model.totalColumns) + rowIndex);
            rowIndex++;

            model.rows.add(SheetRow.values(rowIndex, headers, 2, 42));
            rowIndex++;

            if (entry.getValue().isEmpty()) {
                List<String> empty = new ArrayList<>();
                empty.add("조회 기간 내 점검 내역이 없습니다.");
                model.rows.add(SheetRow.values(rowIndex, empty, 0, 26));
                rowIndex++;
                continue;
            }

            for (RowData data : entry.getValue()) {
                List<String> values = new ArrayList<>();
                values.add(formatTimestamp(data.inspectionDate(), data.inspectionTime()));
                values.add(data.inspectionDate() == null ? "" : DATE_DOT.format(data.inspectionDate()));
                values.add(text(data.inspectorName()));
                for (ItemColumn column : itemColumns) {
                    values.add(statusLabel(data.itemResults() == null ? null : data.itemResults().get(column.key())));
                }
                values.add("");
                values.add(text(data.note()));
                int dataRow = rowIndex;
                model.rows.add(SheetRow.values(dataRow, values, 0, 120));
                imageResolver.apply(data.imagePath()).ifPresent(image -> model.images.add(new EmbeddedImage(
                        dataRow, model.photoColumn, image.bytes(), image.extension(), image.contentType())));
                rowIndex++;
            }
        }
        return model;
    }

    private static String formatTimestamp(LocalDate date, LocalTime time) {
        if (date == null) return "";
        LocalTime safeTime = time == null ? LocalTime.MIDNIGHT : time;
        return DATE_TIME.format(LocalDateTime.of(date, safeTime));
    }

    private static String statusLabel(String status) {
        return switch (String.valueOf(status == null ? "" : status).toUpperCase()) {
            case "NORMAL", "정상" -> "정상";
            case "MAINTENANCE", "NEED_MAINTENANCE", "요정비" -> "요정비";
            case "FAULTY", "불량" -> "불량";
            default -> "";
        };
    }

    private static String contentTypes(WorkbookModel model) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        xml.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        xml.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        if (model.images.stream().anyMatch(i -> "png".equals(i.extension))) {
            xml.append("<Default Extension=\"png\" ContentType=\"image/png\"/>");
        }
        if (model.images.stream().anyMatch(i -> "jpg".equals(i.extension) || "jpeg".equals(i.extension))) {
            xml.append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>");
            xml.append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>");
        }
        xml.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        xml.append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        xml.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        if (!model.images.isEmpty()) {
            xml.append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
        }
        xml.append("</Types>");
        return xml.toString();
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbookXml(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + xml(sheetName == null || sheetName.isBlank() ? "점검보고서" : sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                "</workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\"><font><sz val=\"10\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font></fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEAF2F8\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"2\"><border/><border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/><bottom style=\"thin\"/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }

    private static String sheetXml(WorkbookModel model) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"18\"/>");
        xml.append("<cols>");
        for (int c = 1; c <= model.totalColumns; c++) {
            double width = c == 1 ? 22 : c == 2 ? 14 : c == 3 ? 14 : c == model.photoColumn ? 28 : c == model.totalColumns ? 28 : 26;
            xml.append("<col min=\"").append(c).append("\" max=\"").append(c).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        for (SheetRow row : model.rows) {
            xml.append("<row r=\"").append(row.index).append("\" ht=\"").append(row.height).append("\" customHeight=\"1\">");
            for (int i = 0; i < row.values.size(); i++) {
                xml.append(cell(row.index, i + 1, row.values.get(i), row.style));
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        if (!model.merges.isEmpty()) {
            xml.append("<mergeCells count=\"").append(model.merges.size()).append("\">");
            model.merges.forEach(ref -> xml.append("<mergeCell ref=\"").append(ref).append("\"/>"));
            xml.append("</mergeCells>");
        }
        if (!model.images.isEmpty()) {
            xml.append("<drawing r:id=\"rId1\"/>");
        }
        xml.append("</worksheet>");
        return xml.toString();
    }

    private static String sheetRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>" +
                "</Relationships>";
    }

    private static String drawingXml(List<EmbeddedImage> images) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        for (int i = 0; i < images.size(); i++) {
            EmbeddedImage image = images.get(i);
            int col = image.columnIndex - 1;
            int row = image.rowIndex - 1;
            xml.append("<xdr:oneCellAnchor>")
                    .append("<xdr:from><xdr:col>").append(col).append("</xdr:col><xdr:colOff>70000</xdr:colOff><xdr:row>").append(row).append("</xdr:row><xdr:rowOff>70000</xdr:rowOff></xdr:from>")
                    .append("<xdr:ext cx=\"1550000\" cy=\"1050000\"/>")
                    .append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(i + 1).append("\" name=\"점검사진").append(i + 1).append("\"/><xdr:cNvPicPr/></xdr:nvPicPr>")
                    .append("<xdr:blipFill><a:blip r:embed=\"rId").append(i + 1).append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                    .append("<xdr:spPr><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
        }
        xml.append("</xdr:wsDr>");
        return xml.toString();
    }

    private static String drawingRels(List<EmbeddedImage> images) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < images.size(); i++) {
            EmbeddedImage image = images.get(i);
            xml.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image")
                    .append(i + 1).append('.').append(image.extension).append("\"/>");
        }
        xml.append("</Relationships>");
        return xml.toString();
    }

    private static String cell(int row, int col, String value, int style) {
        return "<c r=\"" + colName(col) + row + "\" t=\"inlineStr\" s=\"" + style + "\"><is><t>" + xml(value) + "</t></is></c>";
    }

    private static void writeEntry(ZipOutputStream zip, String name, String text) throws IOException {
        writeEntry(zip, name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String colName(int oneBased) {
        StringBuilder sb = new StringBuilder();
        int n = oneBased;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }

    private static String extractFilename(String imagePath) {
        String clean = text(imagePath).replace('\\', '/');
        if (clean.isBlank() || clean.contains("..")) return null;
        int idx = clean.lastIndexOf('/');
        String filename = idx >= 0 ? clean.substring(idx + 1) : clean;
        return filename.isBlank() || filename.contains("/") ? null : filename;
    }

    private static String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String xml(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text(value).length(); i++) {
            char ch = text(value).charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '\"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (ch == '\n' || ch == '\r' || ch == '\t' || ch >= 0x20) sb.append(ch);
                }
            }
        }
        return sb.toString();
    }

    private static final class WorkbookModel {
        int totalColumns;
        int photoColumn;
        final List<SheetRow> rows = new ArrayList<>();
        final List<String> merges = new ArrayList<>();
        final List<EmbeddedImage> images = new ArrayList<>();
    }

    private record SheetRow(int index, List<String> values, int style, int height) {
        static SheetRow title(int index, String title, int totalColumns) {
            List<String> values = new ArrayList<>();
            values.add(title);
            for (int i = 1; i < totalColumns; i++) values.add("");
            return new SheetRow(index, values, 1, 24);
        }

        static SheetRow values(int index, List<String> values, int style, int height) {
            return new SheetRow(index, values, style, height);
        }
    }

    private record EmbeddedImage(int rowIndex, int columnIndex, byte[] bytes, String extension, String contentType) {
    }
}
