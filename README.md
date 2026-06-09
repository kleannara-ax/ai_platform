# 설비관리시스템 플랫폼

## Project Overview
- **Name**: platform / 설비관리시스템
- **Goal**: 사업장 설비, 소방설비, 기타설비, 사용자/권한, PS 지분 검사를 통합 관리하는 Spring Boot 기반 내부 업무 시스템입니다.
- **Main Features**:
  - JWT 로그인 및 사용자/역할/메뉴 권한 관리
  - 소방설비 관리: 소화기, 소화전, 수신기, 소방펌프, 도면, QR, 모바일 점검
  - 소방설비 대시보드: 점검 필요 설비 막대 그래프, 3D 설비 현황 원형 그래프, 이상설비 수, 이상설비 도면 바로가기 및 행 클릭 상세 모달
  - 이상설비 집계: 소화기/소화전 비정상 설비와 수신기/소방펌프 불량·요정비 설비를 이상설비에 포함
  - 이상설비 도면 바로가기: 소화기/소화전/수신기/소방펌프 마커 자동 선택, 강조 및 정보 카드 표시
  - 층별 도면: 옥외 항공사진은 전체 화면에 맞게 더 낮은 초기 축소율을 허용하고, 옥외 선택 시 층 드롭다운을 소화기/소화전/수신기·소방펌프 설비 구분으로 전환해 선택된 구분의 마커만 표시
  - 대시보드 이상설비 상세 모달: 넓은 전용 모달로 상세 화면 표시
  - 수신기/소방펌프 현황 그래프: 정상, 점검필요, 요정비, 불량 4상태 구분 표시(요정비/교체필요는 노란색 계열로 통일)
  - 기타설비 관리: 에어컨, 정수기 목록/상세/등록/수정/삭제/점검/이미지 업로드
  - 설비별 건물/층/도면 좌표와 비고 관리
  - 대표 이미지 1장 유지 및 최근 점검 이력 12건 관리
  - 수신기/소방펌프 수정 모달에서 최근 점검 이력 추가/수정/삭제 지원

## URLs
- **Local/Sandbox App**: `http://127.0.0.1:8080`
- **Health Check**: `/api/health`
- **소방설비 화면**:
  - `/index.html` → 설비관리시스템 > 소방설비 대시보드
  - `/fire-map.html` → 메인 도면(수신기/소방펌프 바로가기 포커스 지원: `focusType`, `focusId`)
  - `/maps/floor.html` → 층별 도면(소화기/소화전 바로가기 포커스 지원: `buildingName`, `floorName`, `buildingId`, `floorId`, `focusType`, `focusId`; 옥외 도면은 `outdoorView=ext|hyd|equipment` 또는 바로가기 `focusType`에 따라 소화기/소화전/수신기·소방펌프 중 선택 구분만 표시)
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
  - `facility_equipment`: 에어컨/정수기 공통 설비 마스터
  - `facility_equipment_inspection`: 기타설비 점검 이력
- **File Storage**:
  - 기타설비 업로드: `/data/upload/module_fire/facility/{air-conditioners|water-purifiers}`
  - API file path: `/facility-api/{kind}/files/{filename}`

## Role / Menu Access
- `ROLE_FACILITY_MANAGER`: 소방설비 + 기타설비 전체 접근
- `ROLE_FIRE_MANAGER`: 소방설비만 접근
- `ROLE_EQUIPMENT_MANAGER`: 기타설비만 접근
- `ROLE_ADMIN`: 전체 접근
- 설비관리시스템 메뉴는 `FIRE_MGMT`를 상위 메뉴로 유지하되 이름을 “설비관리시스템”으로 변경했습니다.

## User Guide
1. 로그인 후 설비관리시스템 메뉴로 이동합니다.
2. 소방설비 대시보드에서 설비 유형별 점검 필요 설비 수량/막대 그래프와 3D 설비 현황 원형 그래프를 확인합니다. 수신기/소방펌프 그래프는 정상, 점검필요, 요정비, 불량 4상태로 표시되며, 요정비는 노란색 계열로 표시됩니다. 원형 그래프 또는 범례에 마우스를 올리면 중앙 비율이 해당 상태 기준으로 변경됩니다.
3. 대시보드 하단 이상설비 목록에는 수신기/소방펌프의 `요정비` 상태도 포함됩니다. 행을 클릭하면 넓은 상세 모달이 열리고, `바로가기`를 누르면 층별 도면 또는 메인 도면에서 해당 마커가 실제 클릭된 것처럼 선택·강조되며 정보 카드가 표시됩니다.
4. 층별 도면에서 건물 `옥외`를 선택하면 두 번째 드롭다운이 `소화기`, `소화전`, `수신기/소방펌프` 설비 구분으로 바뀌며 선택된 구분의 마커만 표시됩니다. 옥외 설비 바로가기는 해당 설비 구분을 자동 선택한 뒤 대상 마커를 클릭·강조하고 정보 카드를 표시합니다.
5. 소방설비는 기존 소화기/소화전/수신기/펌프 메뉴에서 관리합니다.
6. 기타설비는 에어컨/정수기 메뉴에서 관리합니다.
7. 목록에서 설비 행을 클릭하면 상세 정보와 점검 이력을 볼 수 있습니다.
8. 권한이 있는 사용자는 추가/수정/삭제/점검 및 이미지 업로드를 수행할 수 있습니다.
9. 수신기/소방펌프 수정 모달의 점검 이력 영역에서 기존 내역은 `수정` 또는 `삭제`할 수 있고, 신규 행은 `추가`할 수 있습니다.
10. 등록/수정 화면에서 도면을 클릭해 X/Y 좌표를 선택할 수 있습니다.

## Deployment / Runtime
- **Runtime**: Spring Boot 3.2.5 + Java 21
- **Build**: `./gradlew :app:bootJar`
- **Process Manager**: PM2 (`platform`)
- **Start script**: `/home/user/webapp/start-app.sh`
- **Last Updated**: 2026-06-09

## Current Status
- 기타설비 도메인/API/화면 기본 구현 완료
- `V15__facility_management_system.sql`로 메뉴/역할/테이블 migration 추가
- 빌드 검증 완료
- 수신기/소방펌프 대시보드 그래프 4상태 반영 완료(요정비/교체필요 색상은 노란색 계열 적용)
- 수신기/소방펌프 점검 이력 삭제 API 및 수정 모달 삭제 버튼 구현 완료
- 이상설비 바로가기 마커 자동 선택 안정화 및 대시보드 상세 모달 확대 완료
- 층별 도면의 옥외 항공사진 전체 보기 축소율 개선 완료
- 층별 도면 옥외 선택 시 층 드롭다운을 소화기/소화전/수신기·소방펌프 설비 구분으로 전환하고 선택 구분 마커만 표시하도록 개선 완료
- 수신기/소방펌프 `요정비` 상태를 대시보드 이상설비 목록/카운트에 포함하도록 개선 완료

## Recommended Next Steps
- 실제 사용자 계정별 역할 부여 후 메뉴 노출 및 API 권한 검증
- 에어컨/정수기 실데이터 등록 테스트
- 운영 DB 적용 시 `mysql --default-character-set=utf8mb4` 사용 권장
