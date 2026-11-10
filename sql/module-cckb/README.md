# module-cckb SQL

CCKB 후면 색상검사(CCKB) 모듈의 DB 스크립트. 플랫폼과 **같은 DB** 를 쓴다(`platform_db`).
`ddl-auto=none` 이므로 아래 순서로 직접 실행한다.

## 실행 순서

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | CCKB 테이블 5개 (구조만). ddl-auto=none 이라 반드시 먼저 실행 |
| 2 | `02_menu.sql` | 플랫폼 메뉴 등록 (CCKB_MGMT **독립 최상위 그룹** + 페이지 1개) + 역할별 노출 권한. `MENU_CODE` UNIQUE 기준 upsert 라 `MENU_ID` 가 바뀌지 않는다 |
| 3 | `03_perm_code.sql` | 공통코드 그룹 `CCKB_PERM` 생성 + 판정코드/기본 기준값 시드. 관리자 명단은 자동으로 채우지 않고 공통코드 관리 화면에서 직접 추가한다 |

```bash
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 01_schema.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 02_menu.sql
mysql -u platform_user --default-character-set=utf8mb4 platform_db < 03_perm_code.sql
```

> ⚠️ **반드시 `--default-character-set=utf8mb4` 옵션을 붙여서 실행할 것.**
> mysql 클라이언트의 기본 접속 캐릭터셋은 `latin1` 이라, 이 옵션 없이 실행하면
> UTF-8로 저장된 한글 리터럴이 latin1로 잘못 해석되어 DB에 깨진 상태(mojibake)로 저장된다.

## 메뉴 배치 원칙 (사용자 명시 지시)

"메뉴 이름은 독립 그룹으로 넣어야 함" — CCKB 는 Fire/Safety 등 기존 그룹 하위가 아니라
**새로운 최상위 그룹(CCKB_MGMT, PARENT_ID=NULL)** 으로 등록한다. `02_menu.sql` 참고.

## 이미지 저장/서빙 방식 (사용자 명시 지시)

"세부공장일보(dailyreport)에 이미지 넣는 거랑 같은 방식으로" — module-dailyreport 의
`ImageController`/`DailyReportImage` 패턴을 그대로 따른다:
- 실제 파일은 로컬 디스크(`uploads/module_cckb/images/`)에 UUID 파일명으로 저장
- DB(`image.FILE_URL`)에는 물리 경로가 아니라 다운로드 API 경로
  (`/cckb-api/images/files/{uuid}.jpg`)를 저장
- `/cckb-api/**` 는 SecurityConfig 상 인증이 필요하므로(permitAll 예외 없음),
  이미지 다운로드도 Authorization(Bearer) 인증을 그대로 요구한다.
- 따라서 프론트엔드는 `<img src="...">` 가 아니라 **인증된 fetch() → blob() →
  URL.createObjectURL()** 로 이미지를 표시해야 한다 (`static/cckb/js/cckb.js` 의
  `CCKB.loadImageBlob()` 참고).

## 스키마 보정 사항 (엑셀 원안 대비)

- `image` 테이블: 엑셀 원안(`CCKB_후면색상검사_AI플랫폼_데이터테이블설계_수정본_V2.xlsx`)에는
  `DELETED_YN` 컬럼이 누락되어 있었다 (다른 4개 테이블은 모두 보유). 다른 테이블과의
  소프트 삭제 표준 일관성을 위해 `DELETED_YN` 컬럼을 추가했다. **사용자 확인 필요** — 운영 DB에
  이미 다른 스키마로 적용된 상태라면 `ALTER TABLE image ADD COLUMN DELETED_YN ...` 로 보정해야 한다.
- `image.FILE_URL`: 엑셀 원안 설명은 "R2/오브젝트 스토리지 경로"였으나, 이미지 저장 방식을
  dailyreport 방식(로컬 디스크 + 인증 API 경로)으로 채택하면서 그 의미가 "다운로드 API 경로"로
  바뀌었다. 추후 R2/오브젝트 스토리지로 전환 시 이 컬럼 의미를 다시 "실제 스토리지 경로"로
  되돌리고 `CckbFileStorageService`/`ImageController` 를 R2 SDK 호출로 교체하면 된다.

## iframe 임베딩 방식 (사용자 명시 지시 — Method B)

CCKB 는 **동적 prefix 패턴**(`cckb::`, module-safety/module-kims 와 동일 방식)을 사용한다.
고정 `menuCodeToPage` 맵 패턴(Fire/PS-Insp/DailyReport 식)은 사용하지 않는다.

