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
| - | **원계약 / 개인정보처리 위탁계약 구분** | 등록·수정 화면의 계약 영역을 두 칸으로 나눴습니다. 위 칸 **원계약**에는 계약 시작일·종료일·계약기간 비고·자동연장·자동연장 비고가 있고, 아래 칸 **개인정보처리 위탁계약**에는 개인정보처리 위탁계약서 작성(Y/N)과 체결일이 있습니다. **계약기간, 계약 상태, 만료 알림은 모두 원계약 시작일·종료일을 기준으로 합니다.** 상세 화면 항목명(원계약 기간 / 원계약 자동연장 / 개인정보처리 위탁계약서 작성·체결일)과 엑셀 헤더(원계약 시작일·종료일, 개인정보처리 위탁계약서 작성)도 같이 바꿨습니다. DB 컬럼은 그대로 쓰므로 SQL 변경은 없습니다(`CONTRACT_START_DATE`/`CONTRACT_END_DATE` = 원계약, `CONTRACT_DOC_YN`/`CONTRACT_SIGNED_DATE` = 개인정보처리 위탁계약). |
| - | **계약서 파일에서 계약일자 자동 입력** | 위수탁 계약서 파일을 고르면 서버가 파일 글자를 읽어 계약일자를 찾고 **개인정보처리 위탁계약 체결일**에 넣고 작성 여부를 **Y** 로 바꿉니다. 원계약 기간이나 올해 날짜와 비교하지 않습니다. 날짜 판단 순서: ① 같은 줄의 '체결일·계약일·작성일·서명일' 표기 옆 날짜(표 형태로 윗줄이 '체결일'인 경우 포함) ② 문서 안 날짜(뒤쪽 서명란일수록 우선) ③ **체결일 표기가 없으면 계약기간 시작일**('부터'·'~' 앞 날짜). 다른 후보 날짜 선택, **[되돌리기]**, **직접 입력**이 모두 가능합니다. 읽는 형식은 pdf·docx·doc·hwp·hwpx·xlsx·xls 이고, 이미지(jpg·png)·zip·스캔 PDF·암호 파일은 글자를 읽을 수 없어 직접 입력 안내만 합니다. 분석 단계에서는 파일을 저장하지 않습니다. |
| - | **위수탁 계약서 여러 건 · 최신 체결일** | 위수탁 계약서는 **체결일자별로 여러 건** 올릴 수 있고, 파일마다 **계약서 체결일**(`pi_contract_file.SIGNED_DATE`)을 저장합니다(업로드 시 자동으로 읽고, 상세 화면 첨부 목록의 연필 버튼으로 고칠 수 있음). 계약의 **개인정보처리 위탁계약 체결일 = 삭제되지 않은 위수탁 계약서 중 체결일이 가장 최근인 날짜**이며, 계약서 업로드·체결일 수정·삭제 때마다 서버가 다시 맞춥니다(같은 날짜면 나중에 올린 파일). 그 파일은 첨부 목록에서 **최신** 배지와 파란 배경으로 표시되고, 위수탁 계약서는 체결일 최신순으로 정렬됩니다. 더 오래된 계약서를 올리면 계약 체결일은 바뀌지 않습니다. 재위탁 동의서·확약서 등 다른 파일은 체결일을 저장하지 않습니다. 체결일이 있는 계약서가 하나도 없으면 계약 화면에서 직접 입력한 값을 그대로 둡니다. |
| - | **기준 연도 요약 카드** | 목록 상단은 **기준 연도 선택 + 카드 3개**이며, 카드 순서는 **계약중 → 전체 계약 → 종료**입니다. **목록에 처음 들어오면 [계약중] 카드가 선택된 상태**로 계약중 계약만 보이고, [초기화]를 눌러도 계약중으로 돌아갑니다. 다른 카드를 고르면 상세 화면을 다녀와도 그 선택이 유지되고, 메뉴로 새로 들어오면 다시 계약중입니다. **기준일** = 지난 연도는 12월 31일, 올해는 **조회 시점(오늘)**. **종료 기준일** = 종료 처리했으면 그 종료일, 아니면 원계약 종료일. ① **전체 계약** = 시작일 ≤ 그해 12/31 이고 종료 기준일 ≥ 전년도 12/31 인 계약 ② **계약중** = 기준일에 계약기간 **안** + **수탁사 점검완료 건수**(그해 정기 점검 업로드, 미점검 건수 함께) ③ **종료** = 기준일에 계약기간 **밖** + **계약종결 체크 업로드 / 미업로드 건수**(계약변경·만료 점검 = 체크리스트 3번 시트). 예: 2025.12.31 종료 → **2025년 계약중, 2026년 종료**(2027년부터는 집계 제외). 연중 만료 계약은 올해 기준 오늘 이전이면 종료, 이후면 계약중. 전체 = 계약중 + 종료. 카드와 작은 숫자 버튼을 누르면 같은 조건으로 목록을 거르고(목록 제목 옆에 조건 표시), 목록 상태 칸에 종료·만료 계약마다 **종결체크** 업로드(초록)/미업로드(빨강) 표시. 엑셀 다운로드도 같은 조건. DB 변경 없음. |
| - | **계약 종료 처리 — 종료일 입력** | 상세 화면 **[계약 종료 처리]** 가 팝업으로 바뀌어 **종료일**과 사유를 입력합니다. 종료일 기본값은 **원계약 종료일**(없으면 오늘)이고, 중도 해지 등은 실제 날짜로 바꿀 수 있습니다. 원계약 시작일보다 빠른 날짜는 거부합니다. API 에서 종료일을 보내지 않아도 원계약 종료일이 기본값입니다(이전에는 오늘). |
| - | **필수 입력 항목** | 계약 등록·수정 시 **수탁업체명, 계약명, 위탁업무 내용, 원계약 시작일, 원계약 종료일**이 필수입니다(화면 빨간 별표 + 서버 검증, 공백만 입력도 거부). 종료일이 시작일보다 빠르면 저장 전에 안내합니다. 필수화 이전에 등록된 계약(계약명·종료일 없음)은 조회·종료 처리·Y/N 변경·첨부·점검은 그대로 되고, **수정 화면에서 저장할 때만** 빈 필수 항목을 채워야 합니다. 계약기간이 정해지지 않은 계약(이용약관 등)은 종료일을 예상 날짜로 넣고 '계약기간 비고'에 설명을 적습니다. DB 변경 없음(컬럼은 NULL 허용 그대로, 기존 데이터 보존). |
| - | **부서별 계약 조회 범위** | **관리자(`ROLE_ADMIN`, `ROLE_PI_ADMIN`)가 아닌 사용자(계약 담당자 `ROLE_PI_USER`, 조회 사용자 `ROLE_PI_VIEWER`)는 계약의 담당부서가 본인 소속부서인 계약만** 봅니다. 소속부서는 플랫폼 사용자 관리의 **부서**(사용자 프로필 부서 코드 → 공통코드 `DEPT` 이름)를 core `UserProfileSyncPort` 로 읽습니다(core·module-common 코드 변경 없음). 계약 담당부서가 부서 이름 또는 부서 코드와 같으면 같은 부서이며, 공백·영문 대소문자 차이는 무시합니다(예: '인사 팀' = '인사팀'). 적용 범위: 목록·요약 카드·엑셀·같은 업체 계약·업체명 조회·업체별 체크리스트 업체 목록은 소속부서 계약만, 다른 부서 계약의 상세·첨부·점검·담당자 이력·양식·수정·Y/N 변경·메일 발송은 **403**("소속부서(인사팀) 계약만 볼 수 있습니다."). **등록·수정**: 비관리자의 담당부서는 소속부서로 고정(화면 잠금, 비우면 소속부서로 저장, 다른 부서는 400). **소속부서가 지정되지 않은 비관리자**는 볼 수 있는 계약이 없고 등록도 할 수 없습니다(화면에 부서 지정 요청 안내). 관리자는 지금처럼 전체 계약을 보고 담당부서를 자유롭게 입력합니다. 만료 알림 자동 발송(스케줄러)은 시스템 작업이라 전체 계약 대상입니다. DB 변경 없음. |
| - | **한 업체 여러 계약 (계약명)** | 한 업체가 본사·지사 등과 날짜나 업무 범위가 조금씩 다른 계약을 여러 건 맺는 경우를 위해 계약마다 **계약명**(`pi_contract.CONTRACT_NAME`, 선택 입력, 최대 200자)을 둡니다. 예: 본사 물류 위탁계약, 부산지사 물류 위탁계약. 업체는 하나이고, 업체명이 같은(㈜·(주)·주식회사·공백 차이 무시) 계약은 같은 업체로 묶습니다. ① **등록·수정 화면**: 업체명 옆 계약명 입력칸. 업체명을 입력하면 그 업체의 기존 계약(계약명·기간·상태)을 안내 상자로 보여주고 계약명으로 구분하라고 안내합니다(중복 등록은 막지 않음). ② **목록**: 계약명 열 추가, 같은 업체 계약이 2건 이상이면 업체명 옆 **계약 N건** 배지 → 누르면 그 업체의 **계약 목록 팝업**(계약명·서비스·업무·담당·원계약 기간·상태·최근 점검, 행 클릭 시 상세 이동, [이 업체로 계약 추가]·[업체 점검 체크리스트 업로드]). 통합검색이 계약명도 찾고, 정렬에 '계약명순' 추가. ③ **상세**: 제목·경로에 계약명 표시, **같은 업체 계약** 섹션(진행 중 먼저, 시작일순, 지금 계약 강조)과 **[이 업체로 계약 추가]**(업체명을 미리 채운 등록 화면). ④ 엑셀 '계약명' 열(수탁업체 명 다음), 만료 알림 메일 제목·본문, 담당자 이력, 업체별 체크리스트 팝업에도 계약명 표시. 계약명이 없으면 기존처럼 서비스명으로 표시합니다. |
| - | 목록에서 계약 삭제 | 계약 목록 오른쪽 끝 **관리** 열의 휴지통 버튼으로 바로 삭제합니다(확인 창 후 소프트 삭제). 관리 열은 **삭제 권한자에게만** 보이고, 가로 스크롤해도 오른쪽에 고정됩니다. 권한 부여 방법은 4장 참고. |
| - | **목록 열 너비·순서 편집** | 계약 목록과 모든 목록 표(같은 업체 계약, 업체 계약 목록 팝업, 협력사 담당자 이력, 위수탁 계약서·증빙, 위수탁 점검 이력, 만료 알림 이력, 점검 항목 팝업, 업체별 체크리스트 팝업·결과)에서 사용자가 직접 바꿉니다. ① **순서**: 열 제목을 끌어 좌우로 옮김(놓을 자리에 파란 선 표시), 키보드는 열 제목에 포커스 후 Alt+Shift+←/→ ② **너비**: 열 제목 오른쪽 경계를 끌기(최소 너비 보호, 넘치면 가로 스크롤), 경계 더블클릭은 그 열만 기본값, 키보드는 경계에 포커스 후 ←/→(Shift 50px) ③ **[열 설정]** 메뉴: 표마다 오른쪽 위 버튼. ◀ ▶ 로 순서 이동, 너비(px) 직접 입력, **초기화**(그 표의 순서·너비 모두 기본값). 체크박스 열, 제목이 빈 버튼 열, 계약 목록의 **관리**(삭제) 열은 자리가 고정됩니다. 설정은 브라우저 `localStorage`(`personalinfo.table.{로그인ID}.{표}.order/widths.v1`)에 **로그인 사용자별·표별로** 저장되며 새로고침·검색·페이지 이동 후에도 유지됩니다(같은 업체 계약 표는 상세 섹션과 팝업이 설정 공유). 이전 버전에서 조정한 계약 목록 너비는 처음 한 번 이어받습니다. 다른 PC·브라우저에는 따라가지 않습니다. 서버·DB 변경 없음. |
| - | 목록에서 Y/N 바로 변경 | 계약 목록의 **ISMS 인증 여부**와 **재위탁 여부**를 스위치로 바로 바꿉니다(등록 권한 필요, 조회 사용자는 Y/N 글자만 보임). 해당 항목만 저장하고 다른 계약 정보는 건드리지 않습니다. 재위탁을 N 으로 바꿔도 재위탁 업체명·업무는 지우지 않습니다. |
| - | **협력사 담당자 이력** | 계약 상세의 기본정보 바로 아래에 **위탁사(당사) 담당자** 변경 이력을 보여줍니다. 위탁사 담당부서·담당자·이메일 중 하나라도 바뀌면 변경 후 값, **그 시점의 계약기간**, 변경자·변경일시를 남깁니다(바뀐 항목은 노란색 강조). **수탁사 담당자·연락처·이메일 변경은 이력을 남기지 않습니다.** 기본은 **같은 업체의 모든 계약** 이력을 함께 보여주고(㈜·(주)·공백 차이 무시), "이 계약만"으로 좁힐 수 있습니다. 이력은 추가만 되고 수정되지 않습니다. 표 제목은 **담당부서 / 담당자 / 담당자 이메일**입니다. **행을 누르면 내역 팝업**이 열려 그 이력의 **변경 전 → 변경 후**(같은 계약의 바로 이전 이력과 비교, 바뀐 항목 강조), 그 시점 계약기간, 변경자·변경일시, 해당 계약의 현재 정보(원계약 기간, 현재 담당자, 위탁계약서 체결일, 최근 점검, 종료일)를 보여줍니다. 다른(이전) 계약 행이면 **[이 계약 상세로 이동]** 버튼이 나옵니다. 키보드(Enter)로도 열 수 있습니다. 서버·DB 변경 없음. |
| - | **계약만료시 점검** | 계약 상세의 **위수탁 점검 이력**과 **만료 알림 발송 이력** 사이에 섹션을 추가했습니다. 항목은 ① **만료 점검 결과(엑셀)**: 체크리스트 '3.계약변경 만료시 체크' 시트를 올리면 계약변경·만료 점검 이력으로 저장·판정합니다(업로드 팝업이 '계약변경·만료 점검'으로 열림). ② **정보보호 및 비밀유지 확약서**와 ③ **개인정보 파기 확인서**는 첨부 구분 `SECURITY_PLEDGE` / `DESTRUCTION_CONFIRM` 으로 저장됩니다. 항목마다 **양식 다운로드**(만료 점검 체크리스트 / 확약서 양식 / 파기 확인서 양식, 계약 정보 자동 입력), 제출 현황(수취 완료·날짜 / 미수취, 여러 건이면 총 건수), 최근 제출 파일 다운로드, 업로드 버튼이 있고, 제목 옆에 **수취 N / 3**, 오른쪽 위에 **[양식 3종 모두 다운로드]** 버튼이 있습니다. 확약서·파기 확인서 양식(docx)에는 위탁사명·수탁업체명·계약명·위탁업무·계약기간·계약종료일·재위탁 업체가 채워지고, 체크리스트는 3번 시트가 열린 상태로 내려받습니다. DB 변경 없음. |
| - | **양식 관리 (보안팀)** | 계약 목록의 **[양식 관리]** 버튼(`#/forms`, 보안팀 관리자 `ROLE_PI_ADMIN`·플랫폼 관리자 `ROLE_ADMIN` 에게만 보임)에서 ① **위수탁 점검 체크리스트**(xlsx, 정기·만료 점검 공용) ② **정보보호 및 비밀유지 확약서** ③ **개인정보 파기 확인서**(docx·doc·hwp·hwpx·pdf)를 버전으로 관리합니다. **사용 중** 양식이 계약 화면 양식 다운로드(빈 양식·계약별 자동 입력)와 **만료 알림 메일 첨부**에 바로 쓰이므로 배포 없이 교체됩니다. ▸ **새 버전 등록**: 파일을 고르면 저장 전에 검사 결과를 보여주고 통과해야 [등록]이 켜짐. 체크리스트는 시스템 판독기로 정기(1·2번 시트)·만료(3번 시트) 점검을 실제로 읽어 보고 **읽지 못하면 등록 불가**(협력사 작성본 자동 판정 보호), 평가 항목 수가 기본 양식과 다르거나 회사명·평가일 라벨, '현황' 열이 없으면 경고. 확약서·파기 확인서 docx 는 자리표시자(`{{수탁업체명}}` 등 7개, 안쪽 공백 허용)를 찾아 자동 입력 항목을 보여주고, 자리표시자가 없거나 모르는 자리표시자는 경고. **hwp·hwpx·doc·pdf 는 등록되지만 자동 입력 없이 원본 그대로** 내려받기·메일 첨부(pdf 는 파일 형식 확인). 변경 메모, 등록 후 바로 사용 지정 여부 선택. ▸ **버전 이력**: 버전(v1, v2…, 삭제해도 번호 재사용 안 함)·파일·변경 메모·검사 결과·등록자/일시·사용 지정자/일시, 원본 다운로드, **미리보기**(샘플 계약 정보로 채운 양식), **[사용 지정]**(이전 버전으로 되돌리기), 삭제(소프트, 사용 중 버전은 불가), **[기본 양식 사용]**(모듈 내장 기본 양식으로 복귀, 등록 버전은 남음). 기본 양식 원본·미리보기도 내려받을 수 있습니다. ▸ **증적**: 점검 업로드 이력에 그 시점 체크리스트 양식 버전(`pi_inspection.FORM_VERSION`), 만료 알림 발송 이력에 첨부한 양식 버전(`pi_alert_log.FORM_VERSIONS`, 예: 체크리스트 기본 / 확약서 v1 / 파기 확인서 v2)을 남기고 상세 화면 알림 이력의 **첨부 양식** 열에 표시합니다. 사용 중 양식 파일을 읽지 못하면 기본 양식으로 대신 처리하고 오류 로그를 남깁니다. SQL `08_form_template.sql`. |
| - | **만료 서류 미수취 (확인 필요 · 처리 필요 항목)** | 종료 처리했거나 원계약 종료일이 만료 알림 기간 안(만료임박)·지난 계약에서 **만료 점검 체크리스트(계약변경·만료 점검 업로드) / 정보보호 및 비밀유지 확약서 / 개인정보 파기 확인서** 중 받지 못한 서류를 계산합니다. ① 목록 상단 **확인 필요 카드**(만료 서류 미수취 / 조치 지연 / 재위탁 동의서 누락 / 파기 지연 건수, 누르면 그 조건으로 목록 필터, 기준 연도와 상관없이 오늘 기준) ② 목록 상태 칸 **만료서류 N/3** 칩 ③ 상세 **처리 필요 항목** 패널(설명 문구 포함, 미수취 서류, [계약만료시 점검으로] 이동, 독촉 메일 켜기/끄기). ④ **종료 처리 시 서류가 빠져 있으면 '만료 서류 미수취 사유'를 반드시 입력**해야 하고(서버도 400), 사유와 독촉 메일 발송 여부를 저장합니다(`pi_contract.EXPIRY_DOCS_NOTE / EXPIRY_DOCS_REMIND_YN`). 종료 취소하면 사유는 지웁니다. ⑤ **종료(만료) 후 매주 1회 독촉 메일**(담당자 + 추가 수신자 + 보안팀, 받지 못한 서류의 양식만 첨부, 미수취 사유 표시, 종료 후 90일까지, 독촉 끄기 가능). |
| - | **점검 조치 기한 지남** | 결과가 **조치 필요**이고 조치 완료가 아닌데 **조치 예정일이 지난** 점검을 조치 지연으로 봅니다. 목록 칩 **조치지연**, 확인 필요 카드, 상세 패널(점검일·조치 예정일·지난 일수·N 항목 수)에 표시하고, 종료되지 않은 계약은 **주 1회 알림 메일**(조치 예정일 후 90일까지)을 보냅니다. 조치 완료로 바꾸면 바로 빠집니다. |
| - | **접근 기록 (보안팀)** | 계약 상세 조회, 점검 상세 조회, 계약서·증빙 다운로드, 점검 원본 다운로드, **계약 목록 엑셀 다운로드**, 접근 기록 엑셀 다운로드를 사용자·로그인 ID·접속 IP(`X-Forwarded-For` 우선)·브라우저·대상(업체·계약·파일명·검색 조건)·행 수와 함께 `pi_access_log` 에 남깁니다(추가만, 수정·삭제 API 없음, 기록 실패가 업무를 막지 않음). **엑셀 다운로드는 사유 필수**(화면 사유 입력 팝업, 서버도 사유 없으면 400). 목록의 **[접근 기록]** 버튼(`#/access-logs`, 관리자만)에서 기간(기본 최근 30일)·행위·사용자·업체로 검색하고 엑셀로 내려받습니다. |
| - | **정기 점검 알림 (4월 10일)** | 정기 점검은 **5월**로 보고, **4월 10일부터 5월 31일까지** 매일 알림 실행 때 올해 정기 점검을 아직 올리지 않은 **진행 중 계약**(5월에 계약기간이 걸치고 종료 처리되지 않은 계약)의 **담당자(+ 추가 수신자)**에게 정기 점검 요청 메일을 1회 보냅니다(계약 정보가 채워진 점검 체크리스트 첨부, 기한 5월 31일). 같은 날 **관리자(보안팀)**에게 대상 목록 요약 메일을 1회 보냅니다(`default-recipients` + `regular-inspection.admin-recipients`, 담당자 이메일 없는 계약 빨간 표시). 연도별로 한 번씩만 보내고, 4월 10일 이후 새로 등록된 계약은 기간 안 다음 실행 때 담당자에게 보냅니다. 날짜·월은 설정으로 바꿀 수 있습니다. |
| - | **서류 일자 · 파기 지연** | 정보보호 및 비밀유지 확약서는 **서명일**, 개인정보 파기 확인서는 **파기 완료일**을 파일마다 저장합니다(`pi_contract_file.DOCUMENT_DATE`, 업로드 때 입력, 첨부 목록 연필 버튼으로 수정·비우기, 오늘보다 늦은 날짜 400, 다른 서류 종류는 400). 기한은 **계약 종료 기준일**(종료 처리일, 없으면 원계약 종료일)부터 두 가지로 봅니다. ① **파기 기한 = 종료 + 5일** — 표준 개인정보 보호지침 제10조 ①("보유 기간 경과·처리 목적 달성 시 … 5일 이내에 파기"). 최근 파기 확인서의 파기 완료일이 이 날짜보다 늦으면 **파기 지연**(목록 칩·카드·상세 패널). ② **파기 확인서 회신 기한 = 종료 + 30일** — 협력사가 확인서를 보내오는 실무 기간. 이 날짜가 지났는데 파기 확인서가 없으면 **파기 확인서 회신 기한 지남**(목록 칩·상세 패널, 독촉 메일에 두 기한 표시). 일수는 설정값(`destruction-days`, `destruction-confirm-reply-days`). **입력 팝업**: 확약서·파기 확인서를 올리거나 연필 버튼을 누르면 팝업이 열리고, 파기 확인서는 두 기한을 함께 보여줍니다. 파기 기한보다 늦은 날짜를 넣으면 '○일 늦습니다 — 파기 지연으로 표시' 경고와 **파기 지연 사유** 칸이 열리고, 저장 전에 한 번 더 확인합니다(`pi_contract_file.DOCUMENT_NOTE`, 파기 확인서만, 500자, 첨부 목록·상세 패널에 표시). 일자를 입력하지 않은 서류는 상세 패널에 '일자 미입력'으로 안내합니다. |
| - | **재위탁 동의서 누락** | 재위탁 여부가 **Y** 인데 **재위탁 동의서 파일**(첨부 구분 `SUBCONTRACT_CONSENT`)이 없으면 목록 칩 **동의서 누락**, 확인 필요 카드, 상세 패널에 경고합니다. 동의서를 올리면 바로 빠집니다. |
| - | **업로드 파일 내용 확인** | 확장자만 바꾼 파일을 막기 위해 저장 전에 파일 내용(서명 바이트·내부 구조)을 확인합니다. pdf: 앞 1KB 안 `%PDF-` / docx·xlsx·hwpx: ZIP 이면서 각각 `word/`·`xl/`·`Contents/`(또는 `mimetype`) 항목 / zip: ZIP 서명 / doc·xls·hwp: OLE2 복합 문서 서명 / jpg·png: 이미지 서명. 맞지 않으면 저장하지 않고 400('파일 내용이 확장자(.x)와 맞지 않습니다…'). 계약서·증빙, 점검 체크리스트, 양식 관리 업로드에 모두 적용됩니다. |
| - | **자동연장 기간 · 만료 대상 제외** | 원계약 영역에 **자동연장 기간**(숫자 + 년/개월, 기본 **1년**, 1개월~10년, `pi_contract.AUTO_RENEW_PERIOD_MONTHS`) 칸을 추가했습니다. 자동연장 Y 일 때만 입력할 수 있고 수정할 수 있습니다. **자동연장 Y + 종료 처리 안 한 계약**은 원계약 종료일이 지나도 추가 계약 없이 그 기간 단위로 계약기간이 이어지는 것으로 보고 **만료임박·만료 상태, 만료 서류 미수취, 파기 기한·확인서 회신 기한, 만료 알림 메일, 만료 서류 독촉 메일, 기준 연도 '종료' 집계에서 모두 뺍니다**(계약중으로 표시). **현재 계약 종료일** = 원계약 종료일 + 연장 횟수 × 자동연장 기간(월말 기준 유지, 예: 2025.12.31 → 2026.12.31)으로 계산해 상세 화면('원계약 기간' 아래 **'현재 계약기간'** 줄 — 시작일 ~ 현재 종료일 + '자동연장 N회 반영' / 아직 연장 전이면 '원계약 기간과 같음 · ○○ 이후 1년 단위 자동연장'), **목록 '계약기간' 열**과 **같은 업체 계약 표의 '계약기간' 열**(상세 섹션·목록 '계약 N건' 팝업·등록 화면 안내 공통, 시작일 ~ 현재 종료일 + '연장N' 칩, 마우스를 올리면 원계약 기간 표시), 만료까지(일, 옆에 '자동연장' 표시), 엑셀 **'현재 계약 종료일'** 열(원계약 종료일 다음, 연장 없으면 원계약 종료일과 같음)과 '자동연장' 열(Y (1년)), 확약서·파기 확인서 양식의 계약기간·계약종료일에 씁니다. 실제로 계약이 끝나면 **[계약 종료 처리]** 를 해야 만료 대상이 되며, 이때 종료일 기본값은 현재(연장된) 계약 종료일입니다. 기존 계약은 기간 12개월(1년)로 채워집니다(SQL `11`). **자동연장 예정 확인 메일**: 자동연장 중이고 만료 알림을 사용하는 계약은 현재 계약 종료일이 만료 알림 기준일(기본 30일) 안에 들어오면 담당자 + 추가 수신자 + 보안팀에게 "○○ 이후 1년 연장 — 계속 유지하는지 확인" 메일을 **연장 주기마다 1회** 보냅니다(유지 / 해지 시 종료 처리·만료 서류 / 조건 변경 안내, 연장 후 종료일 표시). 종료 처리하거나 만료 알림을 끄면 보내지 않습니다. |
| - | **정기 파기확인 (월/분기/반기)** | 이벤트 대행처럼 계약 중에도 개인정보를 주기적으로 파기하는 협력사용입니다. 계약 등록·수정 화면 **'위탁업무 내용' 옆에 [정기 파기확인]** 체크, **주기(월/분기/반기, 기본 분기)**, **요청·독촉 메일 발송** 체크를 두었습니다(`pi_contract.PERIODIC_DESTRUCTION_YN/CYCLE/MAIL_YN`). **체크한 날이 관리 시작일**(`PERIODIC_DESTRUCTION_FROM`)이 되고, 그 날 이후에 끝나는 기간부터 미수취를 따집니다. 체크를 끄고 다시 켜면 그 날부터 다시 시작합니다. **수취 기한 = 기간 마지막 날 + 20일**(설정값 `periodic-destruction.reply-days`, 예: 3분기 → 10/20). 대상 기간은 계약기간(시작일 ~ 종료 처리일, 없으면 자동연장 반영 현재 종료일)과 겹치는 기간입니다. **업로드**: 상세 화면 **[정기 파기확인 현황]** 표(연도 선택, 기간·기간 일자·수취 기한·상태·파기 확인서)에서 기간 행의 [올리기]로 올리거나, 첨부 업로드에서 '개인정보 파기 확인서'를 고르면 나오는 **대상 기간** 선택(기본 직전에 끝난 기간, '계약 종료 파기' 선택 가능)으로 올립니다(`pi_contract_file.DESTRUCTION_PERIOD`, 예: `2026-07` / `2026-Q3` / `2026-H2`). 설명을 비워 두면 어느 경로로 올려도 '2026년 3분기 정기 파기 확인서'처럼 기간 설명이 자동으로 들어갑니다. **관리 시작 전에 끝난 지난 기간도 올릴 수 있고**(상태 '관리 전', 미수취로 보지 않음), 주기가 다른 기간·아직 시작 안 한 기간·계약기간 밖·정기 파기확인이 아닌 계약은 400입니다. 상태: 받음 / 미수취(기한 지남) / 기한 전 / 진행 중 / 관리 전. 정기 파기 확인서는 **계약 종료 만료 서류의 파기 확인서로 세지 않습니다**(계약만료시 점검·파기 기한 판정과 별개). **확인 필요**: 카드·목록 필터 **'정기 파기확인서 미수취'**, 목록 칩('정기파기 분기' / '정기파기 미수취 N'), 상세 처리 필요 항목(미수취 기간·기한 + 현황으로 이동). **메일**: 메일 발송 Y 이면 기간이 끝나면 **제출 요청**, 수취 기한이 지나면 **미수취 독촉**을 기간마다 1회 담당자 + 추가 수신자 + 보안팀에게 보냅니다(파기 확인서 양식 첨부). 메일 발송 N 이면 화면 표시만 합니다. **엑셀**: '정기 파기확인'(Y (분기)), '정기 파기확인서 수취 기간'(받은 기간 전부, 예: 2건: 2026년 3분기, 2026년 2분기 · 없으면 '수취 이력 없음'), '정기 파기확인서 미수취 기간'(수취 기한이 지났는데 못 받은 기간, 없으면 '미수취 없음') 열 추가. |
| - | **업로드 내용 확인** | 첨부를 올리기 전에 서버가 파일 내용을 읽어 확인하고, 맞지 않으면 **저장하지 않습니다**(계약 상세 첨부, 계약만료시 점검, 정기 파기확인 현황, 계약 등록 화면의 계약서 업로드 모두 같은 확인, 화면을 거치지 않고 API로 올려도 서버가 다시 확인). ① **서류 종류**: 문서 제목(앞부분)의 핵심 문구로 위수탁 계약서·재위탁 동의서·정보보호 및 비밀유지 확약서·개인정보 파기 확인서를 구분해, 고른 파일 구분과 다르면 막고 맞는 구분을 안내합니다. ② **날짜**: 입력한 파기 완료일·서명일·체결일이 문서에 적힌 날짜 중에 없으면 막고 문서의 날짜를 보여줍니다(서류 일자는 계약기간 날짜 제외). ③ **업체명**: 문서의 '수탁업체명' 등 표기가 계약 업체와 다르면 막습니다(㈜·(주)·주식회사·띄어쓰기 무시, 업체명 표기를 못 찾으면 확인 생략). ④ **중복 파일**: 이 계약에 같은 내용(SHA-256)의 파일이 이미 있으면 '중복된 파일입니다 — 확인하세요' 오류창으로 막습니다. ①~④는 '내용 직접 확인'으로도 넘길 수 없습니다. **글자를 읽을 수 없는 파일**(스캔 PDF·jpg·png·zip·암호 파일)이나 서류 종류를 알아볼 수 없는 문서는 **'내용 직접 확인했습니다'에 체크해야** 올라갑니다. '기타' 구분은 중복만 확인합니다. 확인 결과는 `pi_contract_file.CONTENT_CHECK`(AUTO 자동 확인 / MANUAL 직접 확인 / NULL 이 기능 이전 파일)에 남습니다(SQL `13`). API: `POST /personalinfo-api/contracts/{id}/files/check`(저장 없이 확인), 업로드에 `contentConfirmed=Y`(직접 확인). |
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
- 대상: 미삭제 + 미종료 + **자동연장 아님** + 알림 사용(Y) + 종료일 있음
- 단계: 남은 일수가 지난 가장 가까운 기준 1개만 발송합니다(서버가 며칠 멈췄다가 다시 떠도 몰아서 보내지 않습니다).
- 중복 방지: `pi_alert_log.DEDUP_KEY = 계약ID:단계:종료일` UNIQUE. WAS 가 여러 대여도 한 번만 발송합니다. 종료일을 바꾸면 새 일정으로 다시 알립니다.
- 수신자: 계약 담당자 이메일 + 계약별 추가 수신자 + `personalinfo.alert.default-recipients`(예: 보안팀)
- **첨부 양식(계약만료시 점검)**: 자동·수동 만료 알림 메일에 ① 계약만료시 점검 체크리스트(.xlsx, 3번 시트) ② 정보보호 및 비밀유지 확약서(.docx) ③ 개인정보 파기 확인서(.docx)를 계약 정보가 채워진 상태로 첨부합니다(한글 파일명). 본문에는 만료일 전에 협력사로부터 3가지 자료를 받아 시스템에 올려 달라는 안내와 **계약 만료 시 협력사 제출 자료** 표(자료별 수취 완료 / 미수취)가 들어갑니다. 양식 하나를 만들지 못해도 나머지 양식으로 메일은 발송됩니다.
- 실패하면 다음 실행 때 최대 3회까지 재시도합니다. 메일 서버가 설정되지 않았으면 `SKIPPED` 로 기록하고, 애플리케이션 기동에는 영향을 주지 않습니다.

