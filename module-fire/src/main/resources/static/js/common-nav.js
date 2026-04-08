(function () {
  function ensureNavTheme() {
    if (document.getElementById("fwCommonNavTheme")) return;
    var style = document.createElement("style");
    style.id = "fwCommonNavTheme";
    style.textContent =
      ".fireweb-navbar{position:relative;z-index:1030;border-bottom-left-radius:14px;border-bottom-right-radius:14px;overflow:visible;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%) !important;}" +
      ".fireweb-navbar .dropdown-menu{z-index:1040;}" +
      ".fireweb-navbar .navbar-brand,.fireweb-navbar .navbar-brand:visited,.fireweb-navbar .nav-link,.fireweb-navbar .nav-link:visited,.fireweb-navbar .nav-link.account-link,.fireweb-navbar .dropdown-toggle,.fireweb-navbar .dropdown-toggle:visited{color:#fff !important;}" +
      ".fireweb-navbar .brand-logos{display:flex;align-items:center;gap:8px;}" +
      ".fireweb-navbar .brand-logos img{height:32px !important;width:auto !important;display:block;object-fit:contain !important;}";
    document.head.appendChild(style);
  }

  function stripListWord(text) {
    return String(text || "").replace(/\s*목록\b/g, "").trim();
  }

  function esc(v) {
    return String(v ?? "").replace(/[&<>"']/g, function (m) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[m];
    });
  }

  function getUser() {
    try {
      return JSON.parse(localStorage.getItem("fireweb_user") || "null");
    } catch (_) {
      return null;
    }
  }

  async function logout() {
    try {
      await window.FireWebCsrf?.ensureToken?.();
      const headers = window.FireWebCsrf?.headers({}, "POST") || {};
      await fetch("/api/auth/logout", { method: "POST", headers: headers });
    } catch (_) {
      // ignore
    }
    localStorage.removeItem("fireweb_user");
    localStorage.removeItem("fw_user");
    localStorage.removeItem("fireweb_token");
    localStorage.removeItem("fw_token");
    location.href = "/login.html";
  }

  function setMenuLinks() {
    var nav = document.querySelector(".fireweb-navbar");
    if (!nav) return;
    var user = getUser();
    var isAdmin = String(user && user.role || "").toUpperCase() === "ADMIN";
    var menu = nav.querySelector(".navbar-nav.ms-3");
    if (!menu) return;
    var defs = [
      { href: "/extinguishers.html", text: "\uC18C\uD654\uAE30" },
      { href: "/hydrants.html", text: "\uC18C\uD654\uC804" },
      { href: "/maps/floor.html?buildingName=%EB%B3%B5%EC%A7%80%EA%B4%80&floorName=1%EC%B8%B5", text: "\uB3C4\uBA74" }
    ];
    if (isAdmin) {
      defs.splice(2, 0,
        { href: "/receivers.html", text: "\uC218\uC2E0\uAE30" },
        { href: "/pumps.html", text: "\uC18C\uBC29\uD38C\uD504" }
      );
      defs.push({ href: "/qr", text: "QR\uCF54\uB4DC" });
    }
    menu.innerHTML = defs.map(function (item) {
      return '<li class="nav-item"><a class="nav-link fw-semibold" href="' + item.href + '">' + item.text + '</a></li>';
    }).join("");
  }

  function syncVisibleLabels() {
    document.title = stripListWord(document.title);

    document.querySelectorAll(".menu-btn, #pageTitle, h3.mb-1.fw-bold, .navbar-nav.ms-3 .nav-link").forEach(function (el) {
      el.textContent = stripListWord(el.textContent);
    });

    var root = document.getElementById("adminEquipmentPage");
    if (root && root.dataset && root.dataset.title) {
      root.dataset.title = stripListWord(root.dataset.title);
    }
  }

  function setAccountArea() {
    var area = document.getElementById("navAccountArea");
    if (!area) return;
    var user = getUser();
    if (!user) {
      area.innerHTML = '<li class="nav-item"><a class="btn btn-sm btn-outline-light" href="/login.html">\uB85C\uADF8\uC778</a></li>';
      return;
    }
    var isAdmin = String(user.role || "").toUpperCase() === "ADMIN";
    area.innerHTML =
      '<li class="nav-item dropdown">' +
      '<a class="nav-link dropdown-toggle fw-semibold account-link" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">' +
      esc(user.displayName || user.username || "\uC0AC\uC6A9\uC790") +
      "</a>" +
      '<ul class="dropdown-menu dropdown-menu-end">' +
      '<li><a class="dropdown-item" href="/account/index.html">\uB0B4 \uC815\uBCF4</a></li>' +
      (isAdmin ? '<li><a class="dropdown-item" href="/account/users.html">\uACC4\uC815\uAD00\uB9AC</a></li>' : "") +
      '<li><hr class="dropdown-divider"></li>' +
      '<li><button type="button" class="dropdown-item text-danger" id="fwCommonLogoutBtn">\uB85C\uADF8\uC544\uC6C3</button></li>' +
      "</ul></li>";
    var btn = document.getElementById("fwCommonLogoutBtn");
    if (btn) {
      btn.addEventListener("click", logout);
    }
  }

  function mount() {
    ensureNavTheme();
    setMenuLinks();
    setAccountArea();
    syncVisibleLabels();
  }

  window.FireWebNav = { mount: mount, logout: logout };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})();
