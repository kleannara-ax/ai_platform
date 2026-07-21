# module-dailyreport SQL 스크립트

## 모듈 설명
**세부공장일보** — 4개 표 + 특이사항 + 이미지 첨부 기반 공장일보 관리 모듈

HTML 원본에서 추출한 320개 셀 데이터, 8명 담당자의 소유권(OWNER_IDS) 및 입력 주기(FREQ_CODE) 포함.

### ★ Phase 4: AI 플랫폼 통합
- 플랫폼 코어 테이블(`core_user`, `core_menu`, `core_menu_permission`) 스텁 포함
- 3계층 권한 모델: 메뉴 접근(1계층) → 셀 접근(2계층) → 관리 페이지 접근(3계층)
- 신규 테이블: `daily_report_cell_auth` (JSON 좌표 기반 셀 단위 접근 권한)
- 레거시 `daily_report_cell_permission` 대체

---

## 파일 목록 및 실행 순서

| 순서 | 파일명 | 설명 | 환경 |
|:----:|--------|------|------|
| 0 | `00_create_database.sql` | DB 생성 + 전용 사용자 생성 + 권한 부여 | **자체 테스트만** (root로 실행) |
| 1 | `01_schema.sql` | DDL — 9 테이블 + 인덱스 + 제약조건 (플랫폼 스텁 3개 포함) | 공통 |
| 2 | `02_seed_data.sql` | 9명 사용자 + 메뉴 3건 + 메뉴권한 10건 + 셀권한 9건 + 3일보 + 320셀 | 개발/테스트 |
| 3 | `03_verify.sql` | 설치 검증 — 13개 섹션 (건수/구조/권한/데이터) | 개발/테스트 |
| - | `99_drop_all.sql` | 전체 초기화 (10 테이블 삭제, FK 안전 순서) | 개발 전용 |

---

## 빠른 시작 (자체 독립 테스트)

> **전제 조건**: MariaDB 10.11+ 설치 완료

```bash
# ① DB + 사용자 생성 (root 권한)
mysql -u root -p < sql/module-dailyreport/00_create_database.sql

# ② 스키마 생성 (core 스텁 + 모듈 테이블)
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/01_schema.sql

# ③ 테스트 데이터 삽입 (메뉴/권한/셀권한/320셀/9사용자)
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/02_seed_data.sql

# ④ 설치 검증
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/03_verify.sql
```

### 접속 정보 (자체 테스트용)
| 항목 | 값 |
|------|-----|
| DB명 | `dailyreport_dev` |
| 사용자 | `factory_admin` |
| 비밀번호 | `Factory2024!` |
| 문자셋 | `utf8mb4 / utf8mb4_general_ci` |

### Spring Boot application.yml (자체 테스트용)
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/dailyreport_dev?useUnicode=true&characterEncoding=utf8mb4
    username: factory_admin
    password: Factory2024!
    driver-class-name: org.mariadb.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MariaDBDialect
    open-in-view: false
```

---

## 플랫폼 통합 시 실행 방법

> 플랫폼에는 이미 `core_user`, `core_menu`, `core_menu_permission` 테이블이 존재합니다.

```bash
# ① 스키마만 실행 (core_* 테이블은 IF NOT EXISTS로 건너뜀)
mysql -u {user} -p {platform_database} < sql/module-dailyreport/01_schema.sql

# ② 모듈 데이터만 삽입 (메뉴/권한/셀권한 INSERT IGNORE)
mysql -u {user} -p {platform_database} < sql/module-dailyreport/02_seed_data.sql
```

`00_create_database.sql`은 실행하지 않습니다.

**주의사항:**
- `core_menu`에 3건 추가됨 (MENU_ID 100, 101, 102)
- `core_menu_permission`에 10건 추가됨 (기존 플랫폼 데이터와 MENU_ID 충돌 확인 필요)
- MENU_ID는 플랫폼 환경에 맞게 조정해야 할 수 있음

---

## 초기화 (재설치)

```bash
# 전체 삭제 (10 테이블, FK 안전 순서)
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/99_drop_all.sql