### 보안팀 후속 알림 규칙 (같은 일일 알림 실행에서 함께 처리, 하나가 실패해도 나머지는 진행)
| 알림 종류 (`pi_alert_log.ALERT_TYPE`) | 대상 | 수신자 | 중복 방지 키 (`DEDUP_KEY`) |
|---|---|---|---|
| `EXPIRY` 만료 알림 (기존) | 미종료 + 알림 사용 + 종료일 단계 | 담당자 + 추가 수신자 + 보안팀 | `계약ID:단계:종료일` |
| `EXPIRY_DOCS` 만료 서류 독촉 | 종료 기준일이 지났고 90일 안, 미수취 서류 있음, 독촉 켜짐 | 담당자 + 추가 수신자 + 보안팀 | `DOCS:계약ID:ISO주차` (주 1회) |
| `ACTION_OVERDUE` 조치 지연 | 조치 필요 + 미완료 + 조치 예정일 지남(90일 안), 미종료 계약 | 담당자 + 추가 수신자 + 보안팀 | `OVERDUE:계약ID:ISO주차` (주 1회) |
| `REGULAR_INSPECTION` 정기 점검 요청 | 4/10~5/31, 올해 정기 점검 없음, 5월에 계약기간이 걸친 미종료 계약 | 담당자 + 추가 수신자 | `REGULAR:계약ID:연도` (연 1회) |
| `REGULAR_SUMMARY` 정기 점검 요약 | 위 대상 목록 (계약 ID 없음) | 보안팀(`default-recipients`) + `regular-inspection.admin-recipients` | `REGULAR_SUMMARY:연도` (연 1회) |
| `AUTO_RENEW_NOTICE` 자동연장 확인 | 자동연장 Y + 미종료 + 만료 알림 사용, 현재 계약 종료일이 만료 알림 기준일(기본 30일) 안 | 담당자 + 추가 수신자 + 보안팀 | `RENEW:계약ID:현재 종료일` (연장 주기마다 1회) |
| `PERIODIC_DESTRUCTION_REQUEST` 정기 파기확인 요청 · `PERIODIC_DESTRUCTION_OVERDUE` 정기 파기확인 독촉 | 정기 파기확인 Y + 메일 발송 Y, 관리 시작일 이후에 끝난 기간: 기간 종료 ~ 수취 기한(종료 + 20일)이면 요청, 기한이 지나도 확인서가 없으면 독촉 | 담당자 + 추가 수신자 + 보안팀 | `PDREQ:계약ID:기간` / `PDOVD:계약ID:기간` (기간마다 각 1회) |

