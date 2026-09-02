# module-safety SQL

안전 작업방식 매뉴얼(SAFETY) 모듈의 DB 스크립트. 플랫폼과 **같은 DB** 를 쓴다(`platform_db`).
`ddl-auto=none` 이므로 아래 순서로 직접 실행한다.

## 실행 순서

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | SAFETY 테이블 4개 (구조만). ddl-auto=none 이라 반드시 먼저 실행 |
| 2 | `02_menu.sql` | 플랫폼 메뉴 등록 (SAFETY_MGMT 그룹 + 페이지 1개 — 엑셀 업로드는 별도 페이지가 아니라 모달로 통합됨) + 역할별 노출 권한 |
| 3 | `03_perm_code.sql` | 공통코드 `SAFETY_PERM` (SAFETY 관리자 명단, 기존 ROLE_ADMIN 계정으로 자동 시딩) |
| 4 | `04_data.sql` | (선택) 최상위 분류 데모 데이터 1건 |
| 5 | `05_category_level.sql` | 분류 3단계(대/중/소) 고정 구조 도입: `LEVEL_NO` 컬럼 추가 + 기존 데이터 레벨 백필 + 소분류가 아닌 곳에 매뉴얼이 붙어 있으면 자동으로 "미분류" 하위 분류를 만들어 이동시키는 1회성 마이그레이션 |

```bash
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 01_schema.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 02_menu.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 03_perm_code.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 04_data.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 05_category_level.sql
```

> ⚠️ **반드시 `--default-character-set=utf8mb4` 옵션을 붙여서 실행할 것.**
> mysql 클라이언트의 기본 접속 캐릭터셋은 `latin1` 이라, 이 옵션 없이 실행하면
> UTF-8로 저장된 한글 리터럴(`INSERT ... VALUES ('안전작업방식 매뉴얼', ...)` 등)이
> latin1로 잘못 해석되어 DB에 깨진 상태(mojibake)로 저장된다. 컬럼 자체는 `utf8mb4`라
> 오류 없이 들어가 버리므로 반드시 옵션을 챙겨야 한다.

모든 파일은 재실행해도 안전하다(표는 `CREATE TABLE IF NOT EXISTS`, 메뉴는 삭제 후 재삽입,
권한 명단/분류는 없을 때만 추가, `05_category_level.sql`은 `ADD COLUMN IF NOT EXISTS` + 마이그레이션
프로시저를 실행 후 즉시 DROP하는 방식이라 반복 실행해도 안전하다).

## 분류 체계 (대분류/중분류/소분류 3단계 고정)

분류는 자기참조 트리(`PARENT_ID`)이지만, **딱 3단계까지만** 만들 수 있도록 서비스 계층에서 강제한다
(`LEVEL_NO`: 1=대분류, 2=중분류, 3=소분류). 예시:

```
대분류: 제지 / 화장지 / 패드 ...
  중분류: 3호기 / 4호기 / 5호기 ...
    소분류: 설비 / 안전 / 작업 / 원료 ...
```

- 모든 레벨은 관리자가 화면에서 자유롭게 추가/수정할 수 있다 (엑셀 업로드 모달 안에서도 각 레벨마다
  인라인 "+" 추가 버튼 제공).
- **매뉴얼은 반드시 소분류(3단계)에만 등록**할 수 있다. 대분류/중분류에 매뉴얼을 붙이려 하면
  `SafetyCategoryService.findActiveMinor()` 가드가 400 에러로 거부한다.
- 소분류 아래에는 4단계 하위 분류를 만들 수 없다(`SafetyCategoryService.create()` 가드).

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
 └─ SAFETY_CATEGORY  /safety/index.html    "분류/매뉴얼 목록"   (ROLE_ADMIN/MANAGER/USER)
```

엑셀 일괄업로드는 더 이상 별도 페이지(`upload.html`)가 아니라 `index.html` 안의 모달(`#excelModal`)로
통합되었다. 별도 메뉴/페이지가 없으므로 위 메뉴 1개만 존재한다.

SPA(`app/src/main/resources/static/index.html`)는 `menuUrl` 이 `/safety/` 로 시작하는 메뉴를
`safety::` 접두사로 감지해 iframe 으로 로드한다(KIMS 모듈과 동일한 패턴, `navigateToSafetyPage()`).

## API 엔드포인트 (`/safety-api/**`)

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/safety-api/categories` | 분류 트리 조회 (각 노드에 `levelNo` 포함) | 인증 사용자 |
| GET | `/safety-api/categories/children?parentId=` | 특정 부모의 하위 분류 목록(parentId 없으면 대분류 목록) — 드릴다운/엑셀 모달용 편의 API | 인증 사용자 |
| GET | `/safety-api/categories/{id}` | 분류 상세 | 인증 사용자 |
| POST/PUT/DELETE | `/safety-api/categories[/{id}]` | 분류 등록/수정/삭제 | 관리자(safetyPerm) |
| GET | `/safety-api/manuals` | 매뉴얼 검색(키워드/분류, 페이징) | 인증 사용자 |
| GET | `/safety-api/categories/{categoryId}/manuals` | 해당 분류에 **직접** 속한 매뉴얼 목록 | 인증 사용자 |
| GET | `/safety-api/manuals/by-category?categoryId=` | 분류 **하위 전체**(대/중/소 무관) 매뉴얼 목록. `categoryId` 생략 시 전체. 좌측 분류 트리에서 어느 단계를 눌러도 그 아래 매뉴얼을 모두 보여주는 데 쓴다 | 인증 사용자 |
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

프론트엔드는 `index.html` 안의 `#excelModal` 모달 하나로 아래 흐름을 처리한다(별도 페이지 없음):

