# module-epr — EPR 포장재 중량 신고

재활용 포장재(EPR) 신고용 업무 모듈. 제품별 입수량·포장중량 기준정보를 관리하고, 연간 판매수량 엑셀을 올려
제품별·파트별 신고중량(kg)을 계산·저장·비교한다.

원래 단독 Spring Boot 프로젝트(`epr-reporting`, Thymeleaf + 자체 로그인)를 업무 모듈 규격 v5 에 맞춰 옮겼다.

| 원래 프로젝트 | 이 모듈 |
|---|---|
| 단독 앱 (`EprApplication`, `application.yml`, Docker, Flyway) | 플랫폼 app 에 포함되는 서브모듈. main class · 설정 파일 · Flyway 없음 |
| 자체 로그인 (이메일·비밀번호, BCrypt, 세션, CSRF, 사용자 관리, 비밀번호 변경) | **제거.** 플랫폼 계정 · JWT 로 통일 |
| `epr_user`, `epr_user_identity` 표 | 만들지 않음 |
| Thymeleaf 서버 렌더링 화면 `/` | 정적 화면 `/epr/index.html` + `/epr-api/**` 호출 |
| `/api/epr/**` (Map · 원시 DTO 응답) | `/epr-api/**` (`ApiResponse<T>`) |
| 계산 결과를 HTTP 세션에 보관 | 계산하면 그 연도로 저장되므로 세션을 쓰지 않음. 리포트는 연도로 내려받음 |
| 기동 시 기본 파트 16개 자동 입력 | `02_seed_data.sql` 로 옮김 |
| QueryDSL | JPQL `@Query` 로 바꿈 (모듈에 QueryDSL 빌드 설정을 넣지 않기 위해) |
| 자연키 PK (제품코드·파트코드·연도) | BIGINT 대리키 PK + 자연키 UNIQUE. 공통 감사 컬럼 7개 추가 |
| 같은 연도 재업로드 시 상세 물리 삭제 (`ON DELETE CASCADE`) | 기존 상세는 `DELETED_YN='Y'` 소프트 삭제 후 새로 저장 |

## 주요 기능

1. **판매수량 계산** — 판매수량 엑셀(.xlsx, 제품코드·제품명·EA)을 올리면 SKU·파트별 신고중량을 계산하고 신고 연도 실적으로 저장
   - 같은 제품코드는 판매수량 합산, BAG/BOX 환산 → 포장중량(g) ÷ 1,000 → 소수 둘째 자리 올림
   - 미등록 제품코드 · 중량 미입력 SKU 를 따로 표시, 미등록은 파트 집계에서 제외
   - 신고 연도당 1건. 이미 저장된 연도는 사용자 확인 후 교체
2. **연도별 비교** — 연도별 총 판매 EA · 신고중량과 전년 대비 증감
3. **저장된 연도 조회 · 결과 리포트 엑셀** (파트별 집계 · SKU 상세 두 시트)
4. **제품 마스터** — 검색·파트 필터, 표에서 바로 수정, 단건 등록, 마스터 엑셀 일괄 등록·갱신, 포장중량만 갱신
5. **파트 코드** — 제품코드 앞 3자리 ↔ 신고 파트명. 없는 코드는 제품 저장 시 `미분류(코드)` 로 자동 생성

## 생성 테이블 (4개)

| 테이블 | 컬럼 수 | COMMENT |
|---|---|---|
| `epr_part_category` | 10 | [module-epr] 파트 코드 |
| `epr_product_master` | 19 | [module-epr] 제품 마스터 |
| `epr_annual_sales` | 16 | [module-epr] 연도별 판매 실적 집계 |
| `epr_annual_sales_detail` | 27 | [module-epr] 연도별 판매 실적 SKU 상세 |

