# module-personalinfo — 개인정보 위수탁 협력사 관리

개인정보 처리 업무를 위탁한 협력사(수탁사)의 **위수탁 계약서**와 **위수탁 점검**을 관리하는 업무 모듈입니다.
기존 플랫폼 app 에 Gradle 서브모듈로 포함되어 동작하며, 로그인·사용자·권한·메뉴는 플랫폼 core 기능을 그대로 사용합니다.

| 항목 | 값 |
|---|---|
| 모듈명 | `personalinfo` (요청 모듈명 "Personal Information" 을 영문 소문자로 바꾼 이름) |
| 모듈 디렉토리 | `module-personalinfo/` |
| 패키지 | `com.company.module.personalinfo` |
| API Prefix | `/personalinfo-api/**` |
| 화면 URL | `/personalinfo/index.html` (플랫폼 SPA 안에서 iframe 으로 로드) |
| 테이블 접두사 | `pi_` |
| 사용 대상 | 당사 계약 담당자, 보안팀 담당자 |

기준 양식(업로드 파일)
- `개인정보 위수탁 협력사 관리_V1.xlsx` → 계약 항목(외부위탁 현황 리스트 열 구성)
- `개인정보 위수탁 협력사 점검 리스트_v1.xlsx` → 점검 체크리스트 양식. 모듈 jar 에 `personalinfo/checklist-template.xlsx` 로 들어 있습니다.

---

## 1. 주요 기능

| # | 요청 기능 | 구현 내용 |
|---|---|---|
| 1 | 위수탁 계약서 업로드 | 계약별 파일 업로드. 구분은 위수탁 계약서, 재위탁 동의서, 정보보호·비밀유지 확약서, 개인정보 파기 확인서, 기타 5가지입니다. 허용 확장자는 pdf, doc(x), hwp(x), xls(x), jpg, png, zip 이고 기본 최대 30MB 입니다. 저장 시 SHA-256 해시를 기록하고 파일명은 UUID 로 바꿉니다(경로 조작 차단). |
| 2 | 계약서 조회 | 목록(검색, 상태 필터, 정렬, 페이징, 상단 요약 카드), 상세(첨부·점검 이력·알림 이력), 엑셀 다운로드(관리대장과 같은 열 순서) |
| 3 | 만료일 알람·메일 발송 | 계약 상태를 자동 계산합니다(계약중/만료임박/만료/종료/기간미정). **자동 메일**은 알림 기준일(기본 30일), D-7, D-1, 당일, 만료 후 단계마다 1회씩 발송됩니다. 상세 화면에서 **수동 메일**도 보낼 수 있고, 발송 이력이 남습니다. |
| 4 | 위수탁 점검 체크리스트 다운로드·업로드 | **다운로드**: 보안팀 양식 원본 또는 회사명·수탁업무를 미리 채운 양식. **업로드**: 협력사가 작성한 엑셀을 읽어 항목별 결과를 저장하고 Y/N/NA 를 집계해 점검 결과(적합/조치 필요/평가 미완료)를 자동 판정합니다. 원본 파일도 보관합니다. |
| 5 | **업체별 점검 체크리스트 업로드** | 같은 업체가 서비스별로 계약이 여러 건이어도, 협력사가 업체 단위로 작성한 체크리스트 **1건을 업로드해 그 업체의 선택한 계약 전체에 점검 이력으로 한 번에 등록**합니다. 업로드 전 미리보기로 체크리스트 회사명에 맞는 업체를 자동 선택하고, 원본 파일은 1번만 저장합니다. |
| - | 재위탁 입력 제어 | 등록·수정 화면에서 **재위탁 여부가 Y 일 때만** 재위탁 업체명·재위탁 업무·재위탁 동의서를 입력할 수 있습니다(N 이면 회색 비활성). N 으로 바꿔도 입력해 둔 값은 지우지 않고 저장해 두며, 다시 Y 로 바꾸면 그대로 쓸 수 있습니다. Y 인데 업체명·업무가 모두 비어 있으면 저장 전에 안내합니다(서버도 같은 검증). |
| - | 목록에서 계약 삭제 | 계약 목록 오른쪽 끝 **관리** 열의 휴지통 버튼으로 바로 삭제합니다(확인 창 후 소프트 삭제). 관리 열은 **삭제 권한자에게만** 보이고, 가로 스크롤해도 오른쪽에 고정됩니다. 권한 부여 방법은 4장 참고. |
| - | 목록 열 너비 조정 | 계약 목록의 열 제목 오른쪽 경계를 끌어 열 너비를 바꿉니다(최소 너비 보호, 넘치면 가로 스크롤). 경계 더블클릭은 그 열만 기본값, **[열 너비 초기화]** 는 전체 기본값입니다. 키보드(경계에 포커스 후 ←/→ 10px, Shift 50px)와 터치도 지원합니다. 너비는 브라우저 `localStorage`(`personalinfo.contractList.colWidths.v1`)에 사용자별로 저장되며 서버·DB 변경은 없습니다. 좁아진 칸은 말줄임(…)으로 표시하고 마우스를 올리면 전체 내용이 보입니다. |
| - | 목록에서 Y/N 바로 변경 | 계약 목록의 **ISMS 인증 여부**와 **재위탁 여부**를 스위치로 바로 바꿉니다(등록 권한 필요, 조회 사용자는 Y/N 글자만 보임). 해당 항목만 저장하고 다른 계약 정보는 건드리지 않습니다. 재위탁을 N 으로 바꿔도 재위탁 업체명·업무는 지우지 않습니다. |
| - | **협력사 담당자 이력** | 계약 상세의 기본정보 바로 아래에 **위탁사(당사) 담당자** 변경 이력을 보여줍니다. 위탁사 담당부서·담당자·이메일 중 하나라도 바뀌면 변경 후 값, **그 시점의 계약기간**, 변경자·변경일시를 남깁니다(바뀐 항목은 노란색 강조). **수탁사 담당자·연락처·이메일 변경은 이력을 남기지 않습니다.** 기본은 **같은 업체의 모든 계약** 이력을 함께 보여주고(㈜·(주)·공백 차이 무시), "이 계약만"으로 좁힐 수 있습니다. 이력은 추가만 되고 수정되지 않습니다. |
| - | 계약 종료 처리 | 종료 처리하면 만료 알림이 멈춥니다. "계약변경·만료 점검"(양식 3번 시트) 업로드를 지원합니다. |
| - | 점검 후속조치 | 조치 예정일과 조치 완료 여부를 관리합니다. |

