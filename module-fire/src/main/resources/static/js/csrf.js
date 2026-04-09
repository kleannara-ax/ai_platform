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

  /** localStorage에서 JWT 토큰을 읽는다 (AI Platform SPA 연동) */
  function getJwtToken() {
    try {
      var u = JSON.parse(localStorage.getItem("fireweb_user") || "null");
      if (u && u.token) return u.token;
    } catch (_) {}
    return localStorage.getItem("fireweb_token")
        || localStorage.getItem("fw_token")
        || "";
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
    location.href = '/index.html';
  }

  window.FireWebCsrf = {
    getToken: function () { return readCookie("XSRF-TOKEN"); },
    getJwtToken: getJwtToken,
    ensureToken: ensureToken,
    headers: headers,
    applyOptions: applyOptions,
    isMutation: isMutation,
    isInIframe: isInIframe,
    goLogin: goLogin
  };
})();