모든 표에 공통 컬럼 `CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY, DELETED_YN, DELETED_AT, DELETED_BY` 가 있다 (아래 매핑표에서는 생략).
FK 는 업무 표끼리만: `epr_product_master.PART_PREFIX → epr_part_category.PART_PREFIX`,
`epr_annual_sales_detail.ANNUAL_SALES_ID → epr_annual_sales.ANNUAL_SALES_ID`. core 표에는 FK 없음.

## 테이블-컬럼 매핑표 (규격 4-1)

| 메뉴/기능 | 테이블명 | 컬럼명 | 타입 | NULL | COMMENT | 대응 입력항목 |
|---|---|---|---|---|---|---|
| 파트 코드 관리 | epr_part_category | PART_CATEGORY_ID | BIGINT | N | 파트 코드 ID | (PK) |
| | | PART_PREFIX | VARCHAR(10) | N | 파트코드 (UNIQUE) | 파트코드 |
| | | PART_NAME | VARCHAR(255) | N | 파트명 | 파트명 |
| 제품 마스터 관리 | epr_product_master | PRODUCT_ID | BIGINT | N | 제품 마스터 ID | (PK) |
| | | PRODUCT_CODE | VARCHAR(30) | N | 제품코드 (UNIQUE) | 제품코드 |
| | | PART_PREFIX | VARCHAR(10) | N | 파트코드 | (제품코드 앞 3자리, 자동) |
| | | PRODUCT_NAME | VARCHAR(255) | N | 제품명 | 제품명 |
| | | EA_PER_BAG | INT | N | EA/BAG | EA/BAG |
| | | BAG_PER_BOX | INT | N | BAG/BOX | BAG/BOX |
| | | EA_PER_BOX | INT | N | EA/BOX | EA/BOX |
| | | BAG_PER_PLT | INT | N | BAG/PLT | BAG/PLT |
| | | BOX_PER_PLT | INT | N | BOX/PLT | BOX/PLT |
| | | BAG_WEIGHT | DECIMAL(15,4) | N | BAG 포장중량(g) | BAG 포장중량(g) |
| | | BOX_WEIGHT | DECIMAL(15,4) | N | BOX 포장중량(g) | BOX 포장중량(g) |
| | | COMPANY_TYPE | VARCHAR(10) | N | 구분 (자사/타사) | 구분 |
| 판매수량 계산 (연도 집계) | epr_annual_sales | ANNUAL_SALES_ID | BIGINT | N | 연도별 판매 실적 ID | (PK) |
| | | REPORT_YEAR | INT | N | 신고 연도 (UNIQUE) | 신고 연도 |
| | | ACCEPTED_ROWS | INT | N | 계산에 쓴 엑셀 행 수 | (계산) |
| | | UNIQUE_SKU_COUNT | INT | N | 계산 SKU 수 | (계산) |
| | | UNREGISTERED_SKU_COUNT | INT | N | 미등록 제품코드 수 | (계산) |
| | | MISSING_WEIGHT_SKU_COUNT | INT | N | 중량 미입력 SKU 수 | (계산) |
| | | TOTAL_SALES_EA | DECIMAL(18,4) | N | 총 판매수량(EA) | (계산) |
| | | TOTAL_REPORT_WEIGHT_KG | DECIMAL(18,2) | N | 총 신고중량(kg) | (계산) |
| | | CALCULATED_AT | DATETIME(6) | N | 계산 일시 | (자동) |
| 판매수량 계산 (SKU 상세, 1:N) | epr_annual_sales_detail | ANNUAL_SALES_DETAIL_ID | BIGINT | N | SKU 상세 ID | (PK) |
| | | ANNUAL_SALES_ID | BIGINT | N | 연도별 판매 실적 ID | (FK) |
| | | REPORT_YEAR | INT | N | 신고 연도 | 신고 연도 |
| | | PRODUCT_CODE | VARCHAR(30) | N | 제품코드 | 판매 엑셀 제품코드 |
| | | PART_PREFIX | VARCHAR(10) | N | 파트코드 | (자동) |
| | | PART_NAME | VARCHAR(255) | N | 계산 당시 파트명 | (자동) |
| | | MASTER_PRODUCT_NAME | VARCHAR(255) | N | 제품명(마스터) | (마스터) |
| | | UPLOADED_PRODUCT_NAME | VARCHAR(255) | N | 업로드 제품명 | 판매 엑셀 제품명 |
| | | SALES_EA | DECIMAL(18,4) | N | 판매수량(EA) | 판매 엑셀 EA |
| | | EA_PER_BAG | INT | N | 계산 당시 EA/BAG | (마스터) |
| | | BAG_WEIGHT_G | DECIMAL(15,4) | N | 계산 당시 BAG 포장중량(g) | (마스터) |
| | | CONVERTED_BAG | DECIMAL(18,6) | N | 환산 BAG 수량 | (계산) |
| | | BAG_REPORT_WEIGHT_KG | DECIMAL(18,2) | N | BAG 신고중량(kg) | (계산) |
| | | EA_PER_BOX | INT | N | 계산 당시 EA/BOX | (마스터) |
| | | BOX_WEIGHT_G | DECIMAL(15,4) | N | 계산 당시 BOX 포장중량(g) | (마스터) |
| | | CONVERTED_BOX | DECIMAL(18,6) | N | 환산 BOX 수량 | (계산) |
| | | BOX_REPORT_WEIGHT_KG | DECIMAL(18,2) | N | BOX 신고중량(kg) | (계산) |
| | | TOTAL_REPORT_WEIGHT_KG | DECIMAL(18,2) | N | SKU 총 신고중량(kg) | (계산) |
| | | STATUS | VARCHAR(30) | N | 상태 (정상/미등록코드/중량미입력) | (계산) |
| | | SOURCE_ROWS | INT | N | 합산된 엑셀 행 수 | (계산) |

