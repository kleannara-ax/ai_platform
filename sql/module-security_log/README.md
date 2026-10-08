# module-security_log — ISMS 보안 로그 이상징후 탐지/검토 모듈

## 1. 모듈 설명

각 시스템 담당자가 **시스템 로그 파일을 업로드**하면, 정보보안팀이 관리하는 **탐지 룰셋**으로 이상징후를 자동 탐지합니다.
탐지 결과는 **월 단위로 검토하고 모니터링**합니다. ISMS-P 인증 기준 2.9.4(로그 및 접속기록 관리)와 2.9.5(로그 및 접속기록 점검)의 정기 점검 증적으로 활용할 수 있습니다.

| 항목 | 내용 |
|---|---|
| 모듈명 | `security_log` |
| 모듈 디렉토리 | `module-security_log/` |
| 패키지 루트 | `com.company.module.security_log` |
| API Prefix | `/security_log-api` |
| 사용 대상 | 각 시스템 담당자, 정보보안팀 담당자 |
| 실행 형태 | 플랫폼 app 모듈에 포함되는 plain jar (독립 실행 아님) |

### 업무 흐름

```
[시스템 담당자]                              [정보보안팀]
  ① 로그 파일 업로드 (월/시스템/로그유형)       ⓪ 룰셋 편집 (등록/수정/테스트/사용여부)
        │                                            │
        ▼                                            │
  ② 자동 분석 (활성 룰 = 해당 로그유형 + ALL) ◀───────┘
        │   - SHA-256 해시 저장, 원본 GZIP 보관
        ▼
  ③ 탐지 결과 생성 (라인 단위, 위험도별 집계)
        │
        ▼
  ④ 탐지 건 검토/조치 등록 (확인/조치완료/오탐)  ◀── 담당자 또는 보안팀
        │
        ▼
  ⑤ 최종 검토완료 (미검토 0건일 때만 가능)    ◀── 정보보안팀 관리자
        │
        ▼
  ⑥ 월별 모니터링 요약 (업로드 현황, 위험도, TOP 룰, 미검토 건수)
```

## 2. 주요 기능

| 구분 | 기능 | 설명 |
|---|---|---|
| 룰셋 편집 | 룰 목록/상세/등록/수정/삭제 | 키워드(쉼표 구분) 또는 정규식, 위험도 4단계, 임계 건수, 로그 유형별 적용 |
| | 사용여부 토글 | 룰을 삭제하지 않고 일시 중지 |
| | 패턴 사전 테스트 | 샘플 로그를 붙여 넣어 매칭 결과를 미리 확인 (저장 안 함) |
| 로그 업로드 | 파일 업로드 + 즉시 분석 | `.log/.txt/.csv/.json/.out`, 최대 50MB, UTF-8/EUC-KR/MS949 |
| | 중복 업로드 차단 | 같은 월·시스템에 같은 해시의 파일을 다시 올리면 거부 |
| | 원본 보관 | GZIP 압축으로 DB에 보관하여 증적 보존과 재분석에 사용 |
| | 재분석 | 룰셋을 변경한 뒤 현재 룰로 다시 분석 (기존 결과는 소프트 삭제) |
| 탐지 검토 | 탐지 목록/상세 | 위험도·검토상태 필터, 라인 번호, 원문 |
| | 단건/일괄 검토 | 미검토 → 이상징후 확인 / 조치완료 / 오탐, 조치 내용 기록 |
| | 최종 검토완료/취소 | 정보보안팀 관리자 전용, 검토완료 후에는 수정·재분석 잠금 |
| 모니터링 | 월별 조회 | `targetYm=YYYYMM` 기준 목록·요약·TOP 10 룰 |

### 탐지 엔진 동작 기준

