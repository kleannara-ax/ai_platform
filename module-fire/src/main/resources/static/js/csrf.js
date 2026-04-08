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

  function isMutation(method) {
    const normalized = String(method || "GET").toUpperCase();
    return normalized === "POST" || normalized === "PUT" || normalized === "PATCH" || normalized === "DELETE";
  }

  function headers(extra, method) {
    const merged = { ...(extra || {}) };
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
    await fetch("/api/auth/csrf", { method: "GET", credentials: "same-origin" });
    return readCookie("XSRF-TOKEN");
  }

  function applyOptions(options) {
    const opts = { ...(options || {}) };
    opts.headers = headers(opts.headers, opts.method);
    return opts;
  }

  window.FireWebCsrf = {
    getToken: function () { return readCookie("XSRF-TOKEN"); },
    ensureToken: ensureToken,
    headers: headers,
    applyOptions: applyOptions,
    isMutation: isMutation
  };
})();
