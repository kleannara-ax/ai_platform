package com.company.module.steamenergy.legacy.db;

import java.util.List;
import java.util.Map;

public record TableDefinition(
        String tableName,
        List<String> uniqueKeys,
        List<String> selectColumns,
        Map<String, String> fieldTypes,
        String orderByClause
) {
}
