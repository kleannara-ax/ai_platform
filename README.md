# 설비관리시스템 플랫폼

## Project Overview
- **Name**: platform / 설비관리시스템
- **Goal**: 사업장 설비, 소방설비, 기타설비, 사용자/권한, PS 지분 검사를 통합 관리하는 Spring Boot 기반 내부 업무 시스템입니다.
- **Main Features**:
  - JWT 로그인 및 사용자/역할/메뉴 권한 관리
  - 소방설비 관리: 소화기, 소화전, 수신기, 소방펌프, QR, 모바일 점검
  - 설비관리시스템 공통 도면 메뉴: `도면 (메인)`, `층별 도면`은 소방설비/기타설비 하위가 아니라 `설비관리시스템` 바로 아래에서 공통 관리
  - 이상설비 집계: 소화기/소화전 비정상 설비와 수신기/소방펌프 불량·요정비 설비를 이상설비에 포함
  - 이상설비 도면 바로가기: 소화기/소화전/수신기/소방펌프 모두 층별 도면(`/maps/floor.html`)에서 마커 자동 선택, 강조 및 정보 카드 표시
  - 층별 도면: 실내/옥외 수신기·소방펌프 마커 포커스 지원, 도면 드래그 시 내부 이미지/마커의 브라우저 기본 드래그 고스트를 방지하고 선택된 마커 정보 카드를 유지하며, 일반 배경 클릭 시에만 닫힘 처리, 옥외 항공사진은 확대 초기화 배율까지만 축소되도록 제한
  - 메인 도면: 이동/확대/축소 시 열린 옥외 설비 미니 모달이 선택 마커를 따라 위치 재계산
  - 대시보드 이상설비 상세 모달: 넓은 전용 모달로 상세 화면 표시
  - 수신기/소방펌프 현황 그래프: 정상, 점검필요, 요정비, 불량 4상태 구분 표시(요정비/교체필요는 밝은 노란색 계열로 통일)
  - 기타설비 관리: 에어컨, 정수기 목록/상세/등록/수정/삭제/점검/이미지 업로드
  - 에어컨 관리 단순화: 건물 번호 기반 식별 No.(예: `1-1`, `2-1`, `130-1`), 제조사, 상세 위치, 실외기 대수(최대 2대), 제조/설치월만 관리하며 실외기 좌표/연결선, 설치연도, 수량 입력은 제거
  - 정수기 관리 단순화: 정수기 종류는 `정수기` 단일 값으로 고정하고, 등록/수정 화면은 설치일, 건물, 층, X/Y 좌표만 사용자 입력으로 표시
  - 기타설비 관리: 기타설비 하위 메뉴는 에어컨/정수기 중심으로 유지하고, 대시보드/도면/층별도면은 메뉴관리·접근권한 대상에서 제거하거나 공통 도면 메뉴로 통합
  - 설비별 건물/층/도면 좌표와 비고 관리
  - 대표 이미지 1장 유지 및 최근 점검 이력 12건 관리
  - 소화기 목록은 `/fire-api/extinguishers`의 서버 페이지네이션을 사용해 200건 단위로 조회·출력하며, 전체 건수는 목록 API의 `totalElements` 기준으로 표시
  - 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 대표사진 업로드/변경 지원(최신 1장만 유지)
  - 수신기/소방펌프 점검 모달에서 점검사진 업로드 지원(저장된 점검 이력에 연결되며 대표사진도 최신 사진으로 갱신)
  - 수신기/소방펌프 점검 내역 엑셀 다운로드: 점검항목결과 통합 컬럼 없이 항목별 결과 컬럼과 실제 비고를 분리하고, 등록된 점검사진을 XLSX 내부 이미지로 첨부하며, 최신 점검 행은 점검사진이 없으면 설비 대표사진을 표시

