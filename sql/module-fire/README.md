# module-fire SQL Scripts

소방 설비 관리 모듈(module-fire)의 DDL 및 초기 데이터 스크립트입니다.

## 파일 목록

| 파일 | 설명 |
|------|------|
| `01_schema.sql` | 소방 모듈 테이블, 인덱스, 뷰 생성 |
| `02_seed_data.sql` | 관리자 계정 + 건물/층 마스터 초기 데이터 |
| `02_menu_data.sql` | 소방 모듈 메뉴 데이터 |
| `V8__add_fire_manager_role.sql` | ROLE_FIRE_MANAGER(소방시설관리) 역할 및 메뉴 권한 추가 |
| `V9__rename_manager_role.sql` | ROLE_MANAGER 역할명 변경 (PS 지분 검사 매니저) |
| `V10__add_fire_user_role.sql` | ROLE_FIRE_USER(소방시설사용자) 읽기 전용 역할 및 메뉴 권한 추가 |
| `V11__add_fire_admin_code_group.sql` | 소방 관리자 권한 공통코드 추가 |
| `V12__remove_fire_user_role.sql` | ROLE_FIRE_USER 제거 및 관련 메뉴 권한 정리 |
| `V15__facility_management_system.sql` | 설비관리시스템 개편, 소방/기타설비 메뉴 분리, 에어컨/정수기 테이블 및 역할 권한 추가 |
| `V16__facility_aircon_pair_fields.sql` | 에어컨 제조사/위치/실외기 대수 등 추가 컬럼 반영 |
| `V17__simplify_facility_aircon_fields.sql` | 에어컨 입력 단순화: 설치연도, 실외기 좌표, 수량 컬럼 제거 |
| `V18__add_fire_equipment_image_paths.sql` | 수신기/소방펌프 대표사진 경로 컬럼 추가 |
| `V19__simplify_facility_water_purifier_fields.sql` | 정수기 입력 단순화: 종류를 '정수기'로 통일하고 설치일/건물/층/X/Y 중심 정책 정리 |
| `V20__fix_facility_role_duplicates.sql` | 운영 DB 역할 공통코드 중복 보정: 기존 소방시설관리 유지, 시설관리/기타시설관리 역할명 정리 |
| `V21__normalize_facility_menu_structure.sql` | 설비관리시스템 메뉴 구조 정규화 |
| `V22__add_new_map_zone_buildings.sql` | 신규 도면 구역 건물 추가 |
| `V23__add_fluidized_bed_incinerator_map_zone.sql` | 유동상소각로 도면 구역 추가 |
| `V24__add_new_incinerator_map_zone_buildings.sql` | 신설소각로 구역 건물/층 추가 |
| `V25__add_pad_tent_warehouse_map_zone_building.sql` | 패드동 천막창고 구역 추가 |
| `V26__add_fire_sprinkler.sql` | 스프링클러 마스터/점검/메뉴 추가 |
| `V27__fix_fire_sprinkler_menu_name_and_legacy_route.sql` | 스프링클러 메뉴명 및 레거시 경로 보정 |
| `V28__add_fire_sprinkler_qr_mobile_fields.sql` | 스프링클러 QR/모바일 필드 추가 |
| `V29__add_other_equipment_qr_menu.sql` | 기타설비 QR 메뉴 및 목록 API 기반 추가 |
| `V30__move_fire_qr_to_facility_root.sql` | QR코드 메뉴를 설비관리시스템 공통 메뉴로 이동 |
| `V31__add_facility_mobile_qr_workflows.sql` | 에어컨 점검 요청/QR 점검 및 정수기 QR 점검 모바일 업무 테이블 추가. 미등록 에어컨/정수기 QR은 모바일 등록 페이지에서 `facility_equipment.QR_KEY`에 연결해 사용. 모바일 QR 점검은 에어컨 점검자 이름+정상/비정상, 정수기 점검자 이름+완료/미완료만 입력하며 사진 업로드는 받지 않음. 구버전 에어컨 접수 컬럼(`REPORTER_PHONE`, `PHOTO_PATH`) 보정 포함 |
| `V32__add_other_facility_admin_code_group.sql` | 기타시설관리 권한 공통코드 `OTHER_PERM/OTHER_ADMIN` 추가 |

## 실행 순서

