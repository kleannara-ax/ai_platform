package com.company.module.steamenergy.service.energy;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 에너지 회계비용 규격 표 하나를 "연·월 행 + 필드 값" 으로 다루는 어댑터 (셀 브리지용).
 *
 * <p>구현은 표마다 생성된다 ({@code *Adapter}, energy_specs.py → gen_energy_all.py). 리플렉션 없이
 * 필드 이름 → getter/setter 를 고정 맵으로 가진다.
 */
public interface EnergyTableAdapter {

    /** 매핑에서 쓰는 표 별칭 (예: ratioBoiler) */
    String alias();

    /** true = 연/월 행, false = 연도 행(비고) */
    boolean monthly();

    /** 이 표의 필드 이름 목록 (SaveRequest 의 값 필드) */
    Set<String> fields();

    /** 그 해의 행 전부 */
    List<EnergyRow> load(int year);

    /** (연, 월) 행 전체 업서트 — values 에 없는 필드는 빈 값으로 저장된다 */
    void save(int yearNo, Integer monthNo, Map<String, Object> values);
}