1. **미리보기** (`POST /excel-upload/preview`, DB 쓰기 없음): 업로드한 엑셀의 각 시트를 파싱해
   시트별 인식 여부/제목/단계 수/사진 수/미리보기 라인을 반환한다.
   - 시트 헤더에 `공정 순서` 가 있으면 매뉴얼 시트로 인식(`recognized=true`)
   - 헤더에 `공정단계`(초지 개요/범례 시트) 가 있으면 **의도적으로 제외**(`recognized=false`,
     사유 표시) — 초지 시트는 매뉴얼이 아닌 개요/범례이므로 업로드 대상에서 항상 제외한다.
   - 프론트엔드는 인식된 시트(`recognized=true`)의 체크박스를 **기본적으로 체크된 상태**로 보여주고,
     사용자는 확인/해제만 하면 된다(요구사항: "시트를 확인하고 기본적으로 적용, 사용자는 확인·수정만").
   - 모달에는 대분류/중분류/소분류 3단계 select 가 있어 매뉴얼이 들어갈 위치(소분류)를 고르며,
     각 select 옆의 "+" 버튼으로 그 자리에서 바로 새 분류를 추가할 수 있다.
2. **확정 업로드** (`POST /excel-upload/confirm`, `categoryId`(소분류 ID) + 선택한 `sheetNames` CSV 전달):
   같은 파일을 다시 업로드받아 재파싱한 뒤, 선택되고 인식된 시트만 매뉴얼+단계+사진으로 실제 저장한다.
   (서버는 상태를 저장하지 않으므로 phase 1/2 모두 같은 파일을 다시 업로드해야 한다)
   `categoryId`는 반드시 소분류(3단계)여야 하며, 아니면 400 에러로 거부된다.

## 프론트엔드 화면 (index.html, 단일 페이지)

플랫폼 SPA 안에서 iframe 모듈로 열린다(메뉴 URL `/safety/index.html`). 좌측 트리 + 우측 목록의
2단 레이아웃이며, 단계를 눌러야 다음 단계가 보이던 드릴다운 방식은 쓰지 않는다.

- **좌측 — 스택형 분류 트리**: 대분류/중분류/소분류를 한 화면에 계층으로 펼쳐 둔다. 화살표로 접고 펴며,
  각 분류 옆 배지는 **하위 분류까지 합산한 매뉴얼 건수**(`CategoryResponse.manualCount`)다.
- **우측 — 매뉴얼 목록**: 좌측에서 어느 단계를 고르든 그 분류 **하위 전체** 매뉴얼을 보여준다
  (`/safety-api/manuals/by-category`). 각 행에 `대분류 > 중분류 > 소분류` 경로를 함께 표시하고,
  제목/경로로 거르는 검색창을 제공한다. 최상단 "전체"를 고르면 모든 매뉴얼을 한 번에 본다.
- **매뉴얼 상세는 모달**: 목록에서 행을 누르면 별도 모달이 열려 수칙(단계) 표를 보여준다.
- **관리 UI는 기본으로 숨긴다**: 상단(및 상세 모달)의 "수정" 버튼을 눌러야 관리 모드가 켜지고,
  그때만 분류 수정/삭제 버튼, 분류·매뉴얼 추가, 엑셀 업로드, 상세 표의 **관리 칸**이 나타난다.
  관리 모드가 꺼져 있으면 관리 칸 자체가 없어 본문 컬럼이 그만큼 넓게 보인다.
- **사진 확대**: 단계 사진을 누르면 확대 뷰어가 열린다(휠/버튼으로 배율 조절, 화면 맞춤,
  새 탭에서 원본 열기, Esc 로 닫기 — Esc 는 뷰어만 닫고 매뉴얼 모달은 유지된다).
- 매뉴얼 목록에는 **출처(엑셀 파일명/시트명) 컬럼을 표시하지 않는다** — 제목·분류 경로·수정일만 노출.
  (백엔드 응답 DTO에는 `sourceFileName`/`sourceSheetName` 필드가 여전히 존재하지만 목록 화면에서만
  숨김 처리했을 뿐, 상세 화면 등에서 필요하면 그대로 사용 가능하다.)

### 로컬 미리보기

DB 없이 화면만 확인할 때는 저장소 루트의 `preview-server.js` 를 쓴다. module-safety 의 정적 파일과
`/safety-api/**` 목(mock) 응답, `SAFETY_MGMT` 메뉴가 등록되어 있어 실제와 같이 **플랫폼 SPA 안의
모듈**로 열린다.

```
node preview-server.js 8081     # http://localhost:8081  (admin / 아무 비밀번호)
```

(사진 업로드·엑셀 업로드는 목 서버에서 지원하지 않으므로 실제 서버에서 확인한다.)

## 참고: 문자 인코딩(mojibake) 관련 주의사항

이 프로젝트의 SQL 파일은 모두 UTF-8로 작성되어 있다. `mysql` CLI로 직접 실행할 때
`--default-character-set=utf8mb4` 를 빠뜨리면 한글이 깨진 채로 저장되므로 반드시 챙길 것.
(과거 이슈: `sql/V2.0.1__fix_korean_mojibake.sql` 참고 — 플랫폼 초기 구축 때도 동일한 원인으로
한 차례 전체 데이터 mojibake 복구가 있었다.)