```
1. ../01_ddl_core.sql   -- 데이터베이스 생성 + web_user 테이블
2. 01_schema.sql        -- 소방 모듈 테이블, 인덱스, 뷰 생성
3. 02_seed_data.sql     -- 관리자 계정 + 건물/층 마스터 초기 데이터
4. V8__add_fire_manager_role.sql   -- 소방시설관리 매니저 역할 추가
5. V9__rename_manager_role.sql     -- ROLE_MANAGER 이름 변경
6. V10__add_fire_user_role.sql     -- 소방시설사용자 역할 추가
7. V11__add_fire_admin_code_group.sql -- 소방 관리자 권한 공통코드 추가
8. V12__remove_fire_user_role.sql  -- 소방시설사용자 역할 제거
9. V15__facility_management_system.sql -- 설비관리시스템/기타설비 확장
10. V16__facility_aircon_pair_fields.sql -- 에어컨 관련 컬럼 추가
11. V17__simplify_facility_aircon_fields.sql -- 에어컨 입력 단순화 컬럼 정리
12. V18__add_fire_equipment_image_paths.sql -- 수신기/소방펌프 대표사진 컬럼 추가
13. V19__simplify_facility_water_purifier_fields.sql -- 정수기 입력 단순화 및 기타설비 컬럼 주석 정리
14. V20__fix_facility_role_duplicates.sql -- ROLE 공통코드 중복 보정 및 시설관리/소방시설관리/기타시설관리 역할명 정리
15. V21__normalize_facility_menu_structure.sql -- 설비관리시스템 메뉴 구조 정규화
16. V22__add_new_map_zone_buildings.sql -- 신규 도면 구역 건물 추가
17. V23__add_fluidized_bed_incinerator_map_zone.sql -- 유동상소각로 도면 구역 추가
18. V24__add_new_incinerator_map_zone_buildings.sql -- 신설소각로 구역 건물/층 추가
19. V25__add_pad_tent_warehouse_map_zone_building.sql -- 패드동 천막창고 구역 추가
20. V26__add_fire_sprinkler.sql -- 스프링클러 테이블/메뉴 추가
21. V27__fix_fire_sprinkler_menu_name_and_legacy_route.sql -- 스프링클러 메뉴명/경로 보정
22. V28__add_fire_sprinkler_qr_mobile_fields.sql -- 스프링클러 QR/모바일 필드 추가
23. V29__add_other_equipment_qr_menu.sql -- 기타설비 QR 메뉴 추가
24. V30__move_fire_qr_to_facility_root.sql -- QR 메뉴 공통 위치 이동
25. V31__add_facility_mobile_qr_workflows.sql -- 기타설비 모바일 QR 업무 테이블 추가
26. V32__add_other_facility_admin_code_group.sql -- 기타시설관리 권한 공통코드 추가
```

## 사전 조건

- MariaDB 10.11+ (utf8mb4, InnoDB)
- `ddl-auto=none` 설정이므로 스키마 변경은 반드시 SQL로 관리

## 실행 방법

```bash
# 1. 데이터베이스 + 사용자 테이블 생성
mysql -u root platform_db < sql/01_ddl_core.sql

# 2. 소방 모듈 스키마 생성
mysql -u platform_user -p platform_db < sql/module-fire/01_schema.sql

# 3. 초기 데이터 입력
mysql -u platform_user -p platform_db < sql/module-fire/02_seed_data.sql

# 4. 역할/권한 및 설비관리시스템 migration 적용
# 한글 메뉴명 깨짐 방지를 위해 utf8mb4를 명시합니다.
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V8__add_fire_manager_role.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V9__rename_manager_role.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V10__add_fire_user_role.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V11__add_fire_admin_code_group.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V12__remove_fire_user_role.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V15__facility_management_system.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V16__facility_aircon_pair_fields.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V17__simplify_facility_aircon_fields.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V18__add_fire_equipment_image_paths.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V19__simplify_facility_water_purifier_fields.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V20__fix_facility_role_duplicates.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V21__normalize_facility_menu_structure.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V22__add_new_map_zone_buildings.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V23__add_fluidized_bed_incinerator_map_zone.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V24__add_new_incinerator_map_zone_buildings.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V25__add_pad_tent_warehouse_map_zone_building.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V26__add_fire_sprinkler.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V27__fix_fire_sprinkler_menu_name_and_legacy_route.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V28__add_fire_sprinkler_qr_mobile_fields.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V29__add_other_equipment_qr_menu.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V30__move_fire_qr_to_facility_root.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V31__add_facility_mobile_qr_workflows.sql
mysql --default-character-set=utf8mb4 -u platform_user -p platform_db < sql/module-fire/V32__add_other_facility_admin_code_group.sql
```

## 테이블 구조