### 점검 체크리스트 업로드 규칙
- **정기 점검(REGULAR)**: `1. 관리체계`(14개 항목)와 `2. 개인정보처리시스템`(19개 항목) 시트를 읽습니다.
  - `2.` 시트의 보유여부가 **"미 보유"** 이면 해당 시트는 집계에서 뺍니다(양식 안내문 기준).
- **계약변경·만료 점검(TERMINATION)**: `3.계약변경 만료시 체크` 시트(6개 항목)의 "확인여부" 열을 평가값으로 씁니다.
- 셀 위치를 고정하지 않고 "평가 항목" 머리글을 찾아 열을 정합니다. 협력사가 행을 추가·삭제해도 읽을 수 있고, 병합된 "구분" 셀은 위 값을 이어 씁니다.
- 평가값 정규화: `Y / O / ○ / 충족` → Y, `N / X / 미충족` → N, `NA / N/A / 해당없음` → NA, 빈 값 → 미기재
- 판정: N 이 1건 이상이면 **조치 필요**, 미기재가 있으면 **평가 미완료**, 그 외는 **적합**
- 안내(warnings): 체크리스트 회사명이 계약 업체명과 다를 때(㈜/(주)/공백 차이는 무시), 보유여부 미선택, 해석 불가 값이 있을 때

### 업체별 체크리스트 업로드 규칙
- **업체 묶음 기준**: 업체명에서 `㈜ / (주) / （주） / (株) / 주식회사`, 공백, 대소문자 차이를 뺀 값이 같으면 같은 업체로 봅니다. 예: `㈜가나물류` = `가나 물류(주)` = `주식회사 가나물류`
- **절차** (팝업, 화면 이동 없음): ① 등록할 계약 체크 → ② 작성된 체크리스트 선택(미리보기: 회사명·평가일·Y/N 집계·판정, 회사명이 업체와 다르면 경고) → ③ 점검일·평가자·조치 예정일·비고 입력 후 등록. 결과 표에서 계약을 누르면 상세로 이동하고, 팝업을 닫으면 현재 화면의 최근 점검이 갱신됩니다.
- **등록 대상**: 기본값은 업체의 **종료되지 않은 계약 전체**입니다. 계약변경·만료 점검처럼 종료된 계약에도 등록해야 하면 "종료 포함"을 켜고 직접 선택하세요.
- 다른 업체의 계약 ID 가 섞이면 전체를 거부합니다(400). 한 번에 최대 100건까지 등록할 수 있습니다.
- **한 트랜잭션**으로 처리하므로 계약 하나라도 실패하면 전부 되돌립니다.
- 원본 파일은 1번만 저장하고, 계약별 점검 이력(`pi_inspection`)은 같은 파일 경로와 **묶음 ID(`UPLOAD_GROUP_ID`)** 를 공유합니다. 항목별 결과(`pi_inspection_item`)는 계약마다 따로 저장하므로, 계약별 후속조치·삭제는 기존과 똑같이 할 수 있습니다.
- 계약 상세의 점검 이력에는 **"업체 일괄"** 표시가 붙습니다. 각 계약의 최근 점검 결과도 갱신됩니다.
- 진입: 계약 목록에서 **업체명 클릭**(행의 다른 곳을 누르면 기존처럼 상세로 이동), 또는 계약 상세의 **[업체 계약 일괄 업로드]** 버튼. 등록 권한(WRITE)이 있는 사용자에게만 업체명이 링크로 보입니다.

