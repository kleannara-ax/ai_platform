-- ============================================================
--  module-steam-energy: 스팀에너지 사용자 역할 추가
--  플랫폼 역할은 공통코드(code_detail, GROUP_CODE='ROLE')로 관리된다.
--  추가 역할: 스팀에너지매니저 / 스팀에너지보일러 / 스팀에너지KNE
--  재실행 안전: 기존 동일 CODE 삭제 후 재삽입.
-- ============================================================

-- 역할 추가 (ROLE 그룹 하위) — 있으면 이름/설명만 갱신
--  규격상 무조건 DELETE 는 쓰지 않는다. 코드 단위로 존재 여부를 보고 넣는다.
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, v.CODE, v.CODE_NAME, v.DESCRIPTION, v.EXTRA1, 1, v.SORT, NOW(), NOW()
FROM code_group g
JOIN (
  SELECT 'ROLE_STEAM_MANAGER' AS CODE, '스팀에너지매니저' AS CODE_NAME, '스팀에너지 관리 매니저' AS DESCRIPTION, 'steam_manager' AS EXTRA1, 4 AS SORT
  UNION ALL SELECT 'ROLE_STEAM_BOILER', '스팀에너지보일러', '스팀에너지 보일러 담당', 'steam_boiler', 5
  UNION ALL SELECT 'ROLE_STEAM_KNE',    '스팀에너지KNE',    '스팀에너지 KNE 담당',    'steam_kne',    6
) v
WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (
      SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = v.CODE
  );

UPDATE code_detail d
  JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
  JOIN (
    SELECT 'ROLE_STEAM_MANAGER' AS CODE, '스팀에너지매니저' AS CODE_NAME, '스팀에너지 관리 매니저' AS DESCRIPTION
    UNION ALL SELECT 'ROLE_STEAM_BOILER', '스팀에너지보일러', '스팀에너지 보일러 담당'
    UNION ALL SELECT 'ROLE_STEAM_KNE',    '스팀에너지KNE',    '스팀에너지 KNE 담당'
  ) v ON v.CODE = d.CODE
SET d.CODE_NAME = v.CODE_NAME, d.DESCRIPTION = v.DESCRIPTION, d.UPDATED_AT = NOW()
WHERE g.GROUP_CODE = 'ROLE';