- **KEYWORD**: 쉼표로 구분한 키워드 중 하나라도 포함되면 매칭합니다. 대소문자는 구분하지 않습니다.
- **REGEX**: Java 정규식 `find()`로 매칭합니다. 대소문자는 구분하지 않습니다.
- **임계 건수(THRESHOLD_COUNT)**: 파일 안의 매칭 건수가 임계값 이상일 때만 탐지로 확정합니다. 예: 로그인 실패 10회 이상.
- **안전장치**
  - ReDoS 방지: 정규식 매칭을 라인당 200ms로 제한하고, 매칭에는 라인 앞 4,000자만 사용합니다.
  - 저장 상한: 룰당 최대 1,000건, 업로드당 최대 20,000건까지 저장합니다. 집계 건수는 저장된 건수 기준입니다.
  - 바이너리 파일 차단: 파일 앞부분에 NUL 바이트가 있으면 거부합니다.
  - 경로 조작 방지: 업로드 파일명에서 디렉토리 경로를 제거합니다.
- **스냅샷**: 탐지 결과에는 탐지 시점의 룰 코드, 룰명, 위험도를 저장합니다. 이후 룰을 수정해도 탐지 이력은 바뀌지 않습니다.

## 3. 생성 테이블 목록

| 테이블 | 설명 | 주요 컬럼 | FK (업무 테이블 간) |
|---|---|---|---|
| `sec_log_rule` | 이상징후 탐지 룰셋 | RULE_CODE(UK), LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN | - |
| `sec_log_upload` | 업로드 이력 + 분석 요약 | TARGET_YM, SYSTEM_NAME, LOG_TYPE, FILE_HASH, 위험도별 건수, STATUS, REVIEW_COMMENT | - |
| `sec_log_upload_file` | 원본 로그 (GZIP, LONGBLOB) | UPLOAD_ID, FILE_DATA | `FK_SEC_LOG_UPLOAD_FILE_UPLOAD` → sec_log_upload |
| `sec_log_detection` | 탐지 결과 (라인 단위) | UPLOAD_ID, RULE_ID, SEVERITY, LINE_NUMBER, LOG_CONTENT, REVIEW_STATUS, ACTION_CONTENT | `FK_SEC_LOG_DETECTION_UPLOAD`, `FK_SEC_LOG_DETECTION_RULE` |

- 모든 테이블에 공통 컬럼(CREATED_AT/BY, UPDATED_AT/BY, DELETED_YN/AT/BY)이 있습니다.
- core 테이블(core_user 등)에는 FK가 없고, 사용자 ID 값만 저장합니다.
- 원본 파일(LONGBLOB)은 목록 조회 성능을 위해 별도 테이블로 분리했습니다.

### 코드값

| 구분 | 값 |
|---|---|
| LOG_TYPE | `ALL`(룰 전용), `LINUX`, `WINDOWS`, `WEB`, `WAS`, `DB`, `NETWORK`, `APP`, `ETC` |
| MATCH_TYPE | `KEYWORD`, `REGEX` |
| SEVERITY | `LOW`(낮음), `MEDIUM`(보통), `HIGH`(높음), `CRITICAL`(심각) |
| 업로드 STATUS | `ANALYZED`(분석완료/검토대기), `REVIEWED`(검토완료) |
| REVIEW_STATUS | `PENDING`(미검토), `CONFIRMED`(이상징후 확인), `RESOLVED`(조치완료), `FALSE_POSITIVE`(오탐) |

## 4. API 목록

모든 응답은 `ApiResponse<T>`로 감싸서 반환합니다. 페이징 응답은 `Page<T>`입니다.

### 4-1. 룰셋 편집 (`/security_log-api/rules`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/rules?q=&logType=&useYn=&page=0&size=50` | 룰 목록 (룰셋 편집 화면) | ADMIN, MANAGER, USER |
| GET | `/security_log-api/rules/{ruleId}` | 룰 상세 | ADMIN, MANAGER, USER |
| POST | `/security_log-api/rules` | 룰 등록 | ADMIN |
| PUT | `/security_log-api/rules/{ruleId}` | 룰 수정 (RULE_CODE 변경 불가) | ADMIN |
| PATCH | `/security_log-api/rules/{ruleId}/use-yn` | 사용여부 변경 `{ "useYn": "N" }` | ADMIN |
| POST | `/security_log-api/rules/test` | 패턴 사전 테스트 (저장 안 함) | ADMIN |
| DELETE | `/security_log-api/rules/{ruleId}` | 룰 삭제 (소프트) | ADMIN |

