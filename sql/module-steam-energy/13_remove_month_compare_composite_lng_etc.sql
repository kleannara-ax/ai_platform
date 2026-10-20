-- 회계비용 마감비교(energy-month-compare) — 복합 LNG 사용량, 처분부담금 등 기존 입력값 정리
--
-- 배경
--   · "복합 LNG 사용량, 처분부담금 등" 은 이제 SRF Boiler Client Invoice 의 청구금액 합계
--     (전력 제외, 원 → 백만원)를 화면이 직접 가져온다.
--     (js/energy-linked-sources.js — 마감비교·연료비 배분비율 페이지가 함께 쓴다)
--   · 엑셀 매핑(EnergyMonthCompareWorkbookService.ROW_MAPS 의 11행)도 뺐다.
--
--   그래서 예전에 손으로 넣거나 엑셀로 올린 fuel|compositeLngEtc 값은 읽는 곳이 없다.

DELETE FROM table_cell_value
 WHERE table_name = 'energy_month_compare'
   AND row_key = 'fuel|compositeLngEtc';
