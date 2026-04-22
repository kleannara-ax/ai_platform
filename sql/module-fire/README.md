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

## 실행 순서

```
1. ../01_ddl_core.sql   -- 데이터베이스 생성 + web_user 테이블
2. 01_schema.sql        -- 소방 모듈 테이블, 인덱스, 뷰 생성
3. 02_seed_data.sql     -- 관리자 계정 + 건물/층 마스터 초기 데이터
4. V8__add_fire_manager_role.sql   -- 소방시설관리 매니저 역할 추가
5. V9__rename_manager_role.sql     -- ROLE_MANAGER 이름 변경
6. V10__add_fire_user_role.sql     -- 소방시설사용자 역할 추가
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

# 4. 역할 추가 (소방시설관리 매니저 + 소방시설사용자)
mysql -u platform_user -p platform_db < sql/module-fire/V8__add_fire_manager_role.sql
mysql -u platform_user -p platform_db < sql/module-fire/V9__rename_manager_role.sql
mysql -u platform_user -p platform_db < sql/module-fire/V10__add_fire_user_role.sql
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

### 점검 방식 차이점

- **소화기/소화전**: `IS_FAULTY`(0=정상, 1=비정상) + `FAULT_REASON`(불량 사유)
- **수신기/소방펌프**: `INSPECTION_STATUS`(NORMAL/ABNORMAL) + 개별 항목별 상태 컬럼 + `NOTE`(비고)

## 뷰

| 뷰 | 설명 | 점검 관련 컬럼 |
|---|---|---|
| `vw_extinguisher_list` | 소화기 목록 (최종 점검 포함) | LAST_IS_FAULTY, LAST_FAULT_REASON |
| `vw_fire_receiver_list` | 수신기 목록 (최종 점검 포함) | LAST_INSPECTION_STATUS, LAST_INSPECTION_NOTE |
| `vw_fire_pump_list` | 소방펌프 목록 (최종 점검 포함) | LAST_INSPECTION_STATUS, LAST_INSPECTION_NOTE |

## 초기 데이터

### 관리자 계정
- ID: `admin` / PW: `admin1234` / 역할: `ADMIN`

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