이 패턴이 동작하려면 플랫폼 SPA 쉘(`app/src/main/resources/static/index.html`)에
아래 변경이 필요하다. **이 파일은 module-cckb 외부(app 모듈 공용 파일)이므로, "모듈 격리
원칙"에 대한 사용자 최종 확인(현재 보류 중) 전까지는 적용하지 않는다.**

### 적용 대기 중인 diff (4곳)

1. **전역 변수 선언** (기존 `kimsMenuNames`/`safetyMenuNames` 선언부 근처):
   ```javascript
   let cckbMenuNames = {};
   ```

2. **메뉴 트리 순회로 cckbMenuNames 채우기** (기존 `walkKims`/`walkSafety` 함수 근처):
   ```javascript
   function walkCckb(menus) {
       (menus || []).forEach(function (menu) {
           if (menu.menuUrl && String(menu.menuUrl).indexOf('/cckb/') === 0) {
               cckbMenuNames[menu.menuUrl] = menu.menuName;
           }
           if (menu.children && menu.children.length) walkCckb(menu.children);
       });
   }
   // userMenus 로딩 완료 후 walkKims(userMenus); walkSafety(userMenus); 와 나란히 walkCckb(userMenus); 호출 추가
   ```

3. **`renderMenuNode()` 함수 — page 변수 계산부**:
   ```javascript
   const isKimsPage = menu.menuUrl && String(menu.menuUrl).indexOf('/kims/') === 0;
   const isSafetyPage = menu.menuUrl && String(menu.menuUrl).indexOf('/safety/') === 0;
   const isCckbPage = menu.menuUrl && String(menu.menuUrl).indexOf('/cckb/') === 0;  // ← 추가
   const page = isKimsPage ? ('kims::' + menu.menuUrl)
       : (isSafetyPage ? ('safety::' + menu.menuUrl)
       : (isCckbPage ? ('cckb::' + menu.menuUrl)                                     // ← 추가
       : pageForMenuCode(menu.menuCode)));
   ```

4. **`navigate()` 함수 — kims/safety 분기 바로 뒤에 cckb 분기 추가**:
   ```javascript
   if (typeof page === 'string' && page.indexOf('cckb::') === 0) {
       var cckbUrl = page.slice(6);
       var cckbName = (cckbMenuNames && cckbMenuNames[cckbUrl]) || 'CCKB 후면 색상검사';
       document.getElementById('pageTitle').textContent = cckbName;
       document.getElementById('breadcrumb').textContent = '모듈 > CCKB 후면 색상검사 > ' + cckbName;
       document.getElementById('topbarActions').innerHTML = '';
       navigateToCckbPage(cckbUrl);
       return;
   }
   ```

5. **`navigateToKimsPage`/`navigateToSafetyPage` 함수 바로 뒤에 신규 함수 추가**:
   ```javascript
   function navigateToCckbPage(path) {
       var el = document.getElementById('pageContent');
       el.className = 'page fire-iframe-mode';
       el.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text3)"><span>불러오는 중...</span></div>';
       var iframe = document.createElement('iframe');
       iframe.className = 'fire-iframe';
       iframe.id = 'cckbIframe';
       iframe.onload = function() { iframe.classList.add('loaded'); };
       iframe.src = path;
       el.innerHTML = '';
       el.appendChild(iframe);
   }
   ```

6. **`isValidPage()`/`validPages` 세션 복원 검증 로직**에 prefix 체크 추가:
   ```javascript
   // 기존: p.indexOf('kims::') === 0 || p.indexOf('safety::') === 0
   // 추가:
   || p.indexOf('cckb::') === 0
   ```

### 플랫폼 공용 Gradle 파일에 필요한 추가 (settings.gradle / app/build.gradle)

```gradle
// settings.gradle
include 'module-cckb'

// app/build.gradle (dependencies 블록)
implementation project(':module-cckb')
```

위 3개 파일(`settings.gradle`, `app/build.gradle`, `app/.../index.html`)은 모두
**module-cckb 외부**이며, 사용자가 "5번 모듈 격리 원칙은 보류" 라고 명시했으므로
Genspark 가 직접 수정하지 않는다. 사용자가 최종 승인하면:
- (a) Genspark 가 직접 반영하거나,
- (b) 사람 운영자가 이 문서를 보고 수동 반영

할 수 있다. module-cckb 자체의 컴파일/실행과는 무관하며, 이 3개 파일 반영 전까지는
`module-cckb` 가 `app` 에 포함되지 않아 전체 빌드에 묶이지 않는다(격리 유지).