### 4-2. 로그 업로드 / 모니터링 (`/security_log-api/uploads`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/uploads?targetYm=202610&systemName=&logType=&status=&maxSeverity=&page=0&size=50` | **목록 화면 (월별 조회)** | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/summary?targetYm=202610` | 월별 모니터링 요약 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/{uploadId}` | **상세 화면** (요약 + 룰별 집계 + 검토현황) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/uploads` (multipart) | **등록 화면** — 업로드 + 즉시 분석 | ADMIN, MANAGER |
| PUT | `/security_log-api/uploads/{uploadId}` | **수정 화면** — 기본정보 수정 (옵션: 재분석) | ADMIN, MANAGER |
| POST | `/security_log-api/uploads/{uploadId}/reanalyze` | 현재 룰셋으로 재분석 | ADMIN, MANAGER |
| GET | `/security_log-api/uploads/{uploadId}/file` | 원본 로그 (Base64) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/uploads/{uploadId}/review` | 최종 검토완료 `{ "reviewComment": "..." }` | ADMIN |
| POST | `/security_log-api/uploads/{uploadId}/review-cancel` | 최종 검토완료 취소 | ADMIN |
| DELETE | `/security_log-api/uploads/{uploadId}` | 삭제 (업로드 + 원본 + 탐지결과 소프트 삭제) | ADMIN |

**업로드 요청 예시 (multipart/form-data)**

| 파트 | 필수 | 예시 |
|---|---|---|
| `file` | O | `web01_secure_202610.log` |
| `targetYm` | O | `202610` |
| `systemName` | O | `그룹웨어` |
| `logType` | O | `LINUX` (`ALL`은 사용 불가) |
| `fileCharset` | - | `UTF-8` (기본값) / `EUC-KR` / `MS949` |
| `remark` | - | `10월 정기 점검분` |

### 4-3. 탐지 결과 검토

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/uploads/{uploadId}/detections?severity=&reviewStatus=&page=0&size=50` | 탐지 목록 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/detections/{detectionId}` | 탐지 상세 | ADMIN, MANAGER, USER |
| PUT | `/security_log-api/uploads/{uploadId}/detections/review` | 단건/일괄 검토 (최대 1,000건) | ADMIN, MANAGER |

```json
{ "detectionIds": [101, 102, 103], "reviewStatus": "FALSE_POSITIVE", "actionContent": "정기 배치 계정의 정상 접속" }
```

### 4-4. 업무 규칙 (BusinessException 발생 조건)

| 조건 | 결과 |
|---|---|
| 같은 월·시스템에 같은 해시의 파일을 다시 업로드 | `DUPLICATE_RESOURCE` (409) "동일 월/시스템에 같은 내용의 로그 파일이 이미 업로드되어 있습니다." |
| 허용되지 않은 확장자, 50MB 초과, 바이너리 파일 | `INVALID_INPUT_VALUE` (400) + 사유 메시지 |
| 정규식 문법 오류 / 빈 키워드 | `INVALID_INPUT_VALUE` (400) "정규식 문법 오류: ..." |
| RULE_CODE 중복 (삭제된 코드 재사용 포함) | `DUPLICATE_RESOURCE` (409) |
| 검토완료 건의 수정·재분석·탐지 검토 | `INVALID_INPUT_VALUE` (400) "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요." |
| 미검토(PENDING) 탐지가 남아 있는 상태에서 최종 검토완료 | `INVALID_INPUT_VALUE` (400) "미검토 탐지 건이 N건 남아 있어 검토완료할 수 없습니다." |
| 삭제되었거나 없는 ID | `RESOURCE_NOT_FOUND` (404) |
| 권한 없음 / 비로그인 | 403 / 401 (core 공통 처리) |

## 5. 메뉴 등록 요청 정보

메뉴 seed SQL은 작성하지 않았습니다. 운영자가 플랫폼 **메뉴관리 / 접근 권한** 화면(또는 `MODULE_GUIDE.md` 3장의 `core_menu`, `core_role_menu` 등록 절차)에서 아래 정보로 등록해 주세요.

| 메뉴명 | 메뉴 위치 | 메뉴 URL (프론트) | 사용 API | 접근 역할 |
|---|---|---|---|---|
| 보안로그 관리 (상위) | 정보보안 > 보안로그 관리 | (그룹, URL 없음) | - | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 목록 | 정보보안 > 보안로그 관리 > 로그 업로드 목록 | `/security_log/index.html` | `GET /security_log-api/uploads`, `/summary` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 등록 | (목록 화면 버튼, 메뉴 비노출) | `/security_log/upload.html` | `POST /security_log-api/uploads` | ROLE_ADMIN, ROLE_MANAGER |
| 로그 업로드 상세 | (목록 화면 행 클릭, 메뉴 비노출) | `/security_log/detail.html?id={id}` | `GET /uploads/{id}`, `/detections` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 수정 | (상세 화면 버튼, 메뉴 비노출) | `/security_log/edit.html?id={id}` | `PUT /security_log-api/uploads/{id}` | ROLE_ADMIN, ROLE_MANAGER |
| 탐지 룰셋 편집 | 정보보안 > 보안로그 관리 > 탐지 룰셋 편집 | `/security_log/rules.html` | `/security_log-api/rules/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |

