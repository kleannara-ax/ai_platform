# module-steam-energy SQL

스팀에너지관리 모듈의 DB 스크립트. **ddl-auto=none** 이므로 반드시 아래 순서로 직접 실행한다.

## 실행 순서

파일 번호 순서대로 실행하면 된다(01 → 06). 번호가 곧 실행 순서다. 모든 파일은 재실행해도 안전하다.

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | JPA 영역 표 17개 (steam_equipment·steam_price·steam_daily_usage + 원단위 일지 4개 + 에너지 회계비용 10개: steam_energy_*) + 인덱스. 감사·소프트 삭제 컬럼 포함 |
| 2 | `02_legacy_schema.sql` | 이관 대시보드 표 3개 (steam_cell_value + 퇴역한 steam_unit_usage·steam_unit_usage_raw) + 인덱스 |
| 3 | `03_seed_data.sql` | 초기 데이터 (설비 마스터 12건 + 단가 샘플 6건) |
| 4 | `04_menu_data.sql` | 플랫폼 상위 메뉴 등록 (STEAM_ENERGY_MGMT) + 역할 권한 |
| 5 | `05_submenus.sql` | 스팀 하위 메뉴(그룹 + 페이지) 전체 재등록 + 역할 권한 |
| 6 | `06_roles.sql` | 스팀 전용 역할 3종을 공통코드 ROLE 그룹에 등록 |

```bash
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 01_schema.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 02_legacy_schema.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 03_seed_data.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 04_menu_data.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 05_submenus.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 06_roles.sql
```

표를 만들고(01~02) 데이터를 넣고(03), 메뉴·권한(04~06)을 등록하는 순서다.

**전제** — 플랫폼 공통 표(`core_menu`·`core_role_menu`·`code_group`·`code_detail`)가 먼저 있어야 한다.
04~06 이 거기에 메뉴·역할을 넣는다. `--default-character-set=utf8mb4` 를 붙여 한글 메뉴명이 깨지지 않게 한다.

`steam_cell_value` 를 화면별로 어떻게 나눠 쓰는지(네임스페이스·col_index)는
실행 대상이 아니라 문서다 — [CELL_CONVENTIONS.md](CELL_CONVENTIONS.md) 참고.

### 운영 데이터를 함께 옮길 때

로컬에서 뽑은 데이터 파일(`steam-data-YYYYMMDD.sql`, REPLACE INTO)이 있으면
**위 01~06 을 모두 실행한 뒤 마지막에** 넣는다. 데이터 파일은 현재 표 이름
(`steam_cell_value`·`steam_unit_usage`·`steam_unit_usage_raw`)으로 뽑은 것이어야 한다.

```bash
mysql -u {user} -p --default-character-set=utf8mb4 {database} < steam-data-YYYYMMDD.sql
```

### 이미 쓰고 있는 DB 에 반영할 때

**번호 순서대로, 아직 안 돌린 것만** 돌린다. 어디까지 돌렸는지는 배포 기록에 남긴다.
2026-10-22 기준으로 로컬·개발서버는 모두 이 폴더의 현재 상태와 같다.

### 정리 이력 (2026-10-22)

개발서버까지 모두 반영된 시점에 **신규 설치 기준으로 파일을 정리**했다. 그전까지 있던 마이그레이션 파일은
지웠고, 내용은 아래와 같다. 필요하면 git 이력(커밋 `bf41665` 까지의 `sql/module-steam-energy/`)에서 볼 수 있다.

| 옛 파일 | 하던 일 | 지금은 |
|---|---|---|
| `03_rename_steam_tables` | `table_cell_value`·`unit_usage`·`unit_usage_raw` → `steam_` 접두로 RENAME | 02 가 처음부터 새 이름으로 만든다 |
| `04_audit_columns` | steam_* 표에 감사·소프트 삭제 컬럼/인덱스 보강 | 01 에 포함 |
| `06_price_code_split` | 폐합성소각로 단가 코드 `waste_*` → `waste1_*`/`waste2_*` 분리 | 시드가 새 코드만 쓴다 |
| `07_remove_fixed_cost_items` | 단가 입력 "(추가) 고정비용" 항목 제거 | 시드에 없음 |
| `11_remove_combo_deduction_items` | 단가 입력 복합보일러 "(차감)" 3항목 제거 | 시드에 없음 |
| `12_remove_month_compare_power_rows` | 마감비교 전력비·에너지 합산 행, 고정값으로 바뀐 차감비 2항목 제거 | 화면이 저장하지 않는다 |
| `13_`·`14_remove_month_compare_composite_*` | 마감비교 복합보일러 스팀구매·복합 LNG 등 입력값 제거 (인보이스 연동으로 대체) | 화면이 저장하지 않는다 |
| `15_remove_waste1_price_items` | 단가 입력 폐합성소각로 1호기 스팀단가 제거 | 시드에 없음 |

## SQL 작성 규칙

이 폴더에 SQL 을 추가할 때 지키는 규칙이다. 지금까지 파일들이 실제로 따르는 방식을 정리한 것이라,
정리 SQL 의 본보기는 위 "정리 이력" 의 옛 파일을 git 에서 보면 된다.

### 1. 파일 이름과 번호

- 이름은 `NN_무엇을_하는지.sql` — 두 자리 번호 + 소문자 snake_case. 번호가 곧 실행 순서다.
- **번호는 마지막 번호 다음을 쓰고, 한 번 쓴 번호는 재사용하지 않는다.** 순서가 꼬이면 다른 환경에서 재현이 안 된다.
- **이미 배포된 파일은 고치지 않는다.** 바꿀 게 있으면 새 번호로 "되돌리는/고치는" 파일을 추가한다.
  예외는 신규 설치용 `01_schema`·`02_legacy_schema`·`03_seed_data` 뿐이다 — 정리 SQL 로 지운 항목은 시드에서도 함께 빼서
  새로 까는 DB 와 기존 DB 가 같은 상태가 되게 한다.
- 파일을 추가하면 위 **실행 순서 표와 명령 블록**에 같은 번호로 한 줄씩 넣는다.
- **모든 환경이 같은 상태가 된 시점**에는 마이그레이션 파일을 걷어내고 신규 설치 파일로 합칠 수 있다(위 "정리 이력").
  그때는 옛 파일이 하던 일과 git 이력 위치를 README 에 표로 남긴다. 아직 안 맞춘 환경이 하나라도 있으면 하지 않는다.

### 2. 머리 주석

파일 첫머리에 아래 네 가지를 적는다. 몇 달 뒤 다른 사람이 봐도 "왜 이 SQL 이 있는지" 알 수 있어야 한다.

```sql
-- 회계비용 마감비교(energy-month-compare) — 화면에서 뺀 항목의 잔여 데이터 정리
--
-- 배경
--   · 화면에서 "2. 전력비", "3. 에너지 합산 / 손익" 을 제거했다.
--   · 엑셀 연동도 해당 행을 매핑에서 뺐다 → 읽는 곳이 없는 잔여 데이터다.
--
-- 남는 것
--   fuel|* 의 나머지 행은 화면이 계속 쓰므로 건드리지 않는다.
--
-- 재실행 안전. DROP/TRUNCATE 없음. DELETE 는 아래 row_key 에만 건다.
```

| 항목 | 내용 |
|------|------|
| 제목 | 어느 화면·기능의 무엇을 하는지 한 줄 |
| 배경 | 왜 필요해졌는지 — 화면/코드 변경과 연결해서 (클래스·상수 이름까지) |
| 남는 것 | 같은 표에서 **건드리지 않는** 범위 (실수로 넓게 지우는 것을 막는다) |
| 안전 선언 | 재실행 가능 여부, 금지 구문 없음, DELETE 범위 |

### 3. 재실행해도 안전해야 한다 (멱등)

같은 파일을 두 번 돌려도 결과가 같고 에러가 없어야 한다. 로컬 `steam` DB 에서 **두 번 실행해 보고** 올린다.

| 하려는 일 | 이렇게 쓴다 | 예 |
|-----------|------------|----|
| 표 만들기 | `CREATE TABLE IF NOT EXISTS` | `01_schema.sql` |
| 컬럼 추가 | `ALTER TABLE … ADD COLUMN IF NOT EXISTS` (MariaDB 10.3+) | 옛 `04_audit_columns` (git 이력) |
| 인덱스 추가 | `CREATE INDEX IF NOT EXISTS` | `01_schema.sql` |
| 행 넣기 | `INSERT … ON DUPLICATE KEY UPDATE` 또는 `INSERT … SELECT … WHERE NOT EXISTS` | `04_menu_data.sql` |
| 코드 바꾸기 | `INSERT IGNORE … SELECT` 로 새 코드에 복사한 뒤 옛 코드 `DELETE` (두 번째 실행은 0건) | 옛 `06_price_code_split` (git 이력) |
| 행 지우기 | `DELETE … WHERE` 로 **자기 코드·row_key 만** | 옛 `11_`·`15_` (git 이력) |

**쓰지 않는 것** — `DROP TABLE`, `TRUNCATE`, `WHERE` 없는 `DELETE`/`UPDATE`,
`USE 데이터베이스명`, `CREATE DATABASE`. DB 이름은 실행하는 쪽이 정한다(`mysql … {database} < 파일`).
주석으로라도 `-- DROP TABLE` 을 남기지 않는다 — 규격 점검에서 금지 구문으로 잡힌다.

파일 머리에 `SET NAMES utf8mb4;` 를 둔다. 한글 메뉴명·항목명이 깨지지 않게 하기 위해서다.

### 4. 공용 표 (core_menu · core_role_menu · code_group · code_detail)

플랫폼과 다른 모듈이 같이 쓰는 표다. **우리 코드(`STEAM_` 접두)만 건드린다.**

- 상위 메뉴 1건은 `ON DUPLICATE KEY UPDATE` 로 넣는다 (`04_menu_data.sql`).
- 하위 메뉴 44건은 **자기 코드를 지우고 다시 넣는 "전체 재등록"** 방식이다 (`05_submenus.sql`).
  `DELETE … WHERE MENU_CODE LIKE 'STEAM_%' AND MENU_CODE <> 'STEAM_ENERGY_MGMT'` 뒤에 `INSERT … SELECT` 를 하므로
  재실행해도 중복되지 않는다. 메뉴 이름·순서를 바꿀 때는 이 파일을 고치지 말고 **새 번호 파일에 UPDATE** 로 넣는다.
- 역할 권한(`core_role_menu`)도 메뉴와 같이 지우고 같이 넣는다. 메뉴만 지우고 권한을 남기면 고아 행이 생긴다.
- 다른 모듈 코드(`KIMS_`, `FIRE_`, `DAILY_` …)는 조회도 하지 않는다.

### 5. 데이터 정리 SQL

화면에서 항목을 뺐으면 저장된 값도 같이 정리한다. 안 그러면 다음 업로드·연동에서 옛 값이 되살아난다.

- 지울 코드를 **파일 안에 목록으로** 적고, 그 목록으로만 `DELETE` 한다 (`LIKE '%'` 로 넓게 잡지 않는다).
- 단가 항목은 두 군데를 같이 본다 — 등록 단가 `steam_cell_value(table_name='unit_price_detail')` 와 샘플 `steam_price`.
- 파일 끝에 `-- 확인용` 으로 전후를 셀 수 있는 `SELECT` 를 주석으로 남긴다.

```sql
DELETE FROM steam_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key IN ('comb_lng_cost', 'comb_disposal', 'comb_depreciation');

DELETE FROM steam_price
 WHERE ITEM_CODE IN ('comb_lng_cost', 'comb_disposal', 'comb_depreciation');

-- 확인용
-- SELECT FACILITY, ITEM_CODE FROM steam_price WHERE FACILITY = '복합보일러';
```

### 6. 새 표를 만들 때

- 표 이름은 **`steam_` 접두 + 소문자 snake_case** (`steam_equipment`, `steam_price`). 규격의 `MOD_{모듈}_` 접두 대신
  이 모듈은 처음부터 `steam_` 으로 통일해 왔다 — 섞지 않는다.
- 컬럼은 대문자 `UPPER_SNAKE_CASE`, 모든 컬럼에 `COMMENT`, 표에도 `COMMENT='[module-steam-energy] …'`.
- `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`.
- 업무 표는 공통 감사 컬럼을 둔다 — `CREATED_AT/CREATED_BY/UPDATED_AT/UPDATED_BY/DELETED_AT/DELETED_BY/DELETED_YN`.
  삭제는 `DELETED_YN='Y'` 소프트 삭제이고, 엔티티는 `@SQLRestriction("DELETED_YN = 'N'")` 로 가린다 (`01_schema.sql` 참고).
- 키 이름 — PK 는 `{TABLE}_ID`, 인덱스 `IDX_STEAM_{표}_{컬럼}`, 외래키 `FK_STEAM_{표}_{참조}`, 유니크 `UK_STEAM_{표}_{컬럼들}` (대문자, `01_schema.sql` 기준).
  이관 표 `steam_cell_value` 의 소문자 `uk_…` 는 옛 이름이라 예외다 — 새로 만들 땐 따라 하지 않는다.
- JPA 엔티티의 `@Table(name=…)`·`@Column(name=…)` 과 글자 하나까지 같아야 한다. `ddl-auto=none` 이라 틀려도 기동 때 안 잡힌다.

### 7. `steam_cell_value` 를 쓸 때

이관 대시보드 화면들은 표를 새로 만들지 않고 `steam_cell_value` 한 표를 **`table_name` 으로 나눠** 쓴다.

- 새 화면이 저장할 게 생기면 `table_name` 을 하나 정하고(소문자 snake_case, 화면 이름을 담아서)
  **[CELL_CONVENTIONS.md](CELL_CONVENTIONS.md) 에 `row_key`·`col_index` 규약을 먼저 적는다.**
- `col_index` 는 화면 항목의 **고정 번호**다. 번호를 재배치하면 기존 데이터가 다른 항목에 붙는다 — 추가만 하고 바꾸지 않는다.
- `row_key` 는 뜻이 보이게 짓는다 — `일(2자리)`, `섹션|항목`, `표|행` 처럼. 셀 주소(`A1`)는 세부 운영내역처럼 양식이 곧 화면인 경우에만.
- 유니크 키가 `(month, table_name, row_key, col_index)` 이므로, 월과 무관한 값도 `month` 를 비워 두지 말고 규약대로 채운다.

### 8. 올리기 전 확인

1. 로컬 `steam` DB 에 **두 번** 실행 — 두 번째가 에러 없이 끝나는지.
2. 지우는 SQL 이면 실행 전후 건수를 비교한다. 예상보다 많이 줄면 `WHERE` 가 넓은 것이다.
3. 실행 순서 표·명령 블록·`CELL_CONVENTIONS.md` 를 같이 고쳤는지.
4. 개발서버에 반영할 땐 **번호 순서대로, 아직 안 돌린 것만** 돌린다. 어디까지 돌렸는지는 배포 기록에 남긴다.

### 9. 데이터 이관 파일

로컬 데이터를 통째로 옮기는 `steam-data-YYYYMMDD.sql` 은 이 폴더에 두지 않고(저장소 밖) 따로 전달한다.

- `REPLACE INTO` 로 만들고, 여러 행을 한 문장에 묶어 한 트랜잭션으로 넣는다 (5만 건 기준 1초 안팎).
- 스팀 표만 담는다 — `steam_cell_value`·`steam_unit_usage`·`steam_unit_usage_raw`·`steam_*`.
  `core_*`·다른 모듈 표, 그리고 `steam_cell_value` 안의 `app_users`(옛 로그인 해시)는 **넣지 않는다.**
- 스키마·메뉴 SQL(01~06)을 모두 돌린 **다음에** 실행한다.

## 테이블