# 재설치
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/01_schema.sql
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/02_seed_data.sql
mysql -u factory_admin -p'Factory2024!' dailyreport_dev < sql/module-dailyreport/03_verify.sql
```

---

## 테이블 구조 (9개 + 1 레거시)

### 플랫폼 코어 스텁 (3개 — `IF NOT EXISTS`)

| 테이블명 | 설명 | Phase 4 추가 |
|----------|------|:---:|
| `core_user` | 사용자 마스터 (BCrypt PASSWORD, IS_ACTIVE) | ★ |
| `core_menu` | 메뉴 계층 (CATEGORY → PAGE) | ★ |
| `core_menu_permission` | 사용자별 메뉴 접근 권한 (READ/WRITE/DELETE/ADMIN) | ★ |

### 모듈 테이블 (6개)

| 테이블명 | 설명 | 비고 |
|----------|------|------|
| `daily_report` | 일보 마스터 | 날짜별 1건 (UK: REPORT_DATE) |
| `daily_report_table` | 표 메타 (4개) | 생산지표, 재공품, 에너지, 보일러 |
| `daily_report_cell` | 셀 데이터 (320개) | EXCEL_COORD, OWNER_IDS, FREQ_CODE 포함 |
| `daily_report_cell_auth` | ★ 셀 접근 권한 (Phase 4 신규) | JSON 좌표 기반, USER_ID+TABLE_CODE UNIQUE |
| `daily_report_remark` | 특이사항 | 카테고리별 메모 |
| `daily_report_image` | 이미지 첨부 | 파일 메타 정보 관리 |

### 레거시 (1개)

| 테이블명 | 설명 | 비고 |
|----------|------|------|
| `daily_report_cell_permission` | 셀 편집 권한 (Phase 1-3) | `daily_report_cell_auth`로 대체됨. 99_drop에서 삭제됨 |

---

## ★ 3계층 권한 모델 (Phase 4)

```
┌─────────────────────────────────────────────────────────┐
│  1계층: 페이지 접근 (core_menu_permission)              │
│  "세부공장일보 입력" 페이지를 볼 수 있는가?             │
│  → MENU_ID=101, CAN_READ=1                             │
│  → AI 플랫폼 기존 접근권한 페이지에서 관리             │
├─────────────────────────────────────────────────────────┤
│  2계층: 셀 접근 (daily_report_cell_auth)                │
│  페이지 내 어떤 셀을 편집할 수 있는가?                  │
│  → USER_ID + TABLE_CODE + CELL_COORDS (JSON 배열)      │
│  → "세부공장일보 접근권한" 관리 페이지에서 관리         │
├─────────────────────────────────────────────────────────┤
│  3계층: 관리 페이지 접근 (core_menu_permission)         │
│  "세부공장일보 접근권한" 관리 페이지를 볼 수 있는가?    │
│  → MENU_ID=102, CAN_READ=1                             │
│  → AI 플랫폼 기존 접근권한 페이지에서 관리             │
└─────────────────────────────────────────────────────────┘
```

### 메뉴 계층 (core_menu)

| MENU_ID | PARENT | MENU_CODE | MENU_NAME | MENU_TYPE | URL |
|---------|--------|-----------|-----------|-----------|-----|
| 100 | NULL | DAILY_REPORT | 세부공장일보 | CATEGORY | - |
| 101 | 100 | DAILY_REPORT_INPUT | 세부공장일보 입력 | PAGE | /dailyreport/index.html |
| 102 | 100 | DAILY_REPORT_AUTH | 세부공장일보 접근권한 | PAGE | /dailyreport/cell-auth-admin.html |

### 메뉴 권한 배분 (core_menu_permission)

| 사용자 | MENU_ID=101 (입력) | MENU_ID=102 (접근권한) |
|--------|:--:|:--:|
| admin | READ+WRITE+ADMIN | READ+WRITE+ADMIN |
| kim~energy (8명) | READ+WRITE | - |

### 셀 접근 권한 (daily_report_cell_auth)

| USER_ID | LOGIN_ID | TABLE_CODE | CELL_COORDS | FREQ_CODE |
|---------|----------|------------|-------------|-----------|
| 2 | kim | TBL_INVENTORY | ["E21","E22","E23","E24"] | event |
| 3 | park | TBL_ENERGY | ["D39","D40","D41","F39","F40","F41"] | monthly |
| 3 | park | TBL_BOILER | ["K36"~"K40","L36"~"L40","O36"~"O40"] | monthly |
| 4 | yoo | TBL_PRODUCTION_INDEX | ["O9","O10","O11"] | event |
| 5 | jung | TBL_PRODUCTION_INDEX | ["E13","E14","O13","O14"] | event |
| 6 | jang | TBL_INVENTORY | ["M25","M26"] | monthly |
| 7 | lee | TBL_INVENTORY | ["M25","M26"] | monthly |
| 8 | choi | TBL_ENERGY | ["D36","D37","D38","F36","F37","F38"] | monthly |
| 9 | energy | TBL_BOILER | ["P36","P37","P38","P39","P40"] | daily |

---

## 4개 표 구성 (HTML 원본 기준)

| # | 코드 | 이름 | 행×열 | 입력 셀 | 담당자 |
|---|------|------|-------|---------|--------|
| 1 | `TBL_PRODUCTION_INDEX` | 주요 생산 지표 현황 | 10×15 | 7 | yoo(3), jung(4) |
| 2 | `TBL_INVENTORY` | 제지 재공품 및 야적현황 | 10×13 | 6 | kim(4), jang+lee(2) |
| 3 | `TBL_ENERGY` | 에너지 원단위 | 8×6 | 12 | choi(6), park(6) |
| 4 | `TBL_BOILER` | 보일러 운영 현황 | 7×8 | 20 | park(15), energy(5) |

**총 320셀, 45개 입력 가능(assignable)**

---

## 테스트 사용자 (9명)

| USER_ID | LOGIN_ID | 이름 | 역할 | 비밀번호 (BCrypt) |
|---------|----------|------|------|-------------------|
| 1 | admin | 관리자 | ADMIN | password123 |
| 2 | kim | 김완중 팀장 | USER | password123 |
| 3 | park | 박지권 책임 | USER | password123 |
| 4 | yoo | 유동현 책임 | USER | password123 |
| 5 | jung | 정상엽 책임 | USER | password123 |
| 6 | jang | 장석환 선임 | USER | password123 |
| 7 | lee | 이도형 사원 | USER | password123 |
| 8 | choi | 최민우 사원 | USER | password123 |
| 9 | energy | 환경에너지팀 반장 | USER | password123 |

---

## 셀 소유권 모델 (OWNER_IDS)

- `OWNER_IDS`: 공백 구분 로그인 ID (예: `"jang lee"`)
- `OWNER_NAMES`: 표시용 이름 (예: `"장석환 선임, 이도형 사원"`)
- `FREQ_CODE`: 입력 주기 (`daily`/`monthly`/`yearly`/`event`)
- `FREQ_LABEL`: 표시용 주기명 (예: `"매일"`, `"매월"`)

### 셀 편집 가능 판단 흐름 (Phase 4)

```
1순위: OWNER_IDS 기반 — 셀에 OWNER_IDS가 있으면 해당 사용자만 편집 가능
2순위: CellAuth 좌표 기반 — OWNER_IDS가 없는 DATA 셀도 cell_auth로 편집 허용
공통: isLocked=true → 편집 불가, 입력 주기 미충족 → 편집 불가
```

### 입력 주기 규칙

| freq_code | 설명 | 입력 시점 |
|-----------|------|----------|
| `daily` | 매일 | 항상 |
| `event` | 발생 시 | 항상 |
| `monthly` | 매월 | 매월 1일만 |
| `yearly` | 매년 | 매년 1월 1일만 |

---

## ER 관계 (Phase 4)

```
core_user (참조만, FK 미설정)
    │
    ├─ core_menu_permission.USER_ID    ← ★ Phase 4
    ├─ daily_report_cell_auth.USER_ID  ← ★ Phase 4
    ├─ daily_report.CREATED_BY / UPDATED_BY
    ├─ daily_report_cell.LAST_EDITOR_ID
    ├─ daily_report_remark.CREATED_BY
    └─ daily_report_image.UPLOADED_BY