### 5-1. 화면 파일 (모듈에 포함)

`module-security_log/src/main/resources/static/security_log/` 에 있으며, 앱 jar 에 포함되어 `/security_log/**` URL 로 서빙됩니다.
module-safety 와 같은 방식으로 플랫폼 SPA 안에서 iframe 으로 열리며, 플랫폼 로그인 세션(`localStorage.fireweb_user` 의 JWT)을 그대로 사용합니다.

| 화면 | 파일 | 역할별 동작 |
|---|---|---|
| 목록 (월별 조회 + 요약 + TOP 10 룰) | `index.html` | 전원 조회. `로그 업로드` 버튼은 ADMIN/MANAGER 만 표시 |
| 등록 (파일 업로드 + 즉시 분석) | `upload.html` | ADMIN/MANAGER. USER 는 권한 없음 안내 |
| 상세 (요약, 룰별 집계, 검토 현황, 탐지 목록, 일괄 검토) | `detail.html` | 검토 저장은 ADMIN/MANAGER. 최종 검토완료·취소·삭제는 ADMIN |
| 수정 (기본정보, 재분석) | `edit.html` | ADMIN/MANAGER. 검토완료 건은 잠김 |
| 탐지 룰셋 편집 (목록, 등록·수정, 사용여부, 패턴 테스트) | `rules.html` | 전원 조회. 편집은 ADMIN |
| 공통 스크립트 / 스타일 | `js/seclog.js`, `css/seclog.css` | - |

> 화면의 버튼 숨김은 사용성용입니다. 실제 권한은 API 의 `@PreAuthorize` 로 서버에서 막습니다.

### 5-2. 화면을 플랫폼에 띄우려면 필요한 core/app 작업 (**core·app 에 추가 필요**)

업무 모듈 규칙상 core·app 을 수정하지 않았기 때문에, 아래 2가지가 반영되기 전에는 운영에서 화면이 열리지 않습니다.
(미리보기 환경에서는 같은 내용을 임시로 적용해 동작을 확인했으며, 그 수정은 커밋하지 않았습니다.)

