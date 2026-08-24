/* =========================================================================
 * KIMS 공통 프론트엔드 스크립트 (무빌드: 순수 JS)
 *  - JWT 토큰을 localStorage 에 보관하고, 모든 API 호출에 Bearer 헤더를 자동 첨부
 *  - 데이터는 항상 /kims-api/** (JWT 보호) 에서 가져온다
 * ========================================================================= */
const KIMS = (() => {
  const TOKEN_KEY = 'kims_token';
  const USER_KEY = 'kims_user';
  const ROLES_KEY = 'kims_roles';
  // 플랫폼(AiPlatform) 로그인 세션. KIMS 는 플랫폼 로그인으로 통일되어 이 값을 그대로 쓴다.
  const PLATFORM_KEY = 'fireweb_user';

  const platformSession = () => {
    try { return JSON.parse(localStorage.getItem(PLATFORM_KEY) || '{}') || {}; }
    catch (e) { return {}; }
  };

  // ---- 토큰/세션 (플랫폼 로그인 우선) ----
  const getToken = () => platformSession().token || localStorage.getItem(TOKEN_KEY) || '';
  const getUser = () => {
    const p = platformSession();
    return p.userName || p.loginId || localStorage.getItem(USER_KEY) || '';
  };
  const getRoles = () => {
    const role = platformSession().role;
    if (role) return [String(role).replace(/^ROLE_/, '')];
    return JSON.parse(localStorage.getItem(ROLES_KEY) || '[]');
  };
  const setSession = (token, user, roles) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, user || '');
    localStorage.setItem(ROLES_KEY, JSON.stringify(roles || []));
  };
  const clear = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(ROLES_KEY);
  };

  // 로그인 안 되어 있으면 로그인 화면으로 보냄
  const requireAuth = () => {
    if (!getToken()) {
      // 플랫폼 로그인 화면으로 (iframe 안이면 최상위 창을 이동)
      (window.top || window).location.href = '/';
      return false;
    }
    return true;
  };

  // ---- API 호출 (ApiResponse<T> 래퍼를 풀어서 data 반환) ----
  async function api(path, { method = 'GET', body = null } = {}) {
    const headers = { 'Authorization': 'Bearer ' + getToken() };
    if (body !== null) headers['Content-Type'] = 'application/json; charset=utf-8';

    const res = await fetch(path, {
      method,
      headers,
      body: body !== null ? JSON.stringify(body) : undefined,
      cache: 'no-store',   // 항상 최신 데이터 (대시보드 등 집계가 브라우저 캐시로 지연되는 문제 방지)
    });

    if (res.status === 401 || res.status === 403) {
      throw new Error('권한이 없거나 토큰이 만료되었습니다. (HTTP ' + res.status + ') 다시 로그인하세요.');
    }
    let json = null;
    try { json = await res.json(); } catch (e) { /* 본문 없음 */ }

    if (!res.ok || (json && json.success === false)) {
      const msg = (json && json.message) ? json.message : ('요청 실패 (HTTP ' + res.status + ')');
      throw new Error(msg);
    }
    return json ? json.data : null;
  }

  // ---- 멀티파트 파일 업로드 (Authorization 헤더 첨부, Content-Type 은 브라우저가 설정) ----
  async function uploadFile(path, file) {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch(path, { method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }, body: fd });
    let json = null;
    try { json = await res.json(); } catch (e) { /* ignore */ }
    if (!res.ok || (json && json.success === false)) {
      throw new Error((json && json.message) ? json.message : ('업로드 실패 (HTTP ' + res.status + ')'));
    }
    return json ? json.data : null;
  }

  // ---- 인증 헤더가 필요한 파일 다운로드 (Excel 등) ----
  async function download(path, filename) {
    const res = await fetch(path, { headers: { 'Authorization': 'Bearer ' + getToken() } });
    if (!res.ok) throw new Error('다운로드 실패 (HTTP ' + res.status + ')');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  // ---- KIMS 관리자 판정 ----
  // 관리자 명단은 공통코드 그룹 'KIMS_PERM' 에서만 관리한다(공통코드 관리 화면).
  // 플랫폼 역할은 보지 않는다 — 서버 판정(KimsPermission)과 같은 기준.
  let _adminCache = null;
  async function isAdmin() {
    if (_adminCache !== null) return _adminCache;
    const session = platformSession();
    const loginId = String(session.loginId || localStorage.getItem(USER_KEY) || '').trim().toLowerCase();
    if (!loginId) return (_adminCache = false);
    try {
      const res = await fetch('/common-api/codes/lookup/KIMS_PERM', {
        headers: { 'Authorization': 'Bearer ' + getToken() },
      });
      const json = await res.json();
      const list = (json && json.success && Array.isArray(json.data)) ? json.data : [];
      _adminCache = list.some(d => String(d.code || '').trim().toLowerCase() === loginId);
    } catch (e) {
      console.warn('KIMS_PERM 조회 실패', e);
      _adminCache = false;
    }
    return _adminCache;
  }

  // ---- 공통 네비게이션 (좌측 세로 사이드바) ----
  function renderNav(active) {
    // 플랫폼 SPA iframe 안에서는 플랫폼 사이드바가 이미 있으므로 KIMS 자체 사이드바는 그리지 않는다.
    if (window.self !== window.top) return;
    const roles = getRoles().join(', ') || '없음';
    const user = getUser() || '사용자';
    const initial = escapeHtml(user.charAt(0) || 'K');
    const links = [
      { href: 'dashboard.html', label: '대시보드', icon: '📊' },
      { href: 'request.html', label: '업무 요청', icon: '📝' },
      { href: 'inventory.html', label: '소모품', icon: '📦' },
      { href: 'ip.html', label: 'PC 관리', icon: '🖥️' },
      { href: 'settlement.html', label: '월말 결산', icon: '📅' },
      { href: 'qr.html', label: 'QR 관리', icon: '🔳' },
    ];
    const items = links.map(l =>
      `<a class="kims-navlink ${l.href === active ? 'active' : ''}" href="${l.href}">
         <span class="kims-navicon">${l.icon}</span><span>${l.label}</span></a>`
    ).join('');
    const css = `<style id="kims-nav-style">
      body { padding-left: 220px; }
      .kims-sidebar { position: fixed; top: 0; left: 0; width: 220px; height: 100vh;
        background: #0f172a; color: #cbd5e1; display: flex; flex-direction: column;
        z-index: 1030; overflow-y: auto; }
      .kims-brand { padding: 18px 20px 14px; border-bottom: 1px solid rgba(255,255,255,.08); }
      .kims-brand .b1 { font-weight: 700; font-size: 1.15rem; color: #fff; letter-spacing: .5px; }
      .kims-brand .b2 { font-size: 11px; color: #64748b; }
      .kims-user { display: flex; gap: 10px; align-items: center; padding: 14px 20px;
        border-bottom: 1px solid rgba(255,255,255,.08); }
      .kims-avatar { width: 36px; height: 36px; border-radius: 50%; background: #2563eb; color: #fff;
        display: flex; align-items: center; justify-content: center; font-weight: 600; flex: 0 0 auto; }
      .kims-user .u1 { font-weight: 600; font-size: 13px; color: #e2e8f0; }
      .kims-user .u2 { font-size: 11px; color: #64748b; }
      .kims-nav { display: flex; flex-direction: column; padding: 10px 0; flex: 1 1 auto; }
      .kims-navlink { display: flex; align-items: center; gap: 10px; padding: 10px 20px;
        color: #cbd5e1; text-decoration: none; font-size: 14px; border-left: 3px solid transparent; }
      .kims-navlink:hover { background: rgba(255,255,255,.06); color: #fff; }
      .kims-navlink.active { background: rgba(37,99,235,.18); color: #fff; border-left-color: #3b82f6; font-weight: 600; }
      .kims-navicon { width: 18px; text-align: center; }
      .kims-logout { margin: 12px 16px 16px; }
    </style>`;
    const html = css + `
      <aside class="kims-sidebar">
        <a class="kims-brand text-decoration-none d-block" href="dashboard.html">
          <div class="b1">KIMS</div><div class="b2">IT Operation Mgmt</div>
        </a>
        <div class="kims-user">
          <div class="kims-avatar">${initial}</div>
          <div><div class="u1">${escapeHtml(user)}</div><div class="u2">${escapeHtml(roles)}</div></div>
        </div>
        <nav class="kims-nav">${items}</nav>
        <button class="btn btn-outline-light btn-sm kims-logout" onclick="KIMS.logout()">로그아웃</button>
      </aside>`;
    const holder = document.getElementById('nav');
    if (holder) holder.innerHTML = html;
  }

  const logout = () => { clear(); (window.top || window).location.href = '/'; };

  // ---- 유틸 ----
  const escapeHtml = (s) => (s == null ? '' : String(s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])));

  const toast = (msg, ok = true) => {
    const box = document.getElementById('msg');
    if (!box) { alert(msg); return; }
    box.className = 'alert ' + (ok ? 'alert-success' : 'alert-danger');
    box.textContent = msg;
    box.classList.remove('d-none');
    setTimeout(() => box.classList.add('d-none'), 4000);
  };

  // ---- 공용 알림 모달 (Bootstrap 비의존, 어느 화면에서도 동작) ----
  const alertModal = (title, bodyHtml, variant = 'danger') => {
    const id = 'kims-alert-overlay';
    const prev = document.getElementById(id); if (prev) prev.remove();
    const color = variant === 'danger' ? '#dc3545' : '#0d6efd';
    const ov = document.createElement('div');
    ov.id = id;
    ov.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:20000;display:flex;align-items:center;justify-content:center;';
    ov.innerHTML =
      '<div style="background:#fff;border-radius:10px;max-width:440px;width:90%;box-shadow:0 12px 48px rgba(0,0,0,.35);overflow:hidden">' +
        '<div style="padding:14px 18px;border-top:5px solid ' + color + ';font-weight:700;font-size:1.05rem">' + title + '</div>' +
        '<div style="padding:6px 18px 18px;line-height:1.6">' + bodyHtml + '</div>' +
        '<div style="padding:12px 18px;text-align:right;border-top:1px solid #eee">' +
          '<button type="button" class="btn btn-primary btn-sm" id="kims-alert-ok">확인</button></div>' +
      '</div>';
    document.body.appendChild(ov);
    const close = () => ov.remove();
    ov.querySelector('#kims-alert-ok').addEventListener('click', close);
    ov.addEventListener('click', e => { if (e.target === ov) close(); });
  };

  return { getToken, getUser, getRoles, isAdmin, setSession, clear, requireAuth, api, uploadFile, download, renderNav, logout, escapeHtml, toast, alertModal };
})();
