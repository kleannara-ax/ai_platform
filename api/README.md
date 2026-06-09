# API 문서 목록

> **AI Platform** REST API 전문 문서
> **최종 업데이트**: 2026-06-05
> **Base URL**: `https://{서버주소}`
> **인증 방식**: JWT Bearer Token

---

## 목차

### 공통

| 번호 | 문서 | 설명 |
|------|------|------|
| 00 | [공통 응답 형식](./00_공통응답형식.md) | ApiResponse, PageResponse, 에러코드, 역할 목록 |

### Core 모듈 (`/api/auth`, `/api/core/*`)

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 01 | [인증 (Auth)](./01_인증_Auth.md) | `/api/auth` | 로그인, 토큰 갱신, 현재 사용자 조회 |
| 02 | [SSO EncData](./02_SSO_EncData.md) | `/api/login` | SSO 암호화 데이터 로그인 (HTML 응답) |
| 03 | [사용자 관리 (Core User)](./03_사용자관리_CoreUser.md) | `/api/core/users` | 사용자 CRUD, 활성화/비활성화, 역할 변경 |
| 05 | [메뉴 관리 (Core Menu)](./05_메뉴관리_CoreMenu.md) | `/api/core/menus` | 메뉴 트리 CRUD, 역할별 메뉴, IP 필터링 |
| 06 | [권한 관리 (Permission)](./06_권한관리_Permission.md) | `/api/core/permissions` | 권한 CRUD, 역할-메뉴-권한 매핑 |

### App 모듈 (`/api/integrated/*`)

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 04 | [통합 사용자 (Integrated User)](./04_통합사용자_IntegratedUser.md) | `/api/integrated/users` | 사용자 + 프로필 통합 CRUD |

### Module-Common (`/api/codes`, `/api/module-common/*`)

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 07 | [공통코드 (Code)](./07_공통코드_Code.md) | `/api/codes` | 코드 그룹/상세 CRUD, 드롭다운 조회 |
| 08 | [부서 관리 (Department)](./08_부서관리_Department.md) | `/api/module-common/departments` | 부서 CRUD, 하위 부서 조회 |
| 09 | [사용자 프로필 (UserProfile)](./09_사용자프로필_UserProfile.md) | `/api/module-common/profiles` | 프로필 CRUD, 부서별 프로필 조회 |

### Module-Fire / Facility (설비관리시스템) (`/fire-api/*`, `/facility-api/*`)

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 10 | [소화기 (Extinguisher)](./10_소방_소화기_Extinguisher.md) | `/fire-api/extinguishers` | 소화기 CRUD, 점검, 이미지 |
| 11 | [소화전 (Hydrant)](./11_소방_소화전_Hydrant.md) | `/fire-api/hydrants` | 소화전 CRUD, 점검, 이미지 |
| 12 | [소방펌프 (Pump)](./12_소방_소방펌프_Pump.md) | `/fire-api/pumps` | 소방펌프 CRUD, 점검, CSV 내보내기 |
| 13 | [수신기 (Receiver)](./13_소방_수신기_Receiver.md) | `/fire-api/receivers` | 수신기 CRUD, 점검, CSV 내보내기 |
| 14 | [대시보드 / 지도 / QR](./14_소방_대시보드_지도_QR.md) | `/fire-api/dashboard`, `/fire-api/maps`, `/fire-api/qr` | 통계, 도면, QR 이미지 생성 |
| 15 | [모바일 점검](./15_소방_모바일점검.md) | `/fire-api/minspection` | QR 스캔 기반 모바일 현장 점검 |
| 18 | [설비관리 기타설비](./18_설비관리_기타설비.md) | `/facility-api/air-conditioners`, `/facility-api/water-purifiers` | 에어컨/정수기 CRUD, 점검, 이미지, 도면 좌표 |

### Module-PS-Insp (PS 지분 검사 모듈) (`/ps-insp-api/*`)

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 16 | [PS 지분 검사](./16_PS점검.md) | `/ps-insp-api/inspections`, `/ps-insp-api/mes`, `/ps-insp-api/config` | 검사 CRUD, MES 전송, PPM 설정 |

### 헬스체크

| 번호 | 문서 | URL Prefix | 설명 |
|------|------|------------|------|
| 17 | [헬스체크](./17_헬스체크.md) | `/api/health`, `/ps-insp-api/health` | 앱/모듈 상태 확인 |

---

## API 엔드포인트 전체 목록 (Quick Reference)