**① core `SecurityConfig` 공개 경로 추가** — iframe 진입은 Authorization 헤더를 보낼 수 없어 지금은 401 이 납니다. 데이터 API(`/security_log-api/**`)는 계속 JWT 로 보호됩니다.
```java
// core/src/main/java/com/company/core/config/SecurityConfig.java 의 기존 permitAll 목록에 추가
.requestMatchers("/security_log/**").permitAll()   // 화면 (플랫폼 SPA 안에서 iframe 로드)
```

**② app `static/index.html` 에 iframe 라우팅 추가** — 플랫폼 SPA 는 `/kims/`, `/safety/` 로 시작하는 메뉴만 iframe 으로 엽니다. `/security_log/` 도 같은 방식으로 열도록 추가해야 합니다. (`isSafetyPage` 처리와 같은 패턴. `menuUrl` 이 `/security_log/` 로 시작하면 `navigateToSafetyPage(menuUrl)` 와 같은 iframe 로더로 열기)

**③ 메뉴 등록** — 아래 값으로 `core_menu` / `core_role_menu` 에 등록합니다 (`MODULE_GUIDE.md` 3장 절차).

| MENU_CODE | 메뉴명 | 상위 | MENU_URL | 접근 역할 |
|---|---|---|---|---|
| `SECLOG_MGMT` | 보안로그 관리 | (최상위 그룹) | (없음) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_UPLOAD` | 로그 업로드 목록 | SECLOG_MGMT | `/security_log/index.html` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_RULE` | 탐지 룰셋 편집 | SECLOG_MGMT | `/security_log/rules.html` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |

등록·상세·수정 화면은 목록 화면 안에서 이동하므로 메뉴로 등록하지 않습니다.

## 6. 권한 (core 역할 매핑 — 매핑안 A 확정)

별도 권한 코드를 만들지 않고, core의 기존 역할(`core_user_role`)을 그대로 사용합니다. Controller에서는 `@PreAuthorize("hasRole(...)")` / `hasAnyRole(...)`로 제어합니다.

| 요구사항 역할 | core 역할 | 사용 가능 기능 |
|---|---|---|
| 관리자 | `ROLE_ADMIN` | 전체 기능 (룰셋 편집, 최종 검토완료/취소, 삭제 포함) |
| 일반 사용자 | `ROLE_MANAGER` | 조회 + 로그 업로드(등록), 업로드 정보 수정, 재분석, 탐지 건 검토/조치 등록 |
| 조회 사용자 | `ROLE_USER` | 조회만 (목록, 요약, 상세, 탐지 목록, 룰 목록, 원본 파일) |

| 기능 | ADMIN | MANAGER | USER | `@PreAuthorize` |
|---|:---:|:---:|:---:|---|
| 조회 (업로드/탐지/룰/요약/원본) | O | O | O | `hasAnyRole('ADMIN', 'MANAGER', 'USER')` |
| 업로드 등록 / 수정 / 재분석 / 탐지 검토 | O | O | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 룰셋 등록·수정·사용여부·테스트·삭제 | O | - | - | `hasRole('ADMIN')` |
| 최종 검토완료 / 검토취소 | O | - | - | `hasRole('ADMIN')` |
| 업로드 삭제 | O | - | - | `hasRole('ADMIN')` |

> 사용자 ID는 다른 업무 모듈과 같은 방식으로 Controller에서 `@AuthenticationPrincipal(expression = "userId") Long userId`로 받아 Service에 전달합니다. core의 `CustomUserDetails.userId`를 사용합니다.

## 7. SQL 실행 순서

| 순서 | 파일 | 내용 | 재실행 |
|---|---|---|---|
| 1 | `01_schema.sql` | 테이블 4개 생성 (rule → upload → upload_file → detection) | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 2 | `02_seed_data.sql` | 기본 탐지 룰 19개 | 가능 (`INSERT IGNORE` + UK) |

```bash
mysql -u <user> -p <database> < sql/module-security_log/01_schema.sql
mysql -u <user> -p <database> < sql/module-security_log/02_seed_data.sql
```

