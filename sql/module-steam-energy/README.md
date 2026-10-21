# module-steam-energy SQL

스팀에너지관리 모듈의 DB 스크립트. **ddl-auto=none** 이므로 반드시 아래 순서로 직접 실행한다.

## 실행 순서

파일 번호 순서대로 실행하면 된다(01 → 15). 번호가 곧 실행 순서다.

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | 신규 JPA 테이블 (steam_equipment·steam_price·steam_daily_usage) |
| 2 | `02_legacy_schema.sql` | 이관 대시보드 테이블 (steam_cell_value·steam_unit_usage·steam_unit_usage_raw) |
| 3 | `03_rename_steam_tables.sql` | 옛 이름 표(`table_cell_value`·`unit_usage`·`unit_usage_raw`)를 `steam_` 접두로 RENAME — 기존 DB 용. 신규 설치는 02 가 새 이름으로 만들므로 아무 것도 안 함 |
| 4 | `04_audit_columns.sql` | 업무 표 공통 컬럼(감사·소프트 삭제) 보강 — 기존 DB 용, 신규 설치는 무해 |
| 5 | `05_seed_data.sql` | 초기 데이터 (설비 마스터 12건 + 단가 샘플) |
| 6 | `06_price_code_split.sql` | 폐합성소각로 단가를 1·2호기로 분리 (옛 waste_* → waste1_*) |
| 7 | `07_remove_fixed_cost_items.sql` | 단가 입력의 (추가) 고정비용 항목 제거 |
| 8 | `08_menu_data.sql` | 플랫폼 상위 메뉴 등록 (STEAM_ENERGY_MGMT) + 역할 권한 |
| 9 | `09_submenus.sql` | 스팀 하위 메뉴(그룹 + 페이지) 전체 재등록 + 역할 권한 |
| 10 | `10_roles.sql` | 스팀 전용 역할 3종을 공통코드 ROLE 그룹에 등록 |
| 11 | `11_remove_combo_deduction_items.sql` | 단가 입력의 복합보일러 (차감) 항목 3종 제거 |
| 12 | `12_remove_month_compare_power_rows.sql` | 회계비용 마감비교 전력비·에너지 합산 행 값 제거 |
| 13 | `13_remove_month_compare_composite_steam.sql` | 마감비교 복합보일러 스팀구매 입력값 제거 (SRF 인보이스 연동) |
| 14 | `14_remove_month_compare_composite_lng_etc.sql` | 마감비교 복합 LNG 사용량·처분부담금 입력값 제거 (Client Invoice 연동) |
| 15 | `15_remove_waste1_price_items.sql` | 단가 입력의 폐합성소각로 1호기 스팀단가 제거 (고정단가는 남김) |

```bash
mysql -u {user} -p {database} < 01_schema.sql
mysql -u {user} -p {database} < 02_legacy_schema.sql
mysql -u {user} -p {database} < 03_rename_steam_tables.sql
mysql -u {user} -p {database} < 04_audit_columns.sql
mysql -u {user} -p {database} < 05_seed_data.sql
mysql -u {user} -p {database} < 06_price_code_split.sql
mysql -u {user} -p {database} < 07_remove_fixed_cost_items.sql
mysql -u {user} -p {database} < 08_menu_data.sql
mysql -u {user} -p {database} < 09_submenus.sql
mysql -u {user} -p {database} < 10_roles.sql
mysql -u {user} -p {database} < 11_remove_combo_deduction_items.sql
mysql -u {user} -p {database} < 12_remove_month_compare_power_rows.sql
mysql -u {user} -p {database} < 13_remove_month_compare_composite_steam.sql
mysql -u {user} -p {database} < 14_remove_month_compare_composite_lng_etc.sql
mysql -u {user} -p {database} < 15_remove_waste1_price_items.sql
```

