# module-steam-energy SQL

스팀에너지관리 모듈의 DB 스크립트. **ddl-auto=none** 이므로 반드시 아래 순서로 직접 실행한다.

## 실행 순서

파일 번호 순서대로 실행하면 된다(01 → 09). 번호가 곧 실행 순서다.

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | 신규 JPA 테이블 (steam_equipment·steam_price·steam_daily_usage) |
| 2 | `02_legacy_schema.sql` | 이관 대시보드 테이블 (table_cell_value·unit_usage·unit_usage_raw) |
| 3 | `03_audit_columns.sql` | 업무 표 공통 컬럼(감사·소프트 삭제) 보강 — 기존 DB 용, 신규 설치는 무해 |
| 4 | `04_seed_data.sql` | 초기 데이터 (설비 마스터 12건 + 단가 샘플) |
| 5 | `05_price_code_split.sql` | 폐합성소각로 단가를 1·2호기로 분리 (옛 waste_* → waste1_*) |
| 6 | `06_remove_fixed_cost_items.sql` | 단가 입력의 (추가) 고정비용 항목 제거 |
| 7 | `07_menu_data.sql` | 플랫폼 상위 메뉴 등록 (STEAM_ENERGY_MGMT) + 역할 권한 |
| 8 | `08_submenus.sql` | 스팀 하위 메뉴(그룹 + 페이지) 전체 재등록 + 역할 권한 |
| 9 | `09_roles.sql` | 스팀 전용 역할 3종을 공통코드 ROLE 그룹에 등록 |

```bash
mysql -u {user} -p {database} < 01_schema.sql
mysql -u {user} -p {database} < 02_legacy_schema.sql
mysql -u {user} -p {database} < 03_audit_columns.sql
mysql -u {user} -p {database} < 04_seed_data.sql
mysql -u {user} -p {database} < 05_price_code_split.sql
mysql -u {user} -p {database} < 06_remove_fixed_cost_items.sql
mysql -u {user} -p {database} < 07_menu_data.sql
mysql -u {user} -p {database} < 08_submenus.sql
mysql -u {user} -p {database} < 09_roles.sql
```

표를 만든 뒤(01~03) 데이터를 넣고, 그다음 메뉴·권한(07~09)을 등록하는 순서다.
모든 파일은 재실행해도 안전하다.

`table_cell_value` 를 화면별로 어떻게 나눠 쓰는지(네임스페이스·col_index)는
실행 대상이 아니라 문서다 — [CELL_CONVENTIONS.md](CELL_CONVENTIONS.md) 참고.

### 운영 데이터를 함께 옮길 때

로컬에서 뽑은 데이터 파일(`steam-data-YYYYMMDD.sql`, REPLACE INTO)이 있으면
**위 01~10 을 모두 실행한 뒤 마지막에** 넣는다.

```bash
mysql -u {user} -p {database} < steam-data-YYYYMMDD.sql
```

## 테이블

| 테이블 | 설명 | UNIQUE 키 |
|--------|------|-----------|
| `steam_equipment` | 스팀 설비 마스터 | `EQUIPMENT_CODE` |
| `steam_price` | 시설별 계약·구매 단가(연/월) | `(YEAR_NO, MONTH_NO, ITEM_CODE)` |
| `steam_daily_usage` | 일일 스팀 사용 실적 | `(USAGE_DATE, EQUIPMENT_ID)` |

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
| 등록 SQL | `03_menu_data.sql`(상위) + `05_submenus.sql`(하위) |
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
| 컬럼명 대문자 SNAKE_CASE | `table_cell_value` 등 소문자 | 이름을 바꾸면 화면·서비스 전부 수정 필요 |

바꾸려면 화면 JS 까지 함께 손봐야 하므로, 별도 과제로 잡고 단계적으로 옮기는 것을 권한다.
