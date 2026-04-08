-- =============================================================================
-- DBbackup 복원 스크립트
-- SQL Server .bak에서 추출한 데이터를 MariaDB에 적용
-- =============================================================================

USE fireweb;

SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------
-- 기존 데이터 정리
-- -----------------------------------------------------------------------
TRUNCATE TABLE fire_hydrant_inspection;
TRUNCATE TABLE extinguisher_inspection;
TRUNCATE TABLE fire_pump_inspection;
TRUNCATE TABLE fire_receiver_inspection;
TRUNCATE TABLE extinguisher;
TRUNCATE TABLE extinguisher_group;
TRUNCATE TABLE fire_hydrant;
TRUNCATE TABLE fire_pump;
TRUNCATE TABLE fire_receiver;
TRUNCATE TABLE floor;
DELETE FROM building WHERE building_id != 99;
ALTER TABLE building AUTO_INCREMENT = 1;
ALTER TABLE floor AUTO_INCREMENT = 1;
ALTER TABLE extinguisher AUTO_INCREMENT = 1;
ALTER TABLE fire_hydrant AUTO_INCREMENT = 1;
ALTER TABLE fire_receiver AUTO_INCREMENT = 1;
ALTER TABLE fire_pump AUTO_INCREMENT = 1;

-- -----------------------------------------------------------------------
-- Building 데이터 (원본: SQL Server Building 테이블)
-- BuildingId 1-13 + 99(옥외)
-- -----------------------------------------------------------------------
INSERT INTO building (building_id, building_name, is_active) VALUES
(1,  '관리동',          1),
(2,  '복지관',          1),
(3,  '화장지 4,5호기',   1),
(4,  '화장지 3,6호기',   1),
(5,  '제지2호기',        1),
(6,  '제지3호기',        1),
(7,  '패드동',           1),
(8,  '유동상소각로',      1),
(9,  '폐수처리장',        1),
(10, '전기현장',          1),
(11, '기관실',            1),
(12, '중문창고',          1),
(13, '전기,공무팀',       1)
ON DUPLICATE KEY UPDATE building_name = VALUES(building_name), is_active = VALUES(is_active);

-- -----------------------------------------------------------------------
-- Floor 데이터 (원본: SQL Server Floor 테이블)
-- 건물별 층 정보
-- -----------------------------------------------------------------------
INSERT INTO floor (floor_id, floor_name, sort_order) VALUES
(1,  '1층',      10),
(2,  '2층',      20),
(3,  '3층',      30),
(4,  '4층',      40),
(5,  'B1(지하1층)',  5),
(6,  '옥상',     50)
ON DUPLICATE KEY UPDATE floor_name = VALUES(floor_name), sort_order = VALUES(sort_order);

-- -----------------------------------------------------------------------
-- Extinguisher 데이터 (원본: SQL Server Extinguisher 테이블)
-- 32개 레코드 추출
-- note_key: UUID (QR용 고정키)
-- -----------------------------------------------------------------------
INSERT INTO extinguisher 
  (extinguisher_id, serial_number, building_id, floor_id, extinguisher_type, manufacture_date, replacement_cycle_years, replacement_due_date, quantity, note_key, created_at)
