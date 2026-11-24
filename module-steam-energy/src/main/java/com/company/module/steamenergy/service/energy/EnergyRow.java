package com.company.module.steamenergy.service.energy;

import java.util.Map;

/**
 * 규격 표의 행 하나 — (연, 월) 키와 필드 값 (월 행이 아니면 monthNo 는 null).
 */
public record EnergyRow(int yearNo, Integer monthNo, Map<String, Object> values) {
}
