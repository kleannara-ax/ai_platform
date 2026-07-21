# module-dailyreport — 세부공장일보

## 개요

7월 공장일보(세부) 입력·조회 모듈. 엑셀 기반 공장일보를 웹 SPA로 전환하여,
AI 플랫폼 인증 + 3계층 권한 모델 + 셀 단위 접근 제어를 제공합니다.

- **Java 17 / Spring Boot 3.2.5 / Gradle multi-module** (plugin jar)
- **MariaDB 10.11+** (utf8mb4 / utf8mb4_general_ci)
- **JPA** (`ddl-auto=none`, `PhysicalNamingStrategyStandardImpl`)
- **프론트엔드**: 순수 HTML/CSS/JS SPA (2개 페이지)

---

## ★ Phase 4: AI 플랫폼 통합 — 3계층 권한 모델

### 1계층: 페이지 접근 (core_menu_permission)
- **목적**: "세부공장일보 입력" 페이지 자체에 접근할 수 있는가?
- **관리**: AI 플랫폼의 기존 접근권한 설정 페이지에서 관리
- **구현**: `MenuPermissionService.canAccessInputPage(userId)`

### 2계층: 셀 접근 (daily_report_cell_auth)
- **목적**: 페이지 내 어떤 셀을 편집할 수 있는가?
- **관리**: "세부공장일보 접근권한" 관리 페이지에서 관리 (cell-auth-admin.html)
- **구현**: `CellAuthService.hasCoordAccess(userId, tableCode, excelCoord)`

### 3계층: 관리 페이지 접근 (core_menu_permission)
- **목적**: "세부공장일보 접근권한" 관리 페이지에 접근할 수 있는가?
- **관리**: AI 플랫폼의 기존 접근권한 설정 페이지에서 관리
- **구현**: `MenuPermissionService.canAccessAuthPage(userId)`

### 인증 흐름

```
AI 플랫폼 로그인 (Spring Security)
    │
    ├─ 세부공장일보 입력 페이지 (/dailyreport/index.html)
    │   ├─ 1계층 검증: /my-permissions → canAccessInput?
    │   ├─ 데이터 로드: /view/render?reportDate=...
    │   ├─ 2계층 검증: 서버에서 cell.editable 계산 (OWNER_IDS + CellAuth)
    │   └─ 저장: /reports/{id}/cells (쓰기 권한 검증)
    │
    └─ 접근권한 관리 페이지 (/dailyreport/cell-auth-admin.html)
        ├─ 3계층 검증: /my-permissions → canAccessAuth?
        └─ CRUD: /cell-auths (관리자 권한 검증)
```

---

## 테이블 구조 (4개 표)

| # | 표 코드 | 표 이름 | 행×열 | 담당 입력 셀 |
|---|---------|---------|-------|-------------|
| 1 | `TBL_PRODUCTION_INDEX` | 주요 생산 지표 현황 | 10×15 | yoo(3), jung(4) = 7셀 |
| 2 | `TBL_INVENTORY` | 제지 재공품 및 야적현황 | 10×13 | kim(4), jang+lee(2) = 6셀 |
| 3 | `TBL_ENERGY` | 에너지 원단위 | 8×6 | choi(6), park(6) = 12셀 |
| 4 | `TBL_BOILER` | 보일러 운영 현황 | 7×8 | park(15), energy(5) = 20셀 |

**전체**: 320 셀, 그 중 45개가 담당자 입력 셀(assignable)

---

## 테스트 사용자 (9명)

| ID | 이름 | 1계층 | 2계층 (셀 권한) | 3계층 |
|----|------|:-----:|----------------|:-----:|
| `admin` | 관리자 | READ+WRITE+ADMIN | - | READ+WRITE+ADMIN |
| `kim` | 김완중 팀장 | READ+WRITE | TBL_INVENTORY: E21~E24 (event) | - |
| `park` | 박지권 책임 | READ+WRITE | TBL_ENERGY: D39~F41 (monthly) + TBL_BOILER: K36~O40 (monthly) | - |
| `yoo` | 유동현 책임 | READ+WRITE | TBL_PRODUCTION_INDEX: O9~O11 (event) | - |
| `jung` | 정상엽 책임 | READ+WRITE | TBL_PRODUCTION_INDEX: E13,E14,O13,O14 (event) | - |
| `jang` | 장석환 선임 | READ+WRITE | TBL_INVENTORY: M25,M26 (monthly) | - |
| `lee` | 이도형 사원 | READ+WRITE | TBL_INVENTORY: M25,M26 (monthly) | - |
| `choi` | 최민우 사원 | READ+WRITE | TBL_ENERGY: D36~F38 (monthly) | - |
| `energy` | 환경에너지팀 반장 | READ+WRITE | TBL_BOILER: P36~P40 (daily) | - |

