# 설비관리시스템 플랫폼

## Project Overview
- **Name**: platform / 설비관리시스템
- **Goal**: 사업장 설비, 소방설비, 기타설비, 사용자/권한, PS 지분 검사를 통합 관리하는 Spring Boot 기반 내부 업무 시스템입니다.
- **Main Features**:
  - JWT 로그인 및 사용자/역할/메뉴 권한 관리
  - 소방설비 관리: 소화기, 스프링클러, 소화전, 수신기, 소방펌프, QR, 모바일 점검
  - 스프링클러 목록 및 QR/모바일 점검 관리: 메뉴 전용 투명 아이콘, 목록/소화기 스타일 상세 모달/등록/수정/삭제, 대표 이미지 1장 업로드·덮어쓰기, 도면 클릭 기반 X/Y 좌표 선택, 상세 이미지·도면 클릭 확대 및 마우스 휠 확대/축소, QR 발급, QR 관리 페이지 내 상세 단독 모달, 모바일 QR 등록/점검, 미등록 QR 등록 화면의 건물/층 분리 선택 및 모바일 도면 두 손가락 확대/축소·드래그 이동, 90일 주기 점검필요 판정, 양호/불량 체크리스트, 고장 필터, 소화기 목록과 동일한 상단 버튼/검색 필터 배치 및 최근 1년 점검 이력 XLSX 다운로드
  - 설비관리시스템 공통 메뉴: `도면 (메인)`, `층별 도면`, 소방설비 `QR코드`는 소방설비/기타설비 하위가 아니라 `설비관리시스템` 바로 아래에서 관리
  - 이상설비 집계: 소화기/소화전 비정상 설비와 수신기/소방펌프 불량·요정비 설비를 이상설비에 포함
  - 이상설비 도면 바로가기: 소화기/소화전/수신기/소방펌프 모두 층별 도면(`/maps/floor.html`)에서 마커 자동 선택, 강조 및 정보 카드 표시
  - 층별 도면: 설비 구분을 페이지 최상단으로 분리하고, 건물/층/선택설비/설비 추가 컨트롤은 별도 카드 없이 대형 도면 카드 상단 여유 공간 안에 한 줄로 배치한 반응형 UI입니다. `도면`/`층별 도면` 제목·설명과 도면 안 설비 목록은 표시하지 않습니다. 도면 카드 상단은 건물/층/선택설비 드롭다운과 `설비 추가`, `확대 초기화` 버튼만 표시하며, 소화기·소화전·스프링클러·에어컨·정수기 마커를 전환 표시합니다. 옥외 도면에서는 수신기·소방펌프를 추가 노출하며, 삭제는 상단 버튼이 아니라 마커 클릭 시 표시되는 미니 모달 안에서만 수행합니다. 상세/점검/추가/수정 작업은 투명 iframe 호스트 안의 해당 작업 모달 하나만 보이도록 처리해 중복 모달처럼 보이지 않게 했습니다.
  - 메인 도면: 이동/확대/축소 시 열린 옥외 설비 미니 모달이 선택 마커를 따라 위치 재계산
  - 메인 도면 신규 구역: `화장지 원단창고`, `화장지 천막창고`, `원료장`, `유동상소각로`, `신설소각로 폐기물 처리동`, `신설소각로 증기터빈동`, `패드동 천막창고` polygon을 추가하고 각 구역의 층별 도면 이미지를 연결
  - 대시보드 이상설비 상세 모달: 넓은 전용 모달로 상세 화면 표시
  - 수신기/소방펌프 현황 그래프: 정상, 점검필요, 요정비, 불량 4상태 구분 표시(요정비/교체필요는 밝은 노란색 계열로 통일)
  - 기타설비 관리: 에어컨, 정수기 목록/상세/등록/수정/삭제/이미지 업로드, 기타설비 QR코드 조회/인쇄, 에어컨 점검 요청, 에어컨/정수기 모바일 QR 점검 처리
  - 기타설비 이미지/QR 정책: 목록에는 대표/마커 이미지를 표시하지 않고, 상세 모달에는 사진 섹션을 항상 표시해 업로드 사진이 없으면 안내 문구를 보여주며, 도면 마커는 업로드 이미지와 분리된 마커 전용 이미지만 사용. 상세 모달 QR코드는 `OTHER_ADMIN` 권한자에게만 표시
  - 에어컨 관리 단순화: 식별 No.는 자동 생성하지 않고 사용자가 직접 입력하며, 제조사, 상세 위치, 실외기 대수(최대 2대), 제조/설치월만 관리하고 실외기 좌표/연결선, 설치연도, 수량 입력은 제거
  - 정수기 관리 단순화: 정수기 종류는 `정수기` 단일 값으로 고정하고, 등록/수정 화면은 설치일, 건물, 층, X/Y 좌표만 사용자 입력으로 표시
  - 기타설비 관리: 기타설비 하위 메뉴는 에어컨/정수기 중심으로 유지하고, 대시보드/도면/층별도면은 메뉴관리·접근권한 대상에서 제거하거나 공통 도면 메뉴로 통합
  - 설비별 건물/층/도면 좌표와 비고 관리
  - 대표 이미지 1장 유지 및 최근 점검 이력 12건 관리
  - 소화기 목록은 `/fire-api/extinguishers`의 서버 페이지네이션을 사용해 200건 단위로 조회·출력하며, 전체 건수는 목록 API의 `totalElements` 기준으로 표시
  - 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 대표사진 업로드/변경 지원(최신 1장만 유지)
  - 수신기/소방펌프 점검 모달 및 모바일 QR 점검에서 정상/요정비/불량 3상태 점검과 점검사진 업로드 지원(저장된 점검 이력에 연결되며 대표사진도 최신 사진으로 갱신)
  - 소화기/소화전/스프링클러 모바일 QR 점검 사진 업로드 지원. 소화기/소화전은 기존 모바일 업로드 UI를 유지하면서 모바일 브라우저의 누락/비표준 `Content-Type`과 `heic`/`heif` 확장자를 허용하도록 보완했고, 업로드 저장 루트를 샌드박스에서도 쓰기 가능한 `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}` 정책으로 통일했으며, 스프링클러는 모바일 점검 모달에 사진 선택/촬영 입력을 추가해 최신 대표사진으로 저장
  - 수신기/소방펌프 모바일 QR 점검 사진 업로드는 모바일 브라우저의 누락/비표준 `Content-Type`과 `heic`/`heif` 확장자를 보완하고, PC 업로드와 동일한 `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}` 저장 루트 정책을 사용하며, 모바일 점검사진 조회 URL은 `<img>` 태그에서 JWT 헤더를 보낼 수 없는 제약을 고려해 파일 조회만 인증 예외로 처리
  - 수신기/소방펌프 점검 내역 엑셀 다운로드: 점검항목결과 통합 컬럼 없이 항목별 결과 컬럼과 실제 비고를 분리하고, 등록된 점검사진을 XLSX 내부 이미지로 첨부하며, 최신 점검 행은 점검사진이 없으면 설비 대표사진을 표시

