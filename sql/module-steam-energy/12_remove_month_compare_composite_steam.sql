-- 회계비용 마감비교(energy-month-compare) — 복합보일러 스팀구매 기존 입력값 정리
--
-- 배경
--   · "복합보일러 스팀구매" 는 이제 SRF Boiler 인보이스 Summary(VK) 의
--     TOTAL SERVICE CHARGES FOR THE MONTH (원) 를 백만원으로 바꿔 화면이 직접 가져온다.
--     (js/energy-linked-sources.js — 마감비교·연료비 배분비율 페이지가 함께 쓴다)
--   · 엑셀 매핑(EnergyMonthCompareWorkbookService.ROW_MAPS 의 3행)도 뺐다.
--
--   그래서 예전에 손으로 넣거나 엑셀로 올린 fuel|compositeSteam 값은 읽는 곳이 없다.

DELETE FROM table_cell_value
 WHERE table_name = 'energy_month_compare'
   AND row_key = 'fuel|compositeSteam';