**비밀번호**: `password123` (BCrypt 해시)

---

## API 엔드포인트

### ★ Phase 4 신규 — 뷰 렌더링 (인증 기반)
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/view/render?reportDate=2024-07-20` | 전체 렌더링 데이터 (세션 인증, 1계층 검증) |
| `GET` | `/dailyreport-api/view/my-permissions` | 현재 사용자의 메뉴 권한 확인 |

### ★ Phase 4 신규 — 셀 접근 권한 관리
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/cell-auths` | 전체 조회 (3계층 검증) |
| `GET` | `/dailyreport-api/cell-auths?userId=2` | 사용자별 조회 |
| `GET` | `/dailyreport-api/cell-auths?tableCode=TBL_XX` | 표별 조회 |
| `GET` | `/dailyreport-api/cell-auths/{authId}` | 단건 조회 |
| `POST` | `/dailyreport-api/cell-auths` | 등록 (관리자 권한) |
| `PUT` | `/dailyreport-api/cell-auths/{authId}` | 수정 (관리자 권한) |
| `PATCH` | `/dailyreport-api/cell-auths/{authId}/deactivate` | 비활성화 (관리자 권한) |
| `DELETE` | `/dailyreport-api/cell-auths/{authId}` | 삭제 (관리자 권한) |

### 일보 CRUD
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/reports` | 일보 목록 (기간/상태 필터, 페이징) |
| `GET` | `/dailyreport-api/reports/{id}` | 일보 상세 |
| `POST` | `/dailyreport-api/reports` | 일보 생성 (4표 자동 생성) |
| `PATCH` | `/dailyreport-api/reports/{id}/status` | 상태 변경 |
| `DELETE` | `/dailyreport-api/reports/{id}` | 일보 삭제 (DRAFT만) |

### 셀 데이터 (1계층 검증)
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/reports/{id}/cells?tableCode=TBL_XX` | 표별 셀 조회 (사용자별 editable) |
| `POST` | `/dailyreport-api/reports/{id}/cells` | 셀 일괄 저장 (쓰기 권한 검증) |
| `PATCH` | `/dailyreport-api/reports/{id}/cells/lock` | 주기별 셀 잠금 (관리자) |

### 특이사항
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/reports/{id}/remarks` | 특이사항 목록 |
| `POST` | `/dailyreport-api/reports/{id}/remarks` | 특이사항 추가 |
| `PUT` | `/dailyreport-api/reports/{id}/remarks/{remarkId}` | 특이사항 수정 |
| `DELETE` | `/dailyreport-api/reports/{id}/remarks/{remarkId}` | 특이사항 삭제 |

### 이미지 첨부
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/reports/{id}/images` | 이미지 목록 |
| `POST` | `/dailyreport-api/reports/{id}/images` | 이미지 업로드 |
| `DELETE` | `/dailyreport-api/reports/{id}/images/{imageId}` | 이미지 삭제 |

### 레거시 — 셀 권한 관리 (Phase 1-3)
| Method | URI | 설명 |
|--------|-----|------|
| `GET` | `/dailyreport-api/permissions` | 권한 조회 |
| `POST` | `/dailyreport-api/permissions` | 권한 추가 |
| `DELETE` | `/dailyreport-api/permissions/{id}` | 권한 삭제 |

---

## 프론트엔드 접근 경로