### 만료 알림 규칙
- 대상: 미삭제 + 미종료 + 알림 사용(Y) + 종료일 있음
- 단계: 남은 일수가 지난 가장 가까운 기준 1개만 발송합니다(서버가 며칠 멈췄다가 다시 떠도 몰아서 보내지 않습니다).
- 중복 방지: `pi_alert_log.DEDUP_KEY = 계약ID:단계:종료일` UNIQUE. WAS 가 여러 대여도 한 번만 발송합니다. 종료일을 바꾸면 새 일정으로 다시 알립니다.
- 수신자: 계약 담당자 이메일 + 계약별 추가 수신자 + `personalinfo.alert.default-recipients`(예: 보안팀)
- 실패하면 다음 실행 때 최대 3회까지 재시도합니다. 메일 서버가 설정되지 않았으면 `SKIPPED` 로 기록하고, 애플리케이션 기동에는 영향을 주지 않습니다.

---

## 2. 생성 테이블 목록 (`01_schema.sql`)

| 테이블 | 설명 | 주요 키/제약 |
|---|---|---|
| `pi_contract` | 위수탁 계약 (외부위탁 현황 1행 = 1건) | PK `CONTRACT_ID`, IDX 종료일/업체명/부서/알림 |
| `pi_contract_file` | 계약서·증빙 첨부파일 메타데이터 | FK → `pi_contract` |
| `pi_inspection` | 점검 이력 (체크리스트 업로드 1건 × 계약) | FK → `pi_contract`, IDX `UPLOAD_GROUP_ID`(업체별 일괄 업로드 묶음, 개별 업로드는 NULL) |
| `pi_inspection_item` | 점검 체크리스트 항목별 결과 | FK → `pi_inspection` |
| `pi_contact_history` | 위탁사 담당자 변경 이력 + 기록 시점 계약기간 (`04`, `05`) | FK → `pi_contract`, IDX 계약+삭제여부+일시 |
| `pi_alert_log` | 만료 알림 발송 이력 | FK → `pi_contract`, `UK_PI_ALERT_LOG_DEDUP` |

- 모든 테이블에 공통 컬럼(`CREATED_AT/BY`, `UPDATED_AT/BY`, `DELETED_YN/AT/BY`)이 있습니다. 모든 테이블·컬럼에 COMMENT 가 있습니다(6개 테이블 154개 컬럼, 누락 0건 검증).
- FK 는 업무 테이블 사이에만 있습니다. `CREATED_BY` 같은 사용자 컬럼은 core_user ID 값만 저장하고 **core 테이블 FK 는 없습니다.**
- 삭제는 모두 소프트 삭제(`DELETED_YN='Y'`)입니다. 업로드한 실제 파일도 감사 증적으로 지우지 않습니다.

---

## 3. API 목록 (`/personalinfo-api`)

