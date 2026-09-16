(function () {
  var inSteamScope = window.location.pathname === "/steam" || window.location.pathname.indexOf("/steam/") === 0;
  if (!inSteamScope) return;

  // ── 플랫폼 JWT ────────────────────────────────────────────────
  // 토큰은 매 요청마다 새로 읽는다. SPA 는 access token 이 만료되면(기본 1시간) 새 토큰을
  // 받아 sessionStorage("auth") 를 먼저 갱신하는데, iframe 이 처음 한 번 읽어 캐시해 두면
  // 혼자 옛 토큰을 들고 있다가 401 을 맞는다. 읽는 순서는 소방 모듈(js/csrf.js)과 맞췄다.
  var urlToken = (function () {
    try {
      return new URLSearchParams(window.location.search).get("_t") || "";
    } catch (error) {
      return "";
    }
  })();

  function normalizeJwt(value) {
    var token = String(value || "").trim();
    if (token.toLowerCase().indexOf("bearer ") === 0) token = token.substring(7).trim();
    // 사용자명 등 JWT 가 아닌 값을 Authorization 으로 보내지 않는다.
    return token.split(".").length === 3 ? token : "";
  }

  function readStore(storage, key) {
    try {
      var raw = storage.getItem(key);
      return raw ? JSON.parse(raw) : null;
    } catch (error) {
      return null;
    }
  }

  function accessTokenOf(saved) {
    if (!saved || typeof saved !== "object") return "";
    return normalizeJwt(saved.token) || normalizeJwt(saved.accessToken);
  }

  function refreshTokenOf(saved) {
    if (!saved || typeof saved !== "object") return "";
    return String(saved.refreshTk || saved.refreshToken || "").trim();
  }

  function currentToken() {
    return accessTokenOf(readStore(sessionStorage, "auth"))
        || accessTokenOf(readStore(localStorage, "fireweb_user"))
        || normalizeJwt(urlToken);
  }

  function currentRefreshToken() {
    return refreshTokenOf(readStore(sessionStorage, "auth"))
        || refreshTokenOf(readStore(localStorage, "fireweb_user"));
  }

  // 갱신한 토큰은 SPA 가 쓰는 자리에 그대로 돌려놓는다 — 다른 화면도 같이 쓴다.
  function saveTokens(accessToken, refreshToken) {
    try {
      var auth = readStore(sessionStorage, "auth") || {};
      auth.token = accessToken;
      if (refreshToken) auth.refreshTk = refreshToken;
      sessionStorage.setItem("auth", JSON.stringify(auth));
    } catch (error) { /* 저장 실패는 다음 요청에서 다시 갱신하면 된다 */ }
    try {
      var saved = readStore(localStorage, "fireweb_user");
      if (saved && typeof saved === "object") {
        saved.token = accessToken;
        if (refreshToken) saved.refreshToken = refreshToken;
        localStorage.setItem("fireweb_user", JSON.stringify(saved));
      }
    } catch (error) { /* 위와 같음 */ }
  }

  var nativeFetch = window.fetch.bind(window);

  // access token 이 만료되면 refresh token 으로 한 번 되살린다.
  // 동시에 여러 요청이 401 을 맞아도 갱신 요청은 하나만 나가게 묶는다.
  var refreshing = null;
  function refreshAccessToken() {
    if (refreshing) return refreshing;
    var refreshToken = currentRefreshToken();
    if (!refreshToken) return Promise.resolve(false);
    refreshing = nativeFetch("/api/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-Refresh-Token": refreshToken }
    }).then(function (res) {
      if (!res.ok) return false;
      return res.json().then(function (json) {
        var data = json && json.data;
        var accessToken = normalizeJwt(data && data.accessToken);
        if (!accessToken) return false;
        saveTokens(accessToken, (data && data.refreshToken) || refreshToken);
        return true;
      });
    }).catch(function () {
      return false;
    }).then(function (ok) {
      refreshing = null;
      return ok;
    });
    return refreshing;
  }

  // refresh 까지 실패 = 로그인 세션이 끝난 것. iframe 안이면 소방 모듈과 같은 신호를 보내
  // SPA 가 안내 문구와 로그아웃을 맡고, 단독으로 열려 있으면 로그인 화면으로 보낸다.
  var expiredNotified = false;
  function sessionExpired() {
    if (expiredNotified) return;
    expiredNotified = true;
    var embedded = true;
    try { embedded = window.self !== window.top; } catch (error) { embedded = true; }
    if (embedded) {
      try {
        window.parent.postMessage({ type: "FIRE_AUTH_EXPIRED" }, "*");
        return;
      } catch (error) { /* 부모에 못 닿으면 아래로 */ }
    }
    try { (window.top || window).location.href = "/"; } catch (error) { window.location.href = "/"; }
  }

  // 화면 코드가 쓰는 /tables 경로를 모듈 API 경로(/steam/api/tables)로 바꾼다.
  function resolveTarget(input) {
    if (typeof input === "string") {
      if (input.indexOf("/steam/api/") === 0) return { target: input, api: true };
      if (input.indexOf("/steam/tables") === 0) return { target: "/steam/api" + input.substring("/steam".length), api: true };
      if (input.indexOf("/tables") === 0) return { target: "/steam/api" + input, api: true };
      if (input.indexOf("tables/") === 0) return { target: "/steam/api/" + input, api: true };
      return { target: input, api: false };
    }
    if (input instanceof Request && input.url) {
      var url = new URL(input.url, window.location.origin);
      if (url.origin !== window.location.origin) return { target: input, api: false };
      if (url.pathname.indexOf("/steam/api/") === 0) return { target: input, api: true };
      if (url.pathname.indexOf("/steam/tables") === 0) {
        return { target: new Request("/steam/api" + url.pathname.substring("/steam".length) + url.search, input), api: true };
      }
      if (url.pathname.indexOf("/tables") === 0) {
        return { target: new Request("/steam/api" + url.pathname + url.search, input), api: true };
      }
      return { target: input, api: false };
    }
    return { target: input, api: false };
  }

  function withAuth(target, init) {
    var token = currentToken();
    if (!token) return { input: target, init: init };
    if (target instanceof Request) {
      var headers = new Headers(target.headers);
      headers.set("Authorization", "Bearer " + token);
      return { input: new Request(target, { headers: headers }), init: init };
    }
    var next = init ? Object.assign({}, init) : {};
    var merged = new Headers(next.headers || {});
    merged.set("Authorization", "Bearer " + token);
    next.headers = merged;
    return { input: target, init: next };
  }

  window.fetch = function (input, init) {
    var resolved = resolveTarget(input);
    if (!resolved.api) return nativeFetch(resolved.target, init);

    // 재시도용 사본을 먼저 떠 둔다 (Request 는 본문을 한 번만 읽을 수 있다).
    var retrySource = resolved.target instanceof Request ? resolved.target.clone() : resolved.target;
    var first = withAuth(resolved.target, init);
    return nativeFetch(first.input, first.init).then(function (res) {
      // 403 은 권한이 없는 것이라 로그인 세션 문제가 아니다 — 그대로 돌려준다.
      if (res.status !== 401) return res;
      return refreshAccessToken().then(function (ok) {
        if (!ok) {
          sessionExpired();
          return res;
        }
        var again = withAuth(retrySource, init);
        return nativeFetch(again.input, again.init).then(function (retried) {
          if (retried.status === 401) sessionExpired();
          return retried;
        });
      });
    });
  };

  function rewriteLocalLink(value) {
    if (!value) return value;
    if (value.indexOf("/css/") === 0) return "/steam" + value;
    if (value.indexOf("/js/") === 0) return "/steam" + value;
    if (value.indexOf("/") === 0 && value.endsWith(".html")) return "/steam" + value;
    return value;
  }

  function rewriteAttributes(root) {
    (root || document).querySelectorAll("[href], [src]").forEach(function (node) {
      if (node.hasAttribute("href")) node.setAttribute("href", rewriteLocalLink(node.getAttribute("href")));
      if (node.hasAttribute("src")) node.setAttribute("src", rewriteLocalLink(node.getAttribute("src")));
    });
  }

  if (window.self !== window.top) {
    var style = document.createElement("style");
    style.textContent = [
      "body{background:transparent!important}",
      "body.platform-embedded{overflow:auto!important}",
      "body.platform-embedded .sidebar-shell,body.platform-embedded .landing-page-copy{display:none!important}",
      "body.platform-embedded .landing-shell,body.platform-embedded .detailframe-shell{display:block!important;min-height:100vh!important}",
      "body.platform-embedded .landing-main,body.platform-embedded .detailframe-main{width:100%!important;max-width:none!important;min-width:0!important;margin:0!important;padding:0!important}",
      "body.platform-embedded .landing-canvas,body.platform-embedded .detailframe-canvas{margin:0!important;box-shadow:none!important;border-radius:0!important}",
      "body.platform-embedded .topbar,body.platform-embedded .app-header,body.platform-embedded .page-help,body.platform-embedded .helper-card{display:none!important}"
    ].join("");
    document.head.appendChild(style);
    document.documentElement.classList.add("platform-embedded");
    if (document.body) document.body.classList.add("platform-embedded");
    document.addEventListener("DOMContentLoaded", function () {
      document.body.classList.add("platform-embedded");
      document.querySelectorAll(".sidebar-shell,.landing-page-copy").forEach(function (node) {
        node.remove();
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { rewriteAttributes(document); });
  } else {
    rewriteAttributes(document);
  }
})();