### 세부공장일보 입력 (1계층)
```
http://localhost:8080/dailyreport/index.html
```
- AI 플랫폼 로그인 후 자동 진입 (자체 로그인 화면 없음)
- `/my-permissions` 호출로 접근 권한 확인
- 날짜 선택 후 조회 → 해당 날짜 일보의 4개 표가 렌더링
- 본인 담당 셀만 파란색(mine)으로 하이라이팅
- 오늘 입력 가능한 주기 셀만 활성화 (나머지는 회색 잠금)
- **새로고침 버튼**: 다른 사용자가 입력한 값을 최신으로 갱신
- **자동 갱신**: 60초마다 자동으로 다른 사용자 입력값 갱신 (수정중에는 일시정지)
- **권한관리 버튼**: 3계층 권한이 있는 사용자에게만 표시

### 접근권한 관리 (3계층)
```
http://localhost:8080/dailyreport/cell-auth-admin.html
```
- 관리자만 접근 가능 (core_menu_permission MENU_ID=102)
- 사용자별 셀 접근 권한 CRUD (등록/수정/비활성화/삭제)
- 표별 필터링, 통계 대시보드
- 셀 좌표 태그 입력 (Enter/쉼표로 추가)

---

## 프로젝트 구조

```
module-dailyreport/
├── build.gradle
├── README.md
└── src/main/
    ├── java/com/company/module/dailyreport/
    │   ├── controller/
    │   │   ├── DailyReportController.java       # 일보 CRUD
    │   │   ├── CellController.java              # 셀 입력/조회 (★ Phase 4: 1계층 검증)
    │   │   ├── ViewRenderController.java        # 렌더링 API (★ Phase 4: 인증+권한)
    │   │   ├── CellAuthController.java          # ★ Phase 4: 셀 권한 CRUD
    │   │   ├── RemarkController.java            # 특이사항
    │   │   ├── ImageController.java             # 이미지 첨부
    │   │   └── CellPermissionController.java    # 레거시 권한 관리
    │   ├── dto/
    │   │   ├── DailyReportRequest.java / DailyReportResponse.java
    │   │   ├── CellResponse.java / CellSaveRequest.java
    │   │   ├── ReportTableResponse.java
    │   │   ├── RemarkRequest.java / RemarkResponse.java
    │   │   ├── ImageResponse.java
    │   │   ├── CellAuthRequest.java             # ★ Phase 4
    │   │   ├── CellAuthResponse.java            # ★ Phase 4
    │   │   └── CellPermissionRequest.java / CellPermissionResponse.java (레거시)
    │   ├── entity/
    │   │   ├── DailyReport.java
    │   │   ├── DailyReportTable.java
    │   │   ├── DailyReportCell.java (OWNER_IDS/FREQ_CODE)
    │   │   ├── DailyReportRemark.java / DailyReportImage.java
    │   │   ├── CellAuth.java                    # ★ Phase 4: JSON 좌표 기반
    │   │   ├── CoreMenu.java                    # ★ Phase 4: 읽기 전용
    │   │   ├── CoreMenuPermission.java          # ★ Phase 4: 읽기 전용
    │   │   └── CellPermission.java (레거시)
    │   ├── repository/
    │   │   ├── DailyReportRepository.java
    │   │   ├── DailyReportTableRepository.java
    │   │   ├── DailyReportCellRepository.java
    │   │   ├── DailyReportRemarkRepository.java / DailyReportImageRepository.java
    │   │   ├── CellAuthRepository.java          # ★ Phase 4
    │   │   ├── CoreMenuRepository.java          # ★ Phase 4
    │   │   ├── CoreMenuPermissionRepository.java # ★ Phase 4
    │   │   └── CellPermissionRepository.java (레거시)
    │   └── service/
    │       ├── DailyReportService.java
    │       ├── CellService.java                 # ★ Phase 4: CellAuth 기반 편집 로직
    │       ├── MenuPermissionService.java       # ★ Phase 4: 메뉴 접근 권한
    │       ├── CellAuthService.java             # ★ Phase 4: 셀 권한 CRUD
    │       └── CellPermissionService.java (레거시)
    └── resources/
        └── static/dailyreport/
            ├── index.html                       # ★ Phase 4: 플랫폼 인증 통합
            └── cell-auth-admin.html             # ★ Phase 4: 관리자 권한 관리

sql/module-dailyreport/
├── 00_create_database.sql    # DB/사용자 생성
├── 01_schema.sql             # DDL (9 테이블, core 스텁 3개 포함) ★ Phase 4
├── 02_seed_data.sql          # 메뉴/권한/셀권한/320셀/9사용자 ★ Phase 4
├── 03_verify.sql             # 검증 쿼리 (13개 섹션) ★ Phase 4
├── 99_drop_all.sql           # 전체 삭제 (10 테이블) ★ Phase 4
└── README.md                 # SQL 실행 가이드 ★ Phase 4
```

