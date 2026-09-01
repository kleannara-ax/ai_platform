# module-kims SQL

KIMS(IT 운영 관리) 모듈의 DB 스크립트. 플랫폼과 **같은 DB** 를 쓴다(로컬: `steam`).
`ddl-auto=none` 이므로 아래 순서로 직접 실행한다.

## 실행 순서

번호는 실행 순서 그대로다(건너뛰는 번호 없음). 모든 파일이 재실행해도 안전한 idempotent
패턴(표는 `IF NOT EXISTS`, 컬럼/인덱스는 `INFORMATION_SCHEMA` 존재 체크 후 추가, 메뉴는 삭제 후
재삽입, 코드/명단은 없을 때만 추가)이므로, **신규 설치든 기존 배포 DB든 01번부터 07번까지
순서대로 전부 실행**하면 된다.

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | KIMS 테이블 11개 (구조만, `SITE` 컬럼 포함). ddl-auto=none 이라 반드시 먼저 실행 |
| 2 | `02_menu.sql` | 플랫폼 메뉴 등록 (KIMS_MGMT 그룹 + 페이지 6개) + 역할별 노출 권한 |
| 3 | `03_perm_code.sql` | 공통코드 `KIMS_PERM` (KIMS 관리자 명단) |
| 4 | `04_data.sql` | 기존 운영 데이터 이관(대용량, 신규 설치 시에는 생략 가능) |
| 5 | `05_pc_site_column.sql` | `ip_address.SITE` 컬럼/인덱스 보강 — 01_schema.sql 에 이미 반영된 신규 설치 DB에서는 실행해도 아무 변화 없음(idempotent). 01_schema.sql 을 갱신하기 전에 만들어진 기존 배포 DB에만 실질적인 효과가 있음 |
| 6 | `06_seoul_perm_code.sql` | 공통코드 `KIMS_PERM_SEOUL` ("KIMS 서울", PC 관리 서울 전용 접근 그룹) 생성 |
| 7 | `07_seoul_ip_band_backfill.sql` | 서울 IP 대역(192.1.17/104/107/117.x) 기존 데이터 `SITE` 보정 |

```bash
mysql -u root --default-character-set=utf8mb4 {db} < 01_schema.sql
mysql -u root --default-character-set=utf8mb4 {db} < 02_menu.sql
mysql -u root --default-character-set=utf8mb4 {db} < 03_perm_code.sql
mysql -u root --default-character-set=utf8mb4 {db} < 04_data.sql
mysql -u root --default-character-set=utf8mb4 {db} < 05_pc_site_column.sql
mysql -u root --default-character-set=utf8mb4 {db} < 06_seoul_perm_code.sql
mysql -u root --default-character-set=utf8mb4 {db} < 07_seoul_ip_band_backfill.sql
```

### 기존 KIMS 데이터를 함께 옮길 때

`01_schema.sql` 대신 KIMS 덤프를 적재하면 표와 데이터가 같이 들어온다.
덤프는 용량(2MB)·성격상 저장소에 넣지 않으므로 파일을 따로 받아서 쓴다.
원본 덤프는 별도 `kims` 데이터베이스를 만들도록 되어 있으니 아래 두 가지를 고쳐서 적재한다.

1. `DROP DATABASE` / `CREATE DATABASE` 두 줄 삭제
2. `USE \`kims\`;` → 플랫폼 DB 이름으로 변경

> KIMS 는 자체 사용자/로그인 테이블(`kims_user`)을 두지 않는다. 로그인·인증은 항상
> 플랫폼 `core_user` 계정을 사용하며, 요청자/담당자 이름 등은 각 업무 테이블의
> 문자열 컬럼(REQUESTER_NAME, ASSIGNEE 등)에 자유 입력으로 저장한다.

## 테이블 (11개, 플랫폼 DB 공용)

| 테이블 | 설명 |
|--------|------|
| `service_request` / `request_log` / `request_attachment` | 업무 요청과 처리 이력·첨부 |
| `ip_address` / `ip_history` | PC(IP) 관리와 변경 이력 |
| `inventory_item` / `inventory_transaction` | 소모품 재고와 입출고 |
| `internet_work` | 인터넷 공사 |
| `program_install` | 프로그램 설치 |
| `supply_issue` | 소모품 지급 |
| `qr_location` | QR 구역 |

모든 테이블은 공통 감사(Audit) 컬럼을 갖는다: `CREATED_AT`/`CREATED_BY`/`UPDATED_AT`/`UPDATED_BY`/
`DELETED_YN`/`DELETED_AT`/`DELETED_BY`. `CREATED_BY`/`UPDATED_BY`/`DELETED_BY` 는 core_user FK 가
아니라 플랫폼 로그인 ID를 담는 문자열(varchar(50))이다.