모든 JSON 응답은 core `ApiResponse<T>` 로 감쌉니다. 파일 다운로드(바이너리) 응답만 예외로, 원본 바이트를 내려줍니다(module-kims 첨부 다운로드와 같은 방식이며, 오류는 core GlobalExceptionHandler 가 ApiResponse 로 응답).

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/me/permissions` | 현재 사용자의 화면 버튼 권한 | 로그인 |
| GET | `/dashboard` | 목록 상단 요약 (상태별·점검별 건수, 만료임박 목록) | READ |
| GET | `/contracts` | 계약 목록. `keyword, deptName, managerName, status, ismsScopeYn, inspectionResult, endDateFrom, endDateTo, page, size, sort` | READ |
| GET | `/contracts/excel` | 목록 엑셀 다운로드 (검색 조건 적용) | READ |
| GET | `/contracts/{id}` | 계약 상세 (첨부/점검/알림 이력 포함) | READ |
| POST | `/contracts` | 계약 등록 | WRITE |
| PUT | `/contracts/{id}` | 계약 수정 | WRITE |
| GET | `/contracts/{id}/contact-history?vendorWide=true` | 위탁사 담당자 변경 이력(계약기간 포함). `vendorWide=false` 면 이 계약만 | READ |
| PATCH | `/contracts/{id}/flags` | 목록에서 Y/N 바로 변경 `{ismsScopeYn?, subcontractYn?}` — 보낸 항목만 변경, 값은 Y/N 만 허용 | WRITE |
| POST | `/contracts/{id}/terminate` | 계약 종료 처리 `{terminatedDate, reason}` | WRITE |
| POST | `/contracts/{id}/reopen` | 종료 취소 | WRITE |
| DELETE | `/contracts/{id}` | 계약 삭제 (소프트) | **DELETE** |
| GET | `/contracts/{id}/files` | 첨부 목록 | READ |
| POST | `/contracts/{id}/files` | 계약서 업로드 (multipart: `file`, `fileType`, `description`) | WRITE |
| GET | `/files/{fileId}/download` | 첨부 다운로드 | READ |
| DELETE | `/files/{fileId}` | 첨부 삭제 (소프트) | **DELETE** |
| GET | `/checklist-template` | 점검 체크리스트 빈 양식 다운로드 | READ |
| GET | `/contracts/{id}/checklist-template` | 회사명·수탁업무를 미리 채운 양식 다운로드 | READ |
| GET | `/contracts/{id}/inspections` | 점검 이력 | READ |
| POST | `/contracts/{id}/inspections` | 작성된 체크리스트 업로드 (multipart: `file`, `inspectionType`, `inspectionDate`, `inspectorName`, `actionDueDate`, `remark`) | WRITE |
| GET | `/inspections/{inspectionId}` | 점검 상세 (항목별 결과) | READ |
| PUT | `/inspections/{inspectionId}` | 후속조치 수정 | WRITE |
| GET | `/inspections/{inspectionId}/download` | 업로드 원본 다운로드 | READ |
| DELETE | `/inspections/{inspectionId}` | 점검 이력 삭제 (소프트, 최근 점검 다시 계산) | **DELETE** |
| GET | `/vendors` | 업체 목록(업체명 기준 묶음, 계약 목록·최근 점검 포함). `keyword, includeTerminated` | READ |
| POST | `/vendors/checklist-preview` | 업체별 업로드 미리보기 — 저장하지 않고 집계·판정·업체 후보 반환 (multipart: `file`, `inspectionType`) | WRITE |
| POST | `/vendors/checklist` | **업체별 체크리스트 업로드** — 1개 파일을 업체의 여러 계약에 등록 (multipart: `vendorKey`, `contractIds`(여러 개, 비우면 미종료 계약 전체), `file`, `inspectionType`, `inspectionDate`, `inspectorName`, `actionDueDate`, `remark`) | WRITE |
| POST | `/contracts/{id}/alerts` | 만료 안내 메일 수동 발송 `{extraRecipients, message}` | WRITE |
| POST | `/alerts/run?baseDate=` | 만료 알림 일괄 실행 (스케줄러 대체/재실행) | **DELETE(관리자)** |

### 화면 (`/personalinfo/index.html`, 해시 라우팅)
| 화면 | 경로 | 내용 |
|---|---|---|
| 목록 | `#/contracts` | 요약 카드(클릭 시 필터), 검색, 정렬, 페이징, 엑셀, 양식 다운로드, ISMS·재위탁 Y/N 스위치, 업체명 클릭 시 업체별 업로드 팝업, 열 너비 조정 |
| 등록 | `#/contracts/new` | 계약 항목 입력, 계약서 파일 함께 업로드 |
| 상세 | `#/contracts/{id}` | 기본정보, 협력사 담당자 이력, 계약서 업로드·다운로드, 체크리스트 양식 다운로드·작성본 업로드, 점검 결과 상세·후속조치, 메일 발송, 종료 처리, 알림 이력 |
| 수정 | `#/contracts/{id}/edit` | 계약 정보 수정 |
| 업체별 체크리스트 업로드 (팝업) | 목록의 업체명 클릭 / 상세 버튼 | 업체 계약 선택 → 체크리스트 미리보기 → 일괄 등록, 결과에서 계약 상세로 이동 |

버튼은 `/me/permissions` 결과에 따라 보이거나 숨겨집니다. 실제 권한 판정은 서버의 `@PreAuthorize` 가 합니다.

---

## 4. 권한 코드 목록

플랫폼 core 는 사용자에게 부여된 **역할 코드(core_user_role.ROLE)를 그대로 GrantedAuthority 로** 넣습니다. 별도의 권한 코드(permission)를 authority 로 주지 않기 때문에, 이 모듈은 역할 코드로 권한을 판정합니다.

| 요청 권한 | 논리 권한 코드 | 판정 역할 (`@PreAuthorize`) |
|---|---|---|
| 조회 | `PERSONALINFO_READ` | `ROLE_ADMIN`, `ROLE_PI_ADMIN`, `ROLE_PI_USER`, `ROLE_PI_VIEWER` |
| 등록/수정 (메일 수동 발송 포함) | `PERSONALINFO_WRITE` | `ROLE_ADMIN`, `ROLE_PI_ADMIN`, `ROLE_PI_USER` |
| 삭제·일괄 알림 실행 | `PERSONALINFO_DELETE` | `ROLE_ADMIN`, `ROLE_PI_ADMIN` |

| 사용자 구분 | 부여할 역할 | 사용 가능 기능 |
|---|---|---|
| 관리자 (보안팀) | `ROLE_PI_ADMIN` (또는 플랫폼 `ROLE_ADMIN`) | 전체 기능 + 삭제 |
| 일반 사용자 (계약 담당자) | `ROLE_PI_USER` | 등록, 수정, 조회, 업로드, 메일 발송 |
| 조회 사용자 | `ROLE_PI_VIEWER` | 조회, 다운로드만 |

