(function () {
  var inSteamScope = window.location.pathname === "/steam" || window.location.pathname.indexOf("/steam/") === 0;
  if (!inSteamScope) return;

  // 플랫폼 JWT — SPA 가 iframe 주소에 붙여주는 _t 파라미터, 없으면 로그인 세션에서 읽는다.
  // 데이터 API(/steam/api/**) 호출에 Authorization 헤더로 실어 보낸다.
  var platformToken = (function () {
    try {
      var fromUrl = new URLSearchParams(window.location.search).get("_t");
      if (fromUrl) return fromUrl;
      var saved = JSON.parse(localStorage.getItem("fireweb_user") || "{}");
      return saved.token || "";
    } catch (error) {
      return "";
    }
  })();

  // 플랫폼 로그인 세션이 없거나 만료되면 데이터 API 가 401 을 준다.
  // 이때 화면을 빈 채로 두면 "페이지가 안 열린다" 로 보이므로 로그인 화면으로 보낸다.
  // (iframe 안이면 최상위 창을 옮긴다)
  var movingToLogin = false;
  function goToLogin() {
    if (movingToLogin) return;
    movingToLogin = true;
    var target = window.top || window;
    try { target.location.href = "/"; } catch (error) { window.location.href = "/"; }
  }

  function withAuth(init) {
    if (!platformToken) return init;
    var next = init ? Object.assign({}, init) : {};
    var headers = new Headers(next.headers || {});
    if (!headers.has("Authorization")) headers.set("Authorization", "Bearer " + platformToken);
    next.headers = headers;
    return next;
  }

  var nativeFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    var needsAuth = false;
    var target = typeof input === "string" ? input : (input instanceof Request ? input.url : "");
    if (target && (target.indexOf("/steam/api/") === 0 || target.indexOf("/tables") === 0
        || target.indexOf("tables/") === 0 || target.indexOf("/steam/tables") === 0)) {
      needsAuth = true;
    }
    if (needsAuth) init = withAuth(init);
    if (typeof input === "string" && input.indexOf("/steam/tables") === 0) {
      input = "/steam/api" + input.substring("/steam".length);
    } else if (typeof input === "string" && input.indexOf("/tables") === 0) {
      input = "/steam/api" + input;
    } else if (typeof input === "string" && input.indexOf("tables/") === 0) {
      input = "/steam/api/" + input;
    } else if (input instanceof Request && input.url) {
      var url = new URL(input.url, window.location.origin);
      if (url.origin === window.location.origin && url.pathname.indexOf("/steam/tables") === 0) {
        input = new Request("/steam/api" + url.pathname.substring("/steam".length) + url.search, input);
      } else if (url.origin === window.location.origin && url.pathname.indexOf("/tables") === 0) {
        input = new Request("/steam/api" + url.pathname + url.search, input);
      }
    }
    var response = nativeFetch(input, init);
    if (!needsAuth) return response;
    return response.then(function (res) {
      if (res.status === 401 || res.status === 403) goToLogin();
      return res;
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