표를 만들고 이름·컬럼을 맞춘 뒤(01~04) 데이터를 넣고, 그다음 메뉴·권한(08~10)을 등록하고,
마지막에 화면 변경에 따른 데이터 정리(11~15)를 하는 순서다. 모든 파일은 재실행해도 안전하다.

### 이미 배포된 DB 에 반영할 때

**번호 순서대로, 아직 안 돌린 것만** 돌린다. 기존 DB 에 `01`·`02` 는 다시 돌리지 않는다 — 02 가 새 이름으로
`CREATE TABLE IF NOT EXISTS` 를 하므로, 03 보다 먼저 돌리면 빈 `steam_cell_value` 가 생기고 03 의 보호 조건에
걸려 이름 변경이 건너뛰어진다. `03_rename_steam_tables.sql` 은 **서버를 내린 상태에서** 돌리고 새 코드와 함께
올린다 (옛 코드는 옛 이름을 찾는다).

> **번호 재정렬 (2026-10-21).** 표 이름 변경이 처음엔 `15_` 이었는데, 뒤의 정리 SQL 이 모두 새 이름을 쓰므로
> 기존 DB 에서는 그 앞에 와야 해서 `03_` 으로 옮기고 옛 `03~14` 를 한 칸씩 뒤로 밀었다 (옛 `03_audit_columns`
> → `04_`, … 옛 `14_remove_waste1_price_items` → `15_`). 내용은 같으니 **옛 번호로 이미 돌린 파일은 이름만 보고
> 건너뛰면** 된다. 어디까지 돌렸는지 번호로 기록해 둔 환경은 이 표로 이름을 맞춰 본다.

`steam_cell_value` 를 화면별로 어떻게 나눠 쓰는지(네임스페이스·col_index)는
실행 대상이 아니라 문서다 — [CELL_CONVENTIONS.md](CELL_CONVENTIONS.md) 참고.

### 운영 데이터를 함께 옮길 때

로컬에서 뽑은 데이터 파일(`steam-data-YYYYMMDD.sql`, REPLACE INTO)이 있으면
**위 01~15 를 모두 실행한 뒤 마지막에** 넣는다.

```bash
mysql -u {user} -p {database} < steam-data-YYYYMMDD.sql
```

## SQL 작성 규칙

이 폴더에 SQL 을 추가할 때 지키는 규칙이다. 지금까지 파일들이 실제로 따르는 방식을 정리한 것이라,
새 파일은 기존 파일(특히 `12_`·`15_`)을 본보기로 삼으면 된다.

### 1. 파일 이름과 번호

- 이름은 `NN_무엇을_하는지.sql` — 두 자리 번호 + 소문자 snake_case. 번호가 곧 실행 순서다.
- **번호는 마지막 번호 다음을 쓰고, 한 번 쓴 번호는 재사용하지 않는다.** 순서가 꼬이면 다른 환경에서 재현이 안 된다.
- **이미 배포된 파일은 고치지 않는다.** 바꿀 게 있으면 새 번호로 "되돌리는/고치는" 파일을 추가한다.
  예외는 신규 설치용 `01_schema`·`05_seed_data` 뿐이다 — 정리 SQL(`11_`·`15_`)로 지운 항목은 시드에서도 함께 빼서
  새로 까는 DB 와 기존 DB 가 같은 상태가 되게 한다.
