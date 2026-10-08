/* =========================================================================
 * SECURITY_LOG 공통 프론트엔드 스크립트 (무빌드: 순수 JS)
 *  - 플랫폼 로그인 세션(JWT, localStorage 'fireweb_user')을 재사용한다
 *    (module-safety 의 safety.js / module-kims 의 kims.js 와 동일 패턴)
 *  - 데이터는 항상 /security_log-api/** 에서 가져온다
 * ========================================================================= */
const SECLOG = (() => {
  const API = '/security_log-api';
  const PLATFORM_KEY = 'fireweb_user';

  const session = () => {
    try { return JSON.parse(localStorage.getItem(PLATFORM_KEY) || '{}') || {}; }
    catch (e) { return {}; }
  };
  const getToken = () => session().token || '';
  const getRoles = () => {
    const s = session();
    const list = Array.isArray(s.roles) && s.roles.length ? s.roles : (s.role ? [s.role] : []);
    return list.map(r => String(r).replace(/^ROLE_/, ''));
  };
  const hasAny = (...roles) => getRoles().some(r => roles.includes(r));
  const isAdmin = () => hasAny('ADMIN');
  const canWrite = () => hasAny('ADMIN', 'MANAGER');

  const requireAuth = () => {
    if (!getToken()) {
      document.body.innerHTML =
        '<div style="padding:60px;text-align:center;font-family:sans-serif;color:#64748b">' +
        '<h3>로그인이 필요합니다</h3><p>플랫폼에 로그인한 뒤 메뉴에서 다시 열어 주세요.</p>' +
        '<a href="/" target="_top">플랫폼으로 이동</a></div>';
      return false;
    }
    return true;
  };

  async function parse(res) {
    if (res.status === 401) throw new Error('로그인이 만료되었습니다. 플랫폼에서 다시 로그인하세요. (HTTP 401)');
    if (res.status === 403) throw new Error('이 작업을 수행할 권한이 없습니다. (HTTP 403)');
    if (res.status === 413) throw new Error('파일이 너무 커서 서버가 받지 못했습니다 (HTTP 413). 웹서버 업로드 크기 제한을 확인하세요.');
    let json = null;
    try { json = await res.json(); } catch (e) { /* 본문 없음 */ }
    if (!res.ok || (json && json.success === false)) {
      throw new Error((json && json.message) ? json.message : ('요청 실패 (HTTP ' + res.status + ')'));
    }
    return json ? json.data : null;
  }

  async function api(path, { method = 'GET', body = null } = {}) {
    const headers = { 'Authorization': 'Bearer ' + getToken() };
    if (body !== null) headers['Content-Type'] = 'application/json; charset=utf-8';
    const res = await fetch(API + path, {
      method, headers, cache: 'no-store',
      body: body !== null ? JSON.stringify(body) : undefined,
    });
    return parse(res);
  }

  async function upload(path, formData) {
    const res = await fetch(API + path, {
      method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }, body: formData,
    });
    return parse(res);
  }

  // ---- 표시 유틸 ----
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDt = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '-';
  const fmtNum = (n) => (n ?? 0).toLocaleString('ko-KR');
  const fmtSize = (b) => {
    if (!b && b !== 0) return '-';
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    return (b / 1024 / 1024).toFixed(1) + ' MB';
  };
  const fmtYm = (ym) => ym && ym.length === 6 ? ym.slice(0, 4) + '년 ' + ym.slice(4) + '월' : (ym || '');
  const currentYm = () => { const d = new Date(); return d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0'); };

  const SEVERITY = {
    CRITICAL: { label: '심각', cls: 'sev-critical' },
    HIGH: { label: '높음', cls: 'sev-high' },
    MEDIUM: { label: '보통', cls: 'sev-medium' },
    LOW: { label: '낮음', cls: 'sev-low' },
  };
  const REVIEW = {
    PENDING: { label: '미검토', cls: 'rv-pending' },
    CONFIRMED: { label: '이상징후 확인', cls: 'rv-confirmed' },
    RESOLVED: { label: '조치완료', cls: 'rv-resolved' },
    FALSE_POSITIVE: { label: '오탐', cls: 'rv-fp' },
  };
  const STATUS = {
    ANALYZED: { label: '검토대기', cls: 'st-analyzed' },
    REVIEWED: { label: '검토완료', cls: 'st-reviewed' },
  };
  const LOG_TYPES = [
    ['LINUX', 'Linux/Unix 시스템'], ['WINDOWS', 'Windows 이벤트'], ['WEB', '웹서버'], ['WAS', 'WAS'],
    ['DB', 'DBMS'], ['NETWORK', '방화벽/네트워크'], ['APP', '업무 애플리케이션'], ['ETC', '기타'],
  ];
  const sevBadge = (s) => s ? `<span class="badge-sev ${SEVERITY[s]?.cls || ''}">${SEVERITY[s]?.label || s}</span>` : '<span class="badge-sev sev-none">탐지없음</span>';
  const reviewBadge = (s) => `<span class="badge-rv ${REVIEW[s]?.cls || ''}">${REVIEW[s]?.label || s}</span>`;
  const statusBadge = (s) => `<span class="badge-st ${STATUS[s]?.cls || ''}">${STATUS[s]?.label || s}</span>`;

  // ---- 토스트 ----
  function toast(msg, type = 'success') {
    let box = document.getElementById('toastBox');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toastBox';
      box.className = 'toast-box';
      document.body.appendChild(box);
    }
    const el = document.createElement('div');
    el.className = 'toast-item toast-' + type;
    el.innerHTML = `<i class="fas ${type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check'}"></i> ${esc(msg)}`;
    box.appendChild(el);
    setTimeout(() => { el.classList.add('hide'); setTimeout(() => el.remove(), 300); }, type === 'error' ? 5000 : 2500);
  }

  const qs = (name) => new URLSearchParams(location.search).get(name);
  const go = (page, params) => {
    const q = params ? '?' + new URLSearchParams(params).toString() : '';
    location.href = page + q;
  };

  return {
    api, upload, requireAuth, isAdmin, canWrite, getRoles, session,
    esc, fmtDt, fmtNum, fmtSize, fmtYm, currentYm,
    SEVERITY, REVIEW, STATUS, LOG_TYPES, sevBadge, reviewBadge, statusBadge,
    toast, qs, go,
  };
})();