| 테이블 | 설명 | 점검 방식 |
|---|---|---|
| `web_user` | 웹 사용자 (core) | - |
| `building` | 건물 마스터 | - |
| `floor` | 층 마스터 | - |
| `extinguisher_group` | 소화기 위치 그룹 | - |
| `extinguisher` | 소화기 | - |
| `extinguisher_inspection` | 소화기 점검 이력 | IS_FAULTY + FAULT_REASON |
| `fire_hydrant` | 소화전 | - |
| `fire_hydrant_inspection` | 소화전 점검 이력 | IS_FAULTY + FAULT_REASON |
| `fire_receiver` | 수신기 | - |
| `fire_receiver_inspection` | 수신기 점검 이력 | INSPECTION_STATUS + 개별 상태 컬럼 |
| `fire_pump` | 소방펌프 | - |
| `fire_pump_inspection` | 소방펌프 점검 이력 | INSPECTION_STATUS + 개별 상태 컬럼 |
| `facility_equipment` | 기타설비(에어컨/정수기) 마스터. 에어컨은 종류/제조사/상세 위치/실외기 대수/제조·설치일을 사용하고, 정수기는 종류를 '정수기'로 고정하며 설치일/건물/층/X/Y 좌표만 사용자 입력으로 사용. 미등록 QR 등록 시 QR_KEY를 스캔된 키로 저장 | - |
| `facility_equipment_inspection` | 기타설비 점검 이력 | IS_FAULTY + FAULT_REASON |
| `facility_aircon_fault_report` | 에어컨 점검 요청/모바일 QR 점검 이력 | PC: 접수자 이름 + 소속 + 고장내용 / 모바일 QR: 점검자 이름 + 정상·비정상 |
| `facility_water_disinfection` | 정수기 모바일 QR 점검 이력 | 점검일 + 점검자 + 완료·미완료 |

### 점검 방식 차이점

- **소화기/소화전**: `IS_FAULTY`(0=정상, 1=비정상) + `FAULT_REASON`(불량 사유)
- **수신기/소방펌프**: `INSPECTION_STATUS`(NORMAL/ABNORMAL) + 개별 항목별 상태 컬럼 + `NOTE`(비고)
- **기타설비(정수기)**: `IS_FAULTY`(0=정상, 1=비정상) + `FAULT_REASON`(고장 사유), 최근 12건 이력 유지
- **기타설비(에어컨)**: 목록/상세에서 최종 점검일·점검자와 점검 이력 입력을 표시하지 않고, PC `점검 요청`은 `facility_aircon_fault_report`에 접수자 이름·소속·고장내용을 저장합니다. 모바일 QR 점검은 외부 업체용으로 점검자 이름과 정상/비정상만 저장하며 사진 업로드를 받지 않습니다.
- **기타설비(정수기 모바일 QR)**: 외부 업체용 QR 점검은 점검자 이름과 완료/미완료만 저장하며 사진 업로드를 받지 않습니다. 등록/수정 대표사진 업로드는 유지합니다.

## 뷰

| 뷰 | 설명 | 점검 관련 컬럼 |
|---|---|---|
| `vw_extinguisher_list` | 소화기 목록 (최종 점검 포함) | LAST_IS_FAULTY, LAST_FAULT_REASON |
| `vw_fire_receiver_list` | 수신기 목록 (최종 점검 포함) | LAST_INSPECTION_STATUS, LAST_INSPECTION_NOTE |
| `vw_fire_pump_list` | 소방펌프 목록 (최종 점검 포함) | LAST_INSPECTION_STATUS, LAST_INSPECTION_NOTE |

## 초기 데이터

### 관리자 계정
- ID: `admin` / PW: `admin1234` / 역할: `ADMIN`

### 공통코드 권한
- `FIRE_PERM/FIRE_ADMIN`: 소방시설관리 권한자 ID 목록을 `code_detail.EXTRA_VALUE1`에 콤마 구분으로 저장합니다.
- `OTHER_PERM/OTHER_ADMIN`: 기타시설관리 권한자 ID 목록을 `code_detail.EXTRA_VALUE1`에 콤마 구분으로 저장합니다. 해당 ID만 에어컨/정수기 추가, 삭제, 이동/좌표 변경, QR 확인, 정수기 점검/이력 관리 및 에어컨 점검 요청 버튼과 API를 사용할 수 있습니다.

### 건물 마스터 (10개)
| ID | 건물명 |
|----|--------|
| 1 | 복지관 |
| 2 | 관리동 |
| 3 | 제지1,2호기 |
| 4 | 제지3호기 |
| 5 | 심면펄퍼 |
| 6 | 패드동 |
| 7 | 화장지 3,6호기 |
| 8 | 화장지 4,5호기 |
| 9 | 기저귀동 |
| 99 | 옥외 |

### 층 마스터 (4개)
| ID | 층명 | 정렬 |
|----|------|------|
| 1 | 지하1층(B1) | 0 |
| 2 | 1층 | 1 |
| 3 | 2층 | 2 |
| 4 | 3층 | 3 |