### 삭제 정책 (소프트 삭제)

물리 `DELETE` 는 사용하지 않는다. 아래 4개 엔티티는 `delete()` 비즈니스 메서드로
`DELETED_YN='Y'` 처리만 하며, 목록/검색 조회는 모두 `DELETED_YN='N'` 조건으로 걸러진다.

- `RequestAttachment` (첨부파일 메타데이터, 실제 파일은 업로드 디렉토리에서 즉시 제거)
- `InternetWork` (인터넷 공사)
- `ProgramInstall` (프로그램 설치 내역)
- `QrLocation` (QR 구역)

그 외(`ServiceRequest`, `InventoryItem`, `IpAddress`, `SupplyIssue` 등)는 원래부터
상태 전환(`changeStatus` 등)만으로 관리되고 별도 삭제 기능이 없어 대상이 아니다.
`SupplyIssueService.reverseByRequest()` 의 `deleteAll` 은 "요청 취소/반려 시 지급을 원복"하는
정정성 로직으로, 재고를 함께 되돌리는 보정 처리이며 향후 감사 요구가 생기면 소프트 삭제로
전환을 검토할 수 있으나 현재는 표준 적용 범위에서 제외했다.

## 권한

KIMS 관리자 명단은 **공통코드 그룹 `KIMS_PERM`** 에서 관리한다 (공통코드 관리 화면 → KIMS 관리자 → 코드 추가,
`CODE` = 플랫폼 로그인 ID). 소방 `FIRE_PERM`, PS점검 `PS_INSP_AUTH` 과 같은 방식이다.

PC 관리(IP 관리)의 **서울 전용 접근**은 같은 방식으로 **공통코드 그룹 `KIMS_PERM_SEOUL`(표시명: "KIMS 서울")**
에서 관리한다 (공통코드 관리 화면 → KIMS 서울 → 코드 추가, `CODE` = 플랫폼 로그인 ID).
KIMS_PERM_SEOUL 에 등록된 계정은 PC 관리에서 **서울 데이터만** 조회/수정할 수 있고, **청주 데이터는 완전히 차단**된다.
KIMS 관리자(KIMS_PERM)·플랫폼 `ROLE_MANAGER` 는 이 제한과 무관하게 항상 전체(청주+서울)를 조회/수정한다.

판정은 [`KimsPermission`](../../module-kims/src/main/java/com/company/module/kims/support/KimsPermission.java) 이 하고,
컨트롤러/서비스는 이를 호출한다. `code_group`/`code_detail` 을 JdbcTemplate 으로 직접 조회하는데,
이는 `module-ps-insp` 가 읽기전용 JPA 엔티티로 같은 테이블에 접근하는 것과 동일한 기존 플랫폼 관례를 따른 것이다.

| 대상 | 표현식 | 통과 조건 |
|------|--------|-----------|
| 관리자 전용 | `@kimsPerm.isAdmin(authentication)` | KIMS_PERM 등록자 **또는** 플랫폼 `ROLE_ADMIN` |
| 요청 처리·입력 | `@kimsPerm.canWork(authentication)` | 위 관리자 **또는** 플랫폼 `ROLE_MANAGER` **또는** KIMS_PERM_SEOUL 등록자(서울 전용) |
| 대상 사업장 제한 | `IpAddressService` 내부 `resolveSite`/`assertSiteAccess` (`kimsPerm.allowedSite`/`canWorkOnSite` 사용) | 서울 전용 사용자는 site 파라미터를 무시하고 서버가 "서울"로 강제, 청주 레코드 대상 작업은 ACCESS_DENIED(A004)로 차단 |

플랫폼 `ROLE_ADMIN` 을 항상 통과시키는 이유는 공통코드에서 실수로 전원을 지웠을 때 잠기지 않게 하기 위해서다.
목록은 30초간 캐시되므로 공통코드를 고친 뒤 최대 30초 후 반영된다.

> 메뉴 노출은 여전히 역할 기반(`core_role_menu`: ROLE_ADMIN / ROLE_MANAGER)이다.
> KIMS_PERM/KIMS_PERM_SEOUL 에만 넣고 플랫폼 역할이 ROLE_USER 인 사람은 API 는 되지만 좌측 메뉴가 보이지 않는다.
> (서울 전용 계정을 실제 운용하려면 해당 플랫폼 계정에 ROLE_MANAGER 이상 역할도 함께 부여하거나,
> 플랫폼 메뉴 화면에서 별도로 노출 방법을 검토해야 한다 — 이번 범위에서는 공통코드 권한 판정만 구현했다.)

