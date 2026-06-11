# 설비관리시스템 플랫폼

## Project Overview
- **Name**: platform / 설비관리시스템
- **Goal**: 사업장 설비, 소방설비, 기타설비, 사용자/권한, PS 지분 검사를 통합 관리하는 Spring Boot 기반 내부 업무 시스템입니다.
- **Main Features**:
  - JWT 로그인 및 사용자/역할/메뉴 권한 관리
  - 소방설비 관리: 소화기, 소화전, 수신기, 소방펌프, 도면, QR, 모바일 점검
  - 소방설비 대시보드: 점검 필요 설비 막대 그래프, 3D 설비 현황 원형 그래프, 이상설비 수, 이상설비 도면 바로가기 및 행 클릭 상세 모달
  - 이상설비 집계: 소화기/소화전 비정상 설비와 수신기/소방펌프 불량·요정비 설비를 이상설비에 포함
  - 이상설비 도면 바로가기: 소화기/소화전/수신기/소방펌프 모두 층별 도면(`/maps/floor.html`)에서 마커 자동 선택, 강조 및 정보 카드 표시
  - 층별 도면: 실내/옥외 수신기·소방펌프 마커 포커스 지원, 도면 드래그 시 내부 이미지/마커의 브라우저 기본 드래그 고스트를 방지하고 선택된 마커 정보 카드를 유지하며, 일반 배경 클릭 시에만 닫힘 처리, 옥외 항공사진은 확대 초기화 배율까지만 축소되도록 제한
  - 메인 도면: 이동/확대/축소 시 열린 옥외 설비 미니 모달이 선택 마커를 따라 위치 재계산
  - 대시보드 이상설비 상세 모달: 넓은 전용 모달로 상세 화면 표시
  - 수신기/소방펌프 현황 그래프: 정상, 점검필요, 요정비, 불량 4상태 구분 표시(요정비/교체필요는 밝은 노란색 계열로 통일)
  - 기타설비 관리: 에어컨, 정수기 목록/상세/등록/수정/삭제/점검/이미지 업로드
  - 에어컨 관리 단순화: 건물 번호 기반 식별 No.(예: `1-1`, `2-1`, `130-1`), 제조사, 상세 위치, 실외기 대수(최대 2대), 제조/설치월만 관리하며 실외기 좌표/연결선, 설치연도, 수량 입력은 제거
  - 기타설비 대시보드/도면: 메인 도면은 `/fire-map.html` 원본 화면을 그대로 복사한 파생본으로 사용하되 옥외소화전/옥외 소화기/수신기·소방펌프만 제외하고, 층별 도면은 공통 DB 도면 흐름을 사용하며 에어컨은 `점검요청`, 정수기는 `점검필요` 상태만 표시
  - 설비별 건물/층/도면 좌표와 비고 관리
  - 대표 이미지 1장 유지 및 최근 점검 이력 12건 관리
  - 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 대표사진 업로드/변경 지원(최신 1장만 유지)
  - 수신기/소방펌프 점검 모달에서 점검사진 업로드 지원(저장된 점검 이력에 연결되며 대표사진도 최신 사진으로 갱신)
  - 수신기/소방펌프 점검 내역 엑셀 다운로드: 점검항목결과 통합 컬럼 없이 항목별 결과 컬럼과 실제 비고를 분리하고, 등록된 점검사진을 XLSX 내부 이미지로 첨부

## URLs
- **Local/Sandbox App**: `http://127.0.0.1:8080`
- **Health Check**: `/api/health`
- **소방설비 화면**:
  - `/index.html` → 설비관리시스템 > 소방설비 대시보드
  - `/fire-map.html` → 메인 도면(옥외 소화전/소화기/수신기/소방펌프 미니 모달이 도면 이동·확대에 따라 마커를 추적)
  - `/maps/floor.html` → 층별 도면(소화기/소화전/수신기/소방펌프 바로가기 포커스 지원: `buildingName`, `floorName`, `buildingId`, `floorId`, `focusType`, `focusId`; 옥외 도면은 `outdoorView=ext|hyd|equipment` 또는 바로가기 `focusType`에 따라 소화기/소화전/수신기·소방펌프 중 선택 구분만 표시)
  - `/extinguishers.html`, `/hydrants.html`, `/receivers.html`, `/pumps.html`, `/qr`
- **기타설비 화면**:
  - `/index.html?page=other_dashboard` → 기타설비 대시보드(에어컨/정수기 현황, 에어컨 `점검요청`, 정수기 `점검필요`, 층별 도면 바로가기)
  - `/facility-map.html` → 기타설비 도면(메인): 기존 `/fire-map.html` 화면/배경/zone overlay/tooltip/popup/이동·확대 디테일을 그대로 사용하고, 옥외소화전·옥외 소화기·수신기/소방펌프 UI와 초기 데이터 로딩만 제외. 건물/층 선택 시 `/facility/floor.html`로 이동
  - `/facility/floor.html` → 기타설비 층별 도면(기존 `/maps/floor.html`의 도면 레이어/드래그/확대/정보 카드 패턴 기준, DB `/fire-api/maps/floor-data` 공통 사용, `buildingName`, `floorName`, `buildingId`, `floorId`, `focusType=aircon|water`, `focusId` 지원, 에어컨은 실내기 마커만 표시하고 실외기 대수는 정보로만 표시)
  - `/facility/air-conditioners`
  - `/facility/water-purifiers`