---

## 핵심 편집 로직 (CellService — Phase 4)

```
isCellEditableForUser(cell, loginId, cellAuth):
  1. cell.isLocked? → false (잠금된 셀 편집 불가)
  2. cell.isAssignable? (OWNER_IDS 존재)
     → cell.isOwnedBy(loginId)?
        → canEditByFrequency(cell.freqCode)? → true/false
        → false (비담당)
  3. cell.cellType == 'DATA' && cellAuth != null?
     → cellAuth.coversCoord(cell.excelCoord)?
        → canEditByFrequency(cellAuth.freqCode)? → true/false
  4. → false
```

---

## Phase 완료 현황

| Phase | 설명 | 상태 |
|-------|------|------|
| Phase 1 | Spring Boot 모듈 전체 구현 (Entity/DTO/Service/Controller/SQL) | ✅ 완료 |
| Phase 2 | Standalone DB 구축 스크립트 (생성/시드/검증/리셋) | ✅ 완료 |
| Phase 3 | HTML 원본 UI 매칭 (4표 재구조화, 소유권 모델, 프론트엔드) | ✅ 완료 |
| Phase 4 | AI 플랫폼 통합 (3계층 권한, 셀 권한 관리, 새로고침) | ✅ 완료 |

---

## Phase 4 변경 파일 목록

### 신규 파일 (11개)
| 파일 | 설명 |
|------|------|
| `entity/CellAuth.java` | daily_report_cell_auth 엔티티 (JSON 좌표) |
| `entity/CoreMenu.java` | core_menu 엔티티 (읽기 전용) |
| `entity/CoreMenuPermission.java` | core_menu_permission 엔티티 (읽기 전용) |
| `repository/CellAuthRepository.java` | 셀 권한 리포지토리 |
| `repository/CoreMenuRepository.java` | 메뉴 리포지토리 |
| `repository/CoreMenuPermissionRepository.java` | 메뉴 권한 리포지토리 (JPQL) |
| `dto/CellAuthRequest.java` | 셀 권한 요청 DTO |
| `dto/CellAuthResponse.java` | 셀 권한 응답 DTO |
| `service/MenuPermissionService.java` | 메뉴 접근 권한 서비스 |
| `service/CellAuthService.java` | 셀 권한 CRUD 서비스 |
| `controller/CellAuthController.java` | 셀 권한 REST API |

### 수정 파일 (3 Java + 1 HTML)
| 파일 | 변경 내용 |
|------|----------|
| `service/CellService.java` | CellPermission → CellAuth로 마이그레이션 |
| `controller/ViewRenderController.java` | 인증 기반, /my-permissions 추가, /accounts 제거 |
| `controller/CellController.java` | MenuPermission 검증 추가 |
| `static/dailyreport/index.html` | 로그인 화면 제거, 플랫폼 인증 통합, 새로고침 기능 |

### 신규 프론트엔드 (1개)
| 파일 | 설명 |
|------|------|
| `static/dailyreport/cell-auth-admin.html` | 셀 접근 권한 관리 페이지 (관리자 전용) |

### SQL 스크립트 (4개 수정)
| 파일 | 변경 내용 |
|------|----------|
| `01_schema.sql` | core 스텁 3개 + daily_report_cell_auth 추가 |
| `02_seed_data.sql` | 메뉴/권한/셀권한 추가, 레거시 cell_permission 제거 |
| `03_verify.sql` | 13섹션으로 확장 (메뉴/권한/셀권한 검증) |
| `99_drop_all.sql` | 10 테이블 삭제 (레거시 포함) |

---

## 파일 총계

- **Java**: 8 Entity + 8 Repository + 5 Service + 7 Controller + 11 DTO = **39 Java**
- **HTML**: 2 프론트엔드 SPA (index.html + cell-auth-admin.html)
- **SQL**: 5 스크립트
- **기타**: build.gradle, README.md (×2)