> 삭제는 관리자 역할만 할 수 있습니다(요청 사항 4). 계약 목록의 **관리(삭제) 열**과 상세 화면의 삭제 버튼은 삭제 권한이 있는 사용자에게만 보이고, 서버도 `@PreAuthorize(CAN_DELETE)` 로 한 번 더 막습니다(화면을 우회해 API 를 직접 호출해도 403).

### 삭제 권한을 특정 사용자에게만 주는 방법 (운영자 작업, 코드·SQL 변경 없음)
1. 플랫폼 관리자(`ROLE_ADMIN`)로 로그인 → **코어 > 사용자 관리**
2. 대상 사용자 행의 **역할 변경(👤)** 버튼
3. 삭제를 허용할 사용자: **개인정보위수탁 관리자(`ROLE_PI_ADMIN`)** 체크
   삭제 없이 등록·수정만: **개인정보위수탁 사용자(`ROLE_PI_USER`)**, 조회만: **개인정보위수탁 조회자(`ROLE_PI_VIEWER`)**
4. 저장 후 해당 사용자가 **다시 로그인**하면 적용 (권한은 로그인 토큰에 담기므로 기존 로그인은 만료 전까지 이전 권한 유지)

- 한 사용자에게 여러 역할을 줄 수 있으며(다중 역할), 가장 넓은 권한이 적용됩니다.
- 플랫폼 `ROLE_ADMIN` 은 이 모듈에서도 삭제 권한을 가집니다. 플랫폼 관리자에게는 삭제를 막고 싶다면 `PiRoles.CAN_DELETE` 에서 `ROLE_ADMIN` 을 빼면 됩니다(코드 변경).
- 삭제는 소프트 삭제(`DELETED_YN='Y'`, 삭제자·삭제일시 기록)라 DB 에서 복구할 수 있습니다.

---

## 5. 메뉴 등록 요청 정보

| 항목 | 값 |
|---|---|
| 메뉴명 | 개인정보 위수탁 관리 |
| 메뉴 위치 | 보안관리 > 개인정보 위수탁 관리 (요청서에 위치가 없어 가정. 바꾸려면 `02_seed_data.sql` 의 `PI_MGMT` 이름만 수정) |
| 메뉴 코드 | 상위 `PI_MGMT`, 페이지 `PI_CONTRACT` |
| 메뉴 URL | `/personalinfo/index.html` |
| API Prefix | `/personalinfo-api` |
| 아이콘 | `user-shield`(상위), `file-contract`(페이지) |
| 노출 역할 | `ROLE_ADMIN`, `ROLE_PI_ADMIN`, `ROLE_PI_USER`, `ROLE_PI_VIEWER` |

`02_seed_data.sql` 은 저장소에 있는 core DDL(`core_menu` MENU_CODE UNIQUE, `core_role_menu` ROLE+MENU_ID UNIQUE, 공통코드 `ROLE` 그룹)을 확인한 뒤 **운영자 검토용**으로 작성했습니다. 운영 DB 구조가 다르면 실행하지 말고, 위 표대로 관리 화면(메뉴 관리, 공통코드 관리, 접근 권한)에서 등록하세요.

---

## 6. SQL 실행 순서

| 순서 | 파일 | 설명 |
|---|---|---|
| 1 | `01_schema.sql` | 업무 테이블 5개. `CREATE TABLE IF NOT EXISTS` 만 사용 |
| 2 | `02_seed_data.sql` | 역할 코드 3개(공통코드 ROLE), 메뉴 2개, 역할-메뉴 매핑. upsert / NOT EXISTS / INSERT IGNORE 사용. 업무 데이터는 넣지 않음 |
| 3 | `03_vendor_checklist_upload.sql` | **업체별 업로드용 컬럼 추가** — `pi_inspection.UPLOAD_GROUP_ID` + 인덱스. `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` (MariaDB 10.0.2+). 이미 01 을 받은 운영 DB 에만 필요하지만, 새로 설치할 때 실행해도 아무 변화 없이 통과합니다 |
| 4 | `04_contact_history.sql` | **협력사 담당자 이력 테이블** `pi_contact_history` 추가 + 이력이 없는 기존 계약의 현재 담당자 값을 "기존 데이터 이관"으로 1건 기록. `CREATE TABLE IF NOT EXISTS` / `INSERT ... WHERE NOT EXISTS` 로 재실행해도 중복되지 않습니다 |
| 5 | `05_contact_history_contract_period.sql` | **담당자 이력에 계약기간 추가** — `pi_contact_history` 에 `CONTRACT_START_DATE` / `CONTRACT_END_DATE` / `CONTRACT_PERIOD_NOTE` 컬럼 추가(`ADD COLUMN IF NOT EXISTS`), 계약기간이 비어 있는 기존 이력만 현재 계약 값으로 채움. 04 의 수탁사 담당자 컬럼은 더 이상 쓰지 않지만 DROP 금지 규칙에 따라 남겨둡니다 |

```bash
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/01_schema.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/02_seed_data.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/03_vendor_checklist_upload.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/04_contact_history.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/05_contact_history_contract_period.sql
```

