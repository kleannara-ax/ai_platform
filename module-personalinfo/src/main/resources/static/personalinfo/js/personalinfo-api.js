/* =========================================================================
 * 개인정보 위수탁 모듈 API 클라이언트 (무빌드 순수 JS)
 *  - 플랫폼 로그인 세션(JWT, localStorage 'fireweb_user')을 그대로 사용한다.
 *    (module-safety / module-kims 와 같은 방식. 별도 로그인 기능 없음)
 *  - 모든 데이터는 /personalinfo-api/** 에서 가져오며 ApiResponse<T> 의 data 를 돌려준다.
 * ========================================================================= */
const PI_API = (() => {
  const PLATFORM_KEY = 'fireweb_user';
  const BASE = '/personalinfo-api';

  const session = () => {
    try { return JSON.parse(localStorage.getItem(PLATFORM_KEY) || '{}') || {}; } catch (e) { return {}; }
  };
  const token = () => session().token || '';

  function requireAuth() {
    if (!token()) { (window.top || window).location.href = '/'; return false; }
    return true;
  }

  async function parse(res) {
    if (res.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인하세요.');
    if (res.status === 403) throw new Error('이 기능을 사용할 권한이 없습니다.');
    let json = null;
    try { json = await res.json(); } catch (e) { /* 본문 없음 */ }
    if (!res.ok || (json && json.success === false)) {
      let msg = (json && json.message) || ('요청 실패 (HTTP ' + res.status + ')');
      if (json && json.data && typeof json.data === 'object') {
        const details = Object.values(json.data).filter(v => typeof v === 'string');
        if (details.length) msg += '\n- ' + details.join('\n- ');
      }
      throw new Error(msg);
    }
    return json ? json.data : null;
  }

  async function request(path, { method = 'GET', body, form } = {}) {
    const headers = { 'Authorization': 'Bearer ' + token() };
    let payload;
    if (form) payload = form;
    else if (body !== undefined) { headers['Content-Type'] = 'application/json; charset=utf-8'; payload = JSON.stringify(body); }
    const res = await fetch(BASE + path, { method, headers, body: payload, cache: 'no-store' });
    return parse(res);
  }

  /** 파일 다운로드: Authorization 헤더가 필요하므로 fetch → Blob 으로 받는다 */
  async function download(path, fallbackName) {
    const res = await fetch(BASE + path, { headers: { 'Authorization': 'Bearer ' + token() }, cache: 'no-store' });
    if (!res.ok) { await parse(res); return; }
    const blob = await res.blob();
    let name = fallbackName || 'download';
    const cd = res.headers.get('Content-Disposition') || '';
    const star = /filename\*=UTF-8''([^;]+)/i.exec(cd);
    const plain = /filename="?([^";]+)"?/i.exec(cd);
    if (star) name = decodeURIComponent(star[1]); else if (plain) name = plain[1];
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const qs = (params) => {
    const p = new URLSearchParams();
    Object.entries(params || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.append(k, v); });
    const s = p.toString();
    return s ? '?' + s : '';
  };

  return {
    requireAuth,
    permissions: () => request('/me/permissions'),
    dashboard: () => request('/dashboard'),
    listContracts: (params) => request('/contracts' + qs(params)),
    downloadExcel: (params) => download('/contracts/excel' + qs(params), '외부위탁현황.xlsx'),
    getContract: (id) => request('/contracts/' + id),
    createContract: (body) => request('/contracts', { method: 'POST', body }),
    updateContract: (id, body) => request('/contracts/' + id, { method: 'PUT', body }),
    terminateContract: (id, body) => request('/contracts/' + id + '/terminate', { method: 'POST', body }),
    reopenContract: (id) => request('/contracts/' + id + '/reopen', { method: 'POST' }),
    deleteContract: (id) => request('/contracts/' + id, { method: 'DELETE' }),
    uploadFile: (id, form) => request('/contracts/' + id + '/files', { method: 'POST', form }),
    downloadFile: (fileId, name) => download('/files/' + fileId + '/download', name),
    deleteFile: (fileId) => request('/files/' + fileId, { method: 'DELETE' }),
    downloadBlankTemplate: () => download('/checklist-template', '점검체크리스트.xlsx'),
    downloadTemplate: (id) => download('/contracts/' + id + '/checklist-template', '점검체크리스트.xlsx'),
    uploadInspection: (id, form) => request('/contracts/' + id + '/inspections', { method: 'POST', form }),
    getInspection: (inspectionId) => request('/inspections/' + inspectionId),
    updateInspection: (inspectionId, body) => request('/inspections/' + inspectionId, { method: 'PUT', body }),
    downloadInspection: (inspectionId, name) => download('/inspections/' + inspectionId + '/download', name),
    deleteInspection: (inspectionId) => request('/inspections/' + inspectionId, { method: 'DELETE' }),
    sendAlert: (id, body) => request('/contracts/' + id + '/alerts', { method: 'POST', body }),
    runAlerts: () => request('/alerts/run', { method: 'POST' }),
  };
})();