## URLs
- **Local/Sandbox App**: `http://127.0.0.1:8080`
- **Current Sandbox Public URL**: `https://8080-ii9lpfcjgvbj1ses03an2-ad490db5.sandbox.novita.ai`
- **Health Check**: `/api/health`
- **설비관리시스템 공통/QR 화면**:
  - `/fire-map.html` → `설비관리시스템 > 도면 (메인)` 공통 메뉴에서 로드
  - `/maps/floor.html` → `설비관리시스템 > 층별 도면` 공통 메뉴에서 로드. 최상단 설비 구분 드롭다운과 도면 카드 상단의 건물/층/선택설비 드롭다운, 설비 추가 버튼, 우측 `확대 초기화` 버튼을 한 줄로 표시하고 도면 카드 제목과 도면 내 목록은 표시하지 않음. `domainType=fire|other`, `assetType=ext|hyd|sprinkler|receiver|pump|aircon|water`, `buildingName`, `floorName`, `focusType`, `focusId` 지원
  - `/qr` → `설비관리시스템 > QR코드` 공통 메뉴에서 QR코드 발급/확인 화면 로드. QR 페이지 접속 자체는 허용하되, 좌측 `소방설비` 버튼은 `FIRE_PERM/FIRE_ADMIN.EXTRA_VALUE1`, `기타설비` 버튼은 `OTHER_PERM/OTHER_ADMIN.EXTRA_VALUE1`에 현재 로그인 ID가 등록된 경우에만 표시합니다. 소방설비는 소화기·소화전·수신기·소방펌프·스프링클러 QR을 표시하고 기타설비는 에어컨·정수기 등록 설비 QR을 표시. 에어컨/정수기 QR은 외부 업체용 모바일 점검 페이지로 연결합니다.
- **소방설비 화면**:
  - `/extinguishers.html`, `/sprinklers.html`, `/hydrants.html`, `/receivers.html`, `/pumps.html`
  - `/fire/sprinklers` → `sprinklers.html` 메뉴 URL 매핑
- **소방설비 API**:
  - `/fire-api/sprinklers`: 스프링클러 목록/상세/등록/수정/삭제
  - `/fire-api/sprinklers/{id}/image`: 스프링클러 대표 이미지 업로드/교체. 이미지는 1개만 유지되며 `sprinkler-{id}.{ext}` 파일명으로 기존 파일을 덮어씀
  - `/fire-api/sprinklers/files/{filename}`: 스프링클러 대표 이미지 조회
  - `/fire-api/sprinklers/{id}/inspect`: 스프링클러 당일 점검 등록
  - `/fire-api/sprinklers/{id}/inspections`, `/fire-api/sprinklers/{id}/inspections/{inspectionId}`: 점검 이력 추가/수정/삭제
  - `/fire-api/sprinklers/inspections/export-all`: 스프링클러 점검 이력 XLSX 다운로드
  - `/fire-api/qr/list`, `/fire-api/qr/image?type=spk&id={qrKey}`, `/fire-api/qr/unregistered-serials?sprinklerCount={count}`: 스프링클러 QR 조회/이미지/미등록 QR 생성
  - `/fire-api/minspection/sprinklers/by-key?key={qrKey}`, `/fire-api/minspection/sprinklers/register`, `/fire-api/minspection/sprinklers/{id}`, `/fire-api/minspection/sprinklers/{id}/inspect`, `/fire-api/minspection/sprinklers/{id}/image`: 모바일 스프링클러 등록/점검/사진 업로드 API
  - `/minspection/sprinklers/{qrKey}`: 스프링클러 모바일 QR 등록/점검 페이지
- **기타설비 화면**:
  - `/facility/air-conditioners`
  - `/facility/water-purifiers`
  - `/facility/qr`
- **기타설비 API**:
  - `/facility-api/air-conditioners`
  - `/facility-api/water-purifiers`
  - `/facility-api/qr/list`: 기타설비 QR 목록 조회
  - `/facility-api/qr/unregistered-keys?airconCount={count}&waterCount={count}`: 에어컨/정수기 미등록 QR 키 생성
  - `/fire-api/qr/image?type=aircon&id={qrKey}`, `/fire-api/qr/image?type=water&id={qrKey}`: 기타설비 QR 이미지 생성. QR 스캔 대상은 각각 `/minspection/air-conditioners/{qrKey}`, `/minspection/water-purifiers/{qrKey}`
  - `/minspection/air-conditioners/{qrKey}`: 등록 QR은 모바일 에어컨 점검(점검자 이름, 정상/비정상), 미등록 QR은 에어컨 신규 등록 페이지
  - `/minspection/water-purifiers/{qrKey}`: 등록 QR은 모바일 정수기 점검(점검자 이름, 완료/미완료), 미등록 QR은 정수기 신규 등록 페이지
  - `/facility-api/mobile/air-conditioners/by-key?key={qrKey}`, `/facility-api/mobile/air-conditioners/{qrKey}/register`, `/facility-api/mobile/air-conditioners/{qrKey}/fault-reports`: 모바일 에어컨 설비 조회/QR 등록/점검 저장 API
  - `/facility-api/mobile/water-purifiers/by-key?key={qrKey}`, `/facility-api/mobile/water-purifiers/{qrKey}/register`, `/facility-api/mobile/water-purifiers/{qrKey}/disinfections`: 모바일 정수기 설비 조회/QR 등록/점검 저장 API
  - `/facility-api/mobile/files/{aircon-faults|water-disinfections}/{filename}`: 과거 모바일 QR 업무 사진 조회 호환 endpoint