### 인증 (permitAll)
| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/login/sendEncData` | SSO 로그인 |
| GET | `/api/auth/csrf` | CSRF (빈 응답) |
| GET | `/api/health` | 앱 헬스체크 |
| GET | `/ps-insp-api/health` | PS 모듈 헬스체크 |
| GET | `/fire-api/qr/image` | QR 이미지 생성 |
| GET | `/facility/air-conditioners` | 에어컨 관리 화면 |
| GET | `/facility/water-purifiers` | 정수기 관리 화면 |

### 인증 필요
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/auth/me` | 현재 사용자 조회 |
| POST | `/api/core/users` | 사용자 생성 |
| GET | `/api/core/users` | 사용자 목록 |
| GET | `/api/core/users/{id}` | 사용자 조회 |
| PUT | `/api/core/users/{id}` | 사용자 수정 |
| PATCH | `/api/core/users/{loginId}/disable` | 사용자 비활성화 (`loginId` 기준) |
| PATCH | `/api/core/users/{loginId}/enable` | 사용자 활성화 (`loginId` 기준) |
| PATCH | `/api/core/users/{id}/role` | 역할 변경 |
| GET | `/api/integrated/users` | 통합 사용자 목록 |
| GET | `/api/integrated/users/{id}` | 통합 사용자 조회 |
| POST | `/api/integrated/users` | 통합 사용자 생성 |
| PUT | `/api/integrated/users/{id}` | 통합 사용자 수정 |
| PATCH | `/api/integrated/users/{loginId}/disable` | 통합 비활성화 (`loginId` 기준) |
| PATCH | `/api/integrated/users/{loginId}/enable` | 통합 활성화 (`loginId` 기준) |
| PATCH | `/api/integrated/users/{id}/role` | 통합 역할 변경 |
| GET | `/api/core/menus` | 메뉴 트리 (활성) |
| GET | `/api/core/menus/tree` | 메뉴 트리 (전체) |
| GET | `/api/core/menus/list` | 메뉴 플랫 목록 |
| GET | `/api/core/menus/list/all` | 메뉴 전체 목록 |
| GET | `/api/core/menus/role/{role}` | 역할별 메뉴 |
| GET | `/api/core/menus/{id}` | 메뉴 상세 |
| POST | `/api/core/menus` | 메뉴 생성 |
| PUT | `/api/core/menus/{id}` | 메뉴 수정 |
| DELETE | `/api/core/menus/{id}` | 메뉴 삭제 |
| GET | `/api/core/menus/my-ip` | 접속 IP 조회 |
| GET | `/api/core/permissions` | 권한 목록 |
| GET | `/api/core/permissions/{id}` | 권한 상세 |
| POST | `/api/core/permissions` | 권한 생성 |
| PUT | `/api/core/permissions/{id}` | 권한 수정 |
| GET | `/api/core/permissions/roles` | 역할 매핑 목록 |
| GET | `/api/core/permissions/roles/{role}` | 역할 매핑 조회 |
| PUT | `/api/core/permissions/roles` | 역할 매핑 갱신 |
| GET | `/api/codes/groups` | 코드 그룹 목록 |
| GET | `/api/codes/groups/{id}` | 코드 그룹 상세 |
| GET | `/api/codes/groups/code/{groupCode}` | 그룹코드로 조회 |
| POST | `/api/codes/groups` | 코드 그룹 생성 |
| PUT | `/api/codes/groups/{id}` | 코드 그룹 수정 |
| DELETE | `/api/codes/groups/{id}` | 코드 그룹 삭제 |
| GET | `/api/codes/groups/{id}/details` | 하위 코드 목록 |
| GET | `/api/codes/lookup/{groupCode}` | 드롭다운 조회 |
| GET | `/api/codes/details/{id}` | 코드 상세 |
| POST | `/api/codes/groups/{id}/details` | 코드 추가 |
| PUT | `/api/codes/details/{id}` | 코드 수정 |
| DELETE | `/api/codes/details/{id}` | 코드 삭제 |
| POST | `/api/module-common/departments` | 부서 생성 |
| GET | `/api/module-common/departments/{id}` | 부서 조회 |
| GET | `/api/module-common/departments` | 부서 목록 |
| GET | `/api/module-common/departments/{id}/children` | 하위 부서 |
| PATCH | `/api/module-common/departments/{id}/disable` | 부서 비활성화 |
| POST | `/api/module-common/profiles` | 프로필 생성 |
| GET | `/api/module-common/profiles/user/{userId}` | 프로필 조회 |
| GET | `/api/module-common/profiles/department/{deptCode}` | 부서별 프로필 |
| PUT | `/api/module-common/profiles/user/{userId}` | 프로필 수정 |
| GET | `/facility-api/air-conditioners` | 에어컨 목록 |
| GET | `/facility-api/air-conditioners/{id}` | 에어컨 상세 |
| POST | `/facility-api/air-conditioners` | 에어컨 등록/수정 |
| DELETE | `/facility-api/air-conditioners/{id}` | 에어컨 삭제 |
| POST | `/facility-api/air-conditioners/{id}/image` | 에어컨 대표 이미지 업로드 |
| POST | `/facility-api/air-conditioners/inspect` | 에어컨 점검 완료 |
| GET | `/facility-api/water-purifiers` | 정수기 목록 |
| GET | `/facility-api/water-purifiers/{id}` | 정수기 상세 |
| POST | `/facility-api/water-purifiers` | 정수기 등록/수정 |
| DELETE | `/facility-api/water-purifiers/{id}` | 정수기 삭제 |
| POST | `/facility-api/water-purifiers/{id}/image` | 정수기 대표 이미지 업로드 |
| POST | `/facility-api/water-purifiers/inspect` | 정수기 점검 완료 |