### 서울 사업장 IP 대역

서울 사업장의 IP 대역은 **`192.1.17` / `192.1.104` / `192.1.107` / `192.1.117`**(각 `.1~.254`)로 고정되어 있다.
[`IpAddressService`](../../module-kims/src/main/java/com/company/module/kims/service/IpAddressService.java) 의
`SEOUL_GROUPS` 상수 + `isSeoulBandIp()` 로 판정하며, 다음 경로에서 이 대역의 IP는 사업장(SITE)을
**자동으로 "서울"** 로 지정한다(화면에서 탭을 청주로 두고 등록해도 실제 IP 대역 기준으로 보정됨):

- 신규 PC 등록(`create`)
- 엑셀 업로드(`importExcel`, 행 단위로 판정)
- 업무요청 자동반영으로 신규 IP 행이 만들어질 때(`createEmptyIp`)

대시보드 "대역 사용 현황"(`getUtilization`)도 site=서울 조회 시 이 4개 고정 대역을 표시한다(청주의
`USER_GROUPS`/`FACILITY_GROUPS` 와 동일한 고정 리스트 방식).

과거에 이 대역으로 등록된 기존 데이터를 서울로 보정해야 한다면 `07_seoul_ip_band_backfill.sql`
(idempotent, `SITE <> '서울'` AND IP LIKE 해당 대역인 행만 UPDATE)을 실행한다.

## core 에 추가가 필요한 부분 (향후 검토)

신규 "업무 모듈 생성 표준"은 로그인 사용자 정보를
`com.company.core.security.CurrentUserProvider` 로 조회하는 것을 가정하지만,
**core 에는 현재 이 컴포넌트가 존재하지 않는다.** module-fire/module-ps-insp/module-dailyreport
모두 각자 다른 방식(`SecurityContextHolder`, `Authentication` 파라미터,
`@AuthenticationPrincipal`)으로 로그인 사용자를 얻고 있으며, KIMS 도 이번 리팩터링에서
컨트롤러가 `Authentication` 을 직접 받아 `authentication.getName()` 을 로그인 ID로 사용하는
기존 관례를 그대로 따랐다(예: 첨부파일 삭제자, 공사/설치/QR 삭제자 기록).

추후 core 에 `CurrentUserProvider` (또는 동등한 공통 유틸)가 추가되면, 각 모듈의
컨트롤러가 이를 통해 로그인 ID/사용자ID를 일관되게 조회하도록 맞추는 것을 권장한다.
이번 리팩터링에서는 "현재 KIMS 프로젝트만 변경"이라는 범위 제약에 따라 core 는 수정하지 않았다.

또한 `KimsSecurityConfig` (`@Order(-2)` SecurityFilterChain, `/kims/**` + `/qr-api/**` permitAll)는
신규 표준상 "업무 모듈은 SecurityConfig 를 만들지 않는다" 규칙과 상충하지만, 이를 core 의
`SecurityConfig` 로 옮기려면 core 수정이 필요하므로 이번 범위에서는 유지했다. 향후 다른 업무
모듈에도 정적 페이지(직접 URL 접속)가 필요해지면, core 에 공통 패턴으로 흡수하는 방안을 검토한다.

## 자체 검수 결과 (신규 표준 대비)

| 항목 | 상태 | 비고 |
|------|------|------|
| SpringBootApplication/application.yml 미생성 | ✅ 준수 | |
| security 는 compileOnly 만 사용 | ✅ 준수 | core 가 `api` 로 런타임 제공 |
| Entity Builder+정적팩토리+비즈니스 메서드 | ✅ 준수 | `@Setter`/`@Data` 미사용 |
| 공통 감사컬럼(7종) | ✅ 준수 | 전 테이블에 적용 |
| 소프트 삭제(물리 DELETE 금지) | ✅ 준수 | 삭제 기능이 있는 4개 엔티티 전환 완료 |
| Controller `/kims-api/**` + `ApiResponse` 응답 | ✅ 준수 | 기존부터 준수 |
| BusinessException/EntityNotFoundException 사용 | ✅ 준수 | IllegalArgumentException/UncheckedIOException 모두 전환 |
| core_user 등 core 테이블 FK 금지 | ✅ 준수 | createdBy 등은 문자열 로그인ID |
| 자체 사용자 테이블(kims_user) 생성 금지 | ✅ 준수 | 이번 리팩터링에서 제거 |
| CREATE TABLE IF NOT EXISTS / INSERT IGNORE 등 | ✅ 준수 | |
| 메뉴/권한 core 테이블 임의 INSERT 금지 | △ 부분 | `KIMS_PERM`/`KIMS_PERM_SEOUL` 공통코드 그룹만 등록(권한 관리용, 메뉴 아님). 메뉴는 `02_menu.sql` 이 기존부터 등록해왔음(리팩터링 범위 밖) |
| SecurityConfig 미생성 | △ 예외 유지 | `KimsSecurityConfig` — 위 "core 에 추가가 필요한 부분" 참고 |
| CurrentUserProvider 사용 | △ 미사용 | core 에 해당 컴포넌트 없음. `Authentication` 파라미터로 대체(기존 플랫폼 관례) |
| code_group/code_detail 직접 조회 지양 | △ 유지 | `module-ps-insp` 선례와 동일한 기존 플랫폼 관례로 판단, 유지 |

