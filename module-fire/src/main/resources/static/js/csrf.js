(function () {
  function readCookie(name) {
    const prefix = name + "=";
    const parts = document.cookie ? document.cookie.split(";") : [];
    for (let i = 0; i < parts.length; i += 1) {
      const cookie = parts[i].trim();
      if (cookie.startsWith(prefix)) {
        return decodeURIComponent(cookie.substring(prefix.length));
      }
    }
    return "";
  }

  function normalizeJwtToken(value) {
    var token = String(value || "").trim();
    if (!token) return "";
    if (token.toLowerCase().startsWith("bearer ")) {
      token = token.substring(7).trim();
    }
    // 사용자명만 들어있는 fw_user 등을 Authorization 헤더로 보내지 않도록 JWT 형태만 허용한다.
    return token.split(".").length === 3 ? token : "";
  }

  function readStorageObject(storage, key) {
    try {
      var raw = storage.getItem(key);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch (_) {
      return null;
    }
  }

  function tokenFromObject(obj) {
    if (!obj) return "";
    if (typeof obj === "string") return normalizeJwtToken(obj);
    return normalizeJwtToken(obj.token)
        || normalizeJwtToken(obj.accessToken)
        || normalizeJwtToken(obj.jwt)
        || normalizeJwtToken(obj.jwtToken)
        || normalizeJwtToken(obj.access_token)
        || normalizeJwtToken(obj.data && obj.data.accessToken)
        || normalizeJwtToken(obj.auth && obj.auth.token);
  }

  /** sessionStorage/localStorage에서 JWT 토큰을 읽는다 (AI Platform SPA 연동) */
  function getJwtToken() {
    // SPA의 refreshToken()은 sessionStorage auth를 먼저 갱신하므로 최신 토큰을 우선 사용한다.
    return tokenFromObject(readStorageObject(sessionStorage, "auth"))
        || tokenFromObject(readStorageObject(localStorage, "fireweb_user"))
        || tokenFromObject(readStorageObject(localStorage, "fw_user"))
        || normalizeJwtToken(localStorage.getItem("fireweb_token"))
        || normalizeJwtToken(localStorage.getItem("fw_token"))
        || normalizeJwtToken(localStorage.getItem("accessToken"))
        || normalizeJwtToken(localStorage.getItem("token"));
  }

  function refreshTokenFromObject(obj) {
    if (!obj || typeof obj === "string") return "";
    return String(obj.refreshTk || obj.refreshToken || obj.refresh_token || (obj.data && obj.data.refreshToken) || "").trim();
  }

  function getRefreshToken() {
    return refreshTokenFromObject(readStorageObject(sessionStorage, "auth"))
        || refreshTokenFromObject(readStorageObject(localStorage, "fireweb_user"))
        || refreshTokenFromObject(readStorageObject(localStorage, "fw_user"))
        || String(localStorage.getItem("fireweb_refresh_token") || localStorage.getItem("fw_refresh_token") || "").trim();
  }

  function updateStoredJwt(accessToken, refreshToken) {
    try {
      var auth = readStorageObject(sessionStorage, "auth") || {};
      auth.token = accessToken;
      if (refreshToken) auth.refreshTk = refreshToken;
      sessionStorage.setItem("auth", JSON.stringify(auth));
    } catch (_) {}
    ["fireweb_user", "fw_user"].forEach(function (key) {
      try {
        var obj = readStorageObject(localStorage, key);
        if (!obj || typeof obj === "string") return;
        obj.token = accessToken;
        if (refreshToken) obj.refreshToken = refreshToken;
        localStorage.setItem(key, JSON.stringify(obj));
      } catch (_) {}
    });
    try { localStorage.setItem("fireweb_token", accessToken); } catch (_) {}
  }

  async function refreshJwtToken() {
    var refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      var res = await fetch("/api/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-Refresh-Token": refreshToken }
      });
      if (!res.ok) return false;
      var json = await res.json().catch(function () { return null; });
      var accessToken = normalizeJwtToken(json && json.data && json.data.accessToken);
      if (!accessToken) return false;
      updateStoredJwt(accessToken, (json.data && json.data.refreshToken) || refreshToken);
      return true;
    } catch (_) {
      return false;
    }
  }

  function isMutation(method) {
    const normalized = String(method || "GET").toUpperCase();
    return normalized === "POST" || normalized === "PUT" || normalized === "PATCH" || normalized === "DELETE";
  }

  function headers(extra, method) {
    const merged = { ...(extra || {}) };
    // JWT Authorization 헤더 (AI Platform Stateless 인증)
    var jwt = getJwtToken();
    if (jwt) {
      merged["Authorization"] = "Bearer " + jwt;
    }
    // CSRF 헤더 (변이 요청에만)
    if (isMutation(method)) {
      const token = readCookie("XSRF-TOKEN");
      if (token) {
        merged["X-XSRF-TOKEN"] = token;
      }
    }
    return merged;
  }

  async function ensureToken() {
    if (readCookie("XSRF-TOKEN")) {
      return readCookie("XSRF-TOKEN");
    }
    try {
      var opts = {};
      var jwt = getJwtToken();
      if (jwt) opts.headers = { "Authorization": "Bearer " + jwt };
      await fetch("/api/auth/csrf", { method: "GET", credentials: "same-origin", ...opts });
    } catch (_) {}
    return readCookie("XSRF-TOKEN");
  }

  function applyOptions(options) {
    const opts = { ...(options || {}) };
    opts.headers = headers(opts.headers, opts.method);
    return opts;
  }

  /** iframe 내부인지 판별 */
  function isInIframe() {
    try { return window.self !== window.top; } catch(_) { return true; }
  }

  /** 로그인 페이지로 이동 (iframe이면 부모 SPA에 메시지 전달) */
  function goLogin() {
    if (isInIframe()) {
      try { window.parent.postMessage({ type: 'FIRE_AUTH_EXPIRED' }, '*'); } catch(_) {}
      return;
    }
    // returnUrl 파라미터로 현재 페이지 경로를 전달하여 로그인 후 복귀
    var returnUrl = location.pathname + location.search;
    location.href = '/index.html?returnUrl=' + encodeURIComponent(returnUrl);
  }

  window.FireWebCsrf = {
    getToken: function () { return readCookie("XSRF-TOKEN"); },
    getJwtToken: getJwtToken,
    ensureToken: ensureToken,
    refreshJwtToken: refreshJwtToken,
    headers: headers,
    applyOptions: applyOptions,
    isMutation: isMutation,
    isInIframe: isInIframe,
    goLogin: goLogin
  };
})();
