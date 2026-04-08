# module-common SQL 스크립트

module-common 모듈에서 사용하는 DB 스키마 및 데이터 스크립트 모음입니다.

## 파일 목록

| 파일 | 설명 | 원본 |
|------|------|------|
| `V1__user_profile_schema.sql` | 부서(MOD_USER_DEPARTMENT), 프로필(MOD_USER_PROFILE) 테이블 생성 | V1.0.0 |
| `V2__user_profile_data.sql` | 부서·프로필 초기 데이터 (본사, 경영지원본부 등) | V1.0.1 |
| `V3__code_schema.sql` | 공통코드 그룹(MOD_CODE_GROUP), 상세(MOD_CODE_DETAIL) 테이블 생성 | V1.1.0 |
| `V4__code_data.sql` | 공통코드 초기 데이터 + 부서/역할 공통코드 + 메뉴 등록 | V1.1.0, V1.1.1, V1.2.0, V1.3.0 |
| `V5__dept_to_common_code.sql` | 부서 DEPT_ID → DEPT_CODE 마이그레이션 (기존 환경용) | V1.2.0 |
| `V6__code_menu.sql` | 공통코드 관리 메뉴 등록 및 역할별 접근 권한 부여 | V1.1.1 |
| `V7__role_to_common_code.sql` | 역할(Role)을 공통코드 그룹으로 전환 | V1.3.0 |

## 실행 순서

### 신규 설치 (처음부터)

core 스키마/데이터 실행 후 순서대로:

```bash
mariadb -u platform_user -p platform_db < sql/module-common/V1__user_profile_schema.sql
mariadb -u platform_user -p platform_db < sql/module-common/V2__user_profile_data.sql
mariadb -u platform_user -p platform_db < sql/module-common/V3__code_schema.sql
mariadb -u platform_user -p platform_db < sql/module-common/V4__code_data.sql
mariadb -u platform_user -p platform_db < sql/module-common/V5__dept_to_common_code.sql
mariadb -u platform_user -p platform_db < sql/module-common/V6__code_menu.sql
mariadb -u platform_user -p platform_db < sql/module-common/V7__role_to_common_code.sql
```

### 기존 환경 (이미 V1.0.0 ~ V1.3.0 실행된 경우)

이미 적용된 SQL이므로 **별도 실행 불필요**합니다.
이 디렉토리는 module-common 관련 SQL을 한곳에 모아둔 참조용입니다.

## 테이블 목록

| 테이블명 | Prefix | 설명 |
|----------|--------|------|
| `MOD_USER_DEPARTMENT` | MOD_USER_ | 부서 |
| `MOD_USER_PROFILE` | MOD_USER_ | 사용자 프로필 |
| `MOD_CODE_GROUP` | MOD_CODE_ | 공통코드 그룹 |
| `MOD_CODE_DETAIL` | MOD_CODE_ | 공통코드 상세 |