> ⚠️ `--default-character-set=utf8mb4` 를 꼭 붙이세요. 빠지면 한글 메뉴명이 깨집니다.

검증 결과: 플랫폼 core DDL(V1.0.0, V1.4.0, module-common V3/V7, V3.0.0)을 올린 빈 MariaDB 에 두 스크립트를 **두 번씩** 실행해도 오류가 없었고 중복도 생기지 않았습니다(메뉴 2, 역할 3, 매핑 8 유지). DROP / TRUNCATE / DELETE / ALTER ... DROP 은 쓰지 않습니다. `03` 은 기존 테이블(컬럼 없음)과 새 테이블(01 에 컬럼 포함) 양쪽에서 각각 두 번 실행해 오류 없이 통과하는 것을 확인했습니다.

---

## 7. 운영 반영 시 필요한 설정 — 플랫폼 app 설정에 추가 필요

이 모듈은 설정 파일을 만들지 않습니다. 아래 값을 플랫폼 `application.yml`(또는 환경변수)에 추가하세요. 모든 값에 기본값이 있어서 **추가하지 않아도 기동은 됩니다.**

```yaml
personalinfo:
  upload-dir: ${PERSONALINFO_UPLOAD_DIR:/data/upload/personalinfo}  # 계약서/체크리스트 저장 경로 (백업 대상)
  max-file-size-mb: 30                                               # 계약서 1개 최대 크기 (multipart 100MB 이하)
  alert:
    enabled: true                                  # 일일 자동 알림 사용
    cron: "0 30 8 * * *"                           # 매일 08:30 (Asia/Seoul)
    zone: Asia/Seoul
    default-recipients: security@company.com       # 모든 알림에 함께 받을 주소 (보안팀)
    mail-from: noreply@company.com                 # 미설정 시 spring.mail.username
    base-url: https://aiplatform.kleannara.com     # 메일 본문 "상세 보기" 링크
    subject-prefix: "[개인정보 위수탁]"

spring:
  mail:                                            # 사내 SMTP (없으면 메일은 SKIPPED 로 기록만)
    host: smtp.company.com
    port: 25
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties.mail.smtp.auth: false
    properties.mail.smtp.starttls.enable: false
```

- 업로드 디렉토리는 WAS 실행 계정이 쓸 수 있어야 하고, 정기 백업 대상에 넣어야 합니다. WAS 를 여러 대 운영하면 공유 스토리지(NFS 등)를 써야 합니다.
- 기존 `spring.servlet.multipart.max-file-size: 100MB` 설정으로 충분합니다.

### 플랫폼 반영 절차 (수동 등록 방식인 경우, 운영자 작업)
1. `settings.gradle` 에 `include 'module-personalinfo'` 추가
2. `app/build.gradle` 에 `implementation project(':module-personalinfo')` 추가
3. SQL 스크립트 실행 (6장)
4. 아래 8장의 core/app 추가 사항 반영
5. app 빌드 및 배포

---

## 8. core / app 에 추가로 필요한 기능

| # | 구분 | 내용 | 없을 때 영향 |
|---|---|---|---|
| 1 | **core 에 추가 필요** — `SecurityConfig` 공개 경로 | `.requestMatchers("/personalinfo/**").permitAll()` 추가. 화면(정적 HTML/JS)만 해당하며, 데이터 API `/personalinfo-api/**` 는 계속 JWT 인증 + `@PreAuthorize` 로 보호됩니다. (module-fire, ps-insp, dailyreport 와 같은 방식) | iframe 이 Authorization 헤더를 보낼 수 없어 **화면이 401 로 뜨지 않음** |
| 2 | **app 에 추가 필요** — SPA 메뉴 iframe 라우팅 | `app/src/main/resources/static/index.html` 에서 `/safety/` 메뉴처럼 `/personalinfo/` 로 시작하는 메뉴 URL 을 iframe 으로 여는 분기(`isPersonalinfoPage` → `navigateToSafetyPage` 와 같은 함수) 추가 | 메뉴 클릭 시 화면이 열리지 않음 (직접 URL 접속은 가능) |
| 3 | **app 에 추가 필요** — `@EnableScheduling` | `PlatformApplication` 에 `@EnableScheduling` 추가 (현재 `@EnableAsync` 만 있음) | 일일 **자동** 메일이 돌지 않음. 대신 관리자가 목록 화면 "만료 알림 실행"(`POST /alerts/run`)으로 실행 가능 |
| 4 | **core 에 추가 필요** — `CurrentUserProvider` | 표준이 전제한 `com.company.core.security.CurrentUserProvider` 가 core 에 없습니다. 이 모듈은 `SecurityContextHolder` 를 직접 쓰지 않고, 기존 module-dailyreport 와 같이 `@AuthenticationPrincipal(expression = "userId") Long` 으로 core `CustomUserDetails.userId` 를 받습니다. core 에 Provider 가 생기면 Controller 파라미터만 바꾸면 됩니다. | 없음 (현재 방식으로 동작) |
| 5 | core 사용자 조회 Provider 또는 API 필요 (선택) | 화면에 등록자/수정자를 **이름**으로 보여주려면 사용자 ID → 이름 조회 Provider 가 필요합니다(업무 모듈은 core_user 를 직접 조회하지 않음). 현재는 담당자를 이름·이메일 입력 칸으로 받습니다. | 등록자는 ID 로만 저장됨 |

