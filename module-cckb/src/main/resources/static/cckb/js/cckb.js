/* =========================================================================
 * CCKB 공통 프론트엔드 스크립트 (무빌드: 순수 JS)
 *  - 플랫폼(AiPlatform) 로그인 세션(JWT)을 재사용한다 (module-safety 의 safety.js, module-kims 의
 *    kims.js 와 동일 패턴)
 *  - 데이터는 항상 /cckb-api/** 에서 가져온다. ★ 이미지 다운로드도 permitAll 예외 없이 인증이
 *    필요하다(dailyreport ImageController 와 동일 방침 — 사용자 명시 지시). 그래서 <img src="...">
 *    가 아니라 CCKB.loadImageBlob() 로 인증된 fetch()+blob 을 사용해야 한다.
 * ========================================================================= */
const CCKB = (() => {
  const TOKEN_KEY = 'cckb_token';
  const USER_KEY = 'cckb_user';
  const ROLES_KEY = 'cckb_roles';
  // 플랫폼(AiPlatform) 로그인 세션. CCKB 는 플랫폼 로그인으로 통일되어 이 값을 그대로 쓴다.
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
  const getLoginId = () => platformSession().loginId || localStorage.getItem(USER_KEY) || '';
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
    if (res.status === 413) {
      throw new Error('파일이 너무 커서 서버가 받지 못했습니다 (HTTP 413). '
        + '웹서버(nginx: client_max_body_size / Apache: LimitRequestBody)의 업로드 크기 제한을 늘려야 합니다.');
    }
    if (res.status === 401 || res.status === 403) {
      throw new Error('권한이 없거나 토큰이 만료되었습니다. (HTTP ' + res.status + ') 다시 로그인하세요.');
    }
    if (!res.ok || (json && json.success === false)) {
      throw new Error((json && json.message) ? json.message : ('요청 실패 (HTTP ' + res.status + ')'));
    }
    return json ? json.data : null;
  }

  // ---- 인증된 이미지 조회: fetch() → blob() → URL.createObjectURL() ----
  // ★ /cckb-api/images/files/{filename} 은 permitAll 예외가 없다(dailyreport 방식 — 사용자 명시 지시).
  //   그래서 <img src="..."> 로는 Authorization 헤더를 보낼 수 없어 절대 뜨지 않는다.
  //   반드시 이 함수로 blob 을 받아 object URL 을 만들어 써야 한다.
  //   호출한 쪽에서 더 이상 쓰지 않을 때 URL.revokeObjectURL() 로 정리해야 메모리 누수가 없다.
  async function loadImageBlob(fileUrl) {
    const res = await fetch(fileUrl, {
      headers: { 'Authorization': 'Bearer ' + getToken() },
      cache: 'no-store',
    });
    if (!res.ok) {
      throw new Error('이미지를 불러오지 못했습니다. (HTTP ' + res.status + ')');
    }
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  }

  // ---- CCKB 관리자 판정 ----
  // 공통코드 그룹 'CCKB_PERM' 에 등록된 로그인 ID 만 관리자다 (서버 CckbPermission 과 같은 기준).
  // 플랫폼 ROLE_ADMIN 이라도 이 명단에 없으면 관리자가 아니다.
  let _adminCache = null;
  async function isAdmin() {
    if (_adminCache !== null) return _adminCache;
    const loginId = String(getLoginId() || '').trim().toLowerCase();
    if (!loginId) return (_adminCache = false);
    try {
      const res = await fetch('/common-api/codes/lookup/CCKB_PERM', {
        headers: { 'Authorization': 'Bearer ' + getToken() },
      });
      const json = await res.json();
      const list = (json && json.success && Array.isArray(json.data)) ? json.data : [];
      _adminCache = list.some(d => String(d.code || '').trim().toLowerCase() === loginId);
    } catch (e) {
      console.warn('CCKB_PERM 조회 실패', e);
      _adminCache = false;
    }
    return _adminCache;
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

  return {
    getToken, getUser, getLoginId, getRoles, isAdmin, requireAuth,
    api, uploadMultipart, loadImageBlob, logout, escapeHtml, toast,
  };
})();