연도 상세에 계산값을 저장하는 이유: 신고는 그 시점의 마스터 기준으로 확정된 값이어야 하므로, 나중에 마스터 포장중량이 바뀌어도
저장된 연도의 신고중량은 바뀌지 않게 계산 당시 값을 남긴다 (원래 프로젝트와 같다). 파트명 · 자사/타사 구분만 조회 시점 값으로 보여 준다.

## API 목록 (`/epr-api/**`, 플랫폼 JWT 필수)

응답은 모두 `ApiResponse<T>`. 엑셀 내려받기 4개만 파일 본문(xlsx)으로 응답하고, 실패하면 `ApiResponse` 오류로 응답한다.

| Method | URL | 설명 |
|---|---|---|
| GET | `/epr-api/products?q=&part=` | 제품 마스터 목록 (제품코드·제품명 검색, 파트 필터) |
| GET | `/epr-api/products/overview` | 등록 제품 수 |
| POST | `/epr-api/products` | 단건 등록 · 수정 (같은 제품코드가 있으면 수정). 제품코드에 `/` 가 들어갈 수 있어 주소에 코드를 넣는 PUT 은 두지 않았다 |
| GET | `/epr-api/parts` | 파트 코드 목록과 파트별 등록 SKU 수 |
| POST | `/epr-api/parts` | 파트 추가 · 파트명 수정 (삭제된 코드면 되살림) |
| DELETE | `/epr-api/parts/{partPrefix}` | 파트 소프트 삭제 (쓰는 제품이 있으면 거부) |
| GET | `/epr-api/excel/templates/master?includeData=false` | 마스터 양식 (예시 1행) / `includeData=true` 면 현재 전체 마스터 |
| GET | `/epr-api/excel/templates/weight` | 현재 SKU · 중량이 채워진 포장중량 양식 |
| GET | `/epr-api/excel/templates/sales` | 판매수량 양식 |
| POST | `/epr-api/excel/uploads/master` | 마스터 엑셀 일괄 등록·갱신 (multipart `file`) |
| POST | `/epr-api/excel/uploads/weight` | 포장중량만 갱신 (multipart `file`) |
| POST | `/epr-api/annual/calculate` | 판매수량 엑셀 계산 → 연도 저장 (multipart `file`, `year`, `replaceExisting`). 이미 있는 연도인데 `replaceExisting=false` 면 409 |
| GET | `/epr-api/annual/years` | 실적이 저장된 연도 |
| GET | `/epr-api/annual/compare?fromYear=&toYear=` | 연도별 판매량·신고중량, 전년 대비 증감 (최대 31개 연도) |
| GET | `/epr-api/annual/{year}` | 저장된 연도의 결과 (없으면 404) |
| GET | `/epr-api/annual/{year}/exists` | 그 연도 저장 여부 |
| GET | `/epr-api/annual/{year}/report` | 결과 리포트 엑셀 |

