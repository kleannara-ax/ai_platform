package com.company.module.steamenergy.service.table;

import com.company.module.steamenergy.dto.SteamBulkSaveResponse;

import java.util.List;
import java.util.Map;

/**
 * 규격 표 하나를 "필드 이름 → 값" 맵의 목록으로 다루는 어댑터.
 *
 * <p>엑셀 가져오기/내보내기 서비스와 셀 저장소(옛 row_key 를 쓰는 코드)가 표마다 다른 DTO 를 몰라도 되게 한다.
 * 구현은 표마다 생성된다 ({@code *Adapter}). 리플렉션 없이 필드 이름 → getter/setter 를 고정 맵으로 가진다.
 */
public interface SteamTableAdapter {

    /** 표 별칭 (예: comboProduction) */
    String alias();

    /** 시간 키 종류: day(usageDate) · month(yearNo, monthNo) · year(yearNo) */
    String time();

    /** 키 필드 이름 (시간 키 + 추가 키, 순서대로) */
    List<String> keyFields();

    /** 값 필드 이름 */
    List<String> valueFields();

    /** 필드의 자바 타입 (BigDecimal · String · Integer · LocalDate) */
    Class<?> typeOf(String field);

    /** 한 해(month 가 있으면 그 달) 범위의 행 — 키와 값이 한 맵에 들어 있다 */
    List<Map<String, Object>> load(int year, Integer month);

    /** 범위 일괄 저장 — 행 전체 업서트. clearMissing 이면 범위 안에서 목록에 없는 행을 비우거나 지운다 */
    SteamBulkSaveResponse saveBulk(int year, Integer month, List<Map<String, Object>> rows, boolean clearMissing);
}