---

## 9. 구현 파일

```
module-personalinfo/
├── build.gradle                         compileOnly core / security, poi-ooxml, spring-boot-starter-mail
└── src/main/
    ├── java/com/company/module/personalinfo/
    │   ├── controller/  PiContractController, PiContractFileController, PiInspectionController,
    │   │                PiAlertController, PiVendorChecklistController, PiDownloads(다운로드 응답 헬퍼)
    │   ├── dto/         ContractSaveRequest, ContractSearchCondition, ContractTerminateRequest,
    │   │                InspectionUpdateRequest, AlertSendRequest,
    │   │                ContractSummaryResponse, ContractDetailResponse, ContractFileResponse,
    │   │                InspectionResponse, InspectionItemResponse, AlertLogResponse,
    │   │                AlertRunResponse, DashboardResponse, PermissionResponse,
    │   │                VendorSummaryResponse, VendorChecklistPreviewResponse, VendorChecklistUploadResponse
    │   ├── entity/      PiBaseEntity(공통 컬럼), PiContract, PiContractFile, PiInspection,
    │   │                PiInspectionItem, PiAlertLog + Enum(ContractStatus, ContractFileType,
    │   │                InspectionType, InspectionResult, ChecklistSheetType, AlertSendStatus, AlertTriggerType)
    │   ├── repository/  Pi*Repository (5개)
    │   └── service/     PiContractService, PiContractFileService, PiInspectionService,
    │                    ChecklistExcelParser, PiChecklistTemplateService, PiContractExcelService,
    │                    PiAlertMailService, PiAlertScheduler, PiFileStorage, PiExcelSupport, PiRoles,
    │                    PiVendorChecklistService(업체별 업로드), PiVendorNames(업체명 정규화)
    └── resources/
        ├── personalinfo/checklist-template.xlsx      보안팀 점검 체크리스트 원본 양식
        └── static/personalinfo/                      화면 (index.html, css, js)
sql/module-personalinfo/  01_schema.sql, 02_seed_data.sql, 03_vendor_checklist_upload.sql, 04_contact_history.sql, 05_contact_history_contract_period.sql, README.md
```

---

## 10. 검증 내역 (저장소 밖 임시 환경에서 실행, 저장소에는 테스트 하네스를 넣지 않음)

| 검증 | 결과 |
|---|---|
| 컴파일 (Java 17 타깃, core compileOnly) | 통과 |
| Hibernate `ddl-auto=validate` (Entity ↔ 01_schema.sql 컬럼·타입 일치) | 통과 |
| 통합 테스트 13건: core + 모듈 기동, MariaDB, 실제 체크리스트 양식, 로컬 SMTP(GreenMail) | **13/13 통과** |
| 　- 권한: 조회자 등록 403 / 외부 역할 403 / 미로그인 401 / 일반 사용자 삭제 403 / 관리자 삭제 200 | 통과 |
| 　- 계약서 업로드·다운로드, 금지 확장자 거부, `../` 경로 조작 파일명 무력화 | 통과 |
| 　- 체크리스트: 33항목 집계, 병합셀 구분, 중요(●), N 항목 조치계획, "미 보유" 제외, 3번 시트 6항목, 엑셀 아닌 파일 400 | 통과 |
| 　- 상태 필터(만료임박/계약중/기간미정), 기간미정 계약 정렬 시 맨 뒤, 대시보드, 엑셀 다운로드 | 통과 |
| 　- 만료 알림: D-7 발송(담당자+보안팀), 같은 날 재실행 시 중복 0, 4일 뒤 D-1 발송, 수동 발송, 이력 기록 | 통과 |
| 　- 소프트 삭제: DELETED_YN/BY 기록, 항목 33건 함께 소프트 삭제, 최근 점검 다시 계산 | 통과 |
| 　- 업체별 업로드: 표기 다른 업체명 3건 → 1개 업체, 미리보기 자동 매칭·저장 없음, 조회자 403, 타 업체 계약 섞이면 400·저장 0, 미종료 2건 등록(묶음ID 동일·파일 1개·항목 66건), 계약별 최근 점검 반영, 종료 계약 명시 등록, xlsx 아님 400, 없는 업체 404 | 통과 |
| 　- 목록 Y/N 변경: ISMS만/재위탁만/둘 다 변경, 다른 컬럼·재위탁 업체명 보존, UPDATED_BY 기록, 잘못된 값·빈 요청 400, 조회자 403, 없는 계약 404 | 통과 |
| 　- 담당자 이력: 등록 시 1건, 담당자 외 항목 수정·**수탁사 담당자/연락처/이메일만 변경**·이메일 대소문자만 변경 시 추가 없음, 위탁사 담당자 변경 시 바뀐 항목·변경자·**그 시점 계약기간** 기록(등록 이력은 등록 시점 기간 유지), 이전 버전의 수탁사 전용 변경 행 조회 제외, 같은 업체 다른 계약 포함·다른 업체 제외, 이 계약만 조회, 외부 역할 403, 없는 계약 404 | 통과 |
| 　- 목록 삭제(브라우저): 관리자·보안팀 관리자만 관리 열/버튼 표시, 일반·조회 사용자 미표시, 일반 사용자 API 직접 호출 403, 삭제 후 목록·요약 갱신·DELETED_YN/BY 기록, 버튼 클릭 시 상세 이동 안 됨 | 통과 |
| 　- 목록 열 너비 조정(브라우저): 드래그 확대/축소, 최소 너비 보호, 새로고침 후 유지, 더블클릭 단일 열 기본값, 키보드 조정, 전체 초기화, 드래그 중 상세 이동 안 됨·행 클릭 상세 이동 유지 | 통과 |
| 브라우저 E2E (목록 → 등록+파일 → 체크리스트 업로드 → 점검 상세 → 수정, 역할별 버튼 노출) | 통과, 콘솔 오류 0 |
| SQL 2회 실행(재실행 안전), COMMENT 누락 0, core FK 0 | 통과 |