- 두 스크립트 모두 맨 앞에서 `SET NAMES utf8mb4` 를 실행하므로, 접속 클라이언트 문자셋과 관계없이 한글 COMMENT/데이터가 깨지지 않습니다.
- `02_seed_data.sql`의 정규식은 SQL 문자열 안에서 역슬래시를 `\\`로 이스케이프했습니다. DB 세션에 `NO_BACKSLASH_ESCAPES` SQL 모드가 **설정되어 있지 않아야** 정상적으로 저장됩니다.
- 운영자가 이미 수정한 룰은 seed를 다시 실행해도 덮어쓰지 않습니다.
- 기본 룰 19개: 공통 6, Linux 3, Windows 2, Web 5, DB 2, Network 1. 운영 환경의 로그 형식에 맞게 룰셋 편집 화면에서 조정하세요.

## 8. 운영 반영 시 필요한 설정

### 8-1. Gradle 등록 (플랫폼이 수동 등록 방식인 경우, 운영자 작업)

```groovy
// settings.gradle
include 'module-security_log'

// app/build.gradle
implementation project(':module-security_log')
```

### 8-2. 플랫폼 app 설정에 추가 필요

| 설정 | 현재 저장소 상태 | 조치 |
|---|---|---|
| `spring.servlet.multipart.max-file-size` / `max-request-size` | 100MB / 110MB | 추가 작업 없음 (50MB 이상 충족) |
| `@EnableMethodSecurity` | core `SecurityConfig`에 이미 적용 | 추가 작업 없음 |
| 자동 스캔 (`com.company.module`) | `PlatformApplication`에 이미 적용 | 추가 작업 없음 |
| `/security_log-api/**` 인증 | `anyRequest().authenticated()`로 인증 필요 | 추가 작업 없음 |
| Reverse Proxy (Apache/Nginx) 업로드 크기·타임아웃 | 서버 설정 확인 필요 | 업로드 55MB 이상, 타임아웃 120초 이상 권장 |
| MariaDB `max_allowed_packet` | 서버 설정 확인 필요 | 64M 이상 권장 (원본 LONGBLOB 저장) |

### 8-3. 운영 고려사항

- 분석은 업로드 요청 안에서 **동기로 처리**합니다. 50MB, 약 50만 라인 기준으로 수 초에서 수십 초가 걸릴 수 있습니다. 더 큰 파일이 필요하면 비동기 처리 도입을 검토하세요.
- 원본 로그는 DB(LONGBLOB)에 보관하므로 DB 용량을 모니터링해야 합니다. 보관 기간 정책이 정해지면 소프트 삭제 후 별도 아카이빙 절차를 마련하세요.
- 로그에 개인정보가 포함될 수 있으므로, 원본 조회 API(`/file`) 접근 권한을 운영 정책에 맞게 다시 검토하세요.

## 9. core 연동 확인 결과 및 추가 필요 사항

`kleannara-ax/ai_platform` 저장소의 실제 core와 대조해 확인한 결과입니다.

| 항목 | 사용 방식 | 상태 |
|---|---|---|
| 사용자 ID | `@AuthenticationPrincipal(expression = "userId")` (core `CustomUserDetails`) | 확인 완료 |
| `ApiResponse.success(T)`, `success()`, `created(T)` | 그대로 사용 | 확인 완료 |
| `ErrorCode.INVALID_INPUT_VALUE`, `RESOURCE_NOT_FOUND`, `DUPLICATE_RESOURCE`, `INTERNAL_SERVER_ERROR` | 그대로 사용 | 확인 완료 |
| `BusinessException(ErrorCode, String message)` | 상세 오류 메시지에 사용 | 확인 완료 |
| 사용자명 표시 | 응답에는 CREATED_BY/REVIEWED_BY 사용자 ID만 들어 있습니다. 이름을 표시하려면 **core 사용자 조회 Provider 또는 API 필요** | 필요 시 |
| 파일 다운로드 응답 | 규칙상 `ApiResponse`로 감싸야 해서 원본을 Base64로 반환합니다 | 협의 필요 |
| 데이터 범위 권한 | 일반 사용자가 "자기 시스템"만 보게 하려면 사용자-시스템 매핑 정보가 **core에 추가 필요**. 현재는 조회 권한자 전체 조회 | 필요 시 |