- 파일을 추가하면 위 **실행 순서 표와 명령 블록**에 같은 번호로 한 줄씩 넣는다.
- 표 이름을 바꾸는 일(`03_rename_steam_tables`)은 예외적으로 다른 파일의 옛 이름도 함께 고쳤다. 신규 설치가 처음부터
  새 이름으로 만들어져야 하기 때문이다. 기존 DB 는 03 이 `RENAME TABLE` 로 맞춘다. 이때 번호도 실행 순서에 맞춰
  다시 매겼다(위 "번호 재정렬" 참고) — 번호를 바꾸는 일은 이렇게 순서 자체가 틀렸을 때만 하고, 옛→새 대응표를 남긴다.

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
| 컬럼 추가 | `ALTER TABLE … ADD COLUMN IF NOT EXISTS` (MariaDB 10.3+) | `04_audit_columns.sql` |
| 인덱스 추가 | `CREATE INDEX IF NOT EXISTS` | `01_schema.sql` |
| 행 넣기 | `INSERT … ON DUPLICATE KEY UPDATE` 또는 `INSERT … SELECT … WHERE NOT EXISTS` | `08_menu_data.sql` |
| 코드 바꾸기 | `INSERT IGNORE … SELECT` 로 새 코드에 복사한 뒤 옛 코드 `DELETE` (두 번째 실행은 0건) | `06_price_code_split.sql` |
| 행 지우기 | `DELETE … WHERE` 로 **자기 코드·row_key 만** | `11_`·`15_` |

**쓰지 않는 것** — `DROP TABLE`, `TRUNCATE`, `WHERE` 없는 `DELETE`/`UPDATE`,
`USE 데이터베이스명`, `CREATE DATABASE`. DB 이름은 실행하는 쪽이 정한다(`mysql … {database} < 파일`).
주석으로라도 `-- DROP TABLE` 을 남기지 않는다 — 규격 점검에서 금지 구문으로 잡힌다.

파일 머리에 `SET NAMES utf8mb4;` 를 둔다(정리 SQL `11_`~`15_` 처럼). 한글 메뉴명·항목명이 깨지지 않게 하기 위해서다.
옛 파일 일부에는 빠져 있는데, 고치지 않고 새 파일부터 지킨다.

### 4. 공용 표 (core_menu · core_role_menu · code_group · code_detail)

플랫폼과 다른 모듈이 같이 쓰는 표다. **우리 코드(`STEAM_` 접두)만 건드린다.**

- 상위 메뉴 1건은 `ON DUPLICATE KEY UPDATE` 로 넣는다 (`08_menu_data.sql`).
- 하위 메뉴 44건은 **자기 코드를 지우고 다시 넣는 "전체 재등록"** 방식이다 (`09_submenus.sql`).
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
  삭제는 `DELETED_YN='Y'` 소프트 삭제이고, 엔티티는 `@SQLRestriction("DELETED_YN = 'N'")` 로 가린다 (`04_audit_columns.sql` 참고).
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
- 스키마·메뉴 SQL(01~15)을 모두 돌린 **다음에** 실행한다.

## 테이블

| 테이블 | 설명 | UNIQUE 키 |
|--------|------|-----------|
| `steam_equipment` | 스팀 설비 마스터 | `EQUIPMENT_CODE` |
| `steam_price` | 시설별 계약·구매 단가(연/월) | `(YEAR_NO, MONTH_NO, ITEM_CODE)` |
| `steam_daily_usage` | 일일 스팀 사용 실적 | `(USAGE_DATE, EQUIPMENT_ID)` |
| `steam_cell_value` | 이관 대시보드 화면들의 셀 저장소 — `table_name` 으로 논리 표를 나눈다 ([CELL_CONVENTIONS.md](CELL_CONVENTIONS.md)) | `(month, table_name, row_key, col_index)` |
| `steam_unit_usage` | 원단위 일지 (설비·일자별) | `(month, day, machine_no)` |
| `steam_unit_usage_raw` | 원단위 일지 원시값 (디스퍼져·TOC·PICA) | `(month, day)` |

- 컬럼명 대문자 `UPPER_SNAKE_CASE`, 테이블명 소문자 `snake_case`
- `ENGINE=InnoDB`, `CHARSET=utf8mb4`, `COLLATE=utf8mb4_general_ci`
- 모든 컬럼에 `COMMENT` 부여

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
| 등록 SQL | `08_menu_data.sql`(상위) + `09_submenus.sql`(하위) |
| 역할 | `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_USER` + 스팀 전용 3종(`10_roles.sql`) |

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