업로드는 `.xlsx`, 20MB 이하. 해석기는 상단 빈 행 · 좌측 빈 열을 건너뛰고 `제품코드` 헤더 행을 찾는다.

## 메뉴 등록 정보

| 항목 | 값 |
|---|---|
| 메뉴명 | EPR 포장재 중량 신고 (그룹: EPR 포장재 신고) |
| 메뉴 위치 | 최상위 그룹 `EPR_MGMT` > `EPR_REPORT` |
| 메뉴 URL | `/epr/index.html` (플랫폼 SPA 가 `epr::` 페이지로 iframe 에 연다) |
| API Prefix | `/epr-api` |

`03_menu.sql` 이 위 두 메뉴를 넣고 **ROLE_ADMIN 에만** 연다.

## 권한

별도 권한 코드를 두지 않고 **플랫폼 메뉴 권한을 그대로 쓴다.** 모든 EPR API 에
`@PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'EPR_REPORT')")` 가 붙어 있어,
`EPR_REPORT` 메뉴가 열린 역할만 조회·등록·수정·삭제를 할 수 있다.

| 구분 | 권한 |
|---|---|
| 관리자 (ROLE_ADMIN) | 전체 (03_menu.sql 이 연다) |
| 그 외 역할 | 플랫폼 "접근 권한" 화면에서 EPR 메뉴를 체크하면 전체 기능 사용 |
| 조회 전용 · 삭제 관리자 전용 | 원래 프로젝트에 없던 구분이라 두지 않았다. 필요하면 `EPR_PERM` 같은 공통코드 권한을 추가한다 |

원래 프로젝트의 ADMIN 권한은 "사용자 관리" 화면에만 쓰였고 그 화면은 제거했다.

## 로그인 세션 (플랫폼 정책을 그대로 따른다)

| 항목 | 플랫폼 기준 | EPR 화면 |
|---|---|---|
| 토큰 유효시간 | access 1시간 · refresh 7일 (`app/.../application.yml` 의 `jwt.*`) | 따로 정하지 않음 |
| 401 을 받으면 | refresh token 으로 한 번 갱신하고 같은 요청을 다시 보냄 (플랫폼 `index.html` 의 `api()`) | 같음 (`epr.js` 의 `authFetch`) |
| 갱신도 실패하면 | "세션이 만료되었습니다. 다시 로그인해주세요." 안내 후 로그아웃 | 플랫폼 안: `FIRE_AUTH_EXPIRED` 를 보내 플랫폼이 위 안내·로그아웃 (소방·스팀 모듈과 같음). 단독으로 연 화면: `/index.html?returnUrl=…` 로 이동 |
| 토큰을 읽는 순서 | — | `sessionStorage.auth` → `localStorage.fireweb_user` (소방 모듈 `csrf.js` 와 같음). 갱신한 토큰은 두 곳에 다시 저장 |

유효시간이나 갱신 정책을 바꾸려면 플랫폼(core · app) 쪽을 바꾼다. EPR 모듈에는 별도 설정이 없다.

