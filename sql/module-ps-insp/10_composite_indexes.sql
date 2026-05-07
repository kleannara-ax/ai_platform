-- ============================================================
-- module-ps-insp: 복합 인덱스 추가 (이력 조회 성능 튜닝)
-- 적용 대상: 운영 DB (기존 테이블에 인덱스만 추가)
-- ============================================================
-- 
-- 목적: 검사 이력 / 이력 테이블 탭에서 날짜 범위 + 키워드 복합 검색 시
--        풀테이블 스캔 방지 → INSPECTED_AT 범위로 먼저 필터링 후 LIKE 스캔
--
-- 쿼리 패턴:
--   1) WHERE INSPECTED_AT BETWEEN ? AND ? AND IND_BCD LIKE '%keyword%' ORDER BY INSPECTED_AT DESC
--   2) WHERE INSPECTED_AT BETWEEN ? AND ? AND LOTNR  LIKE '%keyword%' ORDER BY INSPECTED_AT DESC
--   3) WHERE INSPECTED_AT BETWEEN ? AND ? AND MATNR  LIKE '%keyword%' ORDER BY INSPECTED_AT DESC
--
-- 참고: LIKE '%keyword%' (양쪽 와일드카드)는 B-Tree 인덱스의 키 검색에 사용 불가하지만,
--       INSPECTED_AT 범위 조건이 선행 컬럼으로 인덱스 범위 스캔을 수행하므로
--       전체 테이블이 아닌 날짜 범위 내 레코드만 LIKE 스캔 → 성능 대폭 개선
-- ============================================================

-- 1) 검사일시 + 바코드 복합 인덱스
CREATE INDEX IF NOT EXISTS IDX_PS_INSP_INSPAT_INDBCD
    ON ps_inspection (INSPECTED_AT DESC, IND_BCD);

-- 2) 검사일시 + LOT 번호 복합 인덱스
CREATE INDEX IF NOT EXISTS IDX_PS_INSP_INSPAT_LOTNR
    ON ps_inspection (INSPECTED_AT DESC, LOTNR);

-- 3) 검사일시 + 자재코드 복합 인덱스
CREATE INDEX IF NOT EXISTS IDX_PS_INSP_INSPAT_MATNR
    ON ps_inspection (INSPECTED_AT DESC, MATNR);

-- ── 확인 ──
-- 인덱스 적용 확인:
-- SHOW INDEX FROM ps_inspection WHERE Key_name LIKE 'IDX_PS_INSP_INSPAT%';
