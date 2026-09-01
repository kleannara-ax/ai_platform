-- 32_seoul_ip_band_backfill.sql
-- PC 관리 "서울" 사업장 IP 대역 확정 — 서울 사업장은 192.1.17 / 192.1.104 / 192.1.107 / 192.1.117
-- 대역(각 .1~.254)을 사용한다. 이 대역에 해당하는 기존 등록 IP가 있다면(기본값 '청주') SITE를
-- '서울'로 백필한다. idempotent: 이미 서울로 지정된 행은 WHERE 조건에 의해 재실행 시 영향 없음.
--
-- 참고: 애플리케이션 코드(IpAddressService.isSeoulBandIp)도 신규 등록/엑셀 업로드 시
-- 이 4개 대역이면 자동으로 SITE='서울' 로 지정하도록 되어 있다(이 SQL은 과거 데이터 보정용).

SET NAMES utf8mb4;

UPDATE ip_address
SET SITE = '서울'
WHERE SITE <> '서울'
  AND (
        IP_ADDRESS LIKE '192.1.17.%'
     OR IP_ADDRESS LIKE '192.1.104.%'
     OR IP_ADDRESS LIKE '192.1.107.%'
     OR IP_ADDRESS LIKE '192.1.117.%'
  );
