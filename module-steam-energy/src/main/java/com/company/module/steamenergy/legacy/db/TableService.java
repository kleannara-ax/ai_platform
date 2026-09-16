package com.company.module.steamenergy.legacy.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TableService {
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, TableDefinition> definitions;

    public TableService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.definitions = Map.of(
                "unit_usage",
                new TableDefinition(
                        "unit_usage",
                        List.of("month", "day", "machine_no"),
                        List.of(
                                "id", "month", "year_no", "month_no", "day", "machine_no", "main_steam", "coater_steam",
                                "disperser_steam",
                                "ventilation_steam", "production", "production_type", "steam",
                                "created_at", "updated_at"
                        ),
                        Map.ofEntries(
                                Map.entry("month", "string"),
                                Map.entry("year_no", "number"),
                                Map.entry("month_no", "number"),
                                Map.entry("day", "string"),
                                Map.entry("machine_no", "string"),
                                Map.entry("main_steam", "number"),
                                Map.entry("coater_steam", "number"),
                                Map.entry("disperser_steam", "number"),
                                Map.entry("ventilation_steam", "number"),
                                Map.entry("production", "number"),
                                Map.entry("production_type", "string"),
                                Map.entry("steam", "number")
                        ),
                        "year_no, month_no, cast(day as unsigned), id"
                ),
                "unit_usage_raw",
                new TableDefinition(
                        "unit_usage_raw",
                        List.of("month", "day"),
                        List.of(
                                "id", "month", "year_no", "month_no", "day", "disperser_total", "toc_steam",
                                "pica121_vent", "created_at", "updated_at"
                        ),
                        Map.of(
                                "month", "string",
                                "year_no", "number",
                                "month_no", "number",
                                "day", "string",
                                "disperser_total", "number",
                                "toc_steam", "number",
                                "pica121_vent", "number"
                        ),
                        "year_no, month_no, cast(day as unsigned), id"
                ),
                "table_cell_value",
                new TableDefinition(
                        "table_cell_value",
                        List.of("month", "table_name", "row_key", "col_index"),
                        List.of(
                                "id", "month", "year_no", "month_no", "table_name", "row_key", "col_index",
                                "cell_value", "created_at", "updated_at"
                        ),
                        Map.of(
                                "month", "string",
                                "year_no", "number",
                                "month_no", "number",
                                "table_name", "string",
                                "row_key", "string",
                                "col_index", "number",
                                "cell_value", "string"
                        ),
                        "year_no, month_no, table_name, cast(row_key as unsigned), col_index, id"
                )
        );
    }

    public boolean supports(String tableName) {
        return definitions.containsKey(tableName);
    }

    public Map<String, Object> list(String tableName, int page, int limit, String month, String fromMonth, String toMonth) {
        return list(tableName, page, limit, month, fromMonth, toMonth, null);
    }

    public Map<String, Object> list(String tableName, int page, int limit, String month, String fromMonth, String toMonth, String tableNamesCsv) {
        TableDefinition definition = getDefinition(tableName);
        List<String> conditions = new ArrayList<>();
        List<Object> whereArgs = new ArrayList<>();
        if (month != null && !month.isBlank()) {
            conditions.add("month = ?");
            whereArgs.add(month);
        } else if (fromMonth != null && !fromMonth.isBlank() && toMonth != null && !toMonth.isBlank()) {
            conditions.add("month between ? and ?");
            whereArgs.add(fromMonth);
            whereArgs.add(toMonth);
        }

        List<String> tableNameFilter = parseTableNames(tableNamesCsv);
        if (!tableNameFilter.isEmpty() && definition.fieldTypes().containsKey("table_name")) {
            String placeholders = String.join(", ", tableNameFilter.stream().map(_value -> "?").toList());
            conditions.add("table_name in (" + placeholders + ")");
            whereArgs.addAll(tableNameFilter);
        }

        String whereClause = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);

        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from " + definition.tableName() + whereClause,
                Integer.class,
                whereArgs.toArray()
        );

        int offset = (page - 1) * limit;
        String sql = "select " + String.join(", ", definition.selectColumns()) +
                " from " + definition.tableName() +
                whereClause +
                " order by " + definition.orderByClause() + " limit ? offset ?";

        Object[] args = new Object[whereArgs.size() + 2];
        for (int i = 0; i < whereArgs.size(); i += 1) {
            args[i] = whereArgs.get(i);
        }
        args[whereArgs.size()] = limit;
        args[whereArgs.size() + 1] = offset;

        List<Map<String, Object>> data = jdbcTemplate.query(sql, rowMapper(), args);
        return Map.of(
                "data", data,
                "total", total == null ? 0 : total,
                "page", page,
                "limit", limit
        );
    }

    private List<String> parseTableNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    public Map<String, Object> upsert(String tableName, Map<String, Object> payload) {
        TableDefinition definition = getDefinition(tableName);
        Map<String, Object> filtered = filterPayload(definition, payload);
        if (filtered.isEmpty()) {
            return Map.of();
        }

        String existingId = findExistingId(definition, filtered);
        if (existingId != null) {
            String assignments = String.join(", ", filtered.keySet().stream().map(key -> key + " = ?").toList());
            Object[] values = filtered.values().toArray();
            Object[] args = new Object[values.length + 1];
            System.arraycopy(values, 0, args, 0, values.length);
            args[values.length] = Long.parseLong(existingId);
            jdbcTemplate.update("update " + definition.tableName() + " set " + assignments + " where id = ?", args);
            return getById(definition, existingId);
        }

        String columns = String.join(", ", filtered.keySet());
        String placeholders = String.join(", ", filtered.keySet().stream().map(key -> "?").toList());
        jdbcTemplate.update(
                "insert into " + definition.tableName() + " (" + columns + ") values (" + placeholders + ")",
                filtered.values().toArray()
        );

        String createdId = findExistingId(definition, filtered);
        return createdId == null ? Map.of() : getById(definition, createdId);
    }

    public Map<String, Object> patch(String tableName, long id, Map<String, Object> payload) {
        TableDefinition definition = getDefinition(tableName);
        Map<String, Object> filtered = filterPayload(definition, payload);
        if (filtered.isEmpty()) {
            return getById(definition, Long.toString(id));
        }

        String assignments = String.join(", ", filtered.keySet().stream().map(key -> key + " = ?").toList());
        Object[] values = filtered.values().toArray();
        Object[] args = new Object[values.length + 1];
        System.arraycopy(values, 0, args, 0, values.length);
        args[values.length] = id;
        jdbcTemplate.update("update " + definition.tableName() + " set " + assignments + " where id = ?", args);
        return getById(definition, Long.toString(id));
    }

    public List<Map<String, Object>> listAllByMonth(String tableName, String month) {
        TableDefinition definition = getDefinition(tableName);
        return jdbcTemplate.query(
                "select " + String.join(", ", definition.selectColumns()) +
                        " from " + definition.tableName() +
                        " where month = ?" +
                        " order by " + definition.orderByClause(),
                rowMapper(),
                month
        );
    }

    public List<Map<String, Object>> listCellRowsByTableNameThroughMonth(String rowTableName, String throughMonth) {
        TableDefinition definition = getDefinition("table_cell_value");
        return jdbcTemplate.query(
                "select " + String.join(", ", definition.selectColumns()) +
                        " from " + definition.tableName() +
                        " where table_name = ? and month <= ?" +
                        " order by month, " + definition.orderByClause(),
                rowMapper(),
                rowTableName,
                throughMonth
        );
    }

    public List<Map<String, Object>> listCellRowsByTableNameBetweenMonths(String rowTableName, String fromMonth, String toMonth) {
        TableDefinition definition = getDefinition("table_cell_value");
        return jdbcTemplate.query(
                "select " + String.join(", ", definition.selectColumns()) +
                        " from " + definition.tableName() +
                        " where table_name = ? and month between ? and ?" +
                        " order by month, " + definition.orderByClause(),
                rowMapper(),
                rowTableName,
                fromMonth,
                toMonth
        );
    }

    public Map<String, Object> deleteTableCellRowGroup(String month, String rowKey) {
        int deleted = jdbcTemplate.update(
                "delete from table_cell_value where month = ? and row_key = ?",
                month,
                rowKey
        );
        return Map.of(
                "deleted", deleted,
                "month", month,
                "row_key", rowKey
        );
    }

    public int deleteTableCellRowsByMonthAndPrefixes(String month, List<String> rowKeyPrefixes) {
        if (rowKeyPrefixes == null || rowKeyPrefixes.isEmpty()) {
            return 0;
        }
        String conditions = rowKeyPrefixes.stream()
                .map(_prefix -> "row_key like ?")
                .collect(Collectors.joining(" or "));
        Object[] args = new Object[rowKeyPrefixes.size() + 1];
        args[0] = month;
        for (int index = 0; index < rowKeyPrefixes.size(); index += 1) {
            args[index + 1] = rowKeyPrefixes.get(index) + "%";
        }
        return jdbcTemplate.update(
                "delete from table_cell_value where month = ? and (" + conditions + ")",
                args
        );
    }

    public int deleteRowsByMonth(String tableName, String month) {
        TableDefinition definition = getDefinition(tableName);
        return jdbcTemplate.update(
                "delete from " + definition.tableName() + " where month = ?",
                month
        );
    }

    /** 특정 table_name 에 속한 행 중 row_key 가 주어진 prefix 들로 시작하지 않는 것만 삭제. */
    public int deleteTableCellValuesByTableExcludingRowKeyPrefixes(String month, String tableName, List<String> excludedPrefixes) {
        if (excludedPrefixes == null || excludedPrefixes.isEmpty()) {
            return jdbcTemplate.update(
                    "delete from table_cell_value where month = ? and table_name = ?",
                    month, tableName
            );
        }
        StringBuilder sql = new StringBuilder("delete from table_cell_value where month = ? and table_name = ?");
        Object[] args = new Object[2 + excludedPrefixes.size()];
        args[0] = month;
        args[1] = tableName;
        for (int i = 0; i < excludedPrefixes.size(); i += 1) {
            sql.append(" and row_key not like ?");
            args[2 + i] = excludedPrefixes.get(i) + "%";
        }
        return jdbcTemplate.update(sql.toString(), args);
    }

    public int deleteTableCellValuesByTables(String month, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", tableNames.stream().map(_tableName -> "?").toList());
        Object[] args = new Object[tableNames.size() + 1];
        args[0] = month;
        for (int index = 0; index < tableNames.size(); index += 1) {
            args[index + 1] = tableNames.get(index);
        }
        return jdbcTemplate.update(
                "delete from table_cell_value where month = ? and table_name in (" + placeholders + ")",
                args
        );
    }

    public int deleteTableCellValue(String month, String tableName, String rowKey, int colIndex) {
        return jdbcTemplate.update(
                "delete from table_cell_value where month = ? and table_name = ? and row_key = ? and col_index = ?",
                month,
                tableName,
                rowKey,
                colIndex
        );
    }

    private TableDefinition getDefinition(String tableName) {
        TableDefinition definition = definitions.get(tableName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown table: " + tableName);
        }
        return definition;
    }

    private String findExistingId(TableDefinition definition, Map<String, Object> payload) {
        List<String> availableKeys = definition.uniqueKeys().stream()
                .filter(payload::containsKey)
                .toList();
        if (availableKeys.size() != definition.uniqueKeys().size()) {
            return null;
        }

        String whereClause = String.join(" and ", availableKeys.stream().map(key -> key + " = ?").toList());
        List<String> ids = jdbcTemplate.query(
                "select id from " + definition.tableName() + " where " + whereClause + " limit 1",
                (rs, rowNum) -> rs.getString("id"),
                availableKeys.stream().map(payload::get).toArray()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> getById(TableDefinition definition, String id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "select " + String.join(", ", definition.selectColumns()) +
                        " from " + definition.tableName() + " where id = ? limit 1",
                rowMapper(),
                Long.parseLong(id)
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> filterPayload(TableDefinition definition, Map<String, Object> payload) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        definition.fieldTypes().forEach((field, type) -> {
            if (!payload.containsKey(field)) {
                return;
            }
            filtered.put(field, normalizeValue(payload.get(field), type));
        });
        return filtered;
    }

    private Object normalizeValue(Object value, String type) {
        if (value == null) {
            return null;
        }
        if ("number".equals(type)) {
            String raw = Objects.toString(value, "").replace(",", "").trim();
            if (raw.isEmpty()) {
                return null;
            }
            return new BigDecimal(raw);
        }
        return Objects.toString(value, null);
    }

    private RowMapper<Map<String, Object>> rowMapper() {
        return (rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i += 1) {
                String label = meta.getColumnLabel(i);
                Object value = readValue(rs, i);
                row.put(label, value);
            }
            return row;
        };
    }

    private Object readValue(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0 ? decimal.longValue() : decimal.doubleValue();
        }
        return value;
    }

    public int deleteById(String tableName, long id) {
        TableDefinition definition = getDefinition(tableName);
        return jdbcTemplate.update(
                "delete from " + definition.tableName() + " where id = ?",
                id
        );
    }
}