## Data Architecture
- **Database**: MariaDB (`platform_db`)
- **DDL mode**: `spring.jpa.hibernate.ddl-auto=none`; schema changes are managed by SQL scripts.
- **New Tables**:
  - `fire_sprinkler`: 스프링클러 마스터. `SPK-000001` 형식의 ID, 건물/층, X/Y 좌표, 비고, QR_KEY, IMAGE_PATH, 활성 여부 관리
  - `fire_sprinkler_inspection`: 스프링클러 점검 이력. 배관 5개 항목, 헤드 반사판 1개 항목, 제품 이격거리 1개 항목을 `NORMAL`/`FAULTY`로 저장하고 비고와 점검자 정보를 관리
  - `facility_equipment`: 에어컨/정수기 공통 설비 마스터. 에어컨은 사용자가 직접 입력한 `SERIAL_NUMBER`, `EQUIPMENT_TYPE`, `MANUFACTURER`, `LOCATION_DESCRIPTION`, `OUTDOOR_UNIT_COUNT`, `MANUFACTURE_DATE` 중심으로 관리하며 `INSTALLATION_YEAR`, `OUTDOOR_X`, `OUTDOOR_Y`, `QUANTITY`는 `V17__simplify_facility_aircon_fields.sql`에서 제거. 정수기는 `V19__simplify_facility_water_purifier_fields.sql` 기준으로 `EQUIPMENT_TYPE='정수기'` 고정, `MANUFACTURE_DATE`를 설치일로 사용하고 사용자 입력은 건물/층/X/Y 좌표와 설치일만 받음. `IMAGE_PATH`는 사용자가 업로드한 이미지 경로만 저장하며, 도면 마커 이미지는 별도 마커 전용 아이콘을 사용
  - `facility_equipment_inspection`: 기타설비 점검 이력
  - `facility_aircon_fault_report`: 에어컨 점검 요청/모바일 QR 점검 기록. PC 점검 요청은 접수자 이름, 소속, 고장내용을 저장하고, 모바일 QR 점검은 점검자 이름과 정상/비정상 결과를 저장
  - `facility_water_disinfection`: 정수기 모바일 QR 점검 기록. 점검일, 점검자, 완료/미완료 결과를 저장하며 신규 모바일 QR 점검에서는 사진 업로드를 받지 않음