- 상세 화면 **알림 발송 이력**에 **종류** 열이 추가됐습니다. 관리자가 목록의 **[알림 실행]**으로 바로 돌릴 수 있고(`baseDate` 로 특정 날짜 기준 재실행 가능), 결과에 종류별 발송 건수가 나옵니다.

---

## 2. 생성 테이블 목록 (`01_schema.sql`)

| 테이블 | 설명 | 주요 키/제약 |
|---|---|---|
| `pi_contract` | 위수탁 계약 (외부위탁 현황 1행 = 1건) (+ 만료 서류 미수취 사유 `EXPIRY_DOCS_NOTE`·독촉 여부 `EXPIRY_DOCS_REMIND_YN`, `09` / 자동연장 기간 `AUTO_RENEW_PERIOD_MONTHS`, `11`) | PK `CONTRACT_ID`, IDX 종료일/업체명/부서/알림 |
| `pi_contract_file` | 계약서·증빙 첨부파일 메타데이터 (+ 서류 일자 `DOCUMENT_DATE` = 확약서 서명일 / 파기 완료일, `09` / 파기 지연 사유 `DOCUMENT_NOTE`, `10`) | FK → `pi_contract` |
| `pi_inspection` | 점검 이력 (체크리스트 업로드 1건 × 계약) | FK → `pi_contract`, IDX `UPLOAD_GROUP_ID`(업체별 일괄 업로드 묶음, 개별 업로드는 NULL) |
| `pi_inspection_item` | 점검 체크리스트 항목별 결과 | FK → `pi_inspection` |
| `pi_contact_history` | 위탁사 담당자 변경 이력 + 기록 시점 계약기간 (`04`, `05`) | FK → `pi_contract`, IDX 계약+삭제여부+일시 |
| `pi_alert_log` | 알림 발송 이력 (+ 첨부 양식 버전 `FORM_VERSIONS`, `08` / 알림 종류 `ALERT_TYPE`, 관리자 요약은 `CONTRACT_ID` NULL, `09`) | FK → `pi_contract`(NULL 허용), `UK_PI_ALERT_LOG_DEDUP`, IDX 종류 |
| `pi_form_template` | 보안팀 양식 버전 이력 (`08`) — 종류(CHECKLIST/SECURITY_PLEDGE/DESTRUCTION_CONFIRM)·버전 번호·파일·변경 메모·검사 결과·사용 중 여부 | PK `FORM_TEMPLATE_ID`, `UK_PI_FORM_TEMPLATE_VERSION`(종류+버전), IDX 종류+사용중+삭제여부. 다른 테이블과 FK 없음 |
| `pi_access_log` | 접근 기록 (`09`) — 행위(조회·다운로드)·계약·대상·행 수·사유·사용자 ID/로그인 ID/이름·접속 IP·브라우저 | PK `ACCESS_ID`, IDX 일시 / 계약 / 사용자 / 행위. 추가만, FK 없음(계약 삭제와 무관하게 보존) |

