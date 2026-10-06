-- 11_delete_disposed_win7_notebooks.sql
-- 별도관리(IP 없음) 노트북 중 OS가 Win 7 계열인 장비 삭제.
-- 사용 완료 후 창고에 보관하던 노트북으로, 회사 정책 변경에 따라 전량 폐기 처분됨 (2026-09-23).
-- 대상: IP_ADDRESS IS NULL AND DEVICE = '노트북' AND OS_VERSION LIKE 'Win 7%'  (2026-09-23 기준 11대)
-- 연결된 ip_history 도 함께 삭제한다 (앱의 행 삭제와 동일).
-- idempotent: 대상이 없으면 아무 것도 하지 않음. 10번은 이 노트북들을 등록하지 않으므로 10·11 순서와 무관.

SET NAMES utf8mb4;

DELETE h FROM ip_history h
  JOIN ip_address a ON a.IP_ID = h.IP_ID
 WHERE a.IP_ADDRESS IS NULL
   AND a.DEVICE = '노트북'
   AND a.OS_VERSION LIKE 'Win 7%';

DELETE FROM ip_address
 WHERE IP_ADDRESS IS NULL
   AND DEVICE = '노트북'
   AND OS_VERSION LIKE 'Win 7%';