- **File Storage**:
  - 기타설비 업로드: `${MODULE_FIRE_FACILITY_UPLOAD_ROOT:-/data/upload/module_fire/facility 또는 <app-working-dir>/uploads/module_fire/facility}/{air-conditioners|water-purifiers}`. `/data`가 없는 샌드박스/운영 환경에서는 쓰기 가능한 앱 작업 디렉터리 하위로 자동 fallback
  - 수신기 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/receivers`, API file path: `/fire-api/receivers/files/{filename}`
  - 소방펌프 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/pumps`, API file path: `/fire-api/pumps/files/{filename}`
  - 소화기/소화전 모바일 QR 사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/{extinguishers|hydrants}`, API file path: `/fire-api/minspection/files/{extinguishers|hydrants}/{filename}`. 모바일 브라우저의 누락/비표준 이미지 `Content-Type`과 `heic`/`heif` 확장자를 허용하며, 기존 `/data/upload/module_fire/{extinguishers|hydrants}` 파일은 조회 fallback으로 유지
  - PC 소화기/소화전 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/{extinguishers|hydrants}`, API file path: `/fire-api/{extinguishers|hydrants}/files/{filename}`. 모바일 QR 업로드와 같은 쓰기 가능한 저장 루트를 사용하며, 기존 `/data/upload/module_fire/{extinguishers|hydrants}` 파일은 조회 fallback으로 유지
  - 수신기/소방펌프 점검사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/{receiver-inspections|pump-inspections}`. 모바일 QR 업로드도 동일 루트를 사용하며, 기존 `/data/upload/module_fire/{receiver-inspections|pump-inspections}` 파일은 조회 fallback으로 유지
  - 스프링클러 대표사진 업로드: `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/sprinklers`, API file path: `/fire-api/sprinklers/files/{filename}`. 파일명은 `sprinkler-{id}.{ext}`로 저장해 같은 확장자는 덮어쓰고, 확장자 변경 시 기존 이미지 파일을 삭제해 설비별 1장만 유지. 모바일 QR 점검 사진도 같은 저장소와 조회 URL을 사용해 최신 대표사진으로 갱신
  - 스프링클러 좌측 메뉴 전용 투명 마스크 아이콘: `/images/sprinkler-menu.png` (`currentColor`로 사이드바 SVG 아이콘과 동일 색상 적용)
  - 스프링클러 페이지 기본 이미지: `/images/sprinkler.png`
  - 스프링클러 도면상 마커 전용 아이콘: `/images/sprinkler-marker.png`
  - 층별 도면 정적 이미지: `/images/tissue_raw_warehouse_1F.jpg`, `/images/tissue_tent_warehouse_1F.jpg`, `/images/raw_material_yard_1F.jpg`, `/images/fluidized_bed_incinerator_1F.png`, `/images/fluidized_bed_incinerator_2F.png`, `/images/fluidized_bed_incinerator_3F.png`, `/images/new_incinerator_waste_B1.png`, `/images/new_incinerator_waste_1F.png`, `/images/new_incinerator_waste_2F.png`, `/images/new_incinerator_waste_3F.png`, `/images/new_incinerator_waste_4F.png`, `/images/new_incinerator_turbine_1F.png`, `/images/new_incinerator_turbine_2F.png`, `/images/pad_tent_warehouse_1F.jpg`
  - API file path: `/facility-api/{kind}/files/{filename}`
  - 에어컨/정수기 미등록 QR 등록 대표사진도 기타설비 대표사진 저장 정책과 같은 위치에 저장되며 등록 응답의 `imagePath`로 즉시 조회 가능
  - 기타설비 모바일 QR 점검 페이지는 사진 업로드를 받지 않습니다. 등록/수정 화면의 대표사진 업로드는 유지되며, 과거 정수기 소독 완료 사진 조회 파일은 `/facility-api/mobile/files/water-disinfections/{filename}` 호환 endpoint로 조회할 수 있습니다.

## Role / Menu Access
- `ROLE_FACILITY_MANAGER`: 시설관리 — 소방설비 + 기타설비 전체 접근
- `ROLE_FIRE_MANAGER`: 소방시설관리 — 기존 소방설비 전용 역할 유지
- `ROLE_EQUIPMENT_MANAGER`: 기타시설관리 — 기타설비만 접근
- `ROLE_ADMIN`: 전체 접근
- `FIRE_PERM/FIRE_ADMIN`: `code_detail.EXTRA_VALUE1`에 콤마 구분 사용자 ID를 등록해 소방설비 추가/수정/삭제/점검/QR 확인 권한을 제어합니다.
- `OTHER_PERM/OTHER_ADMIN`: `V32__add_other_facility_admin_code_group.sql`로 추가된 기타시설관리 권한입니다. `code_detail.EXTRA_VALUE1`에 콤마 구분 사용자 ID를 등록하며, 등록된 사용자만 에어컨/정수기 추가, 삭제, 좌표 이동/변경, QR 확인, 정수기 점검/이력 관리 및 에어컨 점검 요청 버튼과 API를 사용할 수 있습니다.
- 설비관리시스템 메뉴는 `FIRE_MGMT`를 상위 메뉴로 유지하되 이름을 “설비관리시스템”으로 변경했습니다.
- `V30__move_fire_qr_to_facility_root.sql` 기준으로 `설비관리시스템` 바로 아래 메뉴는 `도면 (메인)`(`FIRE_MAP`) → `층별 도면`(`FIRE_FLOOR`) → `QR코드`(`FIRE_QR`) → `소방설비`(`FIRE_EQUIPMENT_GROUP`) → `기타설비`(`OTHER_EQUIPMENT_GROUP`) 순서로 관리합니다. `FIRE_DASHBOARD`, `OTHER_DASHBOARD`, `OTHER_MAP`, `OTHER_FLOOR`는 메뉴관리/접근권한 대상에서 제거합니다.
- `V29__add_other_equipment_qr_menu.sql`로 기타설비 하위 `QR코드` 메뉴(`OTHER_QR`, `/facility/qr`)를 추가하고 `ROLE_ADMIN`, `ROLE_FACILITY_MANAGER`, `ROLE_EQUIPMENT_MANAGER`에 권한을 부여합니다. `ROLE_FIRE_MANAGER`에는 기타설비 QR 권한을 부여하지 않습니다.
- 운영 DB에서 역할 공통코드가 중복되어 접근 권한 화면에 `소방시설관리`가 2개 표시되는 경우 `V20__fix_facility_role_duplicates.sql`로 ROLE 그룹/코드 중복을 정리하고 위 3개 설비 역할명을 보정합니다.

## User Guide
1. 로그인 후 `설비관리시스템` 메뉴로 이동합니다.
2. `설비관리시스템` 바로 아래의 공통 메뉴 `도면 (메인)`, `층별 도면`, `QR코드`를 확인합니다. QR코드는 소방설비 그룹 하위가 아니라 도면 메뉴들과 같은 상위 레벨에서 표시됩니다.
3. `소방설비` 그룹에서는 소화기, 스프링클러 목록, 소화전, 수신기, 소방펌프 메뉴를 통해 설비 목록, 상세, 등록/수정, 점검 이력을 관리합니다. 스프링클러 목록은 소화기 목록과 동일하게 상단 요약 카드 우측에 상태 필터, `📥 엑셀`, `스프링클러 추가` 버튼을 배치하고, 검색 필터 카드는 검색어/건물/층/검색/초기화만 표시합니다. 엑셀 버튼은 최근 1년 점검 이력을 XLSX로 다운로드합니다. 스프링클러 상세 모달은 소화기 상세와 동일한 카드형 기본정보/점검정보/이미지/도면/QR/점검이력 구조로 표시하되, 점검 이력은 읽기 전용으로만 제공합니다. 이미지 또는 도면을 클릭하면 확대 모달에서 마우스 휠로 확대·축소하고 드래그로 이동할 수 있습니다. 스프링클러 추가/수정 모달에서는 대표 이미지 파일을 선택해 1장만 저장/교체할 수 있고, 새 이미지를 선택하면 기존 이미지를 덮어씁니다. 건물/층 선택 아래의 `도면 위치 선택`에서는 도면을 클릭해 X/Y 좌표를 자동 입력할 수 있습니다. QR 메뉴의 좌측 설비 선택 카드에서는 권한이 있는 설비 구분만 표시합니다. `FIRE_ADMIN` 권한이 있으면 `소방설비` 버튼과 소화기, 소화전, 수신기, 소방펌프, 스프링클러 QR 조회/인쇄가 표시되고, `OTHER_ADMIN` 권한이 있으면 `기타설비` 버튼과 에어컨, 정수기 등록 설비 QR 조회/인쇄가 표시됩니다. 둘 다 없으면 QR 페이지 접근은 가능하지만 설비 구분 버튼과 QR 목록은 표시하지 않습니다. 등록된 스프링클러 QR 카드를 클릭하면 페이지 이동 없이 QR 화면 안의 단일 상세 모달만 표시합니다. 기타설비 QR 이미지는 에어컨/정수기 외부 업체용 모바일 점검 페이지로 연결됩니다. QR 관리 페이지에서 에어컨/정수기도 미등록 QR을 생성할 수 있으며, 미등록 QR을 스캔하면 각 모바일 등록 페이지가 열리고 등록 완료 후 동일 QR이 점검 페이지로 전환됩니다. QR 상세 모달의 이미지, 도면, QR도 소화기 상세 모달과 동일하게 클릭 확대, 마우스 휠 확대·축소, 드래그 이동을 지원하며 도면 확대 시 스프링클러 위치 마커가 함께 표시됩니다. 미등록 QR을 스캔하면 `/minspection/sprinklers/{qrKey}`에서 다른 모바일 설비와 동일한 색상 테마로 건물과 층을 각각 선택하며, 실제 도면이 있는 건물/층만 표시한 뒤 모바일 등록 후 바로 점검할 수 있습니다. 이 모바일 등록 화면의 도면 위치 선택 영역은 한 손가락 탭 좌표 선택을 유지하면서 두 손가락 pinch 확대/축소, 확대 후 드래그 이동, 확대/이동 상태를 반영한 좌표 보정을 지원합니다. 최종 점검일로부터 90일 이상 지나면 점검필요로 표시됩니다.
4. `기타설비` 그룹에서는 에어컨, 정수기, QR코드 메뉴를 표시합니다. 기타설비 하위의 대시보드/도면(메인)/층별 도면 가상 메뉴는 제거되었고, QR코드 메뉴는 메뉴관리/접근권한 관리 대상입니다.
5. 대시보드 메뉴(`FIRE_DASHBOARD`, `OTHER_DASHBOARD`)는 메뉴관리·접근권한 대상에서 제거되었으며, 이전 세션에 삭제된 페이지가 남아 있으면 기본 대시보드로 자동 복귀합니다.
6. 층별 도면에서는 맨 위의 `설비 구분`을 먼저 선택하고, 도면 카드 상단에서 `건물`, `층`, `선택설비`를 선택합니다. 소방설비는 소화기/소화전/스프링클러를 표시하고, 옥외 도면에서는 수신기/소방펌프도 선택설비에 추가됩니다. 기타설비를 선택하면 에어컨/정수기만 표시됩니다. 도면 카드 상단에는 `도면` 제목 없이 건물/층/선택설비 드롭다운과 설비 추가, 확대 초기화 버튼만 한 줄로 표시하며, 별도 컨트롤 카드는 없습니다. 도면 영역은 목록 없이 크게 표시됩니다. `설비 추가`는 현재 선택설비를 현재 도면에서 클릭한 위치에 추가합니다. 설비 삭제는 상단 버튼이 아니라 마커 클릭 시 나타나는 미니 모달의 `삭제` 버튼으로 수행하고, 저장/점검/삭제 후 마커가 갱신됩니다.
7. 도면의 설비 마커를 클릭하면 미니 모달에서 상세/점검/수정/삭제 작업을 바로 선택할 수 있습니다.
8. 권한이 있는 사용자는 추가/수정/삭제/정수기 점검/에어컨 점검 요청 및 이미지 업로드를 수행할 수 있습니다. 에어컨 식별 No.는 자동 생성되지 않으므로 등록/수정 시 반드시 직접 입력해야 합니다. 에어컨 목록의 관리 버튼은 기존 `점검` 대신 `점검 요청`으로 표시되며 접수자 이름, 소속, 고장내용만 입력합니다. 정수기는 등록/수정 시 설치일, 건물, 층, X/Y 좌표만 입력하면 되며 종류는 자동으로 `정수기`로 저장됩니다.
9. 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에서 사진 파일을 선택하면 해당 설비의 대표사진이 최신 1장으로 교체됩니다.
10. 수신기/소방펌프 수정 모달의 점검 이력 영역에서는 기존 내역 `수정`/`삭제`와 신규 행 `추가`만 수행하며, 점검 이력 편집 테이블에는 사진 업로드 입력을 두지 않습니다. 점검사진은 별도 `점검` 모달에서 저장 시 업로드됩니다.
11. 모바일 QR 점검에서 소화기(`/minspection/extinguishers/{qrKey}`), 소화전(`/minspection/hydrants/{qrKey}`), 스프링클러(`/minspection/sprinklers/{qrKey}`)는 점검 저장 후 선택한 사진을 최신 설비 이미지로 저장합니다. 수신기/소방펌프(`/minspection/receivers/{qrKey}`, `/minspection/pumps/{qrKey}`)는 각 점검 항목별로 `정상`, `요정비`, `불량` 중 하나를 선택하며, 요정비/불량 항목은 자동 요약되고 모바일에서 촬영/선택한 사진은 최신 점검 이력과 설비 대표사진에 연결됩니다. 에어컨(`/minspection/air-conditioners/{qrKey}`)은 점검자 이름과 `에어컨이 정상 작동 합니까?(냉풍 및 실내 온도 하강 확인)`의 정상/비정상만 저장하고, 정수기(`/minspection/water-purifiers/{qrKey}`)는 점검자 이름과 `점검을 완료 하셨습니까.`의 완료/미완료만 저장합니다. 에어컨/정수기 QR 점검 페이지에는 사진 업로드 입력을 두지 않습니다.
12. 수신기/소방펌프 점검 내역의 `엑셀 다운로드`를 누르면 조회 기간 내 이력이 XLSX로 저장됩니다. 전원/스위치 등 점검 결과는 항목별 컬럼으로 분리되고, 비고에는 실제 점검 비고만 표시되며 등록된 점검사진은 사진 컬럼에 첨부됩니다.
13. 등록/수정 화면에서 도면을 클릭해 X/Y 좌표를 선택할 수 있습니다. 에어컨도 실내기 위치만 지정하며, 실외기는 좌표 연결 없이 대수만 입력합니다.

## Deployment / Runtime
- **Runtime**: Spring Boot 3.2.5 + Java 21
- **Build**: `./gradlew :app:bootJar`
- **Process Manager**: PM2 (`platform`)
- **Start script**: `/home/user/webapp/start-app.sh`
- **Last Updated**: 2026-07-10

## Current Status
- 기타설비 도메인/API/화면 기본 구현 완료
- `V15__facility_management_system.sql`로 설비관리시스템 상위 메뉴와 소방설비/기타설비 그룹을 추가
- `V20__fix_facility_role_duplicates.sql`로 운영 DB의 ROLE 공통코드 중복 표시 보정 추가: 기존 `ROLE_FIRE_MANAGER`/소방시설관리는 유지하고 `ROLE_FACILITY_MANAGER`/시설관리, `ROLE_EQUIPMENT_MANAGER`/기타시설관리를 명확히 정리
- `V30__move_fire_qr_to_facility_root.sql`로 `설비관리시스템` 하위 정렬을 `도면 (메인)` → `층별 도면` → `QR코드` → `소방설비` → `기타설비` 순서로 보정하고, `FIRE_QR` 권한을 `ROLE_ADMIN`, `ROLE_FACILITY_MANAGER`, `ROLE_FIRE_MANAGER`에 유지
- QR코드 화면의 좌측 설비 선택 카드 안에 `소방설비`/`기타설비` 전환 버튼을 추가했습니다. `소방설비` 버튼은 `FIRE_ADMIN`, `기타설비` 버튼은 `OTHER_ADMIN`의 `EXTRA_VALUE1` 사용자 ID 목록 기준으로만 표시합니다. `소방설비` 선택 시 기존 소화기·소화전·수신기·소방펌프·스프링클러 목록을 유지하고, `기타설비` 선택 시 에어컨·정수기 등록 설비 QR을 실제 목록으로 표시합니다. 에어컨/정수기 QR은 외부 업체용 모바일 점검 페이지로 연결됩니다.
- `index.html`에서 기타설비 하위에 프론트에서만 추가되던 대시보드/도면/층별도면 가상 메뉴 및 라우팅을 제거하고, 기타설비 QR 메뉴(`OTHER_QR`) 라우팅을 `/facility/qr`로 추가
- 에어컨 식별 No. 및 제조사/위치/실외기 대수 관리 구현 완료: 식별 No.는 자동 생성하지 않고 사용자 입력을 필수로 검증하며, 실외기 좌표/연결선, 설치연도, 수량 입력은 `V17__simplify_facility_aircon_fields.sql` 기준으로 제거
- 정수기 종류 입력 제거 및 단순 등록/수정 구현 완료: `V19__simplify_facility_water_purifier_fields.sql` 기준으로 기존 정수기 종류를 `정수기`로 통일하고, 화면 입력은 설치일/건물/층/X/Y 좌표만 표시
- 수신기/소방펌프 점검 이력 삭제 API 및 수정 모달 삭제 버튼 구현 완료
- 층별 도면의 옥외 항공사진 축소 최소값을 확대 초기화 화면 맞춤 배율로 제한하도록 개선 완료
- 층별 도면을 최상단 `설비 구분` 드롭다운과 대형 도면 카드 구조로 재구성하고, 기존 별도 `건물 / 층 / 선택설비` 컨트롤 카드는 제거했습니다. 건물/층/선택설비/설비 추가 컨트롤은 `도면` 제목 없이 도면 카드 상단에 한 줄로 배치하고, 우측에 `확대 초기화` 버튼을 둡니다. `층별 도면` 제목/설명, 도면 내부 목록, 상단 삭제/점검/수정류 버튼은 표시하지 않습니다. 도면이 등록된 건물/층만 표시하며, 소방설비는 소화기·소화전·스프링클러를 표시하고 옥외 도면에서만 수신기·소방펌프를 추가 노출합니다. 기타설비는 에어컨·정수기 마커와 추가/수정/점검 iframe 연동을 지원하며, 삭제는 마커 클릭 미니 모달에서만 수행합니다.
- 층별 도면 드래그 이동 후 불필요하게 마커 정보 카드가 닫히지 않고, 내부 도면/마커 이미지가 브라우저 기본 드래그 이미지로 같이 끌려 보이지 않도록 개선 완료
- 메인 도면의 옥외 설비 미니 모달이 도면 이동/확대/축소 시 선택 마커를 따라 이동하도록 개선 완료
- 수신기/소방펌프 점검 내역 다운로드를 CSV에서 사진 포함 XLSX로 변경하고, 점검항목결과 통합 컬럼을 제거한 뒤 각 점검 항목 결과와 실제 비고를 별도 컬럼으로 출력하도록 개선 완료
- 수신기/소방펌프 수정 모달 상단 설비 기본정보 영역에 대표사진 업로드를 추가하고, 설비별 대표사진은 최신 1장만 유지하도록 개선 완료
- 수신기/소방펌프 점검 모달 저장 시 선택 사진이 생성된 점검 이력에 안정적으로 연결되도록 업로드 대상 식별 로직 개선 완료
- 수신기/소방펌프 모바일 QR 점검에 `요정비` 선택지를 추가하고, 모바일 점검 API가 `NORMAL`/`MAINTENANCE`/`FAULTY`를 정규화해 종합 상태와 항목별 상태에 저장하도록 개선 완료
- 수신기/소방펌프 모바일 QR 점검사진 업로드가 모바일 브라우저의 누락/비표준 이미지 Content-Type과 HEIC/HEIF 파일을 처리하도록 보완하고, 저장 루트를 PC 업로드와 같은 `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}` 정책으로 통일 완료
- 소화기/소화전 모바일 QR 점검사진 업로드의 파일 검증도 모바일 브라우저의 누락/비표준 이미지 Content-Type과 HEIC/HEIF 파일을 처리하도록 보완하고, 업로드 실패 시 서버 오류 메시지를 사용자에게 표시하도록 개선 완료
- 소화기/소화전 모바일 QR 및 PC 대표사진 업로드 저장 경로를 기존 `/data/upload/module_fire` 하드코딩에서 `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}` 정책으로 통일해 샌드박스 권한 문제로 업로드 POST가 500 처리되던 문제를 해결. `/fire-api/minspection/.../image`, `/fire-api/extinguishers/{id}/image`, `/fire-api/hydrants/{id}/image`는 인증 필요를 유지하며, 실제 multipart 검증에서 무인증 401, 인증 포함 200, 반환 이미지 URL GET 200을 확인
- 스프링클러 모바일 QR 점검 모달에 사진 선택/촬영 입력과 `/fire-api/minspection/sprinklers/{id}/image` 업로드 API를 추가해 점검 저장 후 최신 대표사진으로 갱신하도록 개선 완료
- 수신기/소방펌프 점검 내역 XLSX 다운로드 시 최신 점검 행에 점검사진이 없으면 설비 대표사진을 표시하고, 현재 업로드 루트와 기존 `/data/upload/module_fire` 경로를 모두 조회하도록 개선 완료
- 소화기 목록 화면은 한 번에 전체 데이터를 병합하지 않고 `/fire-api/extinguishers?page={page}&size=200`으로 현재 페이지의 최대 200건만 출력하며, 이전/다음 및 페이지 번호 이동 UI를 제공
- SPA 내부 소방 모듈 iframe 로딩 시 버전 쿼리스트링을 갱신해 브라우저에 캐시된 이전 `extinguishers.html`이 계속 표시되는 문제를 방지
- `화장지 원단창고`, `화장지 천막창고`, `원료장`을 메인 도면 polygon 구역과 1층 층별 도면 이미지로 추가하고, `V22__add_new_map_zone_buildings.sql` 및 `FireDataInitializer`로 building 마스터를 보장
- `유동상소각로`를 메인 도면 polygon 구역과 1~3층 층별 도면 이미지로 추가하고, `V23__add_fluidized_bed_incinerator_map_zone.sql` 및 `FireDataInitializer`로 building 마스터를 보장
- `신설소각로 폐기물 처리동`을 메인 도면 polygon 구역과 B1/1~4층 층별 도면 이미지로, `신설소각로 증기터빈동`을 1~2층 층별 도면 이미지로 추가하고, `V24__add_new_incinerator_map_zone_buildings.sql` 및 `FireDataInitializer`로 building 마스터를 보장
- `패드동 천막창고`를 메인 도면 polygon 구역과 1층 층별 도면 이미지로 추가하고, `V25__add_pad_tent_warehouse_map_zone_building.sql` 및 `FireDataInitializer`로 building 마스터를 보장
- `스프링클러 목록`을 소방설비 하위 메뉴(`FIRE_SPRINKLER`)로 추가하고, `V26__add_fire_sprinkler.sql`로 `fire_sprinkler`, `fire_sprinkler_inspection`, 메뉴관리/접근권한 데이터를 반영. `V27__fix_fire_sprinkler_menu_name_and_legacy_route.sql`로 과거 `FIRE_SPRINKLER_PIPE`/`스프링쿨러 배관 목록` 메뉴를 표준 `스프링클러 목록` 메뉴와 `/fire/sprinklers` 경로로 통합
- 스프링클러 목록 UI에서 검색 카드 안에 섞여 있던 시작일·종료일·엑셀 다운로드 버튼을 제거하고, 소화기 목록과 동일하게 상단 요약 카드 우측에 상태 필터 + `📥 엑셀` + 추가 버튼을 배치하도록 정리 완료. 검색 필터는 검색어/건물/층/검색/초기화만 표시하며, 엑셀 버튼은 최근 1년 점검 이력을 다운로드
- 스프링클러 추가/수정 모달에 기존 소방설비와 동일한 `도면 위치 선택` 영역을 추가하여 건물/층 도면을 표시하고, 도면 클릭 시 좌표 X/Y가 자동 반영되도록 개선 완료
- `V28__add_fire_sprinkler_qr_mobile_fields.sql`로 `fire_sprinkler.QR_KEY`, `IMAGE_PATH`를 추가하고, QR 관리 페이지/QR 이미지/미등록 QR 생성/모바일 등록·점검 API와 `/minspection/sprinklers/{qrKey}` 모바일 페이지를 스프링클러에 연결
- 스프링클러 상세 모달을 소화기 상세와 동일한 카드/그리드 구조로 개선하고, 상세 이미지·도면·QR 클릭 확대 및 마우스 휠 확대/축소·드래그 이동을 지원하도록 개선 완료. 상세 모달의 점검 이력은 읽기 전용으로 정리
- 스프링클러 추가/수정 모달에 대표 이미지 파일 선택과 미리보기를 추가하고, 저장 후 `/fire-api/sprinklers/{id}/image`로 업로드해 `fire_sprinkler.IMAGE_PATH`를 갱신하도록 개선 완료. 파일은 `${MODULE_FIRE_UPLOAD_ROOT:-<app-working-dir>/uploads/module_fire}/sprinklers/sprinkler-{id}.{ext}`에 저장되며 같은 확장자는 덮어쓰기, 확장자 변경 시 기존 파일 삭제로 설비별 대표 이미지 1장만 유지
- 스프링클러 모바일 미등록 QR 등록 화면을 기존 모바일 설비와 동일한 보라색 계열 테마로 통일하고, 건물/층 단일 드롭다운을 건물 선택과 층 선택으로 분리. 모바일 등록에는 도면이 실제로 매핑된 건물/층 조합만 노출되며 층 선택 시 해당 도면이 표시됨. 도면 위치 선택은 모바일 두 손가락 pinch 확대/축소, 확대 상태의 드래그 이동, 확대/이동 좌표계를 보정한 한 손가락 탭 위치 선택을 지원하도록 개선 완료
- 소화기/소화전 모바일 미등록 QR 등록 화면도 백엔드 `mapOptions` 중 `planImagePath`가 있는 건물/층만 건물·층 선택에 사용하도록 보강하고, 소화전/수신기/소방펌프 옥외 모바일 등록은 하드코딩 ID 대신 백엔드가 내려주는 옥외 건물/층 마스터를 사용하도록 개선. 옥외 마스터가 없으면 등록을 막아 잘못된 기본 ID로 저장되지 않도록 처리했으며, 모바일 `mapOptions`의 도면 매핑 누락 검증 결과 `화장지 3,6호기 / 3층` 도면도 포함되도록 보정
- QR 관리 페이지에서 등록된 스프링클러 QR 카드를 클릭하면 `/sprinklers.html`로 이동하지 않고 QR 페이지 내부의 단일 `스프링클러 상세` 모달을 표시하도록 개선. iframe host 없이 기본정보/점검정보/점검이력/이미지/도면/QR을 읽기 전용으로 확인 가능하며, 도면 카드에는 중복 위치 텍스트를 표시하지 않음. 상세 모달의 이미지·도면·QR은 소화기 상세 모달과 동일하게 클릭 시 확대 모달로 열리고, 확대 화면에서 마우스 휠 확대·축소 및 드래그 이동을 지원하며 도면은 위치 마커를 함께 표시함
- 새 투명 스프링클러 이미지는 좌측 메뉴 전용 `/images/sprinkler-menu.png`로 분리하고, 좌측 메뉴 아이콘의 흰 배경 스타일을 제거. 이후 낮은 가시성과 외곽 잔상을 줄이기 위해 아이콘 alpha를 정리한 마스크로 보정하고 `currentColor` 기반 CSS mask를 적용해 다른 사이드바 아이콘과 동일 색상으로 표시. 스프링클러 목록 기본 이미지는 기존 `/images/sprinkler.png`를 유지하며, 도면상 마커는 신규 `/images/sprinkler-marker.png` 아이콘을 사용하도록 분리
- 에어컨/정수기 목록과 기타설비 층별 도면 좌측 목록에서 이미지 출력을 제거하고, 상세 모달의 업로드 이미지는 사용자가 업로드한 이미지가 있을 때만 표시하도록 변경. 기타설비 도면 마커는 업로드 이미지나 설비 기본 이미지를 사용하지 않고 마커 전용 아이콘만 사용하며, 원형 배경/크롭 프레임을 제거해 투명 외곽 여백이 보이지 않도록 개선
- `V29__add_other_equipment_qr_menu.sql`, `/facility/qr`, `/facility-api/qr/list`를 추가해 기타설비 QR코드 메뉴를 메뉴관리/접근권한과 동일하게 관리하고, QR 이미지는 `/fire-api/qr/image?type=aircon|water&id={qrKey}`에서 생성
- `V31__add_facility_mobile_qr_workflows.sql`로 에어컨 점검 요청/QR 점검(`facility_aircon_fault_report`)과 정수기 QR 점검(`facility_water_disinfection`) 테이블을 추가하고, `/facility-api/mobile/**` 및 `/minspection/air-conditioners|water-purifiers/{qrKey}` 모바일 QR 업무 흐름을 구현 완료. PC 에어컨 점검 요청 항목은 접수자 이름, 소속, 고장내용을 유지하고, 모바일 QR 점검 항목은 에어컨 점검자 이름+정상/비정상, 정수기 점검자 이름+완료/미완료만 받도록 단순화했습니다. 구버전 `REPORTER_PHONE`, `PHOTO_PATH` 컬럼 보정 SQL을 포함합니다.
- `V32__add_other_facility_admin_code_group.sql`로 `OTHER_PERM/OTHER_ADMIN` 공통코드를 추가하고, 기타설비 목록/층별 도면/상세 모달/QR 목록의 관리 버튼 및 mutation API를 `OTHER_ADMIN.EXTRA_VALUE1` 사용자 ID 목록 기준으로 제어하도록 구현 완료. 에어컨/정수기 상세 모달은 사진 섹션을 항상 표시하며, QR코드 이미지는 `OTHER_ADMIN` 권한자에게만 `/fire-api/qr/image?type=aircon|water&id={qrKey}`로 노출합니다.
- QR 관리 페이지의 미등록 QR 생성 범위를 기타설비까지 확장했습니다. 에어컨/정수기 미등록 QR은 각각 `/minspection/air-conditioners/{qrKey}`, `/minspection/water-purifiers/{qrKey}` 모바일 등록 페이지로 연결되고, 등록 API는 전달받은 QR 키를 `facility_equipment.QR_KEY`에 그대로 저장합니다. 등록 대표사진 업로드는 실제 multipart POST 200 및 반환 이미지 URL GET 200으로 검증했으며, QR 점검 페이지의 사진 업로드 입력은 제거했습니다.

## Recommended Next Steps
- 실제 사용자 계정별 역할 부여 후 `ROLE_ADMIN`, `ROLE_FACILITY_MANAGER`, `ROLE_FIRE_MANAGER`, `ROLE_EQUIPMENT_MANAGER` 메뉴 노출 및 API 권한 검증
- 운영 DB에 `V17__simplify_facility_aircon_fields.sql`, `V19__simplify_facility_water_purifier_fields.sql`, `V20__fix_facility_role_duplicates.sql`, `V21__normalize_facility_menu_structure.sql`, `V22__add_new_map_zone_buildings.sql`, `V23__add_fluidized_bed_incinerator_map_zone.sql`, `V24__add_new_incinerator_map_zone_buildings.sql`, `V25__add_pad_tent_warehouse_map_zone_building.sql`, `V26__add_fire_sprinkler.sql`, `V27__fix_fire_sprinkler_menu_name_and_legacy_route.sql`, `V28__add_fire_sprinkler_qr_mobile_fields.sql`, `V29__add_other_equipment_qr_menu.sql`, `V30__move_fire_qr_to_facility_root.sql`, `V31__add_facility_mobile_qr_workflows.sql`, `V32__add_other_facility_admin_code_group.sql` 순서 적용 후 메뉴관리/접근권한, 신규 도면 구역, 스프링클러 목록/QR/모바일 점검, 기타설비 QR 메뉴 및 에어컨/정수기 모바일 QR 업무 저장 검증
- `설비관리시스템 > 도면 (메인)`, `설비관리시스템 > 층별 도면`, `설비관리시스템 > QR코드`, `소방설비`, `기타설비`의 사이드바 정렬과 권한별 표시 확인
- 에어컨/정수기 실데이터 등록 후 공통 도면 좌표 매칭 검증
- 추가 운영 DB 반영 시 `mysql --default-character-set=utf8mb4` 사용 권장
- 스프링클러 QR 미등록 등록, 등록 QR 점검 저장, PC 목록/점검 이력 반영을 운영 계정으로 통합 검증