## 10. API URL 표기 관련 참고

표준 규칙 `/{모듈명}-api`를 그대로 적용해 `/security_log-api`로 만들었습니다. URL에 밑줄(`_`)이 들어갑니다.
플랫폼 URL 규칙이 kebab-case를 요구하면 Controller 3개의 `@RequestMapping` prefix만 `/security-log-api`로 바꾸면 됩니다. 다른 코드는 수정할 필요가 없습니다.

## 11. 생성 파일 목록

```
module-security_log/
├── build.gradle
└── src/main/java/com/company/module/security_log/
    ├── controller/  SecLogRuleController, SecLogUploadController, SecLogDetectionController
    ├── dto/         RuleSaveRequest, RuleUseYnRequest, RuleTestRequest, LogUploadRequest,
    │                LogUploadUpdateRequest, UploadReviewRequest, DetectionReviewRequest,
    │                RuleResponse, RuleTestResponse, LogUploadResponse, LogUploadDetailResponse,
    │                DetectionResponse, MonthlySummaryResponse, RuleHitCount, OriginalFileResponse
    ├── entity/      SecLogRule, SecLogUpload, SecLogUploadFile, SecLogDetection,
    │                LogType, MatchType, Severity, UploadStatus, ReviewStatus (enum)
    ├── repository/  SecLogRuleRepository, SecLogUploadRepository,
    │                SecLogUploadFileRepository, SecLogDetectionRepository
    └── service/     SecLogRuleService, SecLogUploadService, SecLogDetectionService,
                     LogAnalysisEngine, SecLogConstants
module-security_log/src/main/resources/static/security_log/
├── index.html  upload.html  detail.html  edit.html  rules.html
├── js/seclog.js
└── css/seclog.css
sql/module-security_log/
├── 01_schema.sql
├── 02_seed_data.sql
└── README.md
```

## 12. 자체 검수 체크리스트 결과

**검증 환경**
- 실제 저장소 빌드: `kleannara-ax/ai_platform` main(`de60b2f`)에 임시로 모듈을 포함해 `./gradlew :module-security_log:compileJava :app:bootJar`를 실행했고 성공했습니다. 이 임시 등록은 커밋하지 않았습니다.
- 화면 E2E: 실제 브라우저(Chromium)로 플랫폼에 로그인해 메뉴에서 iframe 으로 열고, 업로드 → 일괄 검토 → 수정 → 최종 검토완료 → 룰 테스트·등록까지 클릭으로 확인했습니다. ADMIN/MANAGER/USER 별 버튼 노출이 의도대로였고 화면 스크립트 오류는 없었습니다.
- E2E: 로컬 MariaDB에 core 스키마와 이 모듈 SQL을 적용한 뒤 앱을 기동하고, ADMIN/MANAGER/USER 계정으로 로그인해 28개 시나리오를 확인했습니다. 권한 허용·403, 업로드·분석, 중복 409, 검토 흐름, 재분석, 원본 일치, 룰 CRUD, 소프트 삭제, 비로그인 401 모두 기대대로 동작했고, Hibernate 매핑 오류는 없었습니다.
- SQL: MariaDB 11.8에서 `01`, `02`를 2회 연속 실행해 정상 동작을 확인했습니다. 컬럼 COMMENT 누락 0건이고, Entity `@Column`과 DDL 컬럼이 4개 테이블 모두 100% 일치합니다.
- 탐지 엔진: seed 룰 19개 정규식 컴파일 성공. 샘플 로그 11라인에서 9개 룰이 기대대로 탐지되었고, 임계 건수(5건 이상)도 확인했습니다. ReDoS 패턴 `(a+)+$`는 203ms에서 안전하게 중단됩니다.