| 테이블 | 설명 | UNIQUE 키 |
|--------|------|-----------|
| `steam_equipment` | 스팀 설비 마스터 | `EQUIPMENT_CODE` |
| `steam_price` | 시설별 계약·구매 단가(연/월) | `(YEAR_NO, MONTH_NO, ITEM_CODE)` |
| `steam_daily_usage` | 일일 스팀 사용 실적 | `(USAGE_DATE, EQUIPMENT_ID)` |
| `steam_cell_value` | 이관 대시보드 화면들의 셀 저장소 — `table_name` 으로 논리 표를 나눈다 ([CELL_CONVENTIONS.md](CELL_CONVENTIONS.md)) | `(month, table_name, row_key, col_index)` |
| `steam_unit_usage` | (퇴역 2026-11) 원단위 일지 — `steam_unit_daily_usage` 로 대체, 읽는 코드 없음 | `(month, day, machine_no)` |
| `steam_unit_usage_raw` | (퇴역 2026-11) 원단위 원시값 — `steam_unit_daily_common` 으로 대체 | `(month, day)` |
| `steam_unit_daily_usage` | 원단위 일지 — 호기별 일일 스팀 사용·생산 실적 (2026-11, 규격 전환) | `(USAGE_DATE, MACHINE_CODE)` |
| `steam_unit_daily_common` | 원단위 일지 — 일자별 공통 계측값 (디스퍼져 총량·TOC·PICA) | `(USAGE_DATE)` |
| `steam_lng_daily_usage` | 일별 LNG 연료 사용량 (보일러·버너·복합+외부, 단가) | `(USAGE_DATE)` |
| `steam_fuel_daily_usage` | 유동상·폐합성소각로·복합·외부 일일 스팀 구매량과 단가 | `(USAGE_DATE)` |
| `steam_energy_accounting_plan` | 회계비용 계획 — 회계비용 계획 (energy-accounting-plan.html) (21개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_fuel_plan` | 연료비 사업계획 — 연료비 사업계획(안) (energy-fuel-plan.html) (29개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_ratio_boiler` | 연료비 배분비율(보일러별) — 연료비 배분비율 › 보일러별 종합 (energy-fuel-ratio.html t01~t03) (19개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_ratio_machine` | 연료비 배분비율(호기별) — 연료비 배분비율 › 호기별 연료비 배분 (energy-fuel-ratio.html t04~t06) (22개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_ratio_burner` | 연료비 배분비율(버너 LNG) — 연료비 배분비율 › 버너 LNG (energy-fuel-ratio.html t10~t12) (13개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_ratio_steam_unit` | 스팀사용량 원단위(기타 포함) — 연료비 배분비율 › 종합 원단위 17번째 표 (energy-fuel-ratio.html t17) (7개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_ratio_ref2019` | 연료비 배분비율(2019 참고) — 연료비 배분비율 › 2019 참고 표 (energy-fuel-ratio.html t18~t20) (31개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_month_compare` | 회계비용 마감비교 — 월별 회계비용 마감 vs 실마감 차이 (energy-month-compare.html) (9개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_energy_month_compare_note` | 회계비용 마감비교 비고 — 월별 회계비용 마감 vs 실마감 차이 — 비고 (energy-month-compare.html) (10개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO)` |
| `steam_energy_fuel_summary` | 연료비 요약 — 연료비 요약 (energy-fuel-summary.html) (49개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_combo_daily_production` | 복합보일러 스팀생산실적 — 복합보일러 › 스팀생산실적 (combo-boiler-steam-production.html) (18개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_combo_daily_downtime` | 복합보일러 운휴·디스퍼져 — 복합보일러 › 운휴시간·디스퍼져 (combo-boiler-downtime.html) (9개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_combo_monthly_price` | 복합보일러 월 연료단가 — 복합보일러 › 스팀생산실적 › N월 연료단가 (combo-boiler-steam-production.html) (6개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_combo_recovery_loss` | 복합보일러 스팀 회수 감소 — 복합보일러 › 스팀 회수 감소 (combo-boiler-recovery-loss.html) (6개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO, LINE_NO)` |
| `steam_combo_plan` | 복합보일러 연간 계획 — 복합보일러 › 기타(계획·LNG사용량) (combo-boiler-etc.html) (7개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, TARGET_YM)` |
| `steam_incinerator_contract_daily` | 소각로 도급내역 — 폐합성수지 소각로 › 도급내역 (incinerator-contract.html) (12개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_incinerator_plan_monthly` | 소각로 사업계획대비 — 폐합성수지 소각로 › 사업계획대비 (incinerator-plan-vs.html), 도급내역의 월 인건비 (14개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_incinerator_operation_monthly` | 소각로 계획대비 가동실적 — 폐합성수지 소각로 › 계획대비 가동실적 (incinerator-operation.html) (10개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_incinerator_cost_monthly` | 소각로 총 운영비용 — 폐합성수지 소각로 › 총 운영비용 (incinerator-total-cost.html) (39개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_srf_invoice_summary` | SRF 인보이스 Summary — SRF Boiler invoice › Summary(VK) (srf-boiler-invoice.html) (17개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_srf_invoice_client_item` | SRF 인보이스 Client Invoice — SRF Boiler invoice › Client Invoice (srf-boiler-invoice-client.html) (5개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO, SECTION, LINE_NO)` |
| `steam_srf_invoice_operation_daily` | SRF 인보이스 운영내역 — SRF Boiler invoice › 운영내역 (srf-boiler-invoice-operation.html) (1개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_srf_invoice_monthly` | SRF 인보이스 월별 운영내역 — SRF Boiler invoice › 월별 운영내역 (srf-boiler-invoice-monthly.html) (21개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_srf_invoice_accumulated` | SRF 인보이스 누계 스팀량 — SRF Boiler invoice › Accumulated steam volume (srf-boiler-invoice-accumulated.html) (3개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_srf_invoice_bsc` | SRF 인보이스 BSC 단가 — SRF Boiler invoice › BSC (srf-boiler-invoice-bsc.html) (1개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO)` |
| `steam_srf_invoice_adjustment` | SRF 인보이스 Adjustment Factor — SRF Boiler invoice › Adjustment Factor (srf-boiler-invoice-adjustment.html) (3개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, SECTION, ITEM_CODE)` |
| `steam_fluidized_month` | 유동상 월별 계획·비용 — 유동상 운영내역 › 운영실적(사업계획대비·실적비교·도급비용 지급현황), 월별 운영내역 (unit-fluidized-summary.html, unit-fluidized-monthly.html) (17개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |
| `steam_fluidized_contract_daily` | 유동상 소각로 도급비용 — 유동상 운영내역 › 운영실적 › 소각로 도급비용 (unit-fluidized-summary.html) (4개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_fluidized_srf_inbound_daily` | 유동상 SRF 입고내역 — 유동상 운영내역 › SRF(비성형) 입고내역 (unit-fluidized-srf-inbound.html) (10개 값 컬럼, 2026-11 규격 전환) | `(USAGE_DATE)` |
| `steam_fluidized_srf_rate` | 유동상 SRF 처리단가 — 유동상 운영내역 › SRF(비성형) 입고내역 › 처리단가 (unit-fluidized-srf-inbound.html) (10개 값 컬럼, 2026-11 규격 전환) | `(YEAR_NO, MONTH_NO)` |

- 컬럼명 대문자 `UPPER_SNAKE_CASE`, 테이블명 소문자 `snake_case`
- `ENGINE=InnoDB`, `CHARSET=utf8mb4`, `COLLATE=utf8mb4_general_ci`
- 모든 컬럼에 `COMMENT` 부여

## 원단위 일지 그룹 전환 (2026-11) — 업무 모듈 규격 4-1 맞춤

이관 대시보드 중 **원단위 일지 7개 화면**(오늘 일지 등록, 단가 입력, 스팀사용 원단위, 스팀 사용량, LNG 연료 사용 비용,
유동상/소각로/복합 일일 사용량, 호기별 요약)을 `steam_cell_value` 셀 저장소에서 **실제 컬럼을 가진 표 4개 + 기존 `steam_price`** 로 옮겼다.
API 는 `/steam-energy-api/**`, 응답은 core `ApiResponse<T>`.

**데이터 이관 없음 (단가 제외)** — 새 표 4개는 비어 있다. 지난 데이터가 필요하면 `원단위 일지 누계(YYYY년).xls` 를
`POST /steam-energy-api/unit-log/import-excel?year=YYYY` 로 다시 올려 채운다. 옛 네임스페이스(`usage_m_*`, `lng_m_*`, `flow_m_*`,
`unit_price*`, `steam_unit_usage`·`_raw`)는 다른 묶음 전환이 끝날 때까지 읽기용으로만 남는다.
단가(`unit_price_detail` → `steam_price`)만은 다른 사이트와 공유하는 데이터라 `migration/2026-11-20_unit_price_detail_to_steam_price.sql`
로 옮긴다 (재실행 안전, 신규 설치에서는 불필요).

### 화면 전환 진행 — 7개 화면 모두 완료 (2026-11-20)

| 순서 | 화면 | 읽기/쓰기 |
|---|---|---|
| 1 | 단가 입력 (`unit-price.html`) | `GET /prices/year/{year}` · `POST /prices` · 연동은 `POST /prices/apply-daily`(LNG·유동상·폐합성) + 옛 셀(세부 운영내역·도급내역·Invoice Summary, 해당 묶음 전환 때 제거) |
| 2 | 유동상/소각로/복합 일일 사용량 (`unit-flow-daily.html`) | `GET/POST /fuel-daily-usages` (그 날 행 전체), '단가 적용' = `POST /prices/apply-daily` |
| 3 | LNG 연료 사용 비용 (`unit-lng-cost.html`) | `GET/POST /lng-daily-usages` |
| 4·5 | 스팀사용 원단위 (`unit-steam-unit.html`) · 스팀 사용량 (`unit-steam-usage.html`) | `GET/POST /unit-daily-usages` · `/unit-daily-commons`, 엑셀 `POST /unit-log/import-excel?year=` · `GET /unit-log/export-excel?year=` |
| 6 | 오늘 일지 등록 (`today-entry.html`) | 위 네 표에 각각 POST (비워 둔 칸은 기존 값 유지) |
| 7 | 호기별 요약 (`unit-machine-summary.html`) | 네 표 GET, LNG·연료 비용은 화면 계산 |
| 8 | 유동상 묶음 재연결 | 운전일지·세부 운영내역·운전일지 엑셀의 스팀 송기량(세부 7행) → `steam_fuel_daily_usage.FLUID_STEAM_TON` (`SteamFuelDailyUsageService`) |
| 9 | 정리 | `/steam/api/tables/unit-usage/*`·`SteamUnitExcelImport/ExportService`·`TableService` 의 `steam_unit_usage(_raw)` 정의 제거. 표 자체는 02 에 남김(퇴역 표시) |

저장 규칙(공통): 칸 단위 PATCH 가 아니라 **그 날(·호기) 행 전체 POST 업서트**. 여러 화면이 같은 행의 다른 컬럼을 쓰므로
화면은 저장 직전에 그 행을 다시 읽어 자기 컬럼만 바꿔 보낸다. 삭제된 행과 같은 키로 다시 저장하면 되살린다.
화면 열 자리 버그 2건(PM-3 Ventilation 가동시간, TM-4·5 가동시간이 원단위 열에 입력되던 것)은 전환하면서 바로잡았다.


### 테이블-컬럼 매핑표 (화면 입력항목 ↔ 컬럼)

공통 감사 컬럼 7개(`CREATED_AT`·`CREATED_BY`·`UPDATED_AT`·`UPDATED_BY`·`DELETED_YN`·`DELETED_AT`·`DELETED_BY`)는 생략.
계산값(스팀량 합·원단위·LNG 합계/비용·구매 비용·스팀구매비 등)은 저장하지 않고 화면에서 계산한다.

| 메뉴/기능 | 테이블명 | 컬럼명 | 타입 | NULL | COMMENT | 대응 입력항목 |
|---|---|---|---|---|---|---|
| 스팀사용 원단위 · 스팀 사용량 · 오늘 일지 | `steam_unit_daily_usage` | UNIT_USAGE_ID | BIGINT | N | 원단위 일지 ID | (PK) |
| | | USAGE_DATE | DATE | N | 일자 | 조회 연·월 + 일 |
| | | MACHINE_CODE | VARCHAR(20) | N | 호기 | PM-2 / PM-3 / TM-3 / TM-4 / TM-5 |
| | | MAIN_STEAM | DECIMAL(18,6) | Y | Main 스팀량(톤) | PM-2·PM-3 Main 스팀량 |
| | | COATER_STEAM | DECIMAL(18,6) | Y | Coater 스팀량(톤) | PM-2·PM-3 Coater 스팀량 |
| | | VENTILATION_STEAM | DECIMAL(18,6) | Y | 환기시스템 스팀량(톤) | PM-3 환기시스템 |
| | | STEAM | DECIMAL(18,6) | Y | 스팀량(톤) | TM-3~5 스팀량 |
| | | PRODUCTION_KG | DECIMAL(18,6) | Y | 생산량(kg) | 생산량 |
| | | PRODUCTION_TYPE | VARCHAR(50) | Y | 생산지종 | PM-2·PM-3 생산지종 |
| | | RUNTIME_HOURS | DECIMAL(12,4) | Y | 가동시간(hr) | 호기별 가동시간 |
| | | DISPERSER_RUNTIME_HOURS | DECIMAL(12,4) | Y | 디스퍼져 가동시간(hr) | PM-3 디스퍼져 가동시간 |
| | | VENT_RUNTIME_HOURS | DECIMAL(12,4) | Y | Ventilation 가동시간(hr) | PM-3 Ventilation 가동시간 |
| | | NOTE | VARCHAR(500) | Y | 비고 | PM-2·PM-3 비고 |
| 스팀사용 원단위 공통값 · 오늘 일지 | `steam_unit_daily_common` | UNIT_COMMON_ID | BIGINT | N | 원단위 공통값 ID | (PK) |
| | | USAGE_DATE | DATE | N | 일자 (UNIQUE) | |
| | | DISPERSER_TOTAL | DECIMAL(18,6) | Y | 디스퍼져 스팀량 총량(톤) | 디스퍼져 스팀량 |
| | | TOC_STEAM | DECIMAL(18,6) | Y | TOC 스팀량(환경관리) | TOC 스팀량 |
| | | PICA121_VENT | DECIMAL(18,6) | Y | PICA-121 Vent | PICA-121 Vent |
| LNG 연료 사용 비용 · 오늘 일지 | `steam_lng_daily_usage` | LNG_USAGE_ID | BIGINT | N | LNG 일일 사용 ID | (PK) |
| | | USAGE_DATE | DATE | N | 일자 (UNIQUE) | |
| | | BOILER_20T_1 / BOILER_20T_2 / BOILER_50T / BOILER_60T | DECIMAL(18,6) | Y | 보일러 LNG (N㎥) | LNG (20톤-1) / (20톤-2) / (50톤) / (60톤) |
| | | BOILER_COMBINED / BOILER_NEW_INCINERATOR | DECIMAL(18,6) | Y | 복합보일러 / 신설소각로 LNG | 복합보일러 / 신설소각로 |
| | | BOILER_UNIT_PRICE | DECIMAL(18,6) | Y | 보일러 LNG 단가(원/N㎥) | 단가 (단가 입력 lng_unit 전파) |
| | | BURNER_PM3 / BURNER_TM3 / BURNER_TM4 / BURNER_TM5 | DECIMAL(18,6) | Y | 버너 LNG (N㎥) | PM-3 / TM-3 / TM-4 / TM-5 |
| | | BURNER_UNIT_PRICE | DECIMAL(18,6) | Y | 버너 LNG 단가(원/N㎥) | 단가 (lng_unit 전파) |
| | | MIX_PM2 / MIX_PM3 / MIX_TM3 / MIX_TM4 / MIX_TM5 | DECIMAL(18,6) | Y | 복합+외부 LNG 호기별 | 엑셀 3번 구역 (화면 없음) |
| 유동상/소각로/복합 일일 사용량 · 오늘 일지 | `steam_fuel_daily_usage` | FUEL_USAGE_ID | BIGINT | N | 스팀 구매 일일 사용 ID | (PK) |
| | | USAGE_DATE | DATE | N | 일자 (UNIQUE) | |
| | | FLUID_STEAM_TON / FLUID_UNIT_PRICE | DECIMAL(18,6) | Y | 유동상 스팀량(톤) / 단가(원/톤) | 유동상(톤) / 단가 (fluid_steam 전파) |
| | | INCINERATOR_STEAM_TON / INCINERATOR_UNIT_PRICE | DECIMAL(18,6) | Y | 폐합성소각로 스팀생산량 / 단가 | 폐합성소각로 스팀생산량 / 단가 (waste2_steam 전파) |
| | | COMBINED_STEAM_TON / COMBINED_UNIT_PRICE / COMBINED_DEDUCTION | DECIMAL(18,6) | Y | 복합보일러 스팀생산량 / 단가 / 차감비(백만원) | 복합보일러 표 |
| | | EXT_BOILER_STEAM_TON / EXT_BOILER_UNIT_PRICE / EXT_BOILER_DEDUCTION | DECIMAL(18,6) | Y | 외부보일러 스팀생산량 / 단가 / 차감비(백만원) | 외부보일러 표 |
| 단가 입력 | `steam_price` (기존) | YEAR_NO / MONTH_NO / ITEM_CODE / FACILITY / ITEM_NAME / UNIT / PRICE_VALUE | 기존 | | | 항목코드 × 월 단가 |

### API

| Method | URL | 설명 |
|---|---|---|
| GET | `/steam-energy-api/unit-daily-usages?year=&month=&machineCode=` | 호기별 일일 실적 목록 (month·machineCode 생략 가능) |
| POST | `/steam-energy-api/unit-daily-usages` | (일자+호기) 업서트 — 행 전체 |
| PUT | `/steam-energy-api/unit-daily-usages/month` | 한 달치 일괄 저장 (clearMissing) |
| DELETE | `/steam-energy-api/unit-daily-usages/{id}` | 소프트 삭제 |
| GET/POST/PUT/DELETE | `/steam-energy-api/unit-daily-commons[...]` | 일자별 공통 계측값 (같은 모양, 키 = 일자) |
| GET/POST/PUT/DELETE | `/steam-energy-api/lng-daily-usages[...]` | 일별 LNG |
| GET/POST/PUT/DELETE | `/steam-energy-api/fuel-daily-usages[...]` | 일별 스팀 구매량 |
| POST | `/steam-energy-api/prices/apply-daily` | 등록 단가(lng_unit / fluid_steam / waste2_steam)를 그 달 일별 표에 일괄 적용 |
| POST | `/steam-energy-api/unit-log/import-excel?year=` | 원단위 일지 누계 엑셀 가져오기 (파일이 그 달의 기준) |
| GET | `/steam-energy-api/unit-log/export-excel?year=` | 연도 데이터를 서식에 채워 내려받기 |

쓰기 API 는 `STEAM_ENERGY_MGMT` 메뉴 권한(`@PreAuthorize`)이 필요하다. 같은 일자를 삭제했다가 다시 저장하면 삭제된 행을 되살린다
(UNIQUE 키와 소프트 삭제의 충돌 방지).

## 에너지 회계비용 그룹 전환 (2026-11-20) — 업무 모듈 규격 4-1 맞춤 (묶음 2)

이관 대시보드 중 **에너지 회계비용 5개 화면**(회계비용 계획, 연료비 사업계획, 연료비 배분비율, 월별 회계비용 마감 vs 실마감, 연료비 요약)을
`steam_cell_value` 논리 표 5개(`energy_accounting_plan`·`energy_fuel_plan`·`energy_fuel_ratio`·`energy_month_compare`·`energy_fuel_summary`)에서 **실제 컬럼 표 10개** 로 옮겼다.

설계 규칙:

- 연/월 한 행(비고만 연도 행). **입력 항목 1개 = 컬럼 1개.** 화면에서만 계산하는 소계·원단위·손익 행은 저장하지 않는다.
- 예외: **다른 화면(또는 다른 사이트)이 읽는 계산값**은 COMMENT 에 '화면 계산값' 으로 표시한 컬럼에 저장한다 (원본 화면이 계산을 마치면 `PUT /year` 로 달라진 달만 저장).
  마감비교 ← 배분비율 t04 r03·r07, t06 r03·r07, t11 r09 / 배분비율 ← 마감비교 실적·스팀톤당 연료비·폐합성·유동상 / 연료비 요약 ← 네 화면 / 다른 사이트 ← t17.
- 전년 12월·다음해 1월 열은 따로 저장하지 않고 **이웃 연도 행**(전년도 12월 행, 다음해 1월 행)을 읽고 쓴다.
- 합계·평균 열은 저장하지 않는다. 옛 셀의 '단위 글자'(배분비율 t04 이후 col 1)는 버린다.
- 옛 셀 좌표 ↔ 컬럼 매핑은 스펙 한 곳에서 생성한다: JS `static/steam/js/energy-cell-map.js`, Java `service/energy/EnergyCellMapData`.
  화면은 `energy-store.js` 로 옛 (row_key, col_index) 좌표를 그대로 쓰고, 엑셀 서비스 4개는 `EnergyCellBridge` 로 저장소만 바꿨다(라벨 매핑은 그대로).

### 매핑표 (옛 row_key → 표.컬럼)

| 옛 논리 표 | 옛 row_key | 테이블 | 컬럼 | 구분 | COMMENT |
|---|---|---|---|---|---|
| `energy_accounting_plan` | `r01` | `steam_energy_accounting_plan` | `COMPOSITE_STEAM_BUY` | 입력 | 연료비: 복합보일러 스팀구매(백만원) |
| `energy_accounting_plan` | `r02` | `steam_energy_accounting_plan` | `ZESCO_STEAM_BUY` | 입력 | 연료비: 제스코 스팀구매(백만원) |
| `energy_accounting_plan` | `r03` | `steam_energy_accounting_plan` | `LNG_BUY` | 입력 | 연료비: LNG 구매(백만원) |
| `energy_accounting_plan` | `r04` | `steam_energy_accounting_plan` | `INCINERATOR_COST` | 입력 | 연료비: 폐합성소각로(백만원) |
| `energy_accounting_plan` | `r05` | `steam_energy_accounting_plan` | `FLUID_COST` | 입력 | 연료비: 유동상소각로(백만원) |
| `energy_accounting_plan` | `r07` | `steam_energy_accounting_plan` | `ZESCO_OFFSET` | 입력 | 차감비: 제스코 상계비(용수사용 등) |
| `energy_accounting_plan` | `r08` | `steam_energy_accounting_plan` | `ZESCO_PENALTY` | 입력 | 차감비: 제스코 페널티 |
| `energy_accounting_plan` | `r09` | `steam_energy_accounting_plan` | `ZESCO_ADVANCE_REPAY` | 입력 | 차감비: 제스코 선급금 상환 |
| `energy_accounting_plan` | `r10` | `steam_energy_accounting_plan` | `COMPOSITE_LNG_WATER` | 입력 | 차감비: 복합 LNG·용수사용량 |
| `energy_accounting_plan` | `r11` | `steam_energy_accounting_plan` | `COMPOSITE_INVEST` | 입력 | 차감비: 복합 투자비 감가상각 |
| `energy_accounting_plan` | `r12` | `steam_energy_accounting_plan` | `COMPOSITE_INCOME` | 입력 | 차감비: 복합보일러 처분수익 연료비 차감분 |
| `energy_accounting_plan` | `r13` | `steam_energy_accounting_plan` | `LAND_LEASE` | 입력 | 차감비: 토지 지상권 지료 |
| `energy_accounting_plan` | `r16` | `steam_energy_accounting_plan` | `PAPER_FUEL` | 화면 계산값 | 제지 연료비 = 실적 × 0.7 (화면 계산값, 연료비 요약이 읽음) |
| `energy_accounting_plan` | `r17` | `steam_energy_accounting_plan` | `LIVING_FUEL` | 화면 계산값 | 생활 연료비 = 실적 − 제지 (화면 계산값, 연료비 요약이 읽음) |
| `energy_accounting_plan` | `r19` | `steam_energy_accounting_plan` | `FUEL_VS_PLAN` | 입력 | 회계비용 대비 증감(직접 입력) |
| `energy_accounting_plan` | `r22` | `steam_energy_accounting_plan` | `PAPER_PROD_TON` | 입력 | 제지 제품생산량(톤) |
| `energy_accounting_plan` | `r23` | `steam_energy_accounting_plan` | `TISSUE_PROD_TON` | 입력 | 화장지 제품생산량(톤) |
| `energy_accounting_plan` | `r31` | `steam_energy_accounting_plan` | `STEAM_PROD_TON` | 입력 | 보조: 스팀생산량(톤) |
| `energy_accounting_plan` | `r32` | `steam_energy_accounting_plan` | `LNG_BURNER_COST` | 입력 | 보조: LNG 직버너 사용 비용(백만원) |
| `energy_accounting_plan` | `r36` | `steam_energy_accounting_plan` | `COMPOSITE_NET` | 화면 계산값 | 참고: 복합 = 스팀구매 − 차감비 일부 (화면 계산값, 연료비 요약이 읽음) |
| `energy_accounting_plan` | `r37` | `steam_energy_accounting_plan` | `ZESCO_NET` | 화면 계산값 | 참고: 제스코 = 스팀구매 − 상계비 − 페널티 − 선급금 (화면 계산값) |
| `energy_fuel_plan` | `t01|r01` | `steam_energy_fuel_plan` | `PAPER_PROD_TON` | 입력 | 스팀사용량 계획: 제지 생산량(톤/월) |
| `energy_fuel_plan` | `t01|r02` | `steam_energy_fuel_plan` | `TISSUE_PROD_TON` | 입력 | 스팀사용량 계획: 화장지 생산량(톤/월) |
| `energy_fuel_plan` | `t01|r04` | `steam_energy_fuel_plan` | `PAPER_UNIT` | 입력 | 스팀사용량 계획: 제지 생산원단위(톤/톤·제품) |
| `energy_fuel_plan` | `t01|r05` | `steam_energy_fuel_plan` | `TISSUE_UNIT` | 입력 | 스팀사용량 계획: 화장지 생산원단위(톤/톤·제품) |
| `energy_fuel_plan` | `t01|r11` | `steam_energy_fuel_plan` | `PAPER_TOTAL_STEAM` | 화면 계산값 | 제지 총 사용량(톤/월) (화면 계산값, 연료비 요약이 읽음) |
| `energy_fuel_plan` | `t01|r12` | `steam_energy_fuel_plan` | `TISSUE_TOTAL_STEAM` | 화면 계산값 | 화장지 총 사용량(톤/월) (화면 계산값, 연료비 요약이 읽음) |
| `energy_fuel_plan` | `t02|r01` | `steam_energy_fuel_plan` | `ETC_DISPERSER` | 입력 | 기타 스팀사용량: 디스퍼져(톤/월) |
| `energy_fuel_plan` | `t02|r04` | `steam_energy_fuel_plan` | `ETC_TOC` | 입력 | 기타 스팀사용량: TOC 재생설비(톤/월) |
| `energy_fuel_plan` | `t03|r02` | `steam_energy_fuel_plan` | `OPERATING_DAYS` | 입력 | 가동일수(일) |
| `energy_fuel_plan` | `t04|r02` | `steam_energy_fuel_plan` | `PROD_INCINERATOR` | 입력 | 스팀생산량: 폐합성소각로(톤/월) |
| `energy_fuel_plan` | `t04|r03` | `steam_energy_fuel_plan` | `PROD_COMPOSITE` | 입력 | 스팀생산량: 복합보일러(톤/월) |
| `energy_fuel_plan` | `t04|r04` | `steam_energy_fuel_plan` | `PROD_EXT_BOILER` | 입력 | 스팀생산량: 외부보일러(톤/월) |
| `energy_fuel_plan` | `t04|r05` | `steam_energy_fuel_plan` | `PROD_FLUID` | 입력 | 스팀생산량: 유동상소각로(톤/월) |
| `energy_fuel_plan` | `t06|r02` | `steam_energy_fuel_plan` | `LNG_COMPOSITE` | 입력 | 연료사용량: 복합 LNG(㎥/월) |
| `energy_fuel_plan` | `t06|r03` | `steam_energy_fuel_plan` | `LNG_TM3` | 입력 | 연료사용량: 화장지3 LNG(㎥/월) |
| `energy_fuel_plan` | `t06|r04` | `steam_energy_fuel_plan` | `LNG_TM4` | 입력 | 연료사용량: 화장지4 LNG(㎥/월) |
| `energy_fuel_plan` | `t06|r05` | `steam_energy_fuel_plan` | `LNG_TM5` | 입력 | 연료사용량: 화장지5 LNG(㎥/월) |
| `energy_fuel_plan` | `t06|r07` | `steam_energy_fuel_plan` | `LNG_PM3` | 입력 | 연료사용량: 제지3 LNG(㎥/월) |
| `energy_fuel_plan` | `t07|r01` | `steam_energy_fuel_plan` | `PRICE_LNG` | 입력 | 연료단가: LNG 단가(원/㎥) |
| `energy_fuel_plan` | `t07|r02` | `steam_energy_fuel_plan` | `PRICE_INCINERATOR` | 입력 | 운영단가: 소각로 위탁(원/톤) |
| `energy_fuel_plan` | `t07|r03` | `steam_energy_fuel_plan` | `PRICE_COMPOSITE` | 입력 | 운영단가: 복합 기본(원/톤) |
| `energy_fuel_plan` | `t07|r04` | `steam_energy_fuel_plan` | `PRICE_EXT_BOILER` | 입력 | 운영단가: 외부 기본(원/톤) |
| `energy_fuel_plan` | `t07|r05` | `steam_energy_fuel_plan` | `PRICE_FLUID` | 입력 | 운영단가: 유동상소각로(원/톤) |
| `energy_fuel_plan` | `t08|r02` | `steam_energy_fuel_plan` | `OPEX_INCINERATOR` | 입력 | 보일러 운영비: 소각로 운영비(백만원/월) |
| `energy_fuel_plan` | `t08|r05` | `steam_energy_fuel_plan` | `OPEX_FLUID` | 입력 | 보일러 운영비: 유동상소각로(백만원/월) |
| `energy_fuel_plan` | `t09|r04` | `steam_energy_fuel_plan` | `PAY_EXT_ADVANCE` | 입력 | 실지급비: 외부 선급금 차감(백만원/월) |
| `energy_fuel_plan` | `t09|r05` | `steam_energy_fuel_plan` | `PAY_EXT_PENALTY` | 입력 | 실지급비: 외부 페널티 차감(백만원/월) |
| `energy_fuel_plan` | `t09|r06` | `steam_energy_fuel_plan` | `PAY_INCINERATOR` | 입력 | 실지급비: 폐합성소각로(백만원/월) |
| `energy_fuel_plan` | `t09|r07` | `steam_energy_fuel_plan` | `PAY_FLUID` | 입력 | 실지급비: 유동상소각로(백만원/월) |
| `energy_fuel_ratio` | `t01|r01` | `steam_energy_ratio_boiler` | `STEAM_LNG_BOILER` | 입력 | 스팀생산량: LNG 보일러(톤) |
| `energy_fuel_ratio` | `t01|r02` | `steam_energy_ratio_boiler` | `STEAM_COMPOSITE` | 입력 | 스팀생산량: 복합보일러(톤) |
| `energy_fuel_ratio` | `t01|r03` | `steam_energy_ratio_boiler` | `STEAM_INCINERATOR` | 입력 | 스팀생산량: 폐합성소각로(톤) |
| `energy_fuel_ratio` | `t01|r04` | `steam_energy_ratio_boiler` | `STEAM_FLUID` | 입력 | 스팀생산량: 유동상소각로(톤) |
| `energy_fuel_ratio` | `t01|r05` | `steam_energy_ratio_boiler` | `STEAM_EXT_BOILER` | 입력 | 스팀생산량: 외부보일러(톤) |
| `energy_fuel_ratio` | `t01|r07` | `steam_energy_ratio_boiler` | `FLUID_VENT_TON` | 입력 | 유동상 밴트량(톤) |
| `energy_fuel_ratio` | `t01|r08` | `steam_energy_ratio_boiler` | `COMPOSITE_PENALTY_TON` | 입력 | 복합 페널티량(톤) |
| `energy_fuel_ratio` | `t01|r09` | `steam_energy_ratio_boiler` | `COMPOSITE_GUARANTEE_TON` | 입력 | 복합 보증량(톤) |
| `energy_fuel_ratio` | `t02|r01` | `steam_energy_ratio_boiler` | `COST_LNG_BOILER` | 입력 | 보일러 연료비: LNG 보일러(백만원) |
| `energy_fuel_ratio` | `t02|r02` | `steam_energy_ratio_boiler` | `COST_COMPOSITE` | 화면 계산값 | 보일러 연료비: 복합보일러(LNG·페널티 포함) (인보이스 연동 계산값 저장) |
| `energy_fuel_ratio` | `t02|r03` | `steam_energy_ratio_boiler` | `COST_INCINERATOR` | 화면 계산값 | 보일러 연료비: 폐합성소각로 (마감비교 연동값 저장) |
| `energy_fuel_ratio` | `t02|r04` | `steam_energy_ratio_boiler` | `COST_FLUID` | 화면 계산값 | 보일러 연료비: 유동상소각로(잉여포함) (마감비교 연동값 저장) |
| `energy_fuel_ratio` | `t02|r05` | `steam_energy_ratio_boiler` | `COST_EXT_BOILER` | 화면 계산값 | 보일러 연료비: 외부보일러 (마감비교 연동값 저장) |
| `energy_fuel_ratio` | `t02|r07` | `steam_energy_ratio_boiler` | `FLUID_INTERNAL_VENT_COST` | 입력 | 유동상 소내사용 + 밴트스팀 비용(백만원) |
| `energy_fuel_ratio` | `t03|r01` | `steam_energy_ratio_boiler` | `PRICE_LNG_BOILER` | 화면 계산값 | 스팀단가: LNG 보일러(천원/톤) (화면 계산값, 연료비 요약이 읽음) |
| `energy_fuel_ratio` | `t03|r02` | `steam_energy_ratio_boiler` | `PRICE_COMPOSITE` | 화면 계산값 | 스팀단가: 복합보일러(천원/톤) (화면 계산값) |
| `energy_fuel_ratio` | `t03|r03` | `steam_energy_ratio_boiler` | `PRICE_INCINERATOR` | 화면 계산값 | 스팀단가: 폐합성소각로(천원/톤) (화면 계산값) |
| `energy_fuel_ratio` | `t03|r04` | `steam_energy_ratio_boiler` | `PRICE_FLUID` | 화면 계산값 | 스팀단가: 유동상소각로(천원/톤) (화면 계산값) |
| `energy_fuel_ratio` | `t03|r05` | `steam_energy_ratio_boiler` | `PRICE_EXT_BOILER` | 화면 계산값 | 스팀단가: 외부보일러(천원/톤) (화면 계산값) |
| `energy_fuel_ratio` | `t04|r01` | `steam_energy_ratio_machine` | `FUEL_PM2` | 입력 | 호기별 연료비배분: 제지 2호기(백만원) |
| `energy_fuel_ratio` | `t04|r02` | `steam_energy_ratio_machine` | `FUEL_PM3` | 입력 | 호기별 연료비배분: 제지 3호기(백만원) |
| `energy_fuel_ratio` | `t04|r03` | `steam_energy_ratio_machine` | `FUEL_PAPER_SUBTOTAL` | 화면 계산값 | 호기별 연료비배분: 제지 소계 (화면 계산값, 마감비교·요약이 읽음) |
| `energy_fuel_ratio` | `t04|r04` | `steam_energy_ratio_machine` | `FUEL_TM3` | 입력 | 호기별 연료비배분: 화장지 3호기(백만원) |
| `energy_fuel_ratio` | `t04|r05` | `steam_energy_ratio_machine` | `FUEL_TM4` | 입력 | 호기별 연료비배분: 화장지 4호기(백만원) |
| `energy_fuel_ratio` | `t04|r06` | `steam_energy_ratio_machine` | `FUEL_TM5` | 입력 | 호기별 연료비배분: 화장지 5호기(백만원) |
| `energy_fuel_ratio` | `t04|r07` | `steam_energy_ratio_machine` | `FUEL_TISSUE_SUBTOTAL` | 화면 계산값 | 호기별 연료비배분: 화장지 소계 (화면 계산값, 마감비교가 읽음) |
| `energy_fuel_ratio` | `t04|r10` | `steam_energy_ratio_machine` | `FUEL_STEAM_PROD_TON` | 입력 | 호기별 연료비배분: 스팀생산량(톤) |
| `energy_fuel_ratio` | `t05|r01` | `steam_energy_ratio_machine` | `USAGE_PM2` | 입력 | 스팀사용량: 제지 2호기(톤) |
| `energy_fuel_ratio` | `t05|r02` | `steam_energy_ratio_machine` | `USAGE_PM3` | 입력 | 스팀사용량: 제지 3호기(톤) |
| `energy_fuel_ratio` | `t05|r04` | `steam_energy_ratio_machine` | `USAGE_TM3` | 입력 | 스팀사용량: 화장지 3호기(톤) |
| `energy_fuel_ratio` | `t05|r05` | `steam_energy_ratio_machine` | `USAGE_TM4` | 입력 | 스팀사용량: 화장지 4호기(톤) |
| `energy_fuel_ratio` | `t05|r06` | `steam_energy_ratio_machine` | `USAGE_TM5` | 입력 | 스팀사용량: 화장지 5호기(톤) |
| `energy_fuel_ratio` | `t05|r09` | `steam_energy_ratio_machine` | `USAGE_TOTAL_PROD_STEAM` | 입력 | 스팀사용량: 총 생산스팀량(톤) |
| `energy_fuel_ratio` | `t05|r10` | `steam_energy_ratio_machine` | `USAGE_ETC` | 화면 계산값 | 스팀사용량: 기타 사용량(톤) = 총 생산스팀량 − 합계 (계산 가능하면 계산값, 아니면 입력) |
| `energy_fuel_ratio` | `t06|r01` | `steam_energy_ratio_machine` | `PROD_PM2_KG` | 입력 | 제품생산량: 제지2(kg) |
| `energy_fuel_ratio` | `t06|r02` | `steam_energy_ratio_machine` | `PROD_PM3_KG` | 입력 | 제품생산량: 제지3(kg) |
| `energy_fuel_ratio` | `t06|r03` | `steam_energy_ratio_machine` | `PROD_PAPER_SUBTOTAL_KG` | 화면 계산값 | 제품생산량: 제지 소계(kg) (화면 계산값, 마감비교·요약이 읽음) |
| `energy_fuel_ratio` | `t06|r04` | `steam_energy_ratio_machine` | `PROD_TM3_KG` | 입력 | 제품생산량: 화장지3(kg) |
| `energy_fuel_ratio` | `t06|r05` | `steam_energy_ratio_machine` | `PROD_TM4_KG` | 입력 | 제품생산량: 화장지4(kg) |
| `energy_fuel_ratio` | `t06|r06` | `steam_energy_ratio_machine` | `PROD_TM5_KG` | 입력 | 제품생산량: 화장지5(kg) |
| `energy_fuel_ratio` | `t06|r07` | `steam_energy_ratio_machine` | `PROD_TISSUE_SUBTOTAL_KG` | 화면 계산값 | 제품생산량: 화장지 소계(kg) (화면 계산값, 마감비교·요약이 읽음) |
| `energy_fuel_ratio` | `t10|r01` | `steam_energy_ratio_burner` | `LNG_PM3` | 입력 | 버너 LNG 사용량: 제지3(㎥) |
| `energy_fuel_ratio` | `t10|r02` | `steam_energy_ratio_burner` | `LNG_TM3` | 입력 | 버너 LNG 사용량: 화장지3(㎥) |
| `energy_fuel_ratio` | `t10|r03` | `steam_energy_ratio_burner` | `LNG_TM4` | 입력 | 버너 LNG 사용량: 화장지4(㎥) |
| `energy_fuel_ratio` | `t10|r04` | `steam_energy_ratio_burner` | `LNG_TM5` | 입력 | 버너 LNG 사용량: 화장지5(㎥) |
| `energy_fuel_ratio` | `t11|r01` | `steam_energy_ratio_burner` | `LNG_PRICE_PREV_YEAR` | 입력 | 버너 LNG 비용: 전년 LNG 단가(원/㎥) |
| `energy_fuel_ratio` | `t11|r02` | `steam_energy_ratio_burner` | `LNG_PRICE` | 입력 | 버너 LNG 비용: 당년 LNG 단가(원/㎥) |
| `energy_fuel_ratio` | `t11|r03` | `steam_energy_ratio_burner` | `LNG_PRICE_CHANGE_RATE` | 화면 계산값 | 버너 LNG 비용: 증감률 (두 단가가 있으면 계산값, 아니면 입력) |
| `energy_fuel_ratio` | `t11|r09` | `steam_energy_ratio_burner` | `LNG_COST_TOTAL` | 화면 계산값 | 버너 LNG 비용 합계(백만원) (화면 계산값, 마감비교·요약이 읽음) |
| `energy_fuel_ratio` | `t12|r01` | `steam_energy_ratio_burner` | `LNG_UNIT_PM3` | 화면 계산값 | 버너 LNG 사용량 원단위: 제지3(㎥/제품톤) (화면 계산값, 요약이 읽음) |
| `energy_fuel_ratio` | `t12|r02` | `steam_energy_ratio_burner` | `LNG_UNIT_TM3` | 화면 계산값 | 버너 LNG 사용량 원단위: 화장지3 (화면 계산값) |
| `energy_fuel_ratio` | `t12|r03` | `steam_energy_ratio_burner` | `LNG_UNIT_TM4` | 화면 계산값 | 버너 LNG 사용량 원단위: 화장지4 (화면 계산값) |
| `energy_fuel_ratio` | `t12|r04` | `steam_energy_ratio_burner` | `LNG_UNIT_TM5` | 화면 계산값 | 버너 LNG 사용량 원단위: 화장지5 (화면 계산값) |
| `energy_fuel_ratio` | `t12|r05` | `steam_energy_ratio_burner` | `LNG_UNIT_TISSUE_SUBTOTAL` | 화면 계산값 | 버너 LNG 사용량 원단위: 화장지 소계 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r01` | `steam_energy_ratio_steam_unit` | `UNIT_PM2` | 화면 계산값 | 제지2 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r02` | `steam_energy_ratio_steam_unit` | `UNIT_PM3` | 화면 계산값 | 제지3 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r03` | `steam_energy_ratio_steam_unit` | `UNIT_PAPER_SUBTOTAL` | 화면 계산값 | 제지 소계 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r04` | `steam_energy_ratio_steam_unit` | `UNIT_TM3` | 화면 계산값 | 화장지3 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r05` | `steam_energy_ratio_steam_unit` | `UNIT_TM4` | 화면 계산값 | 화장지4 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r06` | `steam_energy_ratio_steam_unit` | `UNIT_TM5` | 화면 계산값 | 화장지5 (화면 계산값) |
| `energy_fuel_ratio` | `t17|r07` | `steam_energy_ratio_steam_unit` | `UNIT_TISSUE_SUBTOTAL` | 화면 계산값 | 화장지 소계 (화면 계산값) |
| `energy_fuel_ratio` | `t18|r01` | `steam_energy_ratio_ref2019` | `T18_PM1` | 입력 | 2019 호기별 스팀생산량: 제지 1호기 |
| `energy_fuel_ratio` | `t18|r02` | `steam_energy_ratio_ref2019` | `T18_PM2` | 입력 | 2019 호기별 스팀생산량: 제지 2호기 |
| `energy_fuel_ratio` | `t18|r03` | `steam_energy_ratio_ref2019` | `T18_PM3` | 입력 | 2019 호기별 스팀생산량: 제지 3호기 |
| `energy_fuel_ratio` | `t18|r04` | `steam_energy_ratio_ref2019` | `T18_PAPER_SUBTOTAL` | 입력 | 2019 호기별 스팀생산량: 제지 소계 |
| `energy_fuel_ratio` | `t18|r05` | `steam_energy_ratio_ref2019` | `T18_TM1` | 입력 | 2019 호기별 스팀생산량: 화장지 1호기 |
| `energy_fuel_ratio` | `t18|r06` | `steam_energy_ratio_ref2019` | `T18_TM3` | 입력 | 2019 호기별 스팀생산량: 화장지 3호기 |
| `energy_fuel_ratio` | `t18|r07` | `steam_energy_ratio_ref2019` | `T18_TM4` | 입력 | 2019 호기별 스팀생산량: 화장지 4호기 |
| `energy_fuel_ratio` | `t18|r08` | `steam_energy_ratio_ref2019` | `T18_TM5` | 입력 | 2019 호기별 스팀생산량: 화장지 5호기 |
| `energy_fuel_ratio` | `t18|r09` | `steam_energy_ratio_ref2019` | `T18_TISSUE_SUBTOTAL` | 입력 | 2019 호기별 스팀생산량: 화장지 소계 |
| `energy_fuel_ratio` | `t18|r10` | `steam_energy_ratio_ref2019` | `T18_TOTAL` | 입력 | 2019 호기별 스팀생산량: 합계 |
| `energy_fuel_ratio` | `t19|r01` | `steam_energy_ratio_ref2019` | `T19_PM1` | 입력 | 2019 호기별 스팀사용비율: 제지 1호기 |
| `energy_fuel_ratio` | `t19|r02` | `steam_energy_ratio_ref2019` | `T19_PM2` | 입력 | 2019 호기별 스팀사용비율: 제지 2호기 |
| `energy_fuel_ratio` | `t19|r03` | `steam_energy_ratio_ref2019` | `T19_PM3` | 입력 | 2019 호기별 스팀사용비율: 제지 3호기 |
| `energy_fuel_ratio` | `t19|r04` | `steam_energy_ratio_ref2019` | `T19_PAPER_SUBTOTAL` | 입력 | 2019 호기별 스팀사용비율: 제지 소계 |
| `energy_fuel_ratio` | `t19|r05` | `steam_energy_ratio_ref2019` | `T19_TM1` | 입력 | 2019 호기별 스팀사용비율: 화장지 1호기 |
| `energy_fuel_ratio` | `t19|r06` | `steam_energy_ratio_ref2019` | `T19_TM3` | 입력 | 2019 호기별 스팀사용비율: 화장지 3호기 |
| `energy_fuel_ratio` | `t19|r07` | `steam_energy_ratio_ref2019` | `T19_TM4` | 입력 | 2019 호기별 스팀사용비율: 화장지 4호기 |
| `energy_fuel_ratio` | `t19|r08` | `steam_energy_ratio_ref2019` | `T19_TM5` | 입력 | 2019 호기별 스팀사용비율: 화장지 5호기 |
| `energy_fuel_ratio` | `t19|r09` | `steam_energy_ratio_ref2019` | `T19_TISSUE_SUBTOTAL` | 입력 | 2019 호기별 스팀사용비율: 화장지 소계 |
| `energy_fuel_ratio` | `t19|r10` | `steam_energy_ratio_ref2019` | `T19_TOTAL` | 입력 | 2019 호기별 스팀사용비율: 합계 |
| `energy_fuel_ratio` | `t20|r01` | `steam_energy_ratio_ref2019` | `T20_PM1` | 입력 | 2019 호기별 에너지비용 배분: 제지 1호기 |
| `energy_fuel_ratio` | `t20|r02` | `steam_energy_ratio_ref2019` | `T20_PM2` | 입력 | 2019 호기별 에너지비용 배분: 제지 2호기 |
| `energy_fuel_ratio` | `t20|r03` | `steam_energy_ratio_ref2019` | `T20_PM3` | 입력 | 2019 호기별 에너지비용 배분: 제지 3호기 |
| `energy_fuel_ratio` | `t20|r04` | `steam_energy_ratio_ref2019` | `T20_PAPER_SUBTOTAL` | 입력 | 2019 호기별 에너지비용 배분: 제지 소계 |
| `energy_fuel_ratio` | `t20|r05` | `steam_energy_ratio_ref2019` | `T20_TM1` | 입력 | 2019 호기별 에너지비용 배분: 화장지 1호기 |
| `energy_fuel_ratio` | `t20|r06` | `steam_energy_ratio_ref2019` | `T20_TM3` | 입력 | 2019 호기별 에너지비용 배분: 화장지 3호기 |
| `energy_fuel_ratio` | `t20|r07` | `steam_energy_ratio_ref2019` | `T20_TM4` | 입력 | 2019 호기별 에너지비용 배분: 화장지 4호기 |
| `energy_fuel_ratio` | `t20|r08` | `steam_energy_ratio_ref2019` | `T20_TM5` | 입력 | 2019 호기별 에너지비용 배분: 화장지 5호기 |
| `energy_fuel_ratio` | `t20|r09` | `steam_energy_ratio_ref2019` | `T20_TISSUE_SUBTOTAL` | 입력 | 2019 호기별 에너지비용 배분: 화장지 소계 |
| `energy_fuel_ratio` | `t20|r10` | `steam_energy_ratio_ref2019` | `T20_TOTAL` | 입력 | 2019 호기별 에너지비용 배분: 합계 |
| `energy_fuel_ratio` | `t20|r11` | `steam_energy_ratio_ref2019` | `T20_TOTAL_ENERGY_COST` | 입력 | 2019 호기별 에너지비용 배분: 총 에너지비용 |
| `energy_month_compare` | `fuel|zescoSteam` | `steam_energy_month_compare` | `ZESCO_STEAM_BUY` | 입력 | 연료비: 제스코 스팀구매(백만원) |
| `energy_month_compare` | `fuel|lngBuy` | `steam_energy_month_compare` | `LNG_BUY` | 입력 | 연료비: LNG 구매(백만원) |
| `energy_month_compare` | `fuel|zescoOffset` | `steam_energy_month_compare` | `ZESCO_OFFSET` | 입력 | 차감비: 제스코 상계비(유틸리티) |
| `energy_month_compare` | `fuel|zescoPenalty` | `steam_energy_month_compare` | `ZESCO_PENALTY` | 입력 | 차감비: 제스코 페널티 |
| `energy_month_compare` | `fuel|fuelPlan` | `steam_energy_month_compare` | `FUEL_PLAN` | 입력 | 계획: 연료비(회계비용) SG 계획금액(백만원) |
| `energy_month_compare` | `fuel|pyhsynSteam` | `steam_energy_month_compare` | `INCINERATOR_STEAM_BUY` | 화면 계산값 | 연료비: 폐합성소각로 스팀구매 (도급내역 연동값 저장, 배분비율이 읽음) |
| `energy_month_compare` | `fuel|fluidSteam` | `steam_energy_month_compare` | `FLUID_STEAM_BUY` | 화면 계산값 | 연료비: 유동상소각로 스팀구매 (유동상 운영내역 연동값 저장, 배분비율이 읽음) |
| `energy_month_compare` | `fuel|fuelActual` | `steam_energy_month_compare` | `FUEL_ACTUAL` | 화면 계산값 | 실적: 연료비(회계비용) 실적 (화면 계산값, 배분비율이 읽음) |
| `energy_month_compare` | `fuel|fuelSteamUnit` | `steam_energy_month_compare` | `FUEL_STEAM_UNIT` | 화면 계산값 | 보조: 스팀톤당 연료비(천원/톤) (화면 계산값, 배분비율이 읽음) |
| `energy_month_compare` | `note|fuel|연료비` | `steam_energy_month_compare_note` | `NOTE_FUEL` | 비고(문자) | 비고: 연료비 |
| `energy_month_compare` | `note|fuel|차감비` | `steam_energy_month_compare_note` | `NOTE_DEDUCT` | 비고(문자) | 비고: 차감비 |
| `energy_month_compare` | `note|fuel|실적` | `steam_energy_month_compare_note` | `NOTE_ACTUAL` | 비고(문자) | 비고: 실적 |
| `energy_month_compare` | `note|fuel|스팀사용비율` | `steam_energy_month_compare_note` | `NOTE_STEAM_RATIO` | 비고(문자) | 비고: 스팀사용비율 |
| `energy_month_compare` | `note|fuel|계획` | `steam_energy_month_compare_note` | `NOTE_PLAN` | 비고(문자) | 비고: 계획 |
| `energy_month_compare` | `note|fuel|생산량` | `steam_energy_month_compare_note` | `NOTE_PROD` | 비고(문자) | 비고: 생산량 |
| `energy_month_compare` | `note|fuel|제품생산량` | `steam_energy_month_compare_note` | `NOTE_PRODUCT` | 비고(문자) | 비고: 제품생산량 |
| `energy_month_compare` | `note|fuel|원단위` | `steam_energy_month_compare_note` | `NOTE_UNIT` | 비고(문자) | 비고: 원단위 |
| `energy_month_compare` | `note|fuel|손익` | `steam_energy_month_compare_note` | `NOTE_PROFIT` | 비고(문자) | 비고: 손익 |
| `energy_month_compare` | `note|fuel|보조 분석` | `steam_energy_month_compare_note` | `NOTE_AUX` | 비고(문자) | 비고: 보조 분석 |
| `energy_fuel_summary` | `t1|0|prod` | `steam_energy_fuel_summary` | `T1_PM2_PROD` | 입력 | 호기별 손익분석 직접입력: 제지 2호기 제품생산량(톤) |
| `energy_fuel_summary` | `t1|0|fuel` | `steam_energy_fuel_summary` | `T1_PM2_FUEL` | 입력 | 호기별 손익분석 직접입력: 제지 2호기 총연료비용(백만원) |
| `energy_fuel_summary` | `t1|1|prod` | `steam_energy_fuel_summary` | `T1_PM3_PROD` | 입력 | 호기별 손익분석 직접입력: 제지 3호기 제품생산량(톤) |
| `energy_fuel_summary` | `t1|1|fuel` | `steam_energy_fuel_summary` | `T1_PM3_FUEL` | 입력 | 호기별 손익분석 직접입력: 제지 3호기 총연료비용(백만원) |
| `energy_fuel_summary` | `t1|3|prod` | `steam_energy_fuel_summary` | `T1_TM3_PROD` | 입력 | 호기별 손익분석 직접입력: 화장지 3호기 제품생산량(톤) |
| `energy_fuel_summary` | `t1|3|fuel` | `steam_energy_fuel_summary` | `T1_TM3_FUEL` | 입력 | 호기별 손익분석 직접입력: 화장지 3호기 총연료비용(백만원) |
| `energy_fuel_summary` | `t1|4|prod` | `steam_energy_fuel_summary` | `T1_TM4_PROD` | 입력 | 호기별 손익분석 직접입력: 화장지 4호기 제품생산량(톤) |
| `energy_fuel_summary` | `t1|4|fuel` | `steam_energy_fuel_summary` | `T1_TM4_FUEL` | 입력 | 호기별 손익분석 직접입력: 화장지 4호기 총연료비용(백만원) |
| `energy_fuel_summary` | `t1|5|prod` | `steam_energy_fuel_summary` | `T1_TM5_PROD` | 입력 | 호기별 손익분석 직접입력: 화장지 5호기 제품생산량(톤) |
| `energy_fuel_summary` | `t1|5|fuel` | `steam_energy_fuel_summary` | `T1_TM5_FUEL` | 입력 | 호기별 손익분석 직접입력: 화장지 5호기 총연료비용(백만원) |
| `energy_fuel_summary` | `t2|0|usage` | `steam_energy_fuel_summary` | `T2_LNG_USAGE` | 입력 | 운영단가 비교 직접입력: LNG 보일러 스팀사용량(톤) |
| `energy_fuel_summary` | `t2|0|price` | `steam_energy_fuel_summary` | `T2_LNG_PRICE` | 입력 | 운영단가 비교 직접입력: LNG 보일러 스팀단가(천원/톤) |
| `energy_fuel_summary` | `t2|0|cost` | `steam_energy_fuel_summary` | `T2_LNG_COST` | 입력 | 운영단가 비교 직접입력: LNG 보일러 운영비용(백만원) |
| `energy_fuel_summary` | `t2|1|usage` | `steam_energy_fuel_summary` | `T2_COMPOSITE_USAGE` | 입력 | 운영단가 비교 직접입력: 복합보일러 스팀사용량(톤) |
| `energy_fuel_summary` | `t2|1|price` | `steam_energy_fuel_summary` | `T2_COMPOSITE_PRICE` | 입력 | 운영단가 비교 직접입력: 복합보일러 스팀단가(천원/톤) |
| `energy_fuel_summary` | `t2|1|cost` | `steam_energy_fuel_summary` | `T2_COMPOSITE_COST` | 입력 | 운영단가 비교 직접입력: 복합보일러 운영비용(백만원) |
| `energy_fuel_summary` | `t2|2|usage` | `steam_energy_fuel_summary` | `T2_INCINERATOR_USAGE` | 입력 | 운영단가 비교 직접입력: 폐합성소각로 스팀사용량(톤) |
| `energy_fuel_summary` | `t2|2|price` | `steam_energy_fuel_summary` | `T2_INCINERATOR_PRICE` | 입력 | 운영단가 비교 직접입력: 폐합성소각로 스팀단가(천원/톤) |
| `energy_fuel_summary` | `t2|2|cost` | `steam_energy_fuel_summary` | `T2_INCINERATOR_COST` | 입력 | 운영단가 비교 직접입력: 폐합성소각로 운영비용(백만원) |
| `energy_fuel_summary` | `t2|3|usage` | `steam_energy_fuel_summary` | `T2_FLUID_USAGE` | 입력 | 운영단가 비교 직접입력: 유동상소각로 스팀사용량(톤) |
| `energy_fuel_summary` | `t2|3|price` | `steam_energy_fuel_summary` | `T2_FLUID_PRICE` | 입력 | 운영단가 비교 직접입력: 유동상소각로 스팀단가(천원/톤) |
| `energy_fuel_summary` | `t2|3|cost` | `steam_energy_fuel_summary` | `T2_FLUID_COST` | 입력 | 운영단가 비교 직접입력: 유동상소각로 운영비용(백만원) |
| `energy_fuel_summary` | `t2|4|usage` | `steam_energy_fuel_summary` | `T2_EXT_USAGE` | 입력 | 운영단가 비교 직접입력: 외부보일러 스팀사용량(톤) |
| `energy_fuel_summary` | `t2|4|price` | `steam_energy_fuel_summary` | `T2_EXT_PRICE` | 입력 | 운영단가 비교 직접입력: 외부보일러 스팀단가(천원/톤) |
| `energy_fuel_summary` | `t2|4|cost` | `steam_energy_fuel_summary` | `T2_EXT_COST` | 입력 | 운영단가 비교 직접입력: 외부보일러 운영비용(백만원) |
| `energy_fuel_summary` | `t2|0|elec` | `steam_energy_fuel_summary` | `T2_LNG_ELEC` | 입력 | 운영단가 비교 참고: LNG 보일러 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|0|penalty` | `steam_energy_fuel_summary` | `T2_LNG_PENALTY` | 입력 | 운영단가 비교 특이사항: LNG 보일러 페널티 |
| `energy_fuel_summary` | `t2|0|overprice` | `steam_energy_fuel_summary` | `T2_LNG_OVERPRICE` | 입력 | 운영단가 비교 특이사항: LNG 보일러 초과단가영향 |
| `energy_fuel_summary` | `t2|1|elec` | `steam_energy_fuel_summary` | `T2_COMPOSITE_ELEC` | 입력 | 운영단가 비교 참고: 복합보일러 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|1|penalty` | `steam_energy_fuel_summary` | `T2_COMPOSITE_PENALTY` | 입력 | 운영단가 비교 특이사항: 복합보일러 페널티 |
| `energy_fuel_summary` | `t2|1|overprice` | `steam_energy_fuel_summary` | `T2_COMPOSITE_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 복합보일러 초과단가영향 |
| `energy_fuel_summary` | `t2|2|elec` | `steam_energy_fuel_summary` | `T2_INCINERATOR_ELEC` | 입력 | 운영단가 비교 참고: 폐합성소각로 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|2|penalty` | `steam_energy_fuel_summary` | `T2_INCINERATOR_PENALTY` | 입력 | 운영단가 비교 특이사항: 폐합성소각로 페널티 |
| `energy_fuel_summary` | `t2|2|overprice` | `steam_energy_fuel_summary` | `T2_INCINERATOR_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 폐합성소각로 초과단가영향 |
| `energy_fuel_summary` | `t2|3|elec` | `steam_energy_fuel_summary` | `T2_FLUID_ELEC` | 입력 | 운영단가 비교 참고: 유동상소각로 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|3|penalty` | `steam_energy_fuel_summary` | `T2_FLUID_PENALTY` | 입력 | 운영단가 비교 특이사항: 유동상소각로 페널티 |
| `energy_fuel_summary` | `t2|3|overprice` | `steam_energy_fuel_summary` | `T2_FLUID_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 유동상소각로 초과단가영향 |
| `energy_fuel_summary` | `t2|4|elec` | `steam_energy_fuel_summary` | `T2_EXT_ELEC` | 입력 | 운영단가 비교 참고: 외부보일러 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|4|penalty` | `steam_energy_fuel_summary` | `T2_EXT_PENALTY` | 입력 | 운영단가 비교 특이사항: 외부보일러 페널티 |
| `energy_fuel_summary` | `t2|4|overprice` | `steam_energy_fuel_summary` | `T2_EXT_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 외부보일러 초과단가영향 |
| `energy_fuel_summary` | `t2|5|elec` | `steam_energy_fuel_summary` | `T2_SUBTOTAL_ELEC` | 입력 | 운영단가 비교 참고: 소계 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|5|penalty` | `steam_energy_fuel_summary` | `T2_SUBTOTAL_PENALTY` | 입력 | 운영단가 비교 특이사항: 소계 페널티 |
| `energy_fuel_summary` | `t2|5|overprice` | `steam_energy_fuel_summary` | `T2_SUBTOTAL_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 소계 초과단가영향 |
| `energy_fuel_summary` | `t2|6|elec` | `steam_energy_fuel_summary` | `T2_BURNER_LNG_ELEC` | 입력 | 운영단가 비교 참고: 공정 LNG 연료비 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|6|penalty` | `steam_energy_fuel_summary` | `T2_BURNER_LNG_PENALTY` | 입력 | 운영단가 비교 특이사항: 공정 LNG 연료비 페널티 |
| `energy_fuel_summary` | `t2|6|overprice` | `steam_energy_fuel_summary` | `T2_BURNER_LNG_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 공정 LNG 연료비 초과단가영향 |
| `energy_fuel_summary` | `t2|7|elec` | `steam_energy_fuel_summary` | `T2_TOTAL_ELEC` | 입력 | 운영단가 비교 참고: 연료비 합계 전력비용/전력단가 |
| `energy_fuel_summary` | `t2|7|penalty` | `steam_energy_fuel_summary` | `T2_TOTAL_PENALTY` | 입력 | 운영단가 비교 특이사항: 연료비 합계 페널티 |
| `energy_fuel_summary` | `t2|7|overprice` | `steam_energy_fuel_summary` | `T2_TOTAL_OVERPRICE` | 입력 | 운영단가 비교 특이사항: 연료비 합계 초과단가영향 |

열 규약(옛 col_index → 연·월): 회계비용 계획·배분비율 `1 = 전년 12월, 2..13 = 1..12월` / 사업계획·요약 `1..12` / 마감비교 `0 = 전년 12월, 1..12, 13 = 다음해 1월` / 비고 `0`.

### API

| Method | URL | 설명 |
|---|---|---|
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-accounting-plans[?year=] [/{id}]` | 회계비용 계획 — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-fuel-plans[?year=] [/{id}]` | 연료비 사업계획 — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-ratio-boilers[?year=] [/{id}]` | 연료비 배분비율(보일러별) — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-ratio-machines[?year=] [/{id}]` | 연료비 배분비율(호기별) — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-ratio-burners[?year=] [/{id}]` | 연료비 배분비율(버너 LNG) — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-ratio-steam-units[?year=] [/{id}]` | 스팀사용량 원단위(기타 포함) — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-ratio-ref2019s[?year=] [/{id}]` | 연료비 배분비율(2019 참고) — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-month-compares[?year=] [/{id}]` | 회계비용 마감비교 — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| GET / POST / DELETE | `/steam-energy-api/energy-month-compare-notes[?year=] [/{id}]` | 회계비용 마감비교 비고 — 연도 행 1개 |
| GET / POST / PUT /year / DELETE | `/steam-energy-api/energy-fuel-summaries[?year=] [/{id}]` | 연료비 요약 — (연,월) 업서트, 한 해치 일괄(PUT /year, clearMissing) |
| POST | `/steam-energy-api/energy-excel/{page}/import-excel?year=` | 엑셀 가져오기 (page = accounting-plan · fuel-plan · fuel-ratio · month-compare · all) — 그 해 월 행 값을 비운 뒤 시트 값 저장(비고·이웃 연도 행은 유지) |
| GET | `/steam-energy-api/energy-excel/{page}/export-excel?year=` | 서식(6. {연도}년 에너지 회계비용 실적.xlsx)에 채워 내려받기 |

쓰기 API 는 `STEAM_ENERGY_MGMT` 메뉴 권한이 필요하다. 옛 `/steam/api/tables/energy-*/import|export-excel` 은 제거했다.

### 데이터 이관

`migration/2026-11-20_energy_cells_to_tables.sql` — 옛 셀(2025·2026) 을 매핑대로 옮긴다(148행, 재실행 안전). 화면 계산 전용 행·합계 열·단위 글자는 버린다.
옛 네임스페이스 `energy_*` 는 읽기용으로만 남고 더 이상 갱신되지 않는다. 다른 사이트의 t17 읽기는 `api/19_스팀에너지_공유데이터.md` 참고.

## 복합보일러 그룹 전환 (2026-11-20) — 업무 모듈 규격 4-1 맞춤 (묶음 3-A)

복합보일러 4개 화면(스팀생산실적 · 운휴시간 · 스팀 회수 감소 · 기타)을 옛 `steam_cell_value` 논리 표 `combo_boiler_*` 에서 실제 컬럼 표 5개로 옮겼다.
화면의 칸 키는 그대로 두고(`항목-일`, `fuelPrice-설비`, `항목-줄번호`, `combo-plan-행-칸`) 저장할 때 표의 행·컬럼으로 옮긴다 — 화면은 `js/combo-boiler-pages.js` 의 `locate()`, 엑셀은 `service/table/ComboCellStore`.

### 설계 규칙

- 계산값(T/H = T/D ÷ 24, 스팀 합계, 누계·평균, 소계·잔량·진척률, 감소량)은 저장하지 않는다.
- `WASTE_STEAM2` 가 NULL 이면 소각로 도급내역의 2호기 실 스팀량을 따른다 (값이 있으면 그 값). 마감비교의 스팀생산량 합계도 같은 규칙을 쓴다.
- 스팀 회수 감소는 한 건이 한 행(`LINE_NO`). 날짜·시간은 입력한 글자 그대로 둔다 (`3/26`, `17:00~18:25`).
- 연간 계획은 (기준연도, 대상 월) 한 행 — 복합보일러 계획은 전년 6월~당해 8월, 제스코 계획은 전년 5월~당해 5월, LNG 사용량은 당해 1~12월.
- 벤트 손실 3항목과 벤트 손실 단가는 화면에 칸이 없고 엑셀 왕복에만 쓴다.

### 매핑표 (옛 키 → 표.컬럼)

**`steam_combo_daily_production`** — 복합보일러 스팀생산실적 · 키 `(USAGE_DATE)` · 옛 저장소: `combo_boiler_production` 의 `<필드>-<일>`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `COMBO_STEAM` | DECIMAL(18,6) | `comboSteam` | 복합보일러 스팀구매량(T/D) |
| `COMBO_CONDENSATE` | DECIMAL(18,6) | `comboCondensate` | 복합보일러 응축수판매량 |
| `COMBO_MAKEUP` | DECIMAL(18,6) | `comboMakeup` | 복합보일러 보충수판매량 |
| `COMBO_DEAERATOR` | DECIMAL(18,6) | `comboDeaerator` | 복합보일러 탈기기급수량 |
| `COMBO_PROCESS` | DECIMAL(18,6) | `comboProcess` | 복합보일러 공정수판매량 |
| `COMBO_DILUTION` | DECIMAL(18,6) | `comboDilution` | 복합보일러 희석수판매량 |
| `EXTERNAL_STEAM` | DECIMAL(18,6) | `externalSteam` | 외부보일러 스팀구매량(T/D) |
| `EXTERNAL_CONDENSATE` | DECIMAL(18,6) | `externalCondensate` | 외부보일러 응축수판매량 |
| `EXTERNAL_MAKEUP` | DECIMAL(18,6) | `externalMakeup` | 외부보일러 보충수판매량 |
| `WASTE_STEAM1` | DECIMAL(18,6) | `wasteSteam1` | 폐합성 1호 스팀 구입량(T/D) |
| `WASTE_STEAM2` | DECIMAL(18,6) | `wasteSteam2` | 폐합성 2호 스팀 구입량(T/D) — 비어 있으면 소각로 도급내역 실 스팀량(2호기)을 따른다 |
| `KN20` | DECIMAL(18,6) | `kn20` | 깨끗한나라 보일러 20톤(#2) 스팀생산량(T/D) |
| `KN50` | DECIMAL(18,6) | `kn50` | 깨끗한나라 보일러 50톤(#3) 스팀생산량(T/D) |
| `FLUIDIZED_STEAM` | DECIMAL(18,6) | `fluidizedSteam` | 유동상 스팀 생산량(T/D) |
| `FLUIDIZED_CONDENSATE` | DECIMAL(18,6) | `fluidizedCondensate` | 유동상 응축수 |
| `RUNTIME20` | DECIMAL(18,6) | `runtime20` | 20톤(#2) 가동시간(H/D) |
| `RUNTIME50` | DECIMAL(18,6) | `runtime50` | 50톤(#3) 가동시간(H/D) |
| `RUNTIME_FLUIDIZED` | DECIMAL(18,6) | `runtimeFluidized` | 유동상 가동시간(H/D) |

**`steam_combo_daily_downtime`** — 복합보일러 운휴·디스퍼져 · 키 `(USAGE_DATE)` · 옛 저장소: `combo_boiler_downtime` 의 `<필드>-<일>`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `CHANGE_COMBO` | VARCHAR(500) | `changeCombo` | 변동사항(복합보일러) |
| `CHANGE_JESCO` | VARCHAR(500) | `changeJesco` | 변동사항(제스코, 유동상 보일러) |
| `DOWN1` | DECIMAL(18,6) | `down1` | #1호기 운휴시간(분) |
| `DOWN2` | DECIMAL(18,6) | `down2` | #2호기 운휴시간(분) |
| `DISPERSER_STEAM` | DECIMAL(18,6) | `disperserSteam` | 디스퍼져 스팀량 |
| `RUNTIME_MIN` | DECIMAL(18,6) | `runtimeMin` | 디스퍼져 가동시간(분) |
| `VENT_PRODUCTION` | DECIMAL(18,6) | `ventProduction` | 벤트 손실: 생산량 FIT-301 (엑셀 왕복용, 화면 없음) |
| `VENT_INTERNAL` | DECIMAL(18,6) | `ventInternal` | 벤트 손실: 소내 사용량 추정 (엑셀 왕복용, 화면 없음) |
| `VENT_USAGE` | DECIMAL(18,6) | `ventUsage` | 벤트 손실: 사용량 FIT-304 (엑셀 왕복용, 화면 없음) |

**`steam_combo_monthly_price`** — 복합보일러 월 연료단가 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `combo_boiler_production` 의 `fuelPrice-<설비>`, `combo_boiler_downtime` 의 `ventLossUnitPrice`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `FUEL_PRICE_LNG_BOILER` | DECIMAL(18,6) | `fuelPriceLngBoiler` | 연료단가: LNG 보일러 |
| `FUEL_PRICE_FLUIDIZED` | DECIMAL(18,6) | `fuelPriceFluidized` | 연료단가: 유동상 |
| `FUEL_PRICE_COMBO` | DECIMAL(18,6) | `fuelPriceCombo` | 연료단가: 복합보일러 |
| `FUEL_PRICE_WASTE` | DECIMAL(18,6) | `fuelPriceWaste` | 연료단가: 폐합성 |
| `FUEL_PRICE_EXTERNAL` | DECIMAL(18,6) | `fuelPriceExternal` | 연료단가: 외부보일러 |
| `VENT_LOSS_UNIT_PRICE` | DECIMAL(18,6) | `ventLossUnitPrice` | 벤트 손실비용 단가(전월) (엑셀 왕복용, 화면 없음) |

**`steam_combo_recovery_loss`** — 복합보일러 스팀 회수 감소 · 키 `(YEAR_NO, MONTH_NO, LINE_NO)` · 옛 저장소: `combo_boiler_recovery` 의 `<필드>-<줄번호>`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `LINE_NO` (키) | INT | `lineNo` | 줄 번호 (1부터, 그 달의 감소 건 순번) |
| `LOSS_DATE` | VARCHAR(50) | `lossDate` | 날짜 (입력한 글자 그대로, 예: 3/26) |
| `LOSS_TIME` | VARCHAR(100) | `lossTime` | 스팀회수 감소시간 (입력한 글자 그대로, 예: 17:00~18:25) |
| `LOSS_NOTE` | VARCHAR(500) | `lossNote` | 내용 |
| `LOSS_STEAM_RATE` | DECIMAL(18,6) | `lossSteamRate` | 스팀 감소량 기준(T/H) |
| `LOSS_BASE_RATE` | DECIMAL(18,6) | `lossBaseRate` | 기준(T/H) |
| `LOSS_MINUTES` | DECIMAL(18,6) | `lossMinutes` | 감소시간(분) |

**`steam_combo_plan`** — 복합보일러 연간 계획 · 키 `(YEAR_NO, TARGET_YM)` · 옛 저장소: `combo_boiler_etc` 의 `combo-plan-{1,2,4}-<칸>`, `jesco-plan-{0,1}-<칸>`, `lngBoiler-<월>`, `lngCombo-<월>`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `TARGET_YM` (키) | VARCHAR(7) | `targetYm` | 대상 월 (YYYY-MM, 기준연도 화면의 월 칸) |
| `COMBO_BASIC` | DECIMAL(18,6) | `comboBasic` | 복합보일러 계획: 기본 (전년 6월~당해 8월) |
| `COMBO_SPECIAL` | DECIMAL(18,6) | `comboSpecial` | 복합보일러 계획: 특약 |
| `COMBO_ACTUAL` | DECIMAL(18,6) | `comboActual` | 복합보일러 계획: 실적 |
| `JESCO_PLAN` | DECIMAL(18,6) | `jescoPlan` | 제스코 계획: 계획 (전년 5월~당해 5월) |
| `JESCO_ACTUAL` | DECIMAL(18,6) | `jescoActual` | 제스코 계획: 실적 |
| `LNG_BOILER` | DECIMAL(18,6) | `lngBoiler` | LNG사용량: 보일러 (당해 1~12월) |
| `LNG_COMBO` | DECIMAL(18,6) | `lngCombo` | LNG사용량: 복합 |

### API

쓰기는 모두 `STEAM_ENERGY_MGMT` 메뉴 권한이 필요하다. 응답은 `ApiResponse`.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/steam-energy-api/combo-daily-productions?year=[&month=]` | 복합보일러 스팀생산실적 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/combo-daily-productions` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/combo-daily-productions/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/combo-daily-productions/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/combo-daily-downtimes?year=[&month=]` | 복합보일러 운휴·디스퍼져 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/combo-daily-downtimes` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/combo-daily-downtimes/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/combo-daily-downtimes/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/combo-monthly-prices?year=[&month=]` | 복합보일러 월 연료단가 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/combo-monthly-prices` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/combo-monthly-prices/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/combo-monthly-prices/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/combo-recovery-losses?year=[&month=]` | 복합보일러 스팀 회수 감소 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/combo-recovery-losses` | 행 전체 업서트 `(YEAR_NO, MONTH_NO, LINE_NO)` |
| `PUT` | `/steam-energy-api/combo-recovery-losses/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/combo-recovery-losses/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/combo-plans?year=[&month=]` | 복합보일러 연간 계획 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/combo-plans` | 행 전체 업서트 `(YEAR_NO, TARGET_YM)` |
| `PUT` | `/steam-energy-api/combo-plans/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/combo-plans/{id}` | 단건 조회 · 소프트 삭제 |
| `POST` | `/steam-energy-api/combo-excel/import-excel` (file, year) | 워크북 `3. 복합보일러(YYYY년) 스팀구매 현황.xlsx` 가져오기 — 시트가 있는 달을 파일 기준으로 바꾼다 |
| `GET` | `/steam-energy-api/combo-excel/export-excel?year=` | 같은 서식으로 내보내기 |

### 다른 화면과의 연동

- 마감비교(`energy-month-compare.html`)의 스팀생산량 행 ← `steam_combo_daily_production` 의 스팀 7종 월 합계
- SRF 인보이스 운영내역(`SrfBoilerInvoiceWorkbookService`) ← `COMBO_STEAM` · `COMBO_PROCESS` · `COMBO_DEAERATOR` (같은 날)
- 스팀생산실적의 폐합성 2호 기본값 ← 소각로 도급내역 `real|일|steam-2` (묶음 3-B 전환 전까지 옛 셀)

### 데이터 이관

`migration/2026-11-20_combo_cells_to_tables.sql` — 옛 셀을 새 표로 옮긴다 (재실행 안전: 비어 있는 칸만 채움). 로컬 적용 결과: 생산실적 730일, 운휴 546일, 월 단가 24개월, 회수 감소 110건. 연간 계획은 옮길 값이 없다.

## 소각로 위탁 그룹 전환 (2026-11-20) — 업무 모듈 규격 4-1 맞춤 (묶음 3-B)

폐합성수지 소각로 5개 화면(도급내역 · 사업계획대비 · 전년대비 · 계획대비 가동실적 · 총 운영비용)을 옛 `steam_cell_value` 논리 표 `incinerator_*` 에서 실제 컬럼 표 4개로 옮겼다. 사고이력은 묶음 3-E 에서 옮긴다.
화면의 칸 키(`구분|일|항목`, `구분|항목-m월`, `구분|월|항목`)는 그대로 두고 저장할 때 표의 행·컬럼으로 옮긴다 — 화면은 `js/incinerator-store.js`, 엑셀은 `service/table/IncineratorCellStore`.

### 설계 규칙

- 계산값(운영비용 = 스팀량 × 단가, 운휴비용 = 운휴시간 × 고정비, 소계·합계, 달성율·증감)은 저장하지 않는다. 옛 저장소에 엑셀 업로드로 들어와 있던 계산값은 옮기지 않았다.
- 사업계획대비 · 전년대비 · 총 운영비용의 도급 합계는 도급내역 **입력값에서 계산**한다 (예전에는 엑셀로 올린 달에만 값이 나왔다). 결산 1호기 운휴시간이 비어 있고 그 날 고정비가 있으면 도급내역 화면처럼 24 로 본다.
- 스팀량이 하루도 없는 달(단가 입력이 단가만 기록해 둔 달)은 도급 실적이 없는 달로 보고 합계를 만들지 않는다 — 사업계획대비는 그런 달에 저장된 운영비 실적(`OPERATION_ACTUAL`, 엑셀 값)을 보여 준다. 화면(`incinerator-store.js` 의 `computed=1`)과 엑셀 내보내기(`collectContractMonth`)가 같은 규칙을 쓴다.
- `U2_STEAM`(결산 2호기 스팀량)은 실 2호기 스팀량에서 나오는 값이지만 과거 값을 지키려고 컬럼으로 둔다.
- 사업계획대비는 예전에 '조회한 월'마다 12개월 표를 따로 저장했다. 이제 (연, 월) 한 행으로 접는다 — 위탁운영비 이달·누계 칸과 비고는 그 월의 행에 둔다.
- 전년대비는 저장하는 값이 없다 (도급내역에서 계산).
- 운영단가(위탁)는 소수 12자리까지 보존한다 (`DECIMAL(24,12)`).
- 엑셀 가져오기는 고른 달의 도급내역, 그 해의 사업계획대비 표·가동실적·총 운영비용을 파일 기준으로 바꾼다. 다른 달의 도급내역은 건드리지 않는다 (예전에는 그 해 12개월을 지웠다).

### 매핑표 (옛 키 → 표.컬럼)

**`steam_incinerator_contract_daily`** — 소각로 도급내역 · 키 `(USAGE_DATE)` · 옛 저장소: `incinerator_contract` 의 `unit1|일|{steam,unit,runtime,fixed}`, `unit2|일|…`, `real|일|{steam-1,unit-1,steam-2,unit-2}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `U1_STEAM` | DECIMAL(18,6) | `u1Steam` | 결산 1호기 스팀량(톤/일) |
| `U1_UNIT` | DECIMAL(18,6) | `u1Unit` | 결산 1호기 운영단가(원/톤) |
| `U1_RUNTIME` | DECIMAL(18,6) | `u1Runtime` | 결산 1호기 운휴시간(Hr) — 비어 있으면 화면·집계에서 24 로 본다 |
| `U1_FIXED` | DECIMAL(18,6) | `u1Fixed` | 결산 1호기 운휴시간당 고정비(원/시간) — 단가 입력 waste1_fixed_hr 이 그 달 모든 날에 기록 |
| `U2_STEAM` | DECIMAL(18,6) | `u2Steam` | 결산 2호기 스팀량(톤/일) — 실 2호기 스팀량과 상한 336 중 작은 값 (화면 계산값, 과거 값 보존) |
| `U2_UNIT` | DECIMAL(18,6) | `u2Unit` | 결산 2호기 운영단가(원/톤) — 단가 입력 waste2_steam |
| `U2_RUNTIME` | DECIMAL(18,6) | `u2Runtime` | 결산 2호기 운휴시간(Hr) |
| `U2_FIXED` | DECIMAL(18,6) | `u2Fixed` | 결산 2호기 운휴시간당 고정비(원/시간) — 단가 입력 waste2_fixed_hr |
| `REAL_STEAM1` | DECIMAL(18,6) | `realSteam1` | 실 스팀량 기준 1호기 스팀량(톤/일) |
| `REAL_UNIT1` | DECIMAL(18,6) | `realUnit1` | 실 스팀량 기준 1호기 단가(원/톤) |
| `REAL_STEAM2` | DECIMAL(18,6) | `realSteam2` | 실 스팀량 기준 2호기 스팀량(톤/일) — 복합보일러 스팀생산실적의 폐합성 2호 기본값 |
| `REAL_UNIT2` | DECIMAL(18,6) | `realUnit2` | 실 스팀량 기준 2호기 단가(원/톤) — 단가 입력 waste2_steam |

**`steam_incinerator_plan_monthly`** — 소각로 사업계획대비 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `incinerator_plan_vs` 의 `{labor,operation,total}|{plan,actual}-m월`, `break|…`, `notes|m월-u{1,2}` + `incinerator_contract` 의 `summary|0|labor`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `CONTRACT_LABOR` | DECIMAL(18,6) | `contractLabor` | 도급내역: 인건비(원/월) |
| `LABOR_PLAN` | DECIMAL(18,6) | `laborPlan` | 인건비 계획(천원) |
| `LABOR_ACTUAL` | DECIMAL(18,6) | `laborActual` | 인건비 실적(천원) |
| `OPERATION_PLAN` | DECIMAL(18,6) | `operationPlan` | 운영비 계획(천원) |
| `OPERATION_ACTUAL` | DECIMAL(18,6) | `operationActual` | 운영비 실적(천원) — 도급내역으로 계산할 수 없는 달에 쓰는 직접 입력·엑셀 값 |
| `TOTAL_PLAN` | DECIMAL(18,6) | `totalPlan` | 합계 계획(천원) — 직접 입력 |
| `NOTE_U1` | VARCHAR(500) | `noteU1` | 비고: #1 폐합성수지소각로 |
| `NOTE_U2` | VARCHAR(500) | `noteU2` | 비고: #2 폐합성수지소각로 |
| `BRK_MONTH_PLAN` | DECIMAL(18,6) | `brkMonthPlan` | 위탁운영비 이달 계획(백만원) |
| `BRK_MONTH_ACTUAL` | DECIMAL(18,6) | `brkMonthActual` | 위탁운영비 이달 실적(백만원) |
| `BRK_YTD_PLAN` | DECIMAL(18,6) | `brkYtdPlan` | 위탁운영비 누계 계획(백만원) |
| `BRK_YTD_ACTUAL` | DECIMAL(18,6) | `brkYtdActual` | 위탁운영비 누계 실적(백만원) |
| `BRK_PREV_ACTUAL` | DECIMAL(18,6) | `brkPrevActual` | 위탁운영비 전월 실적(백만원) |
| `BRK_NOTE` | VARCHAR(500) | `brkNote` | 위탁운영비 비고 |

**`steam_incinerator_operation_monthly`** — 소각로 계획대비 가동실적 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `incinerator_operation` 의 `hours|월|u{1,2}-{plan,actual}`, `metric|월|{steam,rate,cost}-{plan,actual}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `U1_PLAN` | DECIMAL(18,6) | `u1Plan` | 1호기 가동계획(hr) |
| `U1_ACTUAL` | DECIMAL(18,6) | `u1Actual` | 1호기 가동실적(hr) — 총 운영비용 화면이 읽음 |
| `U2_PLAN` | DECIMAL(18,6) | `u2Plan` | 2호기 가동계획(hr) |
| `U2_ACTUAL` | DECIMAL(18,6) | `u2Actual` | 2호기 가동실적(hr) — 총 운영비용 화면이 읽음 |
| `STEAM_PLAN` | DECIMAL(18,6) | `steamPlan` | 스팀생산량 계획(톤) |
| `STEAM_ACTUAL` | DECIMAL(18,6) | `steamActual` | 스팀생산량 실적(톤) |
| `RATE_PLAN` | DECIMAL(24,12) | `ratePlan` | 운영단가(위탁) 계획(원/톤) |
| `RATE_ACTUAL` | DECIMAL(24,12) | `rateActual` | 운영단가(위탁) 실적(원/톤) — 전체 자릿수 보존 |
| `COST_PLAN` | DECIMAL(18,6) | `costPlan` | 위탁운영비용 계획(천원) |
| `COST_ACTUAL` | DECIMAL(18,6) | `costActual` | 위탁운영비용 실적(천원) |

**`steam_incinerator_cost_monthly`** — 소각로 총 운영비용 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `incinerator_total_cost` 의 `{구분}|월|{항목}` — 컬럼 이름은 구분-항목을 이은 것

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `OP_BURN_BURN_1` | DECIMAL(18,6) | `opBurnBurn1` | 운영현황: 폐기물 실소각량 1호기(톤) |
| `OP_BURN_BURN_2` | DECIMAL(18,6) | `opBurnBurn2` | 운영현황: 폐기물 실소각량 2호기(톤) |
| `OP_BURN_RATE_1` | DECIMAL(18,6) | `opBurnRate1` | 운영현황: 스팀생산원단위 1호기 |
| `OP_BURN_RATE_2` | DECIMAL(18,6) | `opBurnRate2` | 운영현황: 스팀생산원단위 2호기 |
| `OP_COST_UNIT` | DECIMAL(18,6) | `opCostUnit` | 위탁운영비: 위탁계약단가(원/톤) |
| `OP_COST_DEDUCT` | DECIMAL(18,6) | `opCostDeduct` | 위탁운영비: 차감비(천원) |
| `SELF_SRF_IN_QTY` | DECIMAL(18,6) | `selfSrfInQty` | 자체비용: 외부 폐합성수지 입고량(톤) |
| `SELF_SRF_IN_REV` | DECIMAL(18,6) | `selfSrfInRev` | 자체비용: 외부 폐합성수지 수익비용(천원) |
| `SELF_SRF_FLOOR_RECYCLE` | DECIMAL(18,6) | `selfSrfFloorRecycle` | 자체비용: 바닥재 재활용(톤) |
| `SELF_SRF_FLOOR_BURY` | DECIMAL(18,6) | `selfSrfFloorBury` | 자체비용: 바닥재 매립(톤) |
| `SELF_SRF_FLY_SOLID` | DECIMAL(18,6) | `selfSrfFlySolid` | 자체비용: 비산재 고형화(톤) |
| `SELF_SRF_FLY_BURY` | DECIMAL(18,6) | `selfSrfFlyBury` | 자체비용: 비산재 매립(톤) |
| `SELF_SRF_COST_FLOOR` | DECIMAL(18,6) | `selfSrfCostFloor` | 자체비용: 폐기물처리비용 바닥재(천원) |
| `SELF_SRF_COST_FLY` | DECIMAL(18,6) | `selfSrfCostFly` | 자체비용: 폐기물처리비용 비산재(천원) |
| `SELF_SRF_COST_MISC` | DECIMAL(18,6) | `selfSrfCostMisc` | 자체비용: 폐기물처리비용 기타(천원) |
| `SELF_UTIL_P1` | DECIMAL(18,6) | `selfUtilP1` | 유틸리티: 전력 1호기 사용량(Kw) |
| `SELF_UTIL_P2` | DECIMAL(18,6) | `selfUtilP2` | 유틸리티: 전력 2호기 사용량(Kw) |
| `SELF_UTIL_PRATE` | DECIMAL(18,6) | `selfUtilPrate` | 유틸리티: 전력단가 |
| `SELF_UTIL_NAOH_QTY` | DECIMAL(18,6) | `selfUtilNaohQty` | 유틸리티: 가성소다 구입량(Kg) |
| `SELF_UTIL_NAOH_UNIT` | DECIMAL(18,6) | `selfUtilNaohUnit` | 유틸리티: 가성소다 단가 |
| `SELF_UTIL_WATER` | DECIMAL(18,6) | `selfUtilWater` | 유틸리티: 용수사용비(천원) |
| `SELF_UTIL_WASTE_CHARGE` | DECIMAL(18,6) | `selfUtilWasteCharge` | 유틸리티: 폐기물처분부담금(천원) |
| `SELF_UTIL_ETC` | DECIMAL(18,6) | `selfUtilEtc` | 유틸리티: 기타(천원) |
| `SELF_TOTAL_TOTAL` | DECIMAL(18,6) | `selfTotalTotal` | 자체비용 합계: 비용합계(천원) — 화면 입력 |
| `SELF_TOTAL_BURN_RATE` | DECIMAL(18,6) | `selfTotalBurnRate` | 자체비용 합계: 처리량당 비용 — 화면 입력 |
| `SELF_TOTAL_STEAM_RATE` | DECIMAL(18,6) | `selfTotalSteamRate` | 자체비용 합계: 스팀톤당 비용 — 화면 입력 |
| `MAT_OIL_U1` | DECIMAL(18,6) | `matOilU1` | 원부자재: 부생유 #1 사용량 |
| `MAT_OIL_U2` | DECIMAL(18,6) | `matOilU2` | 원부자재: 부생유 #2 사용량 |
| `MAT_OIL_UNIT` | DECIMAL(18,6) | `matOilUnit` | 원부자재: 부생유 단가 |
| `MAT_OIL_CK_QTY` | DECIMAL(18,6) | `matOilCkQty` | 원부자재: 청관제 구입량 |
| `MAT_OIL_CK_UNIT` | DECIMAL(18,6) | `matOilCkUnit` | 원부자재: 청관제 단가 |
| `MAT_CHEM_U_QTY` | DECIMAL(18,6) | `matChemUQty` | 원부자재: 요소수 구입량 |
| `MAT_CHEM_U_UNIT` | DECIMAL(18,6) | `matChemUUnit` | 원부자재: 요소수 단가 |
| `MAT_CHEM_S_QTY` | DECIMAL(18,6) | `matChemSQty` | 원부자재: 소금 구입량 |
| `MAT_CHEM_S_UNIT` | DECIMAL(18,6) | `matChemSUnit` | 원부자재: 소금 단가 |
| `MAT_MISC_A_QTY` | DECIMAL(18,6) | `matMiscAQty` | 원부자재: 분말활성탄 구입량 |
| `MAT_MISC_A_UNIT` | DECIMAL(18,6) | `matMiscAUnit` | 원부자재: 분말활성탄 단가 |
| `MAT_MISC_K_QTY` | DECIMAL(18,6) | `matMiscKQty` | 원부자재: 크링커제거제 구입량 |
| `MAT_MISC_K_UNIT` | DECIMAL(18,6) | `matMiscKUnit` | 원부자재: 크링커제거제 단가 |

### API

쓰기는 모두 `STEAM_ENERGY_MGMT` 메뉴 권한이 필요하다. 응답은 `ApiResponse`.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/steam-energy-api/incinerator-contract-dailies?year=[&month=]` | 소각로 도급내역 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/incinerator-contract-dailies` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/incinerator-contract-dailies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/incinerator-contract-dailies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/incinerator-plan-monthlies?year=[&month=]` | 소각로 사업계획대비 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/incinerator-plan-monthlies` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/incinerator-plan-monthlies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/incinerator-plan-monthlies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/incinerator-operation-monthlies?year=[&month=]` | 소각로 계획대비 가동실적 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/incinerator-operation-monthlies` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/incinerator-operation-monthlies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/incinerator-operation-monthlies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/incinerator-cost-monthlies?year=[&month=]` | 소각로 총 운영비용 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/incinerator-cost-monthlies` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/incinerator-cost-monthlies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/incinerator-cost-monthlies/{id}` | 단건 조회 · 소프트 삭제 |
| `POST` | `/steam-energy-api/incinerator-excel/import-excel` (file, year, month) | 워크북 `5. 소각로 위탁운영비(YYYY년MM월).xlsx` 가져오기 |
| `GET` | `/steam-energy-api/incinerator-excel/export-excel?year=&month=` | 같은 서식으로 내보내기 |

### 다른 화면과의 연동

- 단가 입력(`steam_price`) → `POST /prices/apply-daily`: `waste2_steam` → `U2_UNIT` · `REAL_UNIT2`, `waste1_fixed_hr` → `U1_FIXED`, `waste2_fixed_hr` → `U2_FIXED` (그 달 모든 날)
- 마감비교(`energy-month-compare.html`)의 폐합성 스팀구매 ← 도급내역 입력값에서 계산한 월 합계
- 복합보일러 스팀생산실적의 폐합성 2호 기본값 ← `REAL_STEAM2`
- 총 운영비용 ← 가동실적 `U1_ACTUAL` · `U2_ACTUAL`, 도급내역 월 합계

### 데이터 이관

`migration/2026-11-20_incinerator_cells_to_tables.sql` — 옛 셀을 새 표로 옮긴다 (재실행 안전: 비어 있는 칸만 채움). 저장돼 있던 계산값, 그 달에 없는 날짜(6월 31일), 숫자 칸의 글자는 옮기지 않는다. **2025년 총 운영비용의 유틸리티 칸(전력단가·가성소다 구입량/단가·용수·폐기물처분부담금)과 차감비는 옛 셀 자체가 한 칸씩 밀려 저장돼 있었다** (옛 가져오기 코드의 결함). 이관 뒤 원본 `5. 소각로 위탁운영비 첨부자료(25년10월).xls` 를 2025년 10월로 다시 올리면 바로잡힌다 (로컬은 그렇게 했다).

## SRF 보일러 인보이스 그룹 전환 (2026-11-20) — 업무 모듈 규격 4-1 맞춤 (묶음 3-C)

SRF Boiler invoice 7개 화면(Summary · Client Invoice · 운영내역 · 월별 운영내역 · Accumulated · BSC · Adjustment Factor)을 옛 `steam_cell_value` 논리 표 `srf_boiler_invoice_*` 에서 실제 컬럼 표 7개로 옮겼다. 사고이력은 묶음 3-E 에서 옮긴다.
화면의 칸 키는 그대로 두고 저장할 때 표의 행·컬럼으로 옮긴다 — `js/srf-store.js` 가 옛 호출 모양을 받아 새 API 로 보낸다. 이 그룹은 엑셀 가져오기·내보내기가 없다.

### 설계 규칙

- 계산값(BSC·ASC 금액, TOTAL SERVICE CHARGES, VAT, DEDUCTIONS, NET CASH, 줄 금액·소계, 누계, AF 합계)은 저장하지 않는다.
- Summary 의 `OTHERS_0` · `OTHERS_1` · `UTILITIES_DEDUCTION` · `OTHER_DEDUCTION_1` · `OTHER_DEDUCTION_2` 는 NULL 이면 Client Invoice 값을 따른다 (값이 있으면 그 값). 마감비교·배분비율이 읽는 복합보일러 스팀구매 합계도 같은 규칙을 쓴다 (`energy-linked-sources.js`).
- 월별 운영내역과 Accumulated 는 화면이 6월~다음해 5월을 한 해로 보지만 표에는 실제 연/월로 저장한다 (옛 키의 칸 번호 0~6 → 그 해 6~12월, 7~11 → 다음 해 1~5월).
- 운영내역은 전력량만 입력이다. 스팀생산량·공정수·탈기기급수량은 복합보일러 스팀생산실적(`steam_combo_daily_production`)에서 오고 나머지는 계산이다.
- Client Invoice 는 한 달 16줄 고정 (유틸리티 10 + 두 방향 기타 3줄씩), Adjustment Factor 는 한 해 24줄 고정이다.

### 매핑표 (옛 키 → 표.컬럼)

**`steam_srf_invoice_summary`** — SRF 인보이스 Summary · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `srf_boiler_invoice_summary` 의 `summary-vk-r{엑셀 행}` (col_index 4 = D열, 6 = F열): r16 r18 r20 r22 r27 r29 r31 r34 r36 r37 r38 r44(F) r51 r53 r55 r57 r59 순서로 아래 컬럼

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `BSC_RATE` | DECIMAL(18,6) | `bscRate` | 1. BSC ▸ BASE SERVICE CHARGE (Won/ton steam) — 단가 입력 comb_bsc |
| `BSC_FACTOR` | DECIMAL(18,6) | `bscFactor` | BSC ESCALATION FACTOR — 단가 입력 comb_bsc_factor |
| `AFBSCR_BSC` | DECIMAL(18,6) | `afbscrBsc` | AFBSCr - BSC (Won/ton steam) |
| `BSC_STEAM_AMOUNT` | DECIMAL(18,6) | `bscSteamAmount` | BSC STEAM SUPPLY AMOUNT (tons/month) — 단가 입력 comb_bsc_guarantee |
| `ASC_RATE` | DECIMAL(18,6) | `ascRate` | 2. ASC ▸ ADDITIONAL SERVICE CHARGE (Won/ton steam) — 단가 입력 comb_asc |
| `ASC_FACTOR` | DECIMAL(18,6) | `ascFactor` | ASC ESCALATION FACTOR — 단가 입력 comb_asc_factor |
| `ASC_STEAM_AMOUNT` | DECIMAL(18,6) | `ascSteamAmount` | ASC STEAM SUPPLY AMOUNT (tons/month) |
| `OTHERS_0` | DECIMAL(18,6) | `others0` | 3. OTHERS 첫 금액(Won) — 비어 있으면 Client Invoice 의 깨끗한나라←베올리아 1번째 줄(수량×단가)을 따른다 |
| `OTHERS_1` | DECIMAL(18,6) | `others1` | 3. OTHERS 1)(Won) — 비어 있으면 Client Invoice 의 깨끗한나라←베올리아 2번째 줄을 따른다 |
| `OTHERS_2` | DECIMAL(18,6) | `others2` | 3. OTHERS 2)(Won) |
| `OTHERS_3` | DECIMAL(18,6) | `others3` | 3. OTHERS 3)(Won) |
| `PAYABLE_ADJUST` | DECIMAL(18,6) | `payableAdjust` | VAT 와 TOTAL AMOUNT PAYABLE 사이 조정 금액(Won) |
| `LIQUIDATED_DAMAGE` | DECIMAL(18,6) | `liquidatedDamage` | DEDUCTIONS: LIQUIDATED DAMAGE(Won) |
| `ASC_REFUND_ANNUAL` | DECIMAL(18,6) | `ascRefundAnnual` | DEDUCTIONS: ASC REFUND(Annual)(Won) |
| `UTILITIES_DEDUCTION` | DECIMAL(18,6) | `utilitiesDeduction` | DEDUCTIONS: UTILITIES DEDUCTION COSTS(Won) — 비어 있으면 Client Invoice 유틸리티 소계를 따른다 |
| `OTHER_DEDUCTION_1` | DECIMAL(18,6) | `otherDeduction1` | OTHER DEDUCTION COSTS (1)(Won) — 비어 있으면 Client Invoice 깨끗한나라→베올리아 1번째 줄을 따른다 |
| `OTHER_DEDUCTION_2` | DECIMAL(18,6) | `otherDeduction2` | OTHER DEDUCTION COSTS (2)(Won) — 비어 있으면 Client Invoice 깨끗한나라→베올리아 2번째 줄을 따른다 |

**`steam_srf_invoice_client_item`** — SRF 인보이스 Client Invoice · 키 `(YEAR_NO, MONTH_NO, SECTION, LINE_NO)` · 옛 저장소: `srf_boiler_invoice_client` 의 `{구분}|{줄}|{qty,price,note,label,unit}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `SECTION` (키) | VARCHAR(20) | `section` | 구분 (utilities: 유틸리티 10줄 / other-out: 깨끗한나라→베올리아 3줄 / other-in: 깨끗한나라←베올리아 3줄) |
| `LINE_NO` (키) | INT | `lineNo` | 줄 번호 (0부터) |
| `ITEM_LABEL` | VARCHAR(200) | `itemLabel` | 항목 이름 (other-out · other-in 만 입력) |
| `UNIT_LABEL` | VARCHAR(50) | `unitLabel` | 단위 (other-out · other-in 만 입력) |
| `QTY` | DECIMAL(18,6) | `qty` | 수량 |
| `UNIT_PRICE` | DECIMAL(18,6) | `unitPrice` | 단가(원) |
| `NOTE` | VARCHAR(500) | `note` | 비고 |

**`steam_srf_invoice_operation_daily`** — SRF 인보이스 운영내역 · 키 `(USAGE_DATE)` · 옛 저장소: `srf_boiler_invoice_operation` 의 `operation-r{일+4}` col_index 12

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `POWER_KWH` | DECIMAL(18,6) | `powerKwh` | 전력량(Kw/D) |

**`steam_srf_invoice_monthly`** — SRF 인보이스 월별 운영내역 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `srf_boiler_invoice_monthly` (연 키 = 6월이 속한 해) 의 `{steam,ops,etc}|{칸 0~11}|{항목}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `STEAM_QTY` | DECIMAL(18,6) | `steamQty` | 스팀 구매량(ton) |
| `STEAM_UNIT` | DECIMAL(18,6) | `steamUnit` | 스팀 단가(원/ton) |
| `POWER_QTY` | DECIMAL(18,6) | `powerQty` | 전력 사용량(kwh) |
| `POWER_UNIT` | DECIMAL(18,6) | `powerUnit` | 전력 단가(원) |
| `LNG_QTY` | DECIMAL(18,6) | `lngQty` | LNG 사용량(Nm3) |
| `LNG_UNIT` | DECIMAL(18,6) | `lngUnit` | LNG 단가(원) |
| `PROCESS_QTY` | DECIMAL(18,6) | `processQty` | 공정수 사용량(m3) |
| `PROCESS_UNIT` | DECIMAL(18,6) | `processUnit` | 공정수 단가(원) |
| `SOFT_QTY` | DECIMAL(18,6) | `softQty` | 청수 사용량(m3) |
| `SOFT_UNIT` | DECIMAL(18,6) | `softUnit` | 청수 단가(원) |
| `SOFTEN_QTY` | DECIMAL(18,6) | `softenQty` | 연수 사용량(m3) |
| `SOFTEN_UNIT` | DECIMAL(18,6) | `softenUnit` | 연수 단가(원) |
| `LIFE_QTY` | DECIMAL(18,6) | `lifeQty` | 생활용수 사용량(m3) |
| `LIFE_UNIT` | DECIMAL(18,6) | `lifeUnit` | 생활용수 단가(원) |
| `ETC_SPECIAL` | DECIMAL(18,6) | `etcSpecial` | 기타: 특약 미달금액(원, +) |
| `ETC_BSC7` | DECIMAL(18,6) | `etcBsc7` | 기타: 7차연도 BSC물량 정산금(원, +) |
| `ETC_SRF_SHARE` | DECIMAL(18,6) | `etcSrfShare` | 기타: SRF이익 분배금(원, −) |
| `ETC_INSPECT` | DECIMAL(18,6) | `etcInspect` | 기타: 보일러 검사수수료(원, −) |
| `ETC_OVER7` | DECIMAL(18,6) | `etcOver7` | 기타: 7차연도 단가 초과 지급분(원, −) |
| `ETC_AIR_OVER` | DECIMAL(18,6) | `etcAirOver` | 기타: 대기초과배출부과금(원, −) |
| `ETC_WASTE` | DECIMAL(18,6) | `etcWaste` | 기타: 폐기물 처분부담금(원, −) |

**`steam_srf_invoice_accumulated`** — SRF 인보이스 누계 스팀량 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `srf_boiler_invoice_accumulated` (연 키 = 6월이 속한 해) 의 `{칸 0~11}|{contract,bsc,asc}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `CONTRACT_VOLUME` | DECIMAL(18,6) | `contractVolume` | Contract Supply Volume(ton) |
| `BSC_VOLUME` | DECIMAL(18,6) | `bscVolume` | BSC steam supply volume(ton) |
| `ASC_VOLUME` | DECIMAL(18,6) | `ascVolume` | ASC steam supply volume(ton) |

**`steam_srf_invoice_bsc`** — SRF 인보이스 BSC 단가 · 키 `(YEAR_NO)` · 옛 저장소: `srf_boiler_invoice_bsc` 의 `rate`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `BSC_RATE` | DECIMAL(18,6) | `bscRate` | Base Service Charge Rate(Won/ton) |

**`steam_srf_invoice_adjustment`** — SRF 인보이스 Adjustment Factor · 키 `(YEAR_NO, SECTION, ITEM_CODE)` · 옛 저장소: `srf_boiler_invoice_adjustment` 의 `{표}|{항목}|{coef,base,present}`

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `SECTION` (키) | VARCHAR(20) | `section` | 표 구분 (afbsc / afbsc-rebase / afasc) |
| `ITEM_CODE` (키) | VARCHAR(20) | `itemCode` | 항목 (a, b, c, d, e, e-bottom, e-fly, f, g, h) |
| `COEFFICIENT` | DECIMAL(18,6) | `coefficient` | 계수 |
| `BASE_VALUE` | DECIMAL(18,6) | `baseValue` | 기준값(Base) |
| `PRESENT_VALUE` | DECIMAL(18,6) | `presentValue` | 현재값(Present) |

### API

쓰기는 모두 `STEAM_ENERGY_MGMT` 메뉴 권한이 필요하다. 응답은 `ApiResponse`.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/steam-energy-api/srf-invoice-summaries?year=[&month=]` | SRF 인보이스 Summary 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-summaries` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/srf-invoice-summaries/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-summaries/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-client-items?year=[&month=]` | SRF 인보이스 Client Invoice 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-client-items` | 행 전체 업서트 `(YEAR_NO, MONTH_NO, SECTION, LINE_NO)` |
| `PUT` | `/steam-energy-api/srf-invoice-client-items/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-client-items/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-operation-dailies?year=[&month=]` | SRF 인보이스 운영내역 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-operation-dailies` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/srf-invoice-operation-dailies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-operation-dailies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-monthlies?year=[&month=]` | SRF 인보이스 월별 운영내역 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-monthlies` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/srf-invoice-monthlies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-monthlies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-accumulateds?year=[&month=]` | SRF 인보이스 누계 스팀량 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-accumulateds` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/srf-invoice-accumulateds/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-accumulateds/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-bscs?year=[&month=]` | SRF 인보이스 BSC 단가 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-bscs` | 행 전체 업서트 `(YEAR_NO)` |
| `PUT` | `/steam-energy-api/srf-invoice-bscs/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-bscs/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/srf-invoice-adjustments?year=[&month=]` | SRF 인보이스 Adjustment Factor 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/srf-invoice-adjustments` | 행 전체 업서트 `(YEAR_NO, SECTION, ITEM_CODE)` |
| `PUT` | `/steam-energy-api/srf-invoice-adjustments/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/srf-invoice-adjustments/{id}` | 단건 조회 · 소프트 삭제 |

### 다른 화면과의 연동

- 단가 입력(`steam_price`) → `POST /prices/apply-daily`: `comb_bsc` → `BSC_RATE`, `comb_bsc_factor` → `BSC_FACTOR`, `comb_bsc_guarantee` → `BSC_STEAM_AMOUNT`, `comb_asc` → `ASC_RATE`, `comb_asc_factor` → `ASC_FACTOR` (그 달의 Summary 행)
- Summary ← Client Invoice: 기타 2줄, 유틸리티 소계, 차감 2줄의 기본값
- 마감비교(`energy-month-compare.html`) · 배분비율(`energy-fuel-ratio.html`) ← Summary 의 TOTAL SERVICE CHARGES(입력값에서 계산), Client Invoice 의 전력 외 유틸리티 + 깨끗한나라→베올리아 합계 (`energy-linked-sources.js`)
- 운영내역 ← 복합보일러 스팀생산실적 `COMBO_STEAM` · `COMBO_PROCESS` · `COMBO_DEAERATOR`

### 데이터 이관

`migration/2026-11-20_srf_invoice_cells_to_tables.sql` — Summary 의 입력값을 옮긴다 (재실행 안전). 나머지 화면은 옛 저장소에 값이 든 행이 없다 (빈 문자열뿐). Accumulated 에 남아 있던 옛 형식 2건(`static` / `current|0|bsc` = 37068, `contract` = 38000)은 어느 해 값인지 알 수 없어 옮기지 않았다.

## 유동상 운영 그룹 전환 (2026-11-20) — 업무 모듈 규격 4-1 맞춤 (묶음 3-D)

유동상 운영내역의 운영실적(사업계획대비 · 실적비교 · 소각로 도급비용) · SRF 입고내역 · 월별 운영내역을 옛 `steam_cell_value` 논리 표 `fluidized_plan_actual` · `_plan_summary` · `_year_note` · `_contract_daily` · `_contract_cost` · `_srf_inbound` · `_monthly_cache` 에서 실제 컬럼 표 4개로 옮겼다. 사고이력·설비개선은 묶음 3-E, 세부 운영내역(`fluidized_detail_main`)은 묶음 4 에서 옮긴다.
화면의 칸 키는 그대로 두고 저장할 때 표의 행·컬럼으로 옮긴다 — 화면은 `js/fluidized-store.js`, 엑셀은 `service/table/FluidizedCellStore`.

### 설계 규칙

- 계산값(실적 · 증감 · 합계, T/hr · 운영비용 · 총 운영비용, 일 합계 · 누계 · 업체 합계 · 비용)은 저장하지 않는다.
- 월별 운영내역의 직접 입력 6개(설비개선비용 · 차감비 · 추가 지급분 · 폐수량 · 폐토사 처리량 · 폐토사 처리금액)는 예전에 브라우저(localStorage)에만 있었다. 이제 `steam_fluidized_month` 에 저장한다. 화면을 열 때 그 브라우저에 남은 옛 값이 있고 DB 의 그 달 칸이 비어 있으면 한 번 올린다.
- `CONTRACT_COST` · `OPERATING_COST` 는 월별 운영내역 화면이 계산을 마치면 저장하는 화면 계산값이다 (옛 `fluidized_monthly_cache`). 마감비교의 유동상 스팀구매가 `CONTRACT_COST` 를 읽는다.
- SRF 입고 업체는 화면처럼 10칸 고정이다 (칸 번호 = 화면의 업체 순서). 엑셀 가져오기는 서식의 업체 열 순서대로 칸을 채우며 11번째 열부터는 버린다.
- 실적비교의 월별 특이사항은 예전에 그 해 1월 키에 `note-월` 로 모아 두었다. 이제 그 월의 행에 둔다.

### 매핑표 (옛 키 → 표.컬럼)

**`steam_fluidized_month`** — 유동상 월별 계획·비용 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `fluidized_plan_actual` 의 `operating`/`power`/`waste`/`srf_income` col 0, `fluidized_plan_summary` 의 `monthly-summary`, `fluidized_year_note` 의 `note-월`, `fluidized_contract_cost` 의 `cost` col 0·1·2, `fluidized_monthly_cache` 의 `contractCost`/`operatingCost` (col = 월), 월별 운영내역 화면의 브라우저 저장값

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `PLAN_OPERATING` | DECIMAL(18,6) | `planOperating` | 사업계획: 운영비(백만원) |
| `PLAN_POWER` | DECIMAL(18,6) | `planPower` | 사업계획: 전력비(백만원) |
| `PLAN_WASTE` | DECIMAL(18,6) | `planWaste` | 사업계획: 폐기물처리비(백만원) |
| `PLAN_SRF_INCOME` | DECIMAL(18,6) | `planSrfIncome` | 사업계획: SRF수입금(백만원) |
| `SUMMARY_REMARK` | VARCHAR(1000) | `summaryRemark` | 사업계획대비 요약 비고 |
| `MONTH_NOTE` | VARCHAR(1000) | `monthNote` | 실적비교 월별 특이사항 |
| `LABOR_COST` | DECIMAL(18,6) | `laborCost` | 도급비용 지급현황: 인건비(원) |
| `OPERATING_PAY_COST` | DECIMAL(18,6) | `operatingPayCost` | 도급비용 지급현황: 운영비(원) |
| `IMPROVEMENT_PAY_COST` | DECIMAL(18,6) | `improvementPayCost` | 도급비용 지급현황: 설비개선비용(원) |
| `IMPROVEMENT_COST` | DECIMAL(18,6) | `improvementCost` | 월별 운영내역: 설비개선비용(천원) |
| `DEDUCTION_COST` | DECIMAL(18,6) | `deductionCost` | 월별 운영내역: 차감비(천원) |
| `EXTRA_PAYMENT` | DECIMAL(18,6) | `extraPayment` | 월별 운영내역: 추가 지급분(천원) |
| `WASTEWATER_QTY` | DECIMAL(18,6) | `wastewaterQty` | 월별 운영내역: 폐수량 |
| `SOIL_QTY` | DECIMAL(18,6) | `soilQty` | 월별 운영내역: 폐토사 처리량 |
| `SOIL_COST` | DECIMAL(18,6) | `soilCost` | 월별 운영내역: 폐토사 처리금액(천원) |
| `CONTRACT_COST` | DECIMAL(18,6) | `contractCost` | 도급비(천원) = 스팀 운영비용 + 고정비 + 설비개선비용 − 차감비 + 추가 지급분 (화면 계산값, 마감비교의 유동상 스팀구매가 읽음) |
| `OPERATING_COST` | DECIMAL(18,6) | `operatingCost` | 총 운영비(천원) (화면 계산값) |

**`steam_fluidized_contract_daily`** — 유동상 소각로 도급비용 · 키 `(USAGE_DATE)` · 옛 저장소: `fluidized_contract_daily` 의 `일(두 자리)` col 0·2·4·5

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `STEAM_TON` | DECIMAL(18,6) | `steamTon` | 스팀 생산량(T/D) |
| `STEAM_UNIT_PRICE` | DECIMAL(18,6) | `steamUnitPrice` | 스팀단가(원/톤) |
| `STOP_HOURS` | DECIMAL(18,6) | `stopHours` | 운휴시간(Hr) |
| `FIXED_COST_PER_HOUR` | DECIMAL(18,6) | `fixedCostPerHour` | 운휴시간당 고정비(원/시간) |

**`steam_fluidized_srf_inbound_daily`** — 유동상 SRF 입고내역 · 키 `(USAGE_DATE)` · 옛 저장소: `fluidized_srf_inbound` 의 `entry:일` col 1~10

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `VENDOR01_KG` | DECIMAL(18,6) | `vendor01Kg` | 업체 1번 칸 입고량(kg) — 화면 기본 업체: 대일인터내셔널 |
| `VENDOR02_KG` | DECIMAL(18,6) | `vendor02Kg` | 업체 2번 칸 입고량(kg) — 화면 기본 업체: 그린플러스 |
| `VENDOR03_KG` | DECIMAL(18,6) | `vendor03Kg` | 업체 3번 칸 입고량(kg) — 화면 기본 업체: 대림종합개발 |
| `VENDOR04_KG` | DECIMAL(18,6) | `vendor04Kg` | 업체 4번 칸 입고량(kg) — 화면 기본 업체: 케이제이환경 |
| `VENDOR05_KG` | DECIMAL(18,6) | `vendor05Kg` | 업체 5번 칸 입고량(kg) — 화면 기본 업체: 태창 |
| `VENDOR06_KG` | DECIMAL(18,6) | `vendor06Kg` | 업체 6번 칸 입고량(kg) — 화면 기본 업체: 평일 |
| `VENDOR07_KG` | DECIMAL(18,6) | `vendor07Kg` | 업체 7번 칸 입고량(kg) — 화면 기본 업체: 케이엠 |
| `VENDOR08_KG` | DECIMAL(18,6) | `vendor08Kg` | 업체 8번 칸 입고량(kg) — 화면 기본 업체: 두제 |
| `VENDOR09_KG` | DECIMAL(18,6) | `vendor09Kg` | 업체 9번 칸 입고량(kg) — 화면 기본 업체: 세종스틸 |
| `VENDOR10_KG` | DECIMAL(18,6) | `vendor10Kg` | 업체 10번 칸 입고량(kg) — 화면 기본 업체: 리에나 |

**`steam_fluidized_srf_rate`** — 유동상 SRF 처리단가 · 키 `(YEAR_NO, MONTH_NO)` · 옛 저장소: `fluidized_srf_inbound` 의 `rate` col 1~10

| 컬럼 | 타입 | 화면 필드 | 설명 |
|---|---|---|---|
| `VENDOR01_RATE` | DECIMAL(18,6) | `vendor01Rate` | 업체 1번 칸 처리단가(원/톤) — 화면 기본 업체: 대일인터내셔널 |
| `VENDOR02_RATE` | DECIMAL(18,6) | `vendor02Rate` | 업체 2번 칸 처리단가(원/톤) — 화면 기본 업체: 그린플러스 |
| `VENDOR03_RATE` | DECIMAL(18,6) | `vendor03Rate` | 업체 3번 칸 처리단가(원/톤) — 화면 기본 업체: 대림종합개발 |
| `VENDOR04_RATE` | DECIMAL(18,6) | `vendor04Rate` | 업체 4번 칸 처리단가(원/톤) — 화면 기본 업체: 케이제이환경 |
| `VENDOR05_RATE` | DECIMAL(18,6) | `vendor05Rate` | 업체 5번 칸 처리단가(원/톤) — 화면 기본 업체: 태창 |
| `VENDOR06_RATE` | DECIMAL(18,6) | `vendor06Rate` | 업체 6번 칸 처리단가(원/톤) — 화면 기본 업체: 평일 |
| `VENDOR07_RATE` | DECIMAL(18,6) | `vendor07Rate` | 업체 7번 칸 처리단가(원/톤) — 화면 기본 업체: 케이엠 |
| `VENDOR08_RATE` | DECIMAL(18,6) | `vendor08Rate` | 업체 8번 칸 처리단가(원/톤) — 화면 기본 업체: 두제 |
| `VENDOR09_RATE` | DECIMAL(18,6) | `vendor09Rate` | 업체 9번 칸 처리단가(원/톤) — 화면 기본 업체: 세종스틸 |
| `VENDOR10_RATE` | DECIMAL(18,6) | `vendor10Rate` | 업체 10번 칸 처리단가(원/톤) — 화면 기본 업체: 리에나 |

### API

쓰기는 모두 `STEAM_ENERGY_MGMT` 메뉴 권한이 필요하다. 응답은 `ApiResponse`.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/steam-energy-api/fluidized-months?year=[&month=]` | 유동상 월별 계획·비용 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/fluidized-months` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/fluidized-months/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/fluidized-months/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/fluidized-contract-dailies?year=[&month=]` | 유동상 소각로 도급비용 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/fluidized-contract-dailies` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/fluidized-contract-dailies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/fluidized-contract-dailies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/fluidized-srf-inbound-dailies?year=[&month=]` | 유동상 SRF 입고내역 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/fluidized-srf-inbound-dailies` | 행 전체 업서트 `(USAGE_DATE)` |
| `PUT` | `/steam-energy-api/fluidized-srf-inbound-dailies/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/fluidized-srf-inbound-dailies/{id}` | 단건 조회 · 소프트 삭제 |
| `GET` | `/steam-energy-api/fluidized-srf-rates?year=[&month=]` | 유동상 SRF 처리단가 조회 (한 해 또는 한 달) |
| `POST` | `/steam-energy-api/fluidized-srf-rates` | 행 전체 업서트 `(YEAR_NO, MONTH_NO)` |
| `PUT` | `/steam-energy-api/fluidized-srf-rates/bulk` | 범위(한 해·한 달) 일괄 저장 `{year, month?, clearMissing, rows}` |
| `GET` · `DELETE` | `/steam-energy-api/fluidized-srf-rates/{id}` | 단건 조회 · 소프트 삭제 |
| `POST` · `GET` | `/steam/api/tables/fluidized-summary/{import,export}-excel` 등 (옛 경로 그대로) | 유동상 운영내역 워크북은 세부 운영내역(묶음 4) · 사고·개선(묶음 3-E) 시트가 함께 들어 있어 주소는 묶음 4 전환 때 옮긴다. 이 그룹의 시트는 이미 규격 표로 읽고 쓴다 (전체 가져오기 = 그 해의 사업계획·특이사항과 고른 달의 비고·지급현황·일별 도급비용·SRF 입고를 파일 기준으로 바꿈, 탭별 가져오기 = 읽은 칸만 고침) |

### 다른 화면과의 연동

- 마감비교(`energy-month-compare.html`)의 유동상소각로 스팀구매 ← `steam_fluidized_month.CONTRACT_COST` ÷ 1000
- 운영실적 화면의 SRF수입금 실적, 세부 운영내역의 AK46 ← SRF 입고량 × 처리단가 (서버 `FluidizedSummaryWorkbookService` · `FluidizedDetailWorkbookService`)
- 월별 운영내역 ← 세부 운영내역(`fluidized_detail_main`, 묶음 4 전환 전까지 옛 셀)의 월 합계 칸

### 데이터 이관

`migration/2026-11-20_fluidized_cells_to_tables.sql` — 옛 셀을 새 표로 옮긴다 (재실행 안전: 비어 있는 칸만 채움). 월별 운영내역의 직접 입력값은 서버에 없던 값이라 SQL 로 옮길 수 없고, 화면을 열 때 브라우저에 남은 값을 올린다.

## 메뉴 / 권한

- 메뉴 코드: `STEAM_ENERGY_MGMT` → URL `/steam-energy/page`
- 쓰기 API(`POST/PUT/DELETE /steam-energy-api/**`)는 이 메뉴 접근 권한이 필요
- 조회 API는 플랫폼 JWT 인증만으로 접근 가능

## 롤백

```sql
DELETE FROM core_role_menu WHERE menu_id = (SELECT menu_id FROM core_menu WHERE menu_code='STEAM_ENERGY_MGMT');
DELETE FROM core_menu WHERE menu_code = 'STEAM_ENERGY_MGMT';
DROP TABLE IF EXISTS steam_daily_usage;
DROP TABLE IF EXISTS steam_price;
DROP TABLE IF EXISTS steam_equipment;
```

## 업무 모듈 규격 적용 상태

이 모듈은 플랫폼 업무 모듈 규격(Spring Boot 업무 모듈 생성 표준)에 맞춰 정리했다.

### 메뉴 / 권한 등록 정보

| 항목 | 값 |
|------|-----|
| 메뉴명 | 스팀에너지관리 (상위 그룹) + 하위 6그룹 44페이지 |
| 메뉴 URL | `/steam/{page}.html` (SPA 가 iframe 으로 로드) |
| API Prefix | `/steam-energy-api` (신규 JPA 영역) / `/steam/api/tables` (이관 대시보드) |
| 등록 SQL | `04_menu_data.sql`(상위) + `05_submenus.sql`(하위) |
| 역할 | `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_USER` + 스팀 전용 3종(`06_roles.sql`) |

### core 에 추가로 필요한 기능

- **`CurrentUserProvider`** — 규격은 `com.company.core.security.CurrentUserProvider` 로
  현재 로그인 사용자 ID 를 받도록 하지만 core 에 아직 없다.
  그래서 `CREATED_BY / UPDATED_BY / DELETED_BY` 컬럼은 만들어 두고 값은 비워 둔다.
  core 에 제공되면 각 Service 의 저장·수정·삭제에서 채우면 된다.
  (업무 모듈에서 `SecurityContextHolder` 를 직접 다루지 않는다는 규칙에 따라 임의 구현하지 않음)

### 규격에서 벗어난 부분 (이관 대시보드 한정)

`legacy/` 패키지는 기존 스팀 대시보드를 그대로 옮겨온 영역이라 아래가 규격과 다르다.
화면 40여 개와 저장 데이터 5만 건이 얽혀 있어 한 번에 바꾸지 않고 남겨 둔 것이다.

| 규격 | 현재 | 영향 |
|------|------|------|
| API prefix `/{모듈명}-api/**` | `/steam/api/tables/**` | 모든 화면 JS 가 이 경로를 사용 |
| 응답은 `ApiResponse<T>` | `Map<String,Object>` 반환 | 화면 JS 가 `{data, total, page}` 형태를 직접 파싱 |
| 예외는 `BusinessException` | `ResponseStatusException` | 화면이 HTTP 상태코드로 분기 |
| 컬럼명 대문자 SNAKE_CASE | `steam_cell_value` 등 소문자 | 이름을 바꾸면 화면·서비스 전부 수정 필요 |

바꾸려면 화면 JS 까지 함께 손봐야 하므로, 별도 과제로 잡고 단계적으로 옮기는 것을 권한다.

전환 진행: 묶음 1 원단위 일지 7개 화면(2026-11-20 완료), 묶음 2 에너지 회계비용 5개 화면(2026-11-20 완료). 남은 것: 묶음 3(일별 입력형 24개), 묶음 4(양식형 4개), 정리.