## SQL 실행 순서

| 순서 | 파일 | 내용 | 재실행 |
|---|---|---|---|
| 1 | `01_schema.sql` | 업무 표 4개 | 안전 (`CREATE TABLE IF NOT EXISTS`) |
| 2 | `02_seed_data.sql` | 기본 파트 코드 16개 | 안전 (`INSERT IGNORE`, 바꾼 파트명은 덮어쓰지 않음) |
| 3 | `03_menu.sql` | 메뉴 2개 + ROLE_ADMIN 매핑 | 안전 (`ON DUPLICATE KEY UPDATE`, `INSERT IGNORE`) |

```
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 01_schema.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 02_seed_data.sql
mysql -u {user} -p --default-character-set=utf8mb4 {database} < 03_menu.sql
```

DROP · TRUNCATE · 무조건 DELETE · DROP COLUMN 없음. 원래 프로젝트 DB(`epr_reporting`)의 데이터를 옮기는 SQL 은 없다 —
기존 데이터가 필요하면 원래 앱에서 "현재 전체 데이터 다운로드"(마스터)와 연도별 판매 엑셀을 받아 이 모듈 화면에서 다시 올린다.

## 운영 반영 시 필요한 설정

- **플랫폼 등록 (모듈 밖 파일, 각 1곳)**
  - `settings.gradle`: `include 'module-epr'`
  - `app/build.gradle`: `implementation project(':module-epr')`
  - `app/src/main/resources/static/index.html`: `/epr/` 메뉴를 `epr::` 페이지로 iframe 에 여는 분기 (스팀 · KIMS 와 같은 방식)
- **보안**: 모듈 안의 `EprSecurityConfig` 가 화면 경로 `/epr/**` 만 공개(iframe 진입용)한다. core `SecurityConfig` 변경 없음.
  `/epr-api/**` 는 core 기본 체인으로 인증된다.
- **환경변수 · 설정 파일 변경 없음.** 업로드 한도는 플랫폼 `spring.servlet.multipart`(100MB) 안에서 모듈이 20MB 로 직접 검사한다.
  nginx 를 앞에 두는 경우 `client_max_body_size` 가 20MB 이상이어야 한다.
- 화면은 Bootstrap 5.3.3 을 jsDelivr CDN 에서 읽는다 (다른 모듈과 같다).

## core 에 추가로 필요한 기능

- `CurrentUserProvider` 가 core 에 없다. 대신 `@AuthenticationPrincipal(expression = "userId")`(core `CustomUserDetails`)로 사용자 ID 를 받아
  `CREATED_BY · UPDATED_BY · DELETED_BY` 에 저장한다 (module-cckb 와 같은 방식). core 에 Provider 가 생기면 그쪽으로 바꾼다.

## 자체 검수 체크리스트 결과

