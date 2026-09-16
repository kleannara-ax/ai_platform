package com.company.module.steamenergy.legacy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel 시트의 정적 메타데이터(셀 텍스트/스타일/포맷, 컬럼 폭, 행 높이, 머지)를 classpath JSON 에서
 * 로드. 런타임에 Excel 파일을 열지 않아도 페이지 렌더링이 가능하도록 함.
 *
 * JSON 위치: classpath:sheet-metadata/{name}.json
 * 생성 도구: tools/extract-sheet-metadata.py
 */
@Component
public class SheetMetadataLoader {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    /** name 은 확장자 없는 파일명 — 예: "srf-invoice-operation" */
    public Map<String, Object> load(String name) {
        return cache.computeIfAbsent(name, this::loadFromClasspath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadFromClasspath(String name) {
        String path = "/sheet-metadata/" + name + ".json";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Sheet metadata not found in classpath: " + path);
            }
            return mapper.readValue(in, Map.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load sheet metadata: " + path, ex);
        }
    }

    /**
     * 메타데이터의 style 객체(Map<String,Object>)를 inline CSS 문자열로 변환.
     * 키 → CSS 속성 매핑: camelCase 키를 kebab-case 로.
     */
    public static String styleToCss(Map<String, Object> style) {
        if (style == null || style.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : style.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value == null) continue;
            sb.append(camelToKebab(key)).append(':').append(value);
            if (value instanceof Number) {
                // fontSize, fontWeight 등 단위 처리
                if ("fontSize".equals(key)) sb.append("px");
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static String camelToKebab(String s) {
        StringBuilder out = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i += 1) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append('-').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** rows 배열 deep-copy (응답마다 cells 안의 value 만 동적으로 바뀌므로 mutable 사본 필요). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> cloneRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
            if (cells != null) {
                List<Map<String, Object>> cellsCopy = new java.util.ArrayList<>(cells.size());
                for (Map<String, Object> c : cells) cellsCopy.add(new LinkedHashMap<>(c));
                copy.put("cells", cellsCopy);
            }
            out.add(copy);
        }
        return out;
    }
}
