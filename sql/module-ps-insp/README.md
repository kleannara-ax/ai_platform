# module-ps-insp SQL Scripts

## PS 지분 검사 (점보롤 지분 검사) 모듈 DB 스크립트

### 파일 위치

```
sql/module-ps-insp/
├── 01_schema.sql            # DDL (테이블 + 인덱스) - 필수
├── 02_seed_data.sql         # 초기 샘플 데이터 (3건) - 선택
├── 03_menu_data.sql         # 메뉴 등록 (core_menu + core_role_menu) - 필수
├── 04_rename_menu.sql       # 메뉴 명칭 변경 마이그레이션 - 기배포 환경용
└── README.md                # 이 문서
```

> **참고**: `ddl-auto=none` 환경에서 DBA가 직접 실행하는 방식으로 프로젝트 루트 `sql/module-{모듈명}/`에 배치합니다.

### 실행 방법

```bash
# 1. 스키마 생성 (필수)
mysql -u {user} -p {database} < sql/module-ps-insp/01_schema.sql

# 2. 초기 데이터 삽입 (선택 - 테스트/개발 환경용)
mysql -u {user} -p {database} < sql/module-ps-insp/02_seed_data.sql
```

### 테이블 정보

| 항목 | 값 |
|------|-----|
| 테이블명 | `ps_insp_inspection` |
| Engine | InnoDB |
| Charset | utf8mb4 (utf8mb4_general_ci) |
| PK | `INSPECTION_ID` (BIGINT, AUTO_INCREMENT) |
| Unique Key | `UK_PS_INSP_MATNR_LOTNR_INDBCD` → `(MATNR, LOTNR, IND_BCD)` |

### DDL 명명 규칙

| 항목 | 규칙 | 예시 |
|------|------|------|
| 테이블명 | `lower_snake_case` | `ps_insp_inspection` |
| 컬럼명 | `UPPER_SNAKE_CASE` | `INSPECTION_ID`, `IND_BCD` |
| PK / UK 제약조건 | `UPPER_SNAKE_CASE` | `UK_PS_INSP_MATNR_LOTNR_INDBCD` |
| 인덱스 | `IDX_` 접두사 | `IDX_PS_INSP_IND_BCD` |
| 엔진 | InnoDB | `ENGINE=InnoDB` |
| 문자셋 | utf8mb4 | `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci` |
| 컬럼 코멘트 | **필수** | `COMMENT '검사 결과 PK (자동 증가)'` |

### 인덱스

| 인덱스 | 컬럼 | 용도 |
|--------|------|------|
| `IDX_PS_INSP_IND_BCD` | `IND_BCD` | 바코드 검색 |
| `IDX_PS_INSP_LOTNR` | `LOTNR` | LOT 번호 검색 |
| `IDX_PS_INSP_MATNR` | `MATNR` | 자재코드 검색 |
| `IDX_PS_INSP_WERKS` | `WERKS` | 플랜트 검색 |
| `IDX_PS_INSP_MSRM_DATE` | `MSRM_DATE DESC` | 측정일 정렬 |
| `IDX_PS_INSP_INSPECTED_AT` | `INSPECTED_AT DESC` | 검사일 정렬 |
| `IDX_PS_INSP_OPERATOR_ID` | `OPERATOR_ID` | 검사자 검색 |
| `IDX_PS_INSP_STATUS` | `STATUS` | 상태 필터 |

### 플랫폼 통합 (3줄 변경)

1. `settings.gradle` → `include 'module-ps-insp'`
2. `app/build.gradle` → `implementation project(':module-ps-insp')`
3. SQL 스크립트 실행: `sql/module-ps-insp/01_schema.sql`
