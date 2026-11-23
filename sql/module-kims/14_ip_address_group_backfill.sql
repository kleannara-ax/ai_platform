-- 14_ip_address_group_backfill.sql
-- PC 관리 화면에서 신규 등록한 IP 의 IP 그룹(IP_GROUP)이 비어 저장되던 문제 보정.
-- 그룹이 비어 있으면 PC 목록 정렬(그룹 → IP 순)에서 맨 앞에 오고, IP 그룹 필터 조회에서도 빠진다.
-- (코드는 이번 수정으로 신규 등록 시 IP 주소에서 그룹을 채우도록 변경됨)
-- IP 주소가 있는데 그룹이 비어 있는 행만 IP 주소 앞 3자리(예: 100.1.1.65 → 100.1.1)로 채운다.
-- 데이터 보정만 하며 테이블 구조 변경은 없다. idempotent: 비어 있는 행만 갱신 (재실행 안전).

SET NAMES utf8mb4;

UPDATE ip_address
   SET IP_GROUP = SUBSTRING_INDEX(IP_ADDRESS, '.', 3)
 WHERE IP_ADDRESS IS NOT NULL AND IP_ADDRESS <> ''
   AND (IP_GROUP IS NULL OR IP_GROUP = '');