- 모든 테이블에 공통 컬럼(`CREATED_AT/BY`, `UPDATED_AT/BY`, `DELETED_YN/AT/BY`)이 있습니다. 모든 테이블·컬럼에 COMMENT 가 있습니다(7개 테이블, 누락 0건 검증).
- FK 는 업무 테이블 사이에만 있습니다. `CREATED_BY` 같은 사용자 컬럼은 core_user ID 값만 저장하고 **core 테이블 FK 는 없습니다.**
- 삭제는 모두 소프트 삭제(`DELETED_YN='Y'`)입니다. 업로드한 실제 파일도 감사 증적으로 지우지 않습니다.

---

## 3. API 목록 (`/personalinfo-api`)

모든 JSON 응답은 core `ApiResponse<T>` 로 감쌉니다. 파일 다운로드(바이너리) 응답만 예외로, 원본 바이트를 내려줍니다(module-kims 첨부 다운로드와 같은 방식이며, 오류는 core GlobalExceptionHandler 가 ApiResponse 로 응답).

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/me/permissions` | 현재 사용자의 화면 버튼 권한 + 부서 범위 `allDepartments`(관리자 true), `deptName`(비관리자 소속부서, 없으면 생략) | 로그인 |
| GET | `/dashboard?baseYear=2026` | 목록 상단 요약. 기준 연도(없으면 올해) 기준 `baseYear, availableYears, yearTotalCount, yearActiveCount, yearActiveInspectedCount, yearEndedCount, yearEndedTermCheckCount, yearEndedTermMissingCount` + 기존 상태별 건수·만료임박 목록 + 확인 필요(오늘 기준) `expiryDocsMissingCount, actionOverdueCount, consentMissingCount, destructionLateCount` | READ |
| GET | `/contracts` | 계약 목록. `keyword, deptName, managerName, status, ismsScopeYn, inspectionResult, endDateFrom, endDateTo, page, size, sort` + 기준 연도 `baseYear`, `yearStatus`(ACTIVE/ENDED), `yearCheck`(INSPECTED/NOT_INSPECTED/TERM_UPLOADED/TERM_MISSING). `baseYear` 가 없으면 연도 조건 미적용. 행마다 `terminationCheckYn`(계약종결 체크 업로드 여부), `contractName`, `sameVendorContractCount`(같은 업체 계약 수). `keyword` 는 계약명도 검색. `followUp`(EXPIRY_DOCS_MISSING / ACTION_OVERDUE / CONSENT_MISSING / DESTRUCTION_LATE)을 주면 기준 연도와 상관없이 오늘 기준 확인 필요 대상만. 행마다 `expiryDocsNeeded, expiryDocsReceived, expiryDocsMissing, actionOverdueCount, consentMissing, destructionLate, confirmReplyOverdue` | READ |
| GET | `/contracts/excel?reason=` | 목록 엑셀 다운로드 (검색 조건 적용). **`reason`(다운로드 사유, 2자 이상) 필수** — 없으면 400. 접근 기록(조건·행 수·사유) 남김 | READ |
| GET | `/contracts/{id}` | 계약 상세 (첨부/점검/알림 이력 + `followUp`(미수취 서류·조치 지연 점검·동의서 누락·파기 지연·지연 사유·파기 기한·확인서 회신 기한/지남·일자 미입력) + `expiryDocsNote, expiryDocsRemindYn`). 접근 기록 남김 | READ |
| PATCH | `/contracts/{id}/expiry-docs-remind` | 만료 서류 독촉 메일 발송 여부 `{"remindYn":"Y"}` 또는 `"N"` (그 외 400) | WRITE |
| POST | `/contracts` | 계약 등록. 필수: `vendorName`, `contractName`(최대 200자), `entrustedTask`, `contractStartDate`, `contractEndDate`. `autoRenewPeriodMonths`(자동연장 기간 개월, 1~120, 기본 12). 상세·목록 응답에 `autoRenewPeriodMonths, autoRenewing, autoRenewCount, currentEndDate` | WRITE |
| PUT | `/contracts/{id}` | 계약 수정 | WRITE |
| GET | `/contracts/{id}/contact-history?vendorWide=true` | 위탁사 담당자 변경 이력(계약기간 포함). `vendorWide=false` 면 이 계약만 | READ |
| GET | `/contracts/{id}/same-vendor` | 같은 업체(업체명 정규화 기준)의 계약 목록, 이 계약 포함. 진행 중 먼저 → 시작일순 | READ |
| GET | `/vendor-contracts?vendorName=` | 업체명으로 그 업체의 계약 목록(등록 화면 안내용, ㈜·(주)·공백 차이 무시). 없으면 빈 목록 | READ |
| PATCH | `/contracts/{id}/flags` | 목록에서 Y/N 바로 변경 `{ismsScopeYn?, subcontractYn?}` — 보낸 항목만 변경, 값은 Y/N 만 허용 | WRITE |
| PATCH | `/files/{fileId}/signed-date` | 위수탁 계약서 체결일 수정 `{"signedDate":"yyyy-MM-dd"}` (빈 값이면 비움). 위수탁 계약서가 아니면 400. 수정 후 계약 체결일을 최신 계약서 기준으로 다시 맞춤 | WRITE |
| POST | `/contract-doc/analyze` | 위수탁 계약서 분석 (multipart `file`). 계약일자와 후보를 돌려주며 **파일은 저장하지 않음**. 응답: `fileName, textReadable, detectedDate(체결일 표기가 없으면 계약기간 시작일), detectedReason, candidates[{date, kind(SIGNED/DOC/PERIOD), kindLabel, context}], message` | WRITE |
| POST | `/contracts/{id}/terminate` | 계약 종료 처리 `{terminatedDate, reason, expiryDocsNote, expiryDocsRemindYn}`. 종료일 미입력 시 원계약 종료일(없으면 오늘), 원계약 시작일보다 빠르면 400. **만료 서류 3종 중 받지 못한 서류가 있으면 `expiryDocsNote` 필수**(없으면 400, 미수취 서류 이름 안내) | WRITE |
| POST | `/contracts/{id}/reopen` | 종료 취소 | WRITE |
| DELETE | `/contracts/{id}` | 계약 삭제 (소프트) | **DELETE** |
| GET | `/contracts/{id}/files` | 첨부 목록 | READ |
| POST | `/contracts/{id}/files` | 계약서 업로드 (multipart: `file`, `fileType`, `description`) — 위수탁 계약서는 `signedDate`(선택, 없으면 파일에서 읽음)를 함께 저장하고 계약 체결일을 최신 계약서 기준으로 맞춤. 확약서·파기 확인서는 `documentDate`(서명일 / 파기 완료일, 미래 날짜 400), 파기 확인서는 `documentNote`(파기 지연 사유). **파일 내용이 확장자와 다르면 400** | WRITE |
| PATCH | `/files/{fileId}/document-date` | 서류 일자 수정 `{"documentDate":"yyyy-MM-dd", "documentNote":"파기 지연 사유"}` (빈 값이면 비움, `documentNote` 키를 보내지 않으면 기존 사유 유지). 확약서·파기 확인서만, 지연 사유는 파기 확인서만(500자), 미래 날짜·형식 오류 400 | WRITE |
| GET | `/files/{fileId}/download` | 첨부 다운로드 (접근 기록 남김) | READ |
| DELETE | `/files/{fileId}` | 첨부 삭제 (소프트) | **DELETE** |
| GET | `/checklist-template` | 점검 체크리스트 빈 양식 다운로드 | READ |
| GET | `/contracts/{id}/checklist-template` | 회사명·수탁업무를 미리 채운 양식 다운로드 | READ |
| GET | `/expiry-forms/{formType}` | 계약만료시 점검 빈 양식 다운로드. `formType` = `checklist`(xlsx) / `security-pledge`(정보보호 및 비밀유지 확약서, docx) / `destruction-confirm`(개인정보 파기 확인서, docx). 잘못된 값 400 | READ |
| GET | `/contracts/{id}/expiry-forms/{formType}` | 계약 정보를 채운 계약만료시 점검 양식 다운로드 (다른 부서 계약 403) | READ |
| GET | `/contracts/{id}/inspections` | 점검 이력 | READ |
| POST | `/contracts/{id}/inspections` | 작성된 체크리스트 업로드 (multipart: `file`, `inspectionType`, `inspectionDate`, `inspectorName`, `actionDueDate`, `remark`) | WRITE |
| GET | `/inspections/{inspectionId}` | 점검 상세 (항목별 결과, 접근 기록 남김) | READ |
| PUT | `/inspections/{inspectionId}` | 후속조치 수정 | WRITE |
| GET | `/inspections/{inspectionId}/download` | 업로드 원본 다운로드 (접근 기록 남김) | READ |
| DELETE | `/inspections/{inspectionId}` | 점검 이력 삭제 (소프트, 최근 점검 다시 계산) | **DELETE** |
| GET | `/vendors` | 업체 목록(업체명 기준 묶음, 계약 목록·최근 점검 포함). `keyword, includeTerminated` | READ |
| POST | `/vendors/checklist-preview` | 업체별 업로드 미리보기 — 저장하지 않고 집계·판정·업체 후보 반환 (multipart: `file`, `inspectionType`) | WRITE |
| POST | `/vendors/checklist` | **업체별 체크리스트 업로드** — 1개 파일을 업체의 여러 계약에 등록 (multipart: `vendorKey`, `contractIds`(여러 개, 비우면 미종료 계약 전체), `file`, `inspectionType`, `inspectionDate`, `inspectorName`, `actionDueDate`, `remark`) | WRITE |
| POST | `/contracts/{id}/alerts` | 만료 안내 메일 수동 발송 `{extraRecipients, message}` | WRITE |
| POST | `/alerts/run?baseDate=` | 일일 알림 일괄 실행 (만료 알림 + 만료 서류 독촉 + 조치 지연 + 정기 점검 + 자동연장 확인, 스케줄러 대체/재실행). 응답에 `docsReminderCount, actionOverdueCount, regularInspectionCount, regularSummaryCount, autoRenewNoticeCount` 추가 | **DELETE(관리자)** |
| GET | `/access-logs` | 접근 기록 검색 `from, to`(기본 최근 30일), `actionType, contractId, user`(이름·로그인 ID), `vendor`(업체·계약·대상), `page, size` → `{content, totalElements, page, size, totalPages}` | **양식 관리(관리자)** |
| GET | `/access-logs/action-types` | 행위 구분 목록 (계약 상세 조회, 점검 상세 조회, 계약서·증빙 다운로드, 점검 원본 다운로드, 계약 목록 엑셀 다운로드, 접근 기록 엑셀 다운로드) | **양식 관리(관리자)** |
| GET | `/access-logs/excel?reason=` | 접근 기록 엑셀 (검색 조건, 최대 10,000건). 사유 필수, 이 다운로드도 기록 | **양식 관리(관리자)** |
| GET | `/form-templates` | 양식 3종의 사용 중 버전·버전 이력·허용 형식·자리표시자·체크리스트 구조 규칙 | **양식 관리(관리자)** |
| POST | `/form-templates/{formType}/check` | 저장하지 않고 양식 검사만 (multipart: `file`). 체크리스트를 시스템이 읽을 수 없으면 400 | **양식 관리(관리자)** |
| POST | `/form-templates/{formType}` | 새 버전 등록 (multipart: `file`, `changeNote`, `activate`(기본 true)). 검사 실패 시 저장하지 않고 400 | **양식 관리(관리자)** |
| POST | `/form-templates/versions/{id}/activate` | 이 버전을 사용 중으로 지정 (이전 버전으로 되돌리기) | **양식 관리(관리자)** |
| POST | `/form-templates/{formType}/use-default` | 모듈 내장 기본 양식으로 되돌리기 | **양식 관리(관리자)** |
| DELETE | `/form-templates/versions/{id}` | 버전 삭제 (소프트, 사용 중 버전은 400) | **양식 관리(관리자)** |
| GET | `/form-templates/versions/{id}/download` · `/form-templates/{formType}/default/download` | 버전 원본 / 기본 양식 원본 다운로드 | **양식 관리(관리자)** |
| GET | `/form-templates/versions/{id}/preview` · `/form-templates/{formType}/default/preview` | 샘플 계약 정보로 채운 미리보기 (xlsx·docx) | **양식 관리(관리자)** |

`formType` = `checklist` / `security-pledge` / `destruction-confirm`. 계약 화면의 양식 다운로드(`/checklist-template`, `/contracts/{id}/checklist-template`, `/expiry-forms/**`)와 만료 알림 메일 첨부는 모두 **사용 중 양식**(없으면 기본 양식)을 씁니다.

### 화면 (`/personalinfo/index.html`, 해시 라우팅)
| 화면 | 경로 | 내용 |
|---|---|---|
| 목록 | `#/contracts` | 요약 카드(클릭 시 필터), 확인 필요 카드 4종(클릭 시 필터)·상태 칸 확인 필요 칩, 엑셀 사유 입력, 열 순서·너비 편집([열 설정]), 계약명 열, 같은 업체 **계약 N건** 배지 → 업체 계약 목록 팝업, 검색, 정렬, 페이징, 엑셀, 양식 다운로드, ISMS·재위탁 Y/N 스위치, 업체명 클릭 시 업체별 업로드 팝업, 열 너비 조정 |
| 등록 | `#/contracts/new` (`?vendor=업체명` 이면 업체명 미리 채움) | 계약 항목·계약명 입력, 같은 업체 기존 계약 안내, 계약서 파일 함께 업로드 |
| 상세 | `#/contracts/{id}` | 기본정보(계약명), 같은 업체 계약 목록·이 업체로 계약 추가, 협력사 담당자 이력, 계약서 업로드·다운로드, 체크리스트 양식 다운로드·작성본 업로드, 점검 결과 상세·후속조치, 메일 발송, 종료 처리(미수취 사유), 처리 필요 항목 패널, 확약서 서명일·파기 완료일 입력, 알림 발송 이력(종류) |
| 수정 | `#/contracts/{id}/edit` | 계약 정보 수정 |
| 접근 기록 | `#/access-logs` (목록의 [접근 기록] 버튼, 관리자만) | 기간·행위·사용자·업체 검색, 기록 표(일시·행위·업체/계약·대상·행 수·사유·사용자·IP), 사유 입력 후 엑셀 |
| 양식 관리 | `#/forms` (목록의 [양식 관리] 버튼, 관리자만) | 양식 3종 카드: 사용 중 버전, 새 버전 등록(저장 전 검사), 버전 이력·원본 다운로드·미리보기·사용 지정·삭제, 기본 양식 사용·기본 양식 다운로드·미리보기, 자리표시자·체크리스트 구조 안내 |
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
| 양식 관리 (보안팀 양식 등록·교체·되돌리기) · **접근 기록 조회** | `PERSONALINFO_FORM_MANAGE` | `ROLE_ADMIN`, `ROLE_PI_ADMIN` (`PiRoles.CAN_MANAGE_FORMS`) |

| 사용자 구분 | 부여할 역할 | 사용 가능 기능 |
|---|---|---|
| 관리자 (보안팀) | `ROLE_PI_ADMIN` (또는 플랫폼 `ROLE_ADMIN`) | 전체 기능 + 삭제 + **양식 관리** + **접근 기록** |
| 일반 사용자 (계약 담당자) | `ROLE_PI_USER` | **소속부서 계약만** 등록, 수정, 조회, 업로드, 메일 발송 |
| 조회 사용자 | `ROLE_PI_VIEWER` | **소속부서 계약만** 조회, 다운로드 |

> 삭제는 관리자 역할만 할 수 있습니다(요청 사항 4). 계약 목록의 **관리(삭제) 열**과 상세 화면의 삭제 버튼은 삭제 권한이 있는 사용자에게만 보이고, 서버도 `@PreAuthorize(CAN_DELETE)` 로 한 번 더 막습니다(화면을 우회해 API 를 직접 호출해도 403).

### 부서별 조회 범위 — 운영자 확인 사항

- 관리자(`ROLE_ADMIN`, `ROLE_PI_ADMIN`)는 전체 계약을 봅니다. 그 밖의 사용자는 **사용자 관리 화면의 부서**가 지정되어 있어야 하고, 계약의 **담당부서** 값이 그 부서 이름(공통코드 `DEPT` 의 코드명) 또는 부서 코드와 같아야 보입니다.
- 배포 전에 ① 계약 담당자·조회 사용자의 부서가 지정되어 있는지, ② 기존 계약의 담당부서가 공통코드 `DEPT` 부서명과 같은 표기인지(예: '인사팀') 확인하세요. 표기가 다른 기존 계약은 관리자가 수정 화면에서 담당부서를 맞추면 됩니다.
- 부서를 바꾸면 다음 요청부터 바로 반영됩니다(재로그인 불필요).

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
| 6 | `06_contract_file_signed_date.sql` | **위수탁 계약서 파일별 체결일** — `pi_contract_file.SIGNED_DATE` 컬럼(`ADD COLUMN IF NOT EXISTS`, COMMENT 포함)과 인덱스(`CREATE INDEX IF NOT EXISTS`) 추가. 이미 올라가 있는 계약서 파일은 체결일이 비어 있으므로 필요하면 상세 화면에서 입력합니다. 기존 계약 체결일 값은 바꾸지 않습니다. 새로 설치할 때는 01 에 컬럼이 이미 있어 아무 변화 없이 통과합니다 |
| 7 | `07_contract_name.sql` | **계약명** — `pi_contract.CONTRACT_NAME` 컬럼(`ADD COLUMN IF NOT EXISTS`, COMMENT 포함, `VENDOR_NAME` 다음)과 업체명+계약명 인덱스(`CREATE INDEX IF NOT EXISTS`) 추가. 기존 계약은 계약명이 비어 있고 화면에서는 서비스명으로 표시되므로, 필요하면 수정 화면에서 입력합니다. **새 코드 배포 전에 먼저 실행해야 합니다**(컬럼이 없으면 계약 조회가 실패). 새로 설치할 때는 01 에 컬럼이 이미 있어 아무 변화 없이 통과합니다 |
| 8 | `08_form_template.sql` | **양식 관리** — `pi_form_template` 테이블(`CREATE TABLE IF NOT EXISTS`, COMMENT 포함, core FK 없음)과 `pi_inspection.FORM_VERSION`, `pi_alert_log.FORM_VERSIONS` 컬럼(`ADD COLUMN IF NOT EXISTS`) 추가. 등록한 양식이 없으면 지금처럼 모듈 기본 양식을 쓰므로 실행 직후 동작은 같습니다. **새 코드 배포 전에 먼저 실행해야 합니다**(컬럼이 없으면 점검·알림 조회가 실패). 기존 데이터는 바꾸지 않습니다 |
| 9 | `09_security_followup.sql` | **보안팀 확인 필요** — `pi_contract.EXPIRY_DOCS_NOTE / EXPIRY_DOCS_REMIND_YN`, `pi_contract_file.DOCUMENT_DATE`, `pi_alert_log.ALERT_TYPE`(기존 행은 'EXPIRY') 컬럼 추가(`ADD COLUMN IF NOT EXISTS`, COMMENT 포함), `pi_alert_log.CONTRACT_ID` NULL 허용(`MODIFY`, 기존 값 유지), 알림 종류 인덱스, 접근 기록 테이블 `pi_access_log`(`CREATE TABLE IF NOT EXISTS`, core FK 없음) 추가. 업무 데이터 변경·삭제 없음. 두 번 실행해도 오류 없음 |
| 10 | `10_destruction_delay_reason.sql` | **파기 지연 사유** — `pi_contract_file.DOCUMENT_NOTE` 컬럼(`ADD COLUMN IF NOT EXISTS`, COMMENT 포함) 추가. 데이터 변경 없음. 두 번 실행해도 오류 없음 |
| 11 | `11_auto_renew_period.sql` | **자동연장 기간** — `pi_contract.AUTO_RENEW_PERIOD_MONTHS` 컬럼(`ADD COLUMN IF NOT EXISTS`, INT NOT NULL DEFAULT 12, COMMENT 포함) 추가. 기존 계약은 12(1년)로 채워짐. 업무 데이터 변경·삭제 없음. 두 번 실행해도 오류 없음 |
| 12 | `12_periodic_destruction.sql` | **정기 파기확인** — `pi_contract` 에 `PERIODIC_DESTRUCTION_YN`(CHAR(1) NOT NULL DEFAULT 'N'), `PERIODIC_DESTRUCTION_CYCLE`(VARCHAR(10) NOT NULL DEFAULT 'QUARTER'), `PERIODIC_DESTRUCTION_MAIL_YN`(CHAR(1) NOT NULL DEFAULT 'Y'), `PERIODIC_DESTRUCTION_FROM`(DATE NULL), `pi_contract_file` 에 `DESTRUCTION_PERIOD`(VARCHAR(10) NULL) 추가(`ADD COLUMN IF NOT EXISTS`, COMMENT 포함). 기존 계약은 정기 파기확인 N, 기존 파기 확인서는 계약 종료 파기 확인서로 그대로. 업무 데이터 변경·삭제 없음. 두 번 실행해도 오류 없음 |
| 13 | `13_file_content_check.sql` | **업로드 내용 확인 결과** — `pi_contract_file.CONTENT_CHECK`(VARCHAR(10) NULL, COMMENT 포함) 추가(`ADD COLUMN IF NOT EXISTS`). 기존 파일은 NULL(확인 기능 이전). 업무 데이터 변경·삭제 없음. 두 번 실행해도 오류 없음 |

```bash
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/01_schema.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/02_seed_data.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/03_vendor_checklist_upload.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/04_contact_history.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/05_contact_history_contract_period.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/06_contract_file_signed_date.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/07_contract_name.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/08_form_template.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/09_security_followup.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/10_destruction_delay_reason.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/11_auto_renew_period.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/12_periodic_destruction.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < sql/module-personalinfo/13_file_content_check.sql
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
    follow-up-remind-days: 90                      # 만료 서류 독촉·조치 지연 주간 알림 기간 (종료일/조치 예정일 후 N일)
  regular-inspection:
    enabled: true                                  # 정기 점검 알림 사용
    notice-date: "04-10"                           # 알림 시작일 (MM-dd) — 이날부터 점검 월 말일까지 대상 계약에 1회
    month: 5                                       # 정기 점검 월
    admin-recipients: ""                           # 정기 점검 요약을 추가로 받을 관리자 (쉼표 구분, default-recipients 와 합침)
  destruction-days: 5                              # 파기 기한 (계약 종료 기준일 + N일, 표준 개인정보 보호지침 제10조 '5일 이내') — 넘으면 '파기 지연'
  destruction-confirm-reply-days: 30               # 파기 확인서 회신 기한 (계약 종료 기준일 + N일) — 지나도 확인서가 없으면 '회신 기한 지남'
  periodic-destruction:
    reply-days: 20                                 # 정기 파기 확인서 수취 기한 (기간 마지막 날 + N일) — 지나도 없으면 '정기 파기확인서 미수취'
  company-name: 깨끗한나라                          # 확약서·파기 확인서·만료 체크리스트의 위탁사명 (비우면 빈칸)

spring:
  mail:                                            # 사내 SMTP (없으면 메일은 SKIPPED 로 기록만)
    host: smtp.company.com
    port: 25
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties.mail.smtp.auth: false
    properties.mail.smtp.starttls.enable: false
```

- 업로드 디렉토리는 WAS 실행 계정이 쓸 수 있어야 하고(양식 관리에서 올린 양식도 `form-template/` 아래에 저장), 정기 백업 대상에 넣어야 합니다. WAS 를 여러 대 운영하면 공유 스토리지(NFS 등)를 써야 합니다.
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
├── build.gradle                         compileOnly core / security, poi-ooxml, spring-boot-starter-mail,
│                                        pdfbox·poi-scratchpad·hwplib(계약서 글자 읽기, 모두 Apache-2.0)
└── src/main/
    ├── java/com/company/module/personalinfo/
    │   ├── controller/  PiContractController, PiContractFileController, PiInspectionController,
    │   │                PiAlertController, PiVendorChecklistController, PiFormTemplateController(양식 관리), PiAccessLogController(접근 기록), PiDownloads(다운로드 응답 헬퍼)
    │   ├── dto/         ContractSaveRequest, ContractSearchCondition, ContractTerminateRequest,
    │   │                InspectionUpdateRequest, AlertSendRequest,
    │   │                ContractSummaryResponse, ContractDetailResponse, ContractFileResponse,
    │   │                InspectionResponse, InspectionItemResponse, AlertLogResponse,
    │   │                AlertRunResponse, DashboardResponse, PermissionResponse,
    │   │                VendorSummaryResponse, VendorChecklistPreviewResponse, VendorChecklistUploadResponse,
    │   │                ContractDocAnalyzeResponse(계약서 분석 결과),
    │   │                FormTemplateGroupResponse, FormTemplateResponse, FormTemplateCheckResponse(양식 관리),
    │   │                FollowUpResponse(보안팀 확인 필요), AccessLogResponse(접근 기록)
    │   ├── entity/      PiBaseEntity(공통 컬럼), PiContract, PiContractFile, PiInspection,
    │   │                PiInspectionItem, PiAlertLog, PiContactHistory, PiFormTemplate, PiAccessLog + Enum(ContractStatus, ContractFileType, FormTemplateType,
    │   │                InspectionType, InspectionResult, ChecklistSheetType, AlertSendStatus, AlertTriggerType, AlertType, AccessActionType)
    │   ├── repository/  Pi*Repository (8개, PiFormTemplateRepository·PiAccessLogRepository 포함)
    │   └── service/     PiContractService, PiContractFileService, PiInspectionService,
    │                    ChecklistExcelParser, PiChecklistTemplateService, PiContractExcelService,
    │                    PiAlertMailService, PiAlertScheduler, PiFileStorage, PiExcelSupport, PiRoles,
    │                    PiVendorChecklistService(업체별 업로드), PiVendorNames(업체명 정규화),
    │                    PiContractDocAnalyzer(계약서 계약일자 읽기), PiPdfFontWarmup(PDF 글꼴 캐시 미리 만들기),
    │                    PiYearScope(기준 연도 계약중/종료 판정 규칙),
    │                    PiAccessScope / PiAccessScopeResolver(비관리자 소속부서 계약 조회 범위),
    │                    PiExpiryFormService(계약만료시 점검 양식 3종 생성·메일 첨부),
    │                    PiFormTemplateService(양식 버전 관리·검사, 사용 중 양식 제공),
    │                    PiFollowUpRules(만료 서류·조치 지연·동의서 누락·파기 지연 규칙), PiAccessLogService(접근 기록)
    └── resources/
        ├── personalinfo/checklist-template.xlsx      보안팀 점검 체크리스트 원본 양식
        ├── personalinfo/security-pledge-template.docx      정보보호 및 비밀유지 확약서 양식 ({{수탁업체명}} 등 자리표시자)
        ├── personalinfo/destruction-confirm-template.docx  개인정보 파기 확인서 양식
        └── static/personalinfo/                      화면 (index.html, css, js — personalinfo-table.js: 목록 열 너비·순서 편집)
sql/module-personalinfo/  01_schema.sql, 02_seed_data.sql, 03_vendor_checklist_upload.sql, 04_contact_history.sql, 05_contact_history_contract_period.sql, 06_contract_file_signed_date.sql, 07_contract_name.sql, 08_form_template.sql, 09_security_followup.sql, 10_destruction_delay_reason.sql, 11_auto_renew_period.sql, 12_periodic_destruction.sql, 13_file_content_check.sql, README.md
```

---

## 10. 검증 내역 (저장소 밖 임시 환경에서 실행, 저장소에는 테스트 하네스를 넣지 않음)

| 검증 | 결과 |
|---|---|
| 컴파일 (Java 17 타깃, core compileOnly) | 통과 |
| Hibernate `ddl-auto=validate` (Entity ↔ 01_schema.sql 컬럼·타입 일치) | 통과 |
| 통합 테스트 28건: core + 모듈 기동, MariaDB, 실제 체크리스트 양식, 로컬 SMTP(GreenMail) | **28/28 통과** |
| 업로드 내용 확인(테스트 28): 서류 종류 불일치(파기 확인서를 확약서·계약서로) 400·맞는 구분 안내, 파기 완료일 불일치 400·문서 날짜 표시, 일치 문서 자동 통과(㈜/주식회사 차이 무시)·AUTO 기록, 같은 파일 중복 400·기존 파일 안내, 업체명 불일치 400, 확약서 서명일 일치, 이미지 파일 직접 확인 없이 400·체크 시 저장·MANUAL 기록, 서류 종류 모르는 문서 직접 확인 필요, 기타 구분 중복만 확인, ①~④는 직접 확인 체크로도 400, 막힌 업로드 미저장. 기존 테스트의 업로드는 직접 확인 체크로 처리. SQL 13 두 번 실행 오류 없음. 브라우저: 서류 종류·중복·업체명 오류창, 이미지 파일 직접 확인 체크 창, 막힌 업로드 미저장 | **통과** |
| 자동연장 현재 계약기간 표시: 목록 '계약기간' 열(현재 종료일 + 연장N 칩), 같은 업체 계약 표 '계약기간' 열, 상세 '현재 계약기간' 줄, 엑셀 '현재 계약 종료일' 열 (통합 테스트 25에서 엑셀 열 위치·값 검증, 브라우저 확인) | **통과** |
| 정기 파기확인(테스트 27): 등록 시 관리 시작일, 분기 미수취 판정(종료 + 20일)·목록 필터·요약 카드, 기간 지정 업로드 후 받음 처리·만료 서류와 분리, 관리 시작 전 지난 기간 업로드 허용, 주기 불일치·미래 기간·형식 오류·확약서 기간 지정·일반 계약 기간 지정 400, 현황 API(연도별 상태·업로드 선택지), 수정 시 시작일 유지/재체크 시 오늘, 엑셀 열, 월 주기 요청 메일 1건·독촉 메일 2개 기간·메일 끔 미발송·재실행 중복 없음. SQL 12 두 번 실행 오류 없음. 브라우저: 등록 폼 체크·주기·메일·안내 문구, 상세 정기 파기확인 줄·현황 표, 첨부 업로드 대상 기간 선택, 목록 칩·확인 필요 카드 | **통과** |
| 　- 자동연장 확인 메일: 원계약 종료 20일 전 자동연장 계약 → 확인 메일 1건(담당자+보안팀, 제목 종료일·'1년 연장', 본문 유지 확인·종료 처리 안내·연장 후 종료일) / 이미 연장돼 현재 종료일 355일 남은 계약·만료 알림 미사용 계약 미발송 / 만료 알림 메일 미발송 / 같은 주기 재실행 0건·이력 1건 / 다음 주기(현재 종료일 15일 전) 발송 / 알림 이력 종류 '자동연장 확인' / 종료 처리 후 미발송 | 통과 |
| 　- 자동연장: 기간 미입력 → 12개월, 원계약 종료 10일 지난 자동연장 계약 = 계약중·연장 1회·현재 종료일 +1년·만료까지(일) 현재 종료일 기준·만료 서류 대상 아님·파기 기한 없음 / 6개월 단위 → +6개월 / 자동연장 N → 만료·만료 서류 미수취 / 기간 0·121 → 400 / 목록 상태 필터 만료=N 계약만, 계약중=자동연장 계약 포함, 만료 서류 미수취 필터 제외, 기준 연도 '종료' 제외 / 만료 알림 메일 자동연장 계약 미발송·N 계약 발송 / 기간 2년으로 수정 → 현재 종료일 재계산 / 종료 처리 기본 종료일 = 연장된 종료일, 종료 후 만료 서류 대상 / SQL 11 두 번 실행 오류 없음 / 브라우저: 자동연장 Y 계약 계약중·'자동연장' 표시, 수정 화면 기간 1년, N 계약은 기간 칸 비활성 → Y 선택 시 활성 | 통과 |
| 　- 파일 내용 확인: 가짜 pdf·ZIP 조각 docx·xlsx 를 docx 로·GIF 를 jpg 로·글자 hwp·docx 를 xlsx 로 → 모두 400·저장 0건 / 실제 docx·hwp·hwpx·png 200 | 통과 |
| 　- 재위탁 동의서: 재위탁 Y·동의서 없음 → `consentMissing`·목록 필터·요약 카드 포함, 동의서 업로드 후 빠짐·카드 -1 | 통과 |
| 　- 파기 기한·회신 기한: 종료 60일 지남·확인서 없음 → 회신 기한(종료 + 30일) 지남, 파기 기한 종료 + 5일 / 확인서 업로드(지연 사유 함께 저장) 후 회신 지연 해소 / 종료 + 50일 파기 → 파기 지연·사유 표시, 일자만 수정 시 사유 유지, 종료 + 3일로 수정·사유 비움 → 해소, 기한 당일(종료 + 5일)은 지연 아님 / 확약서에 지연 사유 400(업로드·수정), 501자 400 / SQL 10 두 번 실행 오류 없음 / 브라우저: 팝업에 두 기한 표시, 기한보다 늦은 날짜 → 지연 경고·사유 칸, 저장 시 재확인 창(취소 시 팝업 유지), 기한 안 날짜 → 경고 닫힘 | 통과 |
| 　- 서류 일자·파기 지연: 파기 완료일 미래 400, 저장·라벨 '파기 완료일', 파기 지연 필터 포함, 일자 수정 시 해소, 미래·형식 오류 400, 조회자 403, 비우면 '일자 미입력', 확약서 라벨 '서명일', 기타 파일 일자 400 | 통과 |
| 　- 만료 서류: 서류 없이 종료 400(미수취 서류 이름 안내) → 사유 입력 후 종료·사유/독촉 저장, 받은 1종·미수취 2종, 요약 +1, 목록 필터·행 `expiryDocsMissing` / 3종 모두 받은 계약은 사유 없이 종료 / 독촉 메일: 미수취 양식 2종만 첨부·사유 표시, 완비 계약 미발송, 같은 주 재실행 0, 다음 주 재발송, 독촉 끄기(조회자 403·잘못된 값 400) 후 미발송, 알림 이력 종류 | 통과 |
| 　- 조치 지연: 조치 예정일 5일 지남 → 지연 1건·5일·필터·요약, 주간 메일 1회(같은 주 재실행 0), 조치 완료 후 빠짐·다음 주 미발송 | 통과 |
| 　- 정기 점검: 4/9 발송 0 / 4/10 담당자 메일(체크리스트 첨부, 5월 31일 기한, 종료 계약 제외) + 보안팀 요약 1회(`CONTRACT_ID` NULL) / 4/20 재실행 0 / 6/1 0 / 이후 등록 계약은 5/2 담당자만 발송·요약 재발송 없음 | 통과 |
| 　- 접근 기록: 상세 조회·파일 다운로드 기록(로그인 ID·업체), 엑셀 사유 없음·공백 400, 사유 입력 시 기록(사유·조건·행 수 1), 계약 담당자·조회자 403, 행위 6종, 접근 기록 엑셀 사유 필수·기록, 삭제 API 없음 | 통과 |
| 　- SQL 09: 테스트 DB·프리뷰 DB 각각 두 번 실행 오류 없음, Hibernate validate 통과 / 브라우저: 확인 필요 카드 4종·칩·카드 필터(만료 서류 4건), 엑셀 사유 팝업 → 다운로드 → 접근 기록 표시, 상세 처리 필요 항목 패널, 종료 팝업 미수취 서류·사유 필수, 계약 담당자 [접근 기록] 버튼 없음·화면 잠금, 콘솔 오류 0 | 통과 |
| 　- 양식 관리: 권한(보안팀 관리자·플랫폼 관리자 `canManageForms` true, 계약 담당자·조회자 false·API 403) / 처음 3종 모두 '기본' / 시스템이 못 읽는 체크리스트·확장자 다른 파일 검사·등록 400, 저장 0건 / 체크리스트 v1·v2 등록 → v2 사용 중·v1 해제·사용 중 1건, 사용 중 양식이 빈 양식·계약별 양식·만료 체크리스트에 반영(계약 정보 채움 유지), 점검 업로드 `formVersion`=v2 / v1 사용 지정(일반 사용자 403)·반영, 사용 중 삭제 400, v2 삭제 후 원본 404, v1 원본 바이트 동일, 기본 양식 복귀, 삭제 후에도 번호 v3, `activate=false` 면 사용 중 유지 / 확약서 docx: 자리표시자 자동 입력(`{{ 계약명 }}` 공백 허용), 모르는 자리표시자 경고·그대로 남김, 미리보기 샘플 값 / 파기 확인서: 가짜 pdf·xlsx 400, hwp 등록(자동 입력 없음 경고), pdf 등록 → 계약 다운로드가 원본 바이트 그대로·`.pdf` 파일명 / 만료 안내 메일: 첨부가 사용 중 양식(확약서 docx·파기 확인서 pdf), 발송 이력 `formVersions`='체크리스트 기본 / 확약서 v1 / 파기 확인서 v2' / 잘못된 양식 종류 400 / SQL 08 두 번 실행 오류 없음·Hibernate validate 통과 / 브라우저: 관리자만 [양식 관리] 버튼, 깨진 체크리스트 오류 표시·등록 버튼 비활성, 확약서 docx 검사(자동 입력 항목·모르는 자리표시자 경고)·등록·v1 사용 중, 미리보기 샘플 값, 계약 상세 확약서 다운로드에 새 양식 반영, 기본 양식 복귀, 계약 담당자는 버튼 없음·`#/forms` 안내, 콘솔 오류 0 | 통과 |
| 　- 계약만료시 점검: 확약서·파기 확인서 docx 에 업체명·계약명·위탁업무·재위탁 업체·계약종료일 채움, `{{` 자리표시자 0, 한글 파일명(UTF-8), 체크리스트 활성 시트 '만료', 빈 양식 200, 잘못된 종류 400, 외부 역할 403, 다른 부서 403 / 확약서만 받은 상태에서 만료 안내 메일: 첨부 3건(체크리스트 xlsx, 확약서 docx, 파기 확인서 docx), 본문 '계약 만료 시 협력사 제출 자료'·수취 완료·미수취 / 자동 알림 메일도 첨부 3건 / 브라우저: 섹션 위치(점검 이력과 알림 이력 사이), 양식 3종 다운로드 파일명, 확약서 업로드 후 수취 완료·수취 1/3, 만료 점검 업로드 팝업 구분 '계약변경·만료 점검', 콘솔 오류 0 | 통과 |
| 　- 권한: 조회자 등록 403 / 외부 역할 403 / 미로그인 401 / 일반 사용자 삭제 403 / 관리자 삭제 200 | 통과 |
| 　- 계약서 업로드·다운로드, 금지 확장자 거부, `../` 경로 조작 파일명 무력화 | 통과 |
| 　- 체크리스트: 33항목 집계, 병합셀 구분, 중요(●), N 항목 조치계획, "미 보유" 제외, 3번 시트 6항목, 엑셀 아닌 파일 400 | 통과 |
| 　- 상태 필터(만료임박/계약중/기간미정), 기간미정 계약 정렬 시 맨 뒤, 대시보드, 엑셀 다운로드 | 통과 |
| 　- 만료 알림: D-7 발송(담당자+보안팀), 같은 날 재실행 시 중복 0, 4일 뒤 D-1 발송, 수동 발송, 이력 기록 | 통과 |
| 　- 소프트 삭제: DELETED_YN/BY 기록, 항목 33건 함께 소프트 삭제, 최근 점검 다시 계산 | 통과 |
| 　- 계약서 계약일자 읽기: docx(계약기간보다 '계약체결일' 우선), pdf(한글 글꼴·서명란 작년 날짜도 그대로 계약일자로 반환, 올해 비교 없음), hwpx('작성일'), hwp(한글 바이너리), 윗줄 계약기간 조항 아래 서명 날짜, 표 형태(윗줄 '체결일'), 체결일 표기 없으면 계약기간 시작일('~'·'부터' 모두), 이미지·깨진 pdf → 읽기 불가 안내, 2월 30일 같은 잘못된 날짜 제외, 조회자 403, 금지 확장자 400, 분석 후 첨부 건수 변화 없음 | 통과 |
| 　- 위수탁 계약서 여러 건: 2024(파일에서 읽음)→2026(계약기간 시작일)→나중에 올린 2025(직접 입력) 순 업로드 시 계약 체결일 2024-01-05→2026-01-01→유지, 최신 배지 1건, 기타 파일은 체결일 저장 안 함·수정 400, 2025 계약서를 2026-06-30 으로 고치면 계약 체결일 갱신, 최신 계약서 삭제 시 그다음 최신으로 복귀, 조회자 403, 잘못된 날짜 400, 없는 파일 404, DB SIGNED_DATE 확인 | 통과 |
| 　- SQL 06: 기존 DB 두 번 실행 오류 없음, 01 로 새로 만든 DB 에서도 통과, Hibernate validate 통과 | 통과 |
| 　- 기준 연도 요약: 종료일 미입력 시 원계약 종료일(2019-12-31) 기본값, 중도 종료일(2019-08-01) 저장, 시작일 이전 종료일 400 / 2019(기준일 12/31): 전체 4·계약중 3(12/31 종료 계약 포함, 점검완료 1)·종료 1(종결체크 미업로드) / 2020: 12/31 종료 계약이 종료로 잡힘, 전체 4·계약중 2·종료 2(업로드 1) / 2021·2022 경계 확인 / 올해 기준일=오늘 / 카드 조건별 목록 일치, terminationCheckYn, 기준 연도 없으면 기존 결과, 잘못된 연도 400 | 통과 |
| 　- 요약 카드 순서·기본 선택(브라우저): 카드 순서 기준 연도 / 계약중 / 전체 계약 / 종료, 처음 들어오면 계약중 선택·목록 8건(계약중·만료임박·기간미정만), 전체 클릭 11건, 종료 클릭 3건, 초기화 시 계약중 8건, 상세 다녀온 뒤 마지막 선택 유지, 새로 들어오면 계약중 | 통과 |
| 　- 필수 항목: 계약명 없음·공백만·원계약 시작일 없음·종료일 없음 각각 400 + 안내 문구, 기간 없는 등록 400, 필수화 이전 데이터(기간 없음)는 목록·정렬·기간미정 필터 그대로 / 브라우저: 별표 5개(수탁업체명·계약명·위탁업무·계약 시작일·종료일), 빈 칸 순서대로 저장 차단, 종료일<시작일 안내, 기존 계약 수정 시 빈 계약명에서 차단 | 통과 |
| 　- 부서별 조회 범위: 인사팀 담당자는 인사팀·'HR'(부서 코드) 계약 2건만, 온라인사업팀 담당자는 '온라인 사업팀'(공백 차이) 1건만, 관리자 3건 / 담당부서 검색값으로 범위 우회 불가 / 요약·엑셀·같은 업체 계약·업체명 조회·업체 목록도 범위 적용, `sameVendorContractCount` 도 보이는 계약만 / 다른 부서 계약 상세·첨부·점검·이력·같은 업체·양식·수정·Y/N·메일 403 / 등록 시 담당부서 비우면 소속부서 저장, 다른 부서 400, 수정으로 다른 부서 이동 400, '인사 팀' 허용 / 부서 미지정 비관리자: 목록·요약 0건, 상세 403, 등록 403 / 기존 담당자 이력 테스트: 다른 부서로 변경은 계약 담당자 400·관리자 200 / 브라우저: 인사팀 담당자 안내 배너·목록 2건·담당부서 칸 잠금, 다른 부서 계약 URL 직접 접근 시 사유 표시, 부서 미지정 조회 사용자 안내·0건·등록 버튼 없음, 관리자 11건 전체 | 통과 |
| 　- 한 업체 여러 계약: ㈜테스트상사 / 테스트상사(주) / 주식회사 테스트 상사 3건(본사·부산지사·대전지사) 계약명 저장·수정, 계약명 키워드 검색, 목록 `sameVendorContractCount` 3(다른 업체 1), `/same-vendor` 3건 시작일순·다른 업체 제외, `/vendor-contracts?vendorName=테스트 상사` 3건·없는 업체 0건, 외부 역할 403, 없는 계약 404, 엑셀 4번째 열 '계약명'·값 확인 | 통과 |
| 　- SQL 07: 기존 DB 두 번 실행 오류 없음, Hibernate validate 통과 / 브라우저: 상세 → [이 업체로 계약 추가] → 업체명 미리 채움·기존 계약 안내 → 부산지사 계약 저장 → 같은 업체 계약 3건, 목록 배지 클릭 팝업·[이 업체로 계약 추가], 콘솔 오류 0 | 통과 |
| 　- 업체별 업로드: 표기 다른 업체명 3건 → 1개 업체, 미리보기 자동 매칭·저장 없음, 조회자 403, 타 업체 계약 섞이면 400·저장 0, 미종료 2건 등록(묶음ID 동일·파일 1개·항목 66건), 계약별 최근 점검 반영, 종료 계약 명시 등록, xlsx 아님 400, 없는 업체 404 | 통과 |
| 　- 목록 Y/N 변경: ISMS만/재위탁만/둘 다 변경, 다른 컬럼·재위탁 업체명 보존, UPDATED_BY 기록, 잘못된 값·빈 요청 400, 조회자 403, 없는 계약 404 | 통과 |
| 　- 담당자 이력: 등록 시 1건, 담당자 외 항목 수정·**수탁사 담당자/연락처/이메일만 변경**·이메일 대소문자만 변경 시 추가 없음, 위탁사 담당자 변경 시 바뀐 항목·변경자·**그 시점 계약기간** 기록(등록 이력은 등록 시점 기간 유지), 이전 버전의 수탁사 전용 변경 행 조회 제외, 같은 업체 다른 계약 포함·다른 업체 제외, 이 계약만 조회, 외부 역할 403, 없는 계약 404 | 통과 |
| 　- 목록 삭제(브라우저): 관리자·보안팀 관리자만 관리 열/버튼 표시, 일반·조회 사용자 미표시, 일반 사용자 API 직접 호출 403, 삭제 후 목록·요약 갱신·DELETED_YN/BY 기록, 버튼 클릭 시 상세 이동 안 됨 | 통과 |
| 　- 목록 열 너비·순서 편집(브라우저): 계약 목록 계약명 열을 수탁업체명 앞으로 끌기 → 제목·모든 행 칸 함께 이동, 경계 끌기 240→136px, [열 설정] ◀ 이동·너비 260 입력, 새로고침·검색 후 순서·너비 유지, Alt+Shift+→ 이동, 관리 열 맨 끝 고정, 초기화 시 기본 순서, 행 클릭 상세 이동 유지 / 같은 업체 계약 상태 열 맨 앞 이동 후 다른 계약·업체 계약 팝업에서도 유지, 팝업 안 [열 설정]·초기화 / 담당자 이력 너비 74→154 / 첨부·점검 버튼 열 고정 / 업체별 체크리스트 팝업 체크박스 열 고정·전체 선택 정상 / 점검 항목 팝업 평가 열 이동, 시트 구분 행 유지 / 콘솔 오류 0 | 통과 |
| 　- 목록 열 너비 조정(브라우저, 이전 버전): 드래그 확대/축소, 최소 너비 보호, 새로고침 후 유지, 더블클릭 단일 열 기본값, 키보드 조정, 전체 초기화, 드래그 중 상세 이동 안 됨·행 클릭 상세 이동 유지 | 통과 |
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
