# 플랫폼 SPA 연결 코드 (운영자 작업용)

스팀 모듈 화면(`/steam/*.html`)을 플랫폼 SPA 좌측 메뉴에서 iframe 으로 열기 위한 코드다.
업무 모듈은 `app/` 을 수정하지 않으므로, 아래 5곳은 **플랫폼 담당자가** `app/src/main/resources/static/index.html`
에 넣는다. KIMS(`kims::`)·안전(`safety::`) 모듈이 이미 쓰고 있는 방식과 같고, 그 코드 옆에 나란히 두면 된다.

## 1. 메뉴 이름 보관용 변수

`let safetyMenuNames = {};` 같은 선언 옆에 추가한다.

```js
let steamMenuNames = {};  // /steam/{page}.html -> 메뉴명 (스팀에너지관리 하위 메뉴 제목/브레드크럼용)
```

## 2. 유효한 페이지 키로 인정

```js
var isValidPage = function(p){ return validPages.includes(p) || (typeof p === 'string' && (p.indexOf('kims::') === 0 || p.indexOf('safety::') === 0 || p.indexOf('steam::') === 0)); };
```

## 3. 메뉴 트리에서 이름 맵 만들기

메뉴를 받아온 뒤(다른 모듈의 `walkKims` / `walkSafety` 옆)에 넣는다.

```js
steamMenuNames = {};
(function walkSteam(nodes){ (nodes||[]).forEach(function(m){ if(m.menuUrl && String(m.menuUrl).indexOf('/steam/')===0){ steamMenuNames[m.menuUrl]=m.menuName; } if(m.children&&m.children.length) walkSteam(m.children); }); })(userMenus);
```

## 4. 메뉴 → 페이지 키

`renderMenuNode` 안의 `const page = ...` 를 아래처럼 한 줄 늘린다.

```js
const isSteamPage = menu.menuUrl && String(menu.menuUrl).indexOf('/steam/') === 0;
const page = isKimsPage ? ('kims::' + menu.menuUrl)
           : isSafetyPage ? ('safety::' + menu.menuUrl)
           : isSteamPage ? ('steam::' + menu.menuUrl)
           : pageForMenuCode(menu.menuCode);
```

## 5. 라우팅 + iframe 로더

`kims::` 처리 앞에 라우팅을 넣고, `navigateToKimsPage` 옆에 로더를 둔다.

```js
if (typeof page === 'string' && page.indexOf('steam::') === 0) {
  var steamUrl = page.slice(7);
  var steamName = (steamMenuNames && steamMenuNames[steamUrl]) || '스팀에너지관리';
  document.getElementById('pageTitle').textContent = steamName;
  document.getElementById('breadcrumb').textContent = '모듈 > 스팀에너지관리 > ' + steamName;
  document.getElementById('topbarActions').innerHTML = '';
  navigateToSteamPage(steamUrl);
  return;
}
```

```js
// ══ 스팀에너지관리(module-steam-energy) 모듈 ══
function navigateToSteamPage(path) {
  // 인증은 fireweb_user(localStorage)로 전달된다 — 스팀 쪽 platform-iframe-compat.js 가
  // 저장된 토큰을 읽어 /steam/api/** 호출에 Authorization 헤더로 붙인다.
  var el = document.getElementById('pageContent');
  el.className = 'page fire-iframe-mode';
  el.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text3)"><span>불러오는 중...</span></div>';
  var iframe = document.createElement('iframe');
  iframe.className = 'fire-iframe';
  iframe.id = 'steamIframe';
  iframe.onload = function() { iframe.classList.add('loaded'); };
  iframe.src = path;
  el.innerHTML = '';
  el.appendChild(iframe);
}
```

---

메뉴 자체는 `07_menu_data.sql`(상위 그룹)과 `08_submenus.sql`(하위 44개)로 DB 에 등록된다.
위 코드가 없으면 메뉴는 보이지만 클릭해도 화면이 열리지 않는다.