core_menu (메뉴 계층)                 ← ★ Phase 4
    │
    └─ core_menu_permission.MENU_ID   ← ★ Phase 4

daily_report (1) ──── (N) daily_report_table (CASCADE)
                               │
                               └── (N) daily_report_cell (CASCADE)

daily_report (1) ──── (N) daily_report_remark (CASCADE)
daily_report (1) ──── (N) daily_report_image (CASCADE)

daily_report_cell_auth ── 독립 (USER_ID + TABLE_CODE UNIQUE)  ← ★ Phase 4
```

---

## 03_verify.sql 기대 결과 요약 (Phase 4)

| 섹션 | 항목 | 기대값 |
|------|------|--------|
| [1] | 테이블 존재 | 9개 (core 3 + module 6) |
| [2] | 사용자 수 | 9명 |
| [3] | 메뉴 계층 | 3건 (CATEGORY 1 + PAGE 2) |
| [4] | 입력 페이지 권한 | 9건 (admin + 8명 현업) |
| [5] | 접근권한 페이지 권한 | 1건 (admin만) |
| [6] | 셀 접근 권한 | 9건 (8명 × 담당 표) |
| [7] | 일보 수 | 3건 (7/18 CONFIRMED, 7/19 SUBMITTED, 7/20 DRAFT) |
| [8] | 표 메타 | 12건 (3일보 × 4표) |
| [9] | 셀 데이터 | 320건 (7/20 기준) |
| [10] | cell_auth ↔ OWNER_IDS 교차 검증 | 매칭 확인 |
| [11] | 특이사항/이미지 | 1건 / 2건 |
| [12] | 입력 가능 셀 | 45건 |
| [13] | 전체 요약 | 9 테이블 총 건수 |

---

## 일보 상태 흐름

```
DRAFT (작성중) ──→ SUBMITTED (제출) ──→ CONFIRMED (확정, 전체 셀 잠금)
                     │
                     └──→ DRAFT (반려/재작성)
```
