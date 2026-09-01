# module-safety SQL

안전 작업방식 매뉴얼(SAFETY) 모듈의 DB 스크립트. 플랫폼과 **같은 DB** 를 쓴다(`platform_db`).
`ddl-auto=none` 이므로 아래 순서로 직접 실행한다.

## 실행 순서

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | SAFETY 테이블 4개 (구조만). ddl-auto=none 이라 반드시 먼저 실행 |
| 2 | `02_menu.sql` | 플랫폼 메뉴 등록 (SAFETY_MGMT 그룹 + 페이지 2개) + 역할별 노출 권한 |
| 3 | `03_perm_code.sql` | 공통코드 `SAFETY_PERM` (SAFETY 관리자 명단, 기존 ROLE_ADMIN 계정으로 자동 시딩) |
| 4 | `04_data.sql` | (선택) 최상위 분류 데모 데이터 1건 |

```bash
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 01_schema.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 02_menu.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 03_perm_code.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 04_data.sql
```

> ⚠️ **반드시 `--default-character-set=utf8mb4` 옵션을 붙여서 실행할 것.**
> mysql 클라이언트의 기본 접속 캐릭터셋은 `latin1` 이라, 이 옵션 없이 실행하면
> UTF-8로 저장된 한글 리터럴(`INSERT ... VALUES ('안전작업방식 매뉴얼', ...)` 등)이
> latin1로 잘못 해석되어 DB에 깨진 상태(mojibake)로 저장된다. 컬럼 자체는 `utf8mb4`라
> 오류 없이 들어가 버리므로 반드시 옵션을 챙겨야 한다.

네 파일 모두 재실행해도 안전하다(표는 `CREATE TABLE IF NOT EXISTS`, 메뉴는 삭제 후 재삽입,
권한 명단/분류는 없을 때만 추가).

## 테이블 (4개, 플랫폼 DB 공용)

| 테이블 | 설명 |
|--------|------|
| `safety_manual_category` | 분류(자기참조 트리, `PARENT_ID`) |
| `safety_manual` | 매뉴얼 (원본 엑셀의 시트 1개 = 매뉴얼 1개) |
| `safety_manual_step` | 매뉴얼 단계(원본 엑셀의 행 1개 = 1건) — 공정순서/위험요인/안전보호구/비고 |
| `safety_manual_step_photo` | 단계별 사진 (메타데이터만, 실제 파일은 디스크 `safety.upload-dir`) |

모든 테이블은 공통 감사(Audit) 컬럼 7개를 갖는다: `CREATED_AT`/`CREATED_BY`/`UPDATED_AT`/`UPDATED_BY`/
`DELETED_YN`/`DELETED_AT`/`DELETED_BY`. `CREATED_BY`/`UPDATED_BY`/`DELETED_BY` 는 core_user FK 가
아니라 플랫폼 로그인 ID를 담는 문자열(varchar(50))이다.

### 삭제 정책 (소프트 삭제)

물리 `DELETE` 는 사용하지 않는다. `delete()` 비즈니스 메서드로 `DELETED_YN='Y'` 처리만 하며,
목록/검색 조회는 모두 `DELETED_YN='N'` 조건으로 걸러진다.

- `SafetyManualCategory` — 하위 분류나 매뉴얼이 남아있으면 삭제 거부(가드)
- `SafetyManual` — 삭제 시 소속 `SafetyManualStep` 도 함께 소프트 삭제(cascade)
- `SafetyManualStep` / `SafetyManualStepPhoto` — 개별 소프트 삭제

## 관리자 권한 (SAFETY_PERM)

`SafetyPermission`(`@Component("safetyPerm")`)이 아래 두 조건 중 하나를 만족하면 관리자로 판정한다:

1. 플랫폼 `ROLE_ADMIN` 역할 보유자 (항상 허용)
2. 공통코드 그룹 `SAFETY_PERM` 에 로그인 ID가 등록된 사용자

운영 중 관리자 추가/제거는 플랫폼 "공통코드 관리" 화면 → `SAFETY_PERM` 그룹에서 코드 추가/비활성화로 처리한다.
(코드는 30초 캐시되므로 반영까지 최대 30초 소요될 수 있음)

## 메뉴 구조

