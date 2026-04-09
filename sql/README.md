# FireWeb SQL Scripts

FireWeb 프로젝트의 데이터베이스 스키마 및 초기 데이터 관리 스크립트입니다.

## 디렉토리 구조

```
sql/
├── README.md                    ← 이 파일
├── 01_ddl_core.sql              ← 공통: DB 생성 + web_user 테이블
└── module-fire/
    ├── README.md                ← 소방 모듈 상세 설명
    ├── 01_schema.sql            ← 소방 모듈: 전체 테이블 + 인덱스 + 뷰
    └── 02_seed_data.sql         ← 소방 모듈: 관리자 계정 + 마스터 데이터
```

## 실행 순서

```bash
# 1단계: 공통 (데이터베이스 + 사용자 테이블)
mysql -u root < sql/01_ddl_core.sql

# 2단계: 소방 모듈 스키마
mysql -u platform_user -p platform_db < sql/module-fire/01_schema.sql

# 3단계: 초기 데이터
mysql -u platform_user -p platform_db < sql/module-fire/02_seed_data.sql
```

## 환경 정보

| 항목 | 값 |
|------|-----|
| DBMS | MariaDB 10.11+ |
| 데이터베이스명 | platform_db |
| 문자셋 | utf8mb4 / utf8mb4_general_ci |
| 엔진 | InnoDB |
| JPA ddl-auto | none (SQL로 스키마 관리) |

## 테이블 요약

| 모듈 | 테이블 | 설명 |
|------|--------|------|
| core | `web_user` | 웹 사용자 (로그인/권한) |
| fire | `building` | 건물 마스터 |
| fire | `floor` | 층 마스터 |
| fire | `extinguisher_group` | 소화기 위치 그룹 |
| fire | `extinguisher` | 소화기 |
| fire | `extinguisher_inspection` | 소화기 점검 이력 |
| fire | `fire_hydrant` | 소화전 |
| fire | `fire_hydrant_inspection` | 소화전 점검 이력 |
| fire | `fire_receiver` | 수신기 |
| fire | `fire_receiver_inspection` | 수신기 점검 이력 |
| fire | `fire_pump` | 소방펌프 |
| fire | `fire_pump_inspection` | 소방펌프 점검 이력 |

## 뷰 요약

| 뷰 | 설명 |
|-----|------|
| `vw_extinguisher_list` | 소화기 + 최종 점검 (IS_FAULTY, FAULT_REASON) |
| `vw_fire_receiver_list` | 수신기 + 최종 점검 (INSPECTION_STATUS, NOTE) |
| `vw_fire_pump_list` | 소방펌프 + 최종 점검 (INSPECTION_STATUS, NOTE) |

## 최종 업데이트

- 2026-04-09: 전체 SQL 통합 정리, 구 마이그레이션 파일 삭제