## URLs
- **Local/Sandbox App**: `http://127.0.0.1:8080`
- **Health Check**: `/api/health`
- **설비관리시스템 공통 도면 화면**:
  - `/fire-map.html` → `설비관리시스템 > 도면 (메인)` 공통 메뉴에서 로드
  - `/maps/floor.html` → `설비관리시스템 > 층별 도면` 공통 메뉴에서 로드. 소화기/소화전/수신기/소방펌프 바로가기 포커스 지원: `buildingName`, `floorName`, `buildingId`, `floorId`, `focusType`, `focusId`
- **소방설비 화면**:
  - `/extinguishers.html`, `/hydrants.html`, `/receivers.html`, `/pumps.html`, `/qr`
- **기타설비 화면**:
  - `/facility/air-conditioners`
  - `/facility/water-purifiers`
- **기타설비 API**:
  - `/facility-api/air-conditioners`
  - `/facility-api/water-purifiers`

## Data Architecture
- **Database**: MariaDB (`platform_db`)
- **DDL mode**: `spring.jpa.hibernate.ddl-auto=none`; schema changes are managed by SQL scripts.
- **New Tables**:
  - `facility_equipment`: 에어컨/정수기 공통 설비 마스터. 에어컨은 `EQUIPMENT_TYPE`, `MANUFACTURER`, `LOCATION_DESCRIPTION`, `OUTDOOR_UNIT_COUNT`, `MANUFACTURE_DATE` 중심으로 관리하며 `INSTALLATION_YEAR`, `OUTDOOR_X`, `OUTDOOR_Y`, `QUANTITY`는 `V17__simplify_facility_aircon_fields.sql`에서 제거. 정수기는 `V19__simplify_facility_water_purifier_fields.sql` 기준으로 `EQUIPMENT_TYPE='정수기'` 고정, `MANUFACTURE_DATE`를 설치일로 사용하고 사용자 입력은 건물/층/X/Y 좌표와 설치일만 받음
  - `facility_equipment_inspection`: 기타설비 점검 이력