```
SAFETY_MGMT (그룹, URL 없음)               "안전작업방식 매뉴얼"
 ├─ SAFETY_CATEGORY  /safety/index.html    "분류/매뉴얼 목록"   (ROLE_ADMIN/MANAGER/USER)
 └─ SAFETY_UPLOAD    /safety/upload.html   "엑셀 일괄업로드"    (ROLE_ADMIN/MANAGER 만)
```

SPA(`app/src/main/resources/static/index.html`)는 `menuUrl` 이 `/safety/` 로 시작하는 메뉴를
`safety::` 접두사로 감지해 iframe 으로 로드한다(KIMS 모듈과 동일한 패턴, `navigateToSafetyPage()`).

## API 엔드포인트 (`/safety-api/**`)

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/safety-api/categories` | 분류 트리 조회 | 인증 사용자 |
| GET | `/safety-api/categories/{id}` | 분류 상세 | 인증 사용자 |
| POST/PUT/DELETE | `/safety-api/categories[/{id}]` | 분류 등록/수정/삭제 | 관리자(safetyPerm) |
| GET | `/safety-api/manuals` | 매뉴얼 검색(키워드/분류, 페이징) | 인증 사용자 |
| GET | `/safety-api/categories/{categoryId}/manuals` | 분류별 매뉴얼 목록 | 인증 사용자 |
| GET | `/safety-api/manuals/{manualId}` | 매뉴얼 상세(단계+사진 포함) | 인증 사용자 |
| POST/PUT/DELETE | `/safety-api/manuals[/{id}]` | 매뉴얼 등록/수정/삭제 | 관리자 |
| POST | `/safety-api/manuals/{manualId}/steps` | 단계 추가 | 관리자 |
| PUT/DELETE | `/safety-api/steps/{stepId}` | 단계 수정/삭제 | 관리자 |
| POST | `/safety-api/steps/{stepId}/photos` | 사진 업로드(multipart) | 관리자 |
| GET | `/safety-api/photos/{photoId}/view` | 사진 원본 조회 | **공개**(인증 불필요, `<img>` 태그용) |
| DELETE | `/safety-api/photos/{photoId}` | 사진 삭제 | 관리자 |
| POST | `/safety-api/excel-upload/preview` | 엑셀 업로드 1단계: 형식 확인(DB 미반영) | 관리자 |
| POST | `/safety-api/excel-upload/confirm` | 엑셀 업로드 2단계: 선택 시트 확정 반영 | 관리자 |

`/safety/**` 정적 페이지와 `/safety-api/photos/*/view` 는 `SafetySecurityConfig`(`@Order(-2)`)에서
독립된 `SecurityFilterChain` 으로 permitAll 처리한다(core의 보안 설정은 건드리지 않음).

## 엑셀 일괄업로드 (2단계)

1. **미리보기** (`POST /excel-upload/preview`, DB 쓰기 없음): 업로드한 엑셀의 각 시트를 파싱해
   시트별 인식 여부/제목/단계 수/사진 수/미리보기 라인을 반환한다.
   - 시트 헤더에 `공정 순서` 가 있으면 매뉴얼 시트로 인식(`recognized=true`)
   - 헤더에 `공정단계`(초지 개요/범례 시트) 가 있으면 **의도적으로 제외**(`recognized=false`,
     사유 표시) — 초지 시트는 매뉴얼이 아닌 개요/범례이므로 업로드 대상에서 항상 제외한다.
2. **확정 업로드** (`POST /excel-upload/confirm`, `categoryId` + 선택한 `sheetNames` CSV 전달):
   같은 파일을 다시 업로드받아 재파싱한 뒤, 선택되고 인식된 시트만 매뉴얼+단계+사진으로 실제 저장한다.
   (서버는 상태를 저장하지 않으므로 phase 1/2 모두 같은 파일을 다시 업로드해야 한다)

## 참고: 문자 인코딩(mojibake) 관련 주의사항

이 프로젝트의 SQL 파일은 모두 UTF-8로 작성되어 있다. `mysql` CLI로 직접 실행할 때
`--default-character-set=utf8mb4` 를 빠뜨리면 한글이 깨진 채로 저장되므로 반드시 챙길 것.
(과거 이슈: `sql/V2.0.1__fix_korean_mojibake.sql` 참고 — 플랫폼 초기 구축 때도 동일한 원인으로
한 차례 전체 데이터 mojibake 복구가 있었다.)