---

## 11. 자체 검수 체크리스트 결과

| 번호 | 검수 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | User/Auth/Role/Menu/Security 관련 클래스가 생성되지 않았는가? | 통과 | 권한 판정 상수 `PiRoles` 만 있음(역할 코드 문자열, Entity/Repository 아님) |
| 2 | SecurityConfig, JwtProvider, AuthController 미생성 | 통과 | `SecurityFilterChain` 0건 |
| 3 | application.yml / properties 미생성 | 통과 | 설정은 7장에 문서화 |
| 4 | Dockerfile, docker-compose.yml, Nginx 설정 미생성 | 통과 | |
| 5 | SpringBootApplication main class 미생성 | 통과 | |
| 6 | API URL `/{모듈명}-api/**` 준수 | 통과 | 전 Controller `@RequestMapping("/personalinfo-api")` |
| 7 | 금지 URL(`/api`, `/admin`, `/auth` 등) 미사용 | 통과 | |
| 8 | Entity 에 `@Setter` 없음 | 통과 | `@Setter` 는 Request DTO 에만 있음(허용) |
| 9 | Entity 에 `@Data` 없음 | 통과 | |
| 10 | Service 에서 `entity.setXxx()` 미사용 | 통과 | `update/terminate/delete/restore/applyInspectionResult` 등 비즈니스 메서드만 사용 (`setCellValue` 등은 POI/메일 라이브러리 API) |
| 11 | 모든 Entity 컬럼 `@Column(name = "UPPER_SNAKE_CASE")` | 통과 | ddl-auto=validate 로 검증 |
| 12 | 모든 업무 테이블 공통 컬럼 포함 | 통과 | `PiBaseEntity` + DDL 5개 테이블 |
| 13 | 삭제는 `DELETED_YN` 소프트 삭제 | 통과 | 물리 DELETE 없음 |
| 14 | 모든 테이블·컬럼 COMMENT | 통과 | information_schema 확인, 누락 0 |
| 15 | SQL `DROP TABLE` 없음 | 통과 | |
| 16 | SQL `TRUNCATE TABLE` 없음 | 통과 | |
| 17 | SQL 무조건 `DELETE` 없음 | 통과 | |
| 18 | SQL `ALTER TABLE DROP COLUMN` 없음 | 통과 | |
| 19 | core 테이블 FK 미생성 | 통과 | FK 4개 모두 `pi_*` 사이 |
| 20 | Controller 응답 `ApiResponse<T>` | 통과 | 파일(바이너리) 다운로드 5개만 예외 — 3장 참고 |
| 21 | Entity 를 Controller 에서 직접 반환하지 않음 | 통과 | Response DTO `from()/of()` |
| 22 | RuntimeException / IllegalArgumentException 직접 throw 없음 | 통과 | `BusinessException`, `EntityNotFoundException` + `ErrorCode` |
| 23 | ddl-auto 에 의존하지 않고 DDL 제공 | 통과 | `01_schema.sql` |
| 24 | core 모듈 미수정 | 통과 | git diff 에 core 변경 없음 |
| 25 | app 모듈 미수정 | 통과 | settings.gradle, app/build.gradle, index.html 변경 없음 (8장에 안내) |
| 26 | 메뉴/권한 등록 정보 README 작성 | 통과 | 4장, 5장 |
| 27 | 부족한 core 기능 "core 에 추가 필요" 명시 | 통과 | 8장 (SecurityConfig 공개 경로, CurrentUserProvider, @EnableScheduling, SPA 라우팅) |
