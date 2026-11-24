package com.company.module.steamenergy.service.table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 옛 셀 문자열 ↔ 규격 표 값 변환 (엑셀 서비스·셀 저장소 공용).
 */
public final class CellValues {

    private static final int MAX_TEXT = 500;

    private CellValues() {
    }

    /** 표 값 → 셀 문자열. 숫자는 뒤 0 을 떼고 지수 표기 없이. null 은 null. */
    public static String text(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal number) {
            BigDecimal stripped = number.stripTrailingZeros();
            return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * 셀 문자열 → 표 값. 빈 문자열은 null. 숫자 칸에 숫자가 아닌 글자가 오면 null (저장하지 않는다).
     * 문자 칸은 500자를 넘으면 잘라 낸다.
     */
    public static Object convert(String text, Class<?> type) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        if (type == BigDecimal.class) {
            try {
                return new BigDecimal(trimmed.replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (type == Integer.class) {
            try {
                return new BigDecimal(trimmed.replace(",", "")).intValue();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (type == LocalDate.class) {
            try {
                return LocalDate.parse(trimmed);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT) : trimmed;
    }
}