## 마이그레이션 이력

KIMS 단독 프로젝트 시절의 마이그레이션 SQL(구 01~29번)은 이 폴더에서 뺐다.
현재 스키마가 `01_schema.sql` 에 이미 반영돼 있어 실행할 필요가 없고,
일부는 `DROP` 이나 데이터 재구축이라 운영 DB 에서 잘못 실행하면 위험하기 때문이다.
이력이 필요하면 `Kims/KIMS Ver 0.3.zip` 안의 `KIMS/sql/module-kims/` 를 참고한다.

### 05번 이후 (신규 설치 기준 스키마 확정 이후 변경분)

`01_schema.sql` 은 신규 설치 기준 스키마이므로, 이 파일이 특정 시점 기준으로 확정된 뒤에
생긴 변경분은 `module-fire`(`V8`,`V9`,`V32`~`V39`)와 같은 방식으로 버전드 마이그레이션
파일을 이 폴더에 순서대로 남긴다(번호는 건너뛰지 않고 이어서 붙인다). 이미 배포되어
데이터가 쌓인 DB 는 `01_schema.sql` 을 재실행할 수 없으므로, 아래 파일을 번호 순서대로
실행해 스키마를 맞춘다(모두 `INFORMATION_SCHEMA` 존재 체크 후에만 `ALTER`/`CREATE INDEX`
를 실행하는 idempotent 패턴이라, 이미 반영된 DB에서 재실행해도 안전하다).

| 파일 | 설명 |
|------|------|
| `05_pc_site_column.sql` | PC 관리 사업장(SITE) 구분 도입 — 서울/청주 PC를 별도 관리하기 위해 `ip_address` 에 `SITE varchar(20) NOT NULL DEFAULT '청주'` 컬럼과 `IDX_IP_ADDRESS_SITE` 인덱스를 추가한다. 기존 전체 데이터는 모두 `'청주'`로 채워진다(현행 유지). 조회는 `site` 파라미터가 있으면 해당 사업장만, 없으면 전체(기존 동작 보존)로 동작한다. `01_schema.sql` 은 이 컬럼/인덱스가 이미 반영된 최신 버전이므로, 그 버전으로 신규 설치하는 경우에는 이 파일을 실행해도 아무 변화가 없다(idempotent) — 그보다 이전 버전으로 이미 배포된 DB 에만 실질적인 효과가 있다. |
| `06_seoul_perm_code.sql` | PC 관리 "KIMS 서울" 권한 그룹 도입 — 공통코드 그룹 `KIMS_PERM_SEOUL`("KIMS 서울") 을 생성한다. 이 그룹에 등록된 로그인 ID는 PC 관리에서 서울 데이터만 조회·수정 가능하고 청주는 차단된다(다른 KIMS 기능은 영향 없음). `KIMS_PERM`(관리자)과 달리 초기 멤버를 자동 부트스트랩하지 않으며, 관리자가 공통코드 관리 화면에서 직접 등록한다. 신규 설치·기존 배포 DB 모두 이 파일을 실행해야 한다(스키마 변경이 아니라 코드 그룹 데이터이므로 `01_schema.sql` 에는 포함되지 않음). |
| `07_seoul_ip_band_backfill.sql` | PC 관리 "서울" 사업장 IP 대역 확정 — 서울 사업장 IP 대역을 `192.1.17`/`192.1.104`/`192.1.107`/`192.1.117`(각 `.1~.254`)로 정의하고, 이 대역에 해당하는 기존 등록 IP(기본값 '청주')의 `SITE` 를 '서울'로 백필한다. 애플리케이션 코드(`IpAddressService.isSeoulBandIp`)도 신규 등록/엑셀 업로드 시 이 대역이면 자동으로 `SITE='서울'` 을 지정하므로, 이 SQL 은 과거 데이터 보정용이다(idempotent, `SITE <> '서울'` 조건이라 재실행해도 안전). |