| 번호 | 검수 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | User/Auth/Role/Menu/Security 클래스 미생성 | 통과 | 원래의 `EprUser*`, `EprAccountService`, `EprUserDetailsService`, 사용자·비밀번호 화면 제거 |
| 2 | SecurityConfig, JwtProvider, AuthController 미생성 | 통과 | `EprSecurityConfig` 는 화면 경로 `/epr/**` 공개용 체인만 기여 (KIMS · CCKB 와 같은 방식, 인증 로직 없음) |
| 3 | application.yml, application.properties 미생성 | 통과 | |
| 4 | Dockerfile, docker-compose.yml, Nginx 설정 미생성 | 통과 | 원래의 Dockerfile · compose.yaml · start-epr.sh 는 옮기지 않음 |
| 5 | SpringBootApplication main class 미생성 | 통과 | |
| 6 | API URL /{모듈명}-api/** 규칙 준수 | 통과 | `/epr-api/**` |
| 7 | 금지 URL(/api, /admin, /auth 등) 미사용 | 통과 | 원래의 `/api/epr/**`, `/admin/users`, `/login` 제거 |
| 8 | Entity에 @Setter 없음 | 통과 | |
| 9 | Entity에 @Data 없음 | 통과 | |
| 10 | Service에서 entity.setXxx() 미사용 | 통과 | 생성은 Builder · 정적 팩토리, 변경은 update · updateWeights · delete · restore |
| 11 | 모든 Entity 컬럼에 @Column(name="UPPER_SNAKE_CASE") | 통과 | |
| 12 | 모든 업무 테이블에 공통 컬럼 포함 | 통과 | `EprBaseEntity` |
| 13 | DELETED_YN 기반 소프트 삭제 | 통과 | 파트 삭제, 연도 재업로드 시 기존 상세. 엔티티 `@SQLRestriction("DELETED_YN = 'N'")` |
| 14 | 모든 테이블·컬럼에 COMMENT 존재 | 통과 | |
| 15 | SQL에 DROP TABLE 없음 | 통과 | |
| 16 | SQL에 TRUNCATE TABLE 없음 | 통과 | |
| 17 | SQL에 무조건 DELETE 없음 | 통과 | DELETE 문 자체가 없음 |
| 18 | SQL에 ALTER TABLE DROP COLUMN 없음 | 통과 | |
| 19 | core 테이블(core_user 등)에 FK 미생성 | 통과 | |
| 20 | Controller 응답이 ApiResponse<T> | 통과 (예외 1) | 엑셀 내려받기 4개는 파일 본문 응답 (module-steam-energy 와 같음) |
| 21 | Entity 직접 반환 없음 | 통과 | |
| 22 | RuntimeException/IllegalArgumentException 직접 throw 없음 | 통과 | 원래 코드의 IllegalArgument/IllegalState/ResponseStatusException 을 BusinessException 으로 바꿈 |
| 23 | ddl-auto 미의존, SQL DDL 제공 | 통과 | `01_schema.sql` (Flyway 제거) |
| 24 | core 모듈 미수정 | 통과 | |
| 25 | app 모듈 미수정 | 예외 | 플랫폼이 수동 등록 방식이라 `settings.gradle` · `app/build.gradle` 각 1줄, `app/.../index.html` 에 `epr::` 분기를 넣었다 (위 "운영 반영 시 필요한 설정") |
| 26 | README에 메뉴/권한 등록 정보 작성 | 통과 | |
| 27 | core 추가 필요 사항 README 명시 | 통과 | `CurrentUserProvider` |
| 28 | 메타(EAV) 테이블 없음 | 통과 | |
| 29 | 입력 항목 1개 = 실제 컬럼 1개 (4-1 매핑표 대조) | 통과 | |
| 30 | 런타임 동적 SQL·런타임 DDL 없음 | 통과 | 네이티브 쿼리 4개는 고정 문장(삭제 행 되살리기용 조회) |
| 31 | 등록/수정 메뉴마다 대응 업무 테이블 존재 | 통과 | 판매수량 계산 → epr_annual_sales(+detail), 제품 마스터 → epr_product_master, 파트 코드 → epr_part_category |
| 32 | 금지 식별자 없음 (table_name, column_name, attr_value, data_json, field1, tb_table) | 통과 | |

### 실제 생성된 CREATE TABLE 목록

| 테이블 | 컬럼 수 | COMMENT |
|---|---|---|
| epr_part_category | 10 | [module-epr] 파트 코드 |
| epr_product_master | 19 | [module-epr] 제품 마스터 |
| epr_annual_sales | 16 | [module-epr] 연도별 판매 실적 집계 |
| epr_annual_sales_detail | 27 | [module-epr] 연도별 판매 실적 SKU 상세 |

- 총 업무 테이블 수: **4**
- 4-1 매핑표와 DDL 컬럼 일치 여부: **일치** (매핑표 컬럼 + 공통 컬럼 7개 = DDL 컬럼 수)