- **File Storage**:
  - 기타설비 업로드: `/data/upload/module_fire/facility/{air-conditioners|water-purifiers}`
  - 수신기 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/receivers`, API file path: `/fire-api/receivers/files/{filename}`
  - 소방펌프 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/pumps`, API file path: `/fire-api/pumps/files/{filename}`
  - 수신기/소방펌프 점검사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/{receiver-inspections|pump-inspections}`
  - API file path: `/facility-api/{kind}/files/{filename}`

## Role / Menu Access
- `ROLE_FACILITY_MANAGER`: 시설관리 — 소방설비 + 기타설비 전체 접근
- `ROLE_FIRE_MANAGER`: 소방시설관리 — 기존 소방설비 전용 역할 유지
- `ROLE_EQUIPMENT_MANAGER`: 기타시설관리 — 기타설비만 접근
- `ROLE_ADMIN`: 전체 접근
- 설비관리시스템 메뉴는 `FIRE_MGMT`를 상위 메뉴로 유지하되 이름을 “설비관리시스템”으로 변경했습니다.
- `V21__normalize_facility_menu_structure.sql` 기준으로 `설비관리시스템` 바로 아래 메뉴는 `도면 (메인)`(`FIRE_MAP`) → `층별 도면`(`FIRE_FLOOR`) → `소방설비`(`FIRE_EQUIPMENT_GROUP`) → `기타설비`(`OTHER_EQUIPMENT_GROUP`) 순서로 관리합니다. `FIRE_DASHBOARD`, `OTHER_DASHBOARD`, `OTHER_MAP`, `OTHER_FLOOR`는 메뉴관리/접근권한 대상에서 제거합니다.
- 운영 DB에서 역할 공통코드가 중복되어 접근 권한 화면에 `소방시설관리`가 2개 표시되는 경우 `V20__fix_facility_role_duplicates.sql`로 ROLE 그룹/코드 중복을 정리하고 위 3개 설비 역할명을 보정합니다.

## User Guide
1. 로그인 후 `설비관리시스템` 메뉴로 이동합니다.
2. `설비관리시스템` 바로 아래의 공통 메뉴 `도면 (메인)`과 `층별 도면`에서 소방설비/기타설비가 함께 사용하는 도면을 확인합니다.
3. `소방설비` 그룹에서는 소화기, 소화전, 수신기, 소방펌프, QR 메뉴를 통해 설비 목록, 상세, 등록/수정, 점검 이력을 관리합니다.
4. `기타설비` 그룹에서는 에어컨, 정수기 메뉴만 표시하며, 기타설비 하위의 대시보드/도면(메인)/층별 도면 가상 메뉴는 제거되었습니다.
5. 대시보드 메뉴(`FIRE_DASHBOARD`, `OTHER_DASHBOARD`)는 메뉴관리·접근권한 대상에서 제거되었으며, 이전 세션에 삭제된 페이지가 남아 있으면 기본 대시보드로 자동 복귀합니다.
6. 층별 도면에서 건물 `옥외`를 선택하면 두 번째 드롭다운이 `소화기`, `소화전`, `수신기/소방펌프` 설비 구분으로 바뀌며 선택된 구분의 마커만 표시됩니다. 실내/옥외 수신기·소방펌프 바로가기는 대상 층/설비 구분을 자동 선택한 뒤 대상 마커를 클릭·강조하고 정보 카드를 표시합니다.
7. 목록에서 설비 행을 클릭하면 상세 정보와 점검 이력을 볼 수 있습니다.
8. 권한이 있는 사용자는 추가/수정/삭제/점검 및 이미지 업로드를 수행할 수 있습니다. 정수기는 등록/수정 시 설치일, 건물, 층, X/Y 좌표만 입력하면 되며 종류는 자동으로 `정수기`로 저장됩니다.
9. 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 사진 파일을 선택하면 해당 설비의 대표사진이 최신 1장으로 교체됩니다.
10. 수신기/소방펌프 수정 모달의 점검 이력 영역에서는 기존 내역 `수정`/`삭제`와 신규 행 `추가`만 수행하며, 점검 이력 편집 테이블에는 사진 업로드 입력을 두지 않습니다. 점검사진은 별도 `점검` 모달에서 저장 시 업로드됩니다.
11. 수신기/소방펌프 점검 내역의 `엑셀 다운로드`를 누르면 조회 기간 내 이력이 XLSX로 저장됩니다. 전원/스위치 등 점검 결과는 항목별 컬럼으로 분리되고, 비고에는 실제 점검 비고만 표시되며 등록된 점검사진은 사진 컬럼에 첨부됩니다.
12. 등록/수정 화면에서 도면을 클릭해 X/Y 좌표를 선택할 수 있습니다. 에어컨도 실내기 위치만 지정하며, 실외기는 좌표 연결 없이 대수만 입력합니다.

## Deployment / Runtime
- **Runtime**: Spring Boot 3.2.5 + Java 21
- **Build**: `./gradlew :app:bootJar`
- **Process Manager**: PM2 (`platform`)
- **Start script**: `/home/user/webapp/start-app.sh`
- **Last Updated**: 2026-06-18

## Current Status
- 기타설비 도메인/API/화면 기본 구현 완료
- `V15__facility_management_system.sql`로 설비관리시스템 상위 메뉴와 소방설비/기타설비 그룹을 추가
- `V20__fix_facility_role_duplicates.sql`로 운영 DB의 ROLE 공통코드 중복 표시 보정 추가: 기존 `ROLE_FIRE_MANAGER`/소방시설관리는 유지하고 `ROLE_FACILITY_MANAGER`/시설관리, `ROLE_EQUIPMENT_MANAGER`/기타시설관리를 명확히 정리
- `V21__normalize_facility_menu_structure.sql`로 `설비관리시스템` 하위 정렬을 `도면 (메인)` → `층별 도면` → `소방설비` → `기타설비` 순서로 보정하고, `FIRE_DASHBOARD`, `OTHER_DASHBOARD`, `OTHER_MAP`, `OTHER_FLOOR` 메뉴/권한을 제거
- `index.html`에서 기타설비 하위에 프론트에서만 추가되던 대시보드/도면/층별도면 가상 메뉴 및 라우팅을 제거
- 에어컨 식별 No. 및 제조사/위치/실외기 대수 관리 구현 완료: 실외기 좌표/연결선, 설치연도, 수량 입력은 `V17__simplify_facility_aircon_fields.sql` 기준으로 제거
- 정수기 종류 입력 제거 및 단순 등록/수정 구현 완료: `V19__simplify_facility_water_purifier_fields.sql` 기준으로 기존 정수기 종류를 `정수기`로 통일하고, 화면 입력은 설치일/건물/층/X/Y 좌표만 표시
- 수신기/소방펌프 점검 이력 삭제 API 및 수정 모달 삭제 버튼 구현 완료
- 층별 도면의 옥외 항공사진 축소 최소값을 확대 초기화 화면 맞춤 배율로 제한하도록 개선 완료
- 층별 도면 옥외 선택 시 층 드롭다운을 소화기/소화전/수신기·소방펌프 설비 구분으로 전환하고 선택 구분 마커만 표시하도록 개선 완료
- 층별 도면 드래그 이동 후 불필요하게 마커 정보 카드가 닫히지 않고, 내부 도면/마커 이미지가 브라우저 기본 드래그 이미지로 같이 끌려 보이지 않도록 개선 완료
- 메인 도면의 옥외 설비 미니 모달이 도면 이동/확대/축소 시 선택 마커를 따라 이동하도록 개선 완료
- 수신기/소방펌프 점검 내역 다운로드를 CSV에서 사진 포함 XLSX로 변경하고, 점검항목결과 통합 컬럼을 제거한 뒤 각 점검 항목 결과와 실제 비고를 별도 컬럼으로 출력하도록 개선 완료
- 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에 대표사진 업로드를 추가하고, 설비별 대표사진은 최신 1장만 유지하도록 개선 완료
- 수신기/소방펌프 점검 모달 저장 시 선택 사진이 생성된 점검 이력에 안정적으로 연결되도록 업로드 대상 식별 로직 개선 완료
- 수신기/소방펌프 점검 내역 XLSX 다운로드 시 최신 점검 행에 점검사진이 없으면 설비 대표사진을 표시하고, 현재 업로드 루트와 기존 `/data/upload/module_fire` 경로를 모두 조회하도록 개선 완료
- 소화기 목록 화면은 한 번에 전체 데이터를 병합하지 않고 `/fire-api/extinguishers?page={page}&size=200`으로 현재 페이지의 최대 200건만 출력하며, 이전/다음 및 페이지 번호 이동 UI를 제공
- SPA 내부 소방 모듈 iframe 로딩 시 버전 쿼리스트링을 갱신해 브라우저에 캐시된 이전 `extinguishers.html`이 계속 표시되는 문제를 방지

## Recommended Next Steps
- 실제 사용자 계정별 역할 부여 후 `ROLE_ADMIN`, `ROLE_FACILITY_MANAGER`, `ROLE_FIRE_MANAGER`, `ROLE_EQUIPMENT_MANAGER` 메뉴 노출 및 API 권한 검증
- 운영 DB에 `V17__simplify_facility_aircon_fields.sql`, `V19__simplify_facility_water_purifier_fields.sql`, `V20__fix_facility_role_duplicates.sql`, `V21__normalize_facility_menu_structure.sql` 순서 적용 후 메뉴관리/접근권한 화면 검증
- `설비관리시스템 > 도면 (메인)`, `설비관리시스템 > 층별 도면`, `소방설비`, `기타설비`의 사이드바 정렬과 권한별 표시 확인
- 에어컨/정수기 실데이터 등록 후 공통 도면 좌표 매칭 검증
- 추가 운영 DB 반영 시 `mysql --default-character-set=utf8mb4` 사용 권장