| 번호 | 검수 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | User/Auth/Role/Menu/Security 관련 클래스가 생성되지 않았는가? | 통과 | 클래스명 자동 스캔 결과 0건 |
| 2 | SecurityConfig, JwtProvider, AuthController가 생성되지 않았는가? | 통과 | |
| 3 | application.yml, application.properties가 생성되지 않았는가? | 통과 | 설정값은 `SecLogConstants` 상수 + README 8-2 |
| 4 | Dockerfile, docker-compose.yml, Nginx 설정이 생성되지 않았는가? | 통과 | |
| 5 | SpringBootApplication main class가 생성되지 않았는가? | 통과 | bootJar 없음, `jar { enabled = true }` |
| 6 | API URL이 /{모듈명}-api/** 규칙을 지키는가? | 통과 | `/security_log-api/**` (10장 참고) |
| 7 | 금지 URL /api/**, /admin/**, /auth/** 등을 사용하지 않았는가? | 통과 | |
| 8 | Entity에 @Setter가 없는가? | 통과 | |
| 9 | Entity에 @Data가 없는가? | 통과 | |
| 10 | Service에서 entity.setXxx(...)를 사용하지 않았는가? | 통과 | `update()/review()/completeReview()/applyAnalysisResult()/delete()` 등 비즈니스 메서드만 사용 |
| 11 | 모든 Entity 컬럼에 @Column(name = "UPPER_SNAKE_CASE")가 있는가? | 통과 | 필드 77개 = @Column 77개 |
| 12 | 모든 업무 테이블에 공통 컬럼이 포함되어 있는가? | 통과 | 4개 테이블 × 7개 공통 컬럼 |
| 13 | 삭제 기능이 DELETED_YN 기반 소프트 삭제로 구현되어 있는가? | 통과 | 일괄 삭제도 `UPDATE ... SET DELETED_YN='Y' WHERE UPLOAD_ID=?` |
| 14 | 모든 테이블과 컬럼에 COMMENT가 있는가? | 통과 | information_schema로 확인, 누락 0건 |
| 15 | SQL에 DROP TABLE이 없는가? | 통과 | 룰 패턴 문자열 안의 `drop` 키워드는 탐지용 데이터 |
| 16 | SQL에 TRUNCATE TABLE이 없는가? | 통과 | 위와 동일 (탐지 패턴 데이터에만 존재) |
| 17 | SQL에 무조건 DELETE가 없는가? | 통과 | DELETE 문 없음 |
| 18 | SQL에 ALTER TABLE DROP COLUMN이 없는가? | 통과 | |
| 19 | core_user, core_menu, core_role 등 core 테이블에 FK를 생성하지 않았는가? | 통과 | FK 3개 모두 업무 테이블 간 |
| 20 | Controller 응답이 ApiResponse<T>로 감싸져 있는가? | 통과 | 20개 엔드포인트 전체 |
| 21 | Entity를 Controller에서 직접 반환하지 않는가? | 통과 | `from(Entity)` Response DTO 변환 |
| 22 | RuntimeException, IllegalArgumentException을 직접 throw하지 않는가? | 통과 | 엔진 내부 private 정규식 타임아웃 신호 클래스는 외부로 전파되지 않음 |
| 23 | ddl-auto에 의존하지 않고 SQL DDL을 제공했는가? | 통과 | |
| 24 | core 모듈을 수정하지 않았는가? | 통과 | 실제 core API(`CustomUserDetails`, `ErrorCode`)에 맞춤 |
| 25 | app 모듈을 수정하지 않았는가? | 통과 | settings.gradle, app/build.gradle 미수정 |
| 26 | 메뉴/권한 등록 정보가 README.md에 작성되어 있는가? | 통과 | 5장, 6장 |
| 27 | 필요한 core 기능이 부족한 경우 README.md에 "core에 추가 필요"로 명시했는가? | 통과 | 9장 (실제 core 대조 결과 반영) |
