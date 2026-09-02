/* =========================================================================
 * SAFETY 공통 프론트엔드 스크립트 (무빌드: 순수 JS)
 *  - 플랫폼(AiPlatform) 로그인 세션(JWT)을 재사용한다 (module-kims 의 kims.js 와 동일 패턴)
 *  - 데이터는 항상 /safety-api/** 에서 가져온다 (사진 view 만 공개)
 * ========================================================================= */
const SAFETY = (() => {
  const TOKEN_KEY = 'safety_token';
  const USER_KEY = 'safety_user';
  const ROLES_KEY = 'safety_roles';
  // 플랫폼(AiPlatform) 로그인 세션. SAFETY 는 플랫폼 로그인으로 통일되어 이 값을 그대로 쓴다.
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

  const requireAuth = () => {
    if (!getToken()) {
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
      cache: 'no-store',
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

  // ---- 멀티파트 업로드 (파일 + 추가 필드) ----
  async function uploadMultipart(path, fields) {
    const fd = new FormData();
    Object.entries(fields || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null) fd.append(k, v);
    });
    const res = await fetch(path, { method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }, body: fd });
    let json = null;
    try { json = await res.json(); } catch (e) { /* ignore */ }
    if (!res.ok || (json && json.success === false)) {
      throw new Error((json && json.message) ? json.message : ('요청 실패 (HTTP ' + res.status + ')'));
    }
    return json ? json.data : null;
  }

  // ---- SAFETY 관리자 판정 ----
  // 공통코드 그룹 'SAFETY_PERM' 에 등록된 로그인 ID 만 관리자다 (서버 SafetyPermission 과 같은 기준).
  // 플랫폼 ROLE_ADMIN 이라도 이 명단에 없으면 관리자가 아니다.
  let _adminCache = null;
  async function isAdmin() {
    if (_adminCache !== null) return _adminCache;
    const session = platformSession();
    const loginId = String(session.loginId || localStorage.getItem(USER_KEY) || '').trim().toLowerCase();
    if (!loginId) return (_adminCache = false);
    try {
      const res = await fetch('/common-api/codes/lookup/SAFETY_PERM', {
        headers: { 'Authorization': 'Bearer ' + getToken() },
      });
      const json = await res.json();
      const list = (json && json.success && Array.isArray(json.data)) ? json.data : [];
      _adminCache = list.some(d => String(d.code || '').trim().toLowerCase() === loginId);
    } catch (e) {
      console.warn('SAFETY_PERM 조회 실패', e);
      _adminCache = false;
    }
    return _adminCache;
  }

  // ---- 공통 네비게이션 (좌측 세로 사이드바) ----
  function renderNav(active) {
    // 플랫폼 SPA iframe 안에서는 플랫폼 사이드바가 이미 있으므로 SAFETY 자체 사이드바는 그리지 않는다.
    if (window.self !== window.top) return;
    const roles = getRoles().join(', ') || '없음';
    const user = getUser() || '사용자';
    const initial = escapeHtml(user.charAt(0) || 'S');
    const links = [
      { href: 'index.html', label: '분류/매뉴얼', icon: 'fa-shield-alt' },
    ];
    const items = links.map(l =>
      `<a class="safety-navlink ${l.href === active ? 'active' : ''}" href="${l.href}">
         <span class="safety-navicon"><i class="fas ${l.icon}"></i></span><span>${l.label}</span></a>`
    ).join('');
    const css = `<style id="safety-nav-style">
      body { padding-left: 220px; }
      .safety-sidebar { position: fixed; top: 0; left: 0; width: 220px; height: 100vh;
        background: linear-gradient(180deg,#1e1b4b,#312e81); color: #e0e7ff; display: flex; flex-direction: column;
        z-index: 1030; overflow-y: auto; box-shadow: 2px 0 12px rgba(0,0,0,.08); }
      .safety-brand { padding: 20px 20px 16px; border-bottom: 1px solid rgba(255,255,255,.10); }
      .safety-brand .b1 { font-weight: 800; font-size: 1.2rem; color: #fff; letter-spacing: .5px; }
      .safety-brand .b2 { font-size: 11px; color: #a5b4fc; }
      .safety-user { display: flex; gap: 10px; align-items: center; padding: 16px 20px;
        border-bottom: 1px solid rgba(255,255,255,.10); }
      .safety-avatar { width: 38px; height: 38px; border-radius: 12px; background: linear-gradient(135deg,#f59e0b,#f97316); color: #fff;
        display: flex; align-items: center; justify-content: center; font-weight: 700; flex: 0 0 auto; }
      .safety-user .u1 { font-weight: 700; font-size: 13px; color: #fff; }
      .safety-user .u2 { font-size: 11px; color: #a5b4fc; }
      .safety-nav { display: flex; flex-direction: column; padding: 12px 12px; flex: 1 1 auto; gap: 2px; }
      .safety-navlink { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 10px;
        color: #c7d2fe; text-decoration: none; font-size: 14px; }
      .safety-navlink:hover { background: rgba(255,255,255,.08); color: #fff; }
      .safety-navlink.active { background: #fff; color: #4338ca; font-weight: 700; }
      .safety-navicon { width: 18px; text-align: center; }
      .safety-logout { margin: 12px 16px 16px; border-radius: 10px; border-color: rgba(255,255,255,.3); }
    </style>`;
    const html = css + `
      <aside class="safety-sidebar">
        <a class="safety-brand text-decoration-none d-block" href="index.html">
          <div class="b1">SAFETY</div><div class="b2">안전작업 매뉴얼</div>
        </a>
        <div class="safety-user">
          <div class="safety-avatar">${initial}</div>
          <div><div class="u1">${escapeHtml(user)}</div><div class="u2">${escapeHtml(roles)}</div></div>
        </div>
        <nav class="safety-nav">${items}</nav>
        <button class="btn btn-outline-light btn-sm safety-logout" onclick="SAFETY.logout()">로그아웃</button>
      </aside>`;
    const holder = document.getElementById('nav');
    if (holder) holder.innerHTML = html;
  }

  const logout = () => { (window.top || window).location.href = '/'; };

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

  return { getToken, getUser, getRoles, isAdmin, requireAuth, api, uploadMultipart, renderNav, logout, escapeHtml, toast };
})();