- **기타설비 API**:
  - `/facility-api/air-conditioners`
  - `/facility-api/water-purifiers`

## Data Architecture
- **Database**: MariaDB (`platform_db`)
- **DDL mode**: `spring.jpa.hibernate.ddl-auto=none`; schema changes are managed by SQL scripts.
- **New Tables**:
  - `facility_equipment`: 에어컨/정수기 공통 설비 마스터. 에어컨은 `MANUFACTURER`, `LOCATION_DESCRIPTION`, `OUTDOOR_UNIT_COUNT`, `MANUFACTURE_DATE` 중심으로 관리하며 `INSTALLATION_YEAR`, `OUTDOOR_X`, `OUTDOOR_Y`, `QUANTITY`는 `V17__simplify_facility_aircon_fields.sql`에서 제거
  - `facility_equipment_inspection`: 기타설비 점검 이력
- **File Storage**:
  - 기타설비 업로드: `/data/upload/module_fire/facility/{air-conditioners|water-purifiers}`
  - 수신기 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/receivers`, API file path: `/fire-api/receivers/files/{filename}`
  - 소방펌프 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/pumps`, API file path: `/fire-api/pumps/files/{filename}`
  - 수신기/소방펌프 점검사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/{receiver-inspections|pump-inspections}`
  - API file path: `/facility-api/{kind}/files/{filename}`

## Role / Menu Access
- `ROLE_FACILITY_MANAGER`: 소방설비 + 기타설비 전체 접근
- `ROLE_FIRE_MANAGER`: 소방설비만 접근
- `ROLE_EQUIPMENT_MANAGER`: 기타설비만 접근
- `ROLE_ADMIN`: 전체 접근
- 설비관리시스템 메뉴는 `FIRE_MGMT`를 상위 메뉴로 유지하되 이름을 “설비관리시스템”으로 변경했습니다.

## User Guide
1. 로그인 후 설비관리시스템 메뉴로 이동합니다.
2. 소방설비 대시보드에서 설비 유형별 점검 필요 설비 수량/막대 그래프와 3D 설비 현황 원형 그래프를 확인합니다. 수신기/소방펌프 그래프는 정상, 점검필요, 요정비, 불량 4상태로 표시되며, 요정비/교체필요는 더 밝은 노란색 계열로 표시됩니다. 원형 그래프 또는 범례에 마우스를 올리면 중앙 비율이 해당 상태 기준으로 변경됩니다.
3. 대시보드 하단 이상설비 목록에는 수신기/소방펌프의 `요정비` 상태도 포함됩니다. 행을 클릭하면 넓은 상세 모달이 열리고, `바로가기`를 누르면 모든 설비 유형이 층별 도면으로 이동해 해당 마커가 실제 클릭된 것처럼 선택·강조되며 정보 카드가 표시됩니다.
4. 층별 도면에서 건물 `옥외`를 선택하면 두 번째 드롭다운이 `소화기`, `소화전`, `수신기/소방펌프` 설비 구분으로 바뀌며 선택된 구분의 마커만 표시됩니다. 실내/옥외 수신기·소방펌프 바로가기는 대상 층/설비 구분을 자동 선택한 뒤 대상 마커를 클릭·강조하고 정보 카드를 표시합니다. 도면을 드래그해 이동해도 열린 정보 카드는 유지되며, 배경을 일반 클릭하면 닫힙니다. 옥외 도면은 `확대 초기화` 시의 화면 맞춤 배율까지만 축소됩니다.
5. 소방설비는 기존 소화기/소화전/수신기/펌프 메뉴에서 관리합니다.
6. 기타설비는 기타설비 메뉴의 대시보드, 도면(메인), 층별 도면, 에어컨, 정수기 화면에서 관리합니다. 기타설비 메인 도면은 기존 `/fire-map.html`을 그대로 가져온 화면에서 옥외소화전/옥외 소화기/수신기·소방펌프만 제외한 구성이고, 구역/층 선택은 `/facility/floor.html`로 연동됩니다.
7. 기타설비 대시보드의 바로가기를 누르면 `/facility/floor.html`에서 해당 에어컨/정수기 마커가 자동 선택·강조되고 정보 카드가 표시됩니다. 에어컨은 `점검요청`, 정수기는 `점검필요`로 표시합니다.
8. 목록에서 설비 행을 클릭하면 상세 정보와 점검 이력을 볼 수 있습니다.
9. 권한이 있는 사용자는 추가/수정/삭제/점검 및 이미지 업로드를 수행할 수 있습니다.
10. 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 사진 파일을 선택하면 해당 설비의 대표사진이 최신 1장으로 교체됩니다.
11. 수신기/소방펌프 수정 모달의 점검 이력 영역에서는 기존 내역 `수정`/`삭제`와 신규 행 `추가`만 수행하며, 점검 이력 편집 테이블에는 사진 업로드 입력을 두지 않습니다. 점검사진은 별도 `점검` 모달에서 저장 시 업로드됩니다.
12. 수신기/소방펌프 점검 내역의 `엑셀 다운로드`를 누르면 조회 기간 내 이력이 XLSX로 저장됩니다. 전원/스위치 등 점검 결과는 항목별 컬럼으로 분리되고, 비고에는 실제 점검 비고만 표시되며 등록된 점검사진은 사진 컬럼에 첨부됩니다.
13. 등록/수정 화면에서 도면을 클릭해 X/Y 좌표를 선택할 수 있습니다. 에어컨도 실내기 위치만 지정하며, 실외기는 좌표 연결 없이 대수만 입력합니다.

## Deployment / Runtime
- **Runtime**: Spring Boot 3.2.5 + Java 21
- **Build**: `./gradlew :app:bootJar`
- **Process Manager**: PM2 (`platform`)
- **Start script**: `/home/user/webapp/start-app.sh`
- **Last Updated**: 2026-06-11

## Current Status
- 기타설비 도메인/API/화면 기본 구현 완료
- 기타설비 메인 도면(`/facility-map.html`)은 신규 독립 축약 구현이 아니라 `/fire-map.html` 원본을 그대로 복사한 뒤 기타설비 메뉴/층별 링크만 변경하고 옥외소화전·옥외 소화기·수신기/소방펌프 UI 및 초기 로딩을 제외하도록 정리 완료
- `V15__facility_management_system.sql`로 메뉴/역할/테이블 migration 추가
- 빌드 검증 완료
- 에어컨 식별 No. 및 제조사/위치/실외기 대수 관리 구현 완료: 실외기 좌표/연결선, 설치연도, 수량 입력은 `V17__simplify_facility_aircon_fields.sql` 기준으로 제거
- 수신기/소방펌프 대시보드 그래프 4상태 반영 완료(요정비/교체필요 그래프 색상은 밝은 노란색 계열 적용)
- 수신기/소방펌프 점검 이력 삭제 API 및 수정 모달 삭제 버튼 구현 완료
- 이상설비 바로가기 마커 자동 선택 안정화 및 대시보드 상세 모달 확대 완료
- 층별 도면의 옥외 항공사진 축소 최소값을 확대 초기화 화면 맞춤 배율로 제한하도록 개선 완료
- 층별 도면 옥외 선택 시 층 드롭다운을 소화기/소화전/수신기·소방펌프 설비 구분으로 전환하고 선택 구분 마커만 표시하도록 개선 완료
- 층별 도면 드래그 이동 후 불필요하게 마커 정보 카드가 닫히지 않고, 내부 도면/마커 이미지가 브라우저 기본 드래그 이미지로 같이 끌려 보이지 않도록 개선 완료
- 수신기/소방펌프 `요정비` 상태를 대시보드 이상설비 목록/카운트에 포함하도록 개선 완료
- 수신기/소방펌프 이상설비 바로가기를 층별 도면으로 통일하고 실내 수신기·펌프 마커 포커스를 지원하도록 개선 완료
- 메인 도면의 옥외 설비 미니 모달이 도면 이동/확대/축소 시 선택 마커를 따라 이동하도록 개선 완료
- 수신기/소방펌프 점검 내역 다운로드를 CSV에서 사진 포함 XLSX로 변경하고, 점검항목결과 통합 컬럼을 제거한 뒤 각 점검 항목 결과와 실제 비고를 별도 컬럼으로 출력하도록 개선 완료
- 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에 대표사진 업로드를 추가하고, 설비별 대표사진은 최신 1장만 유지하도록 개선 완료
- 수신기/소방펌프 점검 모달 저장 시 선택 사진이 생성된 점검 이력에 안정적으로 연결되도록 업로드 대상 식별 로직 개선 완료

## Recommended Next Steps
- 실제 사용자 계정별 역할 부여 후 메뉴 노출 및 API 권한 검증
- `V17__simplify_facility_aircon_fields.sql` 운영/로컬 DB 적용 후 에어컨 저장·수정·조회 검증
- 에어컨/정수기 실데이터 등록 후 기타설비 대시보드/메인 도면/층별 도면 포커스 흐름과 공통 DB 도면 좌표 매칭 검증
- 추가 운영 DB 반영 시 `mysql --default-character-set=utf8mb4` 사용 권장