VALUES
(1,  'EXT-000001', 1, 1, '분말소화기 3kg',     '2016-02-01', 5, '2021-02-01', 1, UUID(), NOW()),
(2,  'EXT-000002', 1, 2, '분말소화기 3kg',     '2016-12-17', 5, '2021-12-17', 1, UUID(), NOW()),
(3,  'EXT-000003', 5, 2, '이산화탄소소화기',   '2022-03-01', 5, '2027-03-01', 1, UUID(), NOW()),
(4,  'EXT-000004', 8, 2, '분말소화기 3kg',     '2016-09-15', 5, '2021-09-15', 1, UUID(), NOW()),
(5,  'EXT-000005', 5, 2, '분말소화기 3kg',     '2025-07-16', 5, '2030-07-16', 1, UUID(), NOW()),
(6,  'EXT-000006', 3, 2, '이산화탄소소화기',   '2021-12-15', 5, '2026-12-15', 1, UUID(), NOW()),
(7,  'EXT-000007', 9, 1, '분말소화기 3kg',     '2022-08-12', 5, '2027-08-12', 1, UUID(), NOW()),
(8,  'EXT-000008', 1, 2, '할론소화기',         '2025-05-17', 5, '2030-05-17', 1, UUID(), NOW()),
(9,  'EXT-000009', 1, 2, '분말소화기 3kg',     '2016-02-10', 5, '2021-02-10', 1, UUID(), NOW()),
(10, 'EXT-000010', 2, 1, '분말소화기 20kg',    '2016-07-07', 5, '2021-07-07', 1, UUID(), NOW()),
(11, 'EXT-000011', 6, 2, '분말소화기 3kg',     '2016-03-03', 5, '2021-03-03', 1, UUID(), NOW()),
(12, 'EXT-000012', 3, 2, '이산화탄소소화기',   '2023-03-01', 5, '2028-03-01', 1, UUID(), NOW()),
(13, 'EXT-000013', 5, 2, '분말소화기 3kg',     '2024-04-18', 5, '2029-04-18', 1, UUID(), NOW()),
(14, 'EXT-000014', 4, 2, '분말소화기 3kg',     '2023-02-17', 5, '2028-02-17', 1, UUID(), NOW()),
(15, 'EXT-000015', 13, 1,'이산화탄소소화기',   '2020-07-12', 5, '2025-07-12', 1, UUID(), NOW()),
(16, 'EXT-000016', 12, 3,'이산화탄소소화기',   '2020-09-09', 5, '2025-09-09', 1, UUID(), NOW()),
(17, 'EXT-000017', 13, 2,'분말소화기 3kg',     '2022-10-04', 5, '2027-10-04', 1, UUID(), NOW()),
(18, 'EXT-000018', 2, 1, '분말소화기 3kg',     '2024-04-13', 5, '2029-04-13', 1, UUID(), NOW()),
(19, 'EXT-000019', 6, 5, '분말소화기 3kg',     '2023-07-07', 5, '2028-07-07', 1, UUID(), NOW()),
(20, 'EXT-000020', 3, 3, '이산화탄소소화기',   '2022-03-01', 5, '2027-03-01', 1, UUID(), NOW()),
(21, 'EXT-000021', 5, 2, '이산화탄소소화기',   '2025-04-18', 5, '2030-04-18', 1, UUID(), NOW()),
(22, 'EXT-000022', 4, 2, '분말소화기 3kg',     '2024-02-17', 5, '2029-02-17', 1, UUID(), NOW()),
(23, 'EXT-000023', 13, 1,'분말소화기 3kg',     '2023-07-12', 5, '2028-07-12', 1, UUID(), NOW()),
(24, 'EXT-000024', 12, 3,'분말소화기 3kg',     '2016-10-09', 5, '2021-10-09', 1, UUID(), NOW()),
(25, 'EXT-000025', 13, 2,'분말소화기 3kg',     '2021-10-04', 5, '2026-10-04', 1, UUID(), NOW()),
(26, 'EXT-000026', 1, 2, '분말소화기 3kg',     '2022-04-22', 5, '2027-04-22', 1, UUID(), NOW()),
(27, 'EXT-000027', 2, 3, '분말소화기 3kg',     '2016-07-09', 5, '2021-07-09', 1, UUID(), NOW()),
(28, 'EXT-000028', 2, 3, '분말소화기 3kg',     '2026-02-11', 5, '2031-02-11', 1, UUID(), NOW()),
(29, 'EXT-000029', 2, 3, '분말소화기 3kg',     '2026-02-11', 5, '2031-02-11', 1, UUID(), NOW()),
(30, 'EXT-000030', 2, 3, '분말소화기 3kg',     '2026-02-11', 5, '2031-02-11', 1, UUID(), NOW()),
(31, 'EXT-000031', 2, 3, '분말소화기 3kg',     '2026-01-11', 5, '2031-01-11', 1, UUID(), NOW()),
(32, 'EXT-000032', 2, 3, '분말소화기 3kg',     '2026-01-02', 5, '2031-01-02', 1, UUID(), NOW())
ON DUPLICATE KEY UPDATE serial_number = VALUES(serial_number);

-- -----------------------------------------------------------------------
-- FireHydrant 데이터 (원본: SQL Server FireHydrant 테이블)
-- 소화전 레코드
-- -----------------------------------------------------------------------
INSERT INTO fire_hydrant
  (hydrant_id, serial_number, hydrant_type, operation_type, building_id, floor_id, location_description, image_path, is_active, created_at)
VALUES
(1,  'HYD-000001', 'Indoor',   'Manual', 1, 1, NULL, '/images/indoorfirehydrant.PNG', 1, NOW()),
(2,  'HYD-000002', 'Indoor',   'Manual', 1, 1, NULL, '/uploads/hydrants/hydrant6.jpg', 1, NOW()),
(3,  'HYD-000003', 'Indoor',   'Manual', 1, 1, NULL, NULL, 1, NOW()),
(4,  'HYD-000004', 'Indoor',   'Auto',   1, 1, NULL, NULL, 1, NOW()),
(5,  'HYD-000005', 'Indoor',   'Auto',   1, 1, NULL, NULL, 1, NOW()),
(6,  'HYD-000006', 'Outdoor',  'Manual', 99, 1, '화장지 4호기 앞', NULL, 1, NOW()),
(7,  'HYD-000007', 'Outdoor',  'Manual', 99, 1, '화장지 4호기 앞 계근대 옆 자동문', NULL, 1, NOW()),
(8,  'HYD-000008', 'Outdoor',  'Manual', 99, 1, '아', NULL, 1, NOW()),
(9,  'HYD-000009', 'Outdoor',  'Auto',   99, 1, NULL, '/uploads/hydrants/hyd_20260224_172553_35fdeed18ed64b12ba42f4aea79d61e2.jpg', 1, NOW()),
(10, 'HYD-000010', 'Outdoor',  'Auto',   99, 1, NULL, '/uploads/hydrants/hydrant20.png', 1, NOW()),
(11, 'HYD-000011', 'Outdoor',  'Manual', 99, 1, NULL, NULL, 1, NOW())
ON DUPLICATE KEY UPDATE serial_number = VALUES(serial_number);

SET FOREIGN_KEY_CHECKS = 1;

-- 검증
SELECT 'Buildings:' as tbl, COUNT(*) as cnt FROM building
UNION ALL
SELECT 'Floors:', COUNT(*) FROM floor
UNION ALL
SELECT 'Extinguishers:', COUNT(*) FROM extinguisher
UNION ALL
SELECT 'FireHydrants:', COUNT(*) FROM fire_hydrant
UNION ALL
SELECT 'FireReceivers:', COUNT(*) FROM fire_receiver
UNION ALL
SELECT 'FirePumps:', COUNT(*) FROM fire_pump;
