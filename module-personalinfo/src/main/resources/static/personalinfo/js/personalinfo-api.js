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
    if (res.status === 403) {
      // 서버가 이유를 보내면 그대로 보여준다 (예: 소속부서 계약만 볼 수 있습니다)
      let m = null;
      try { const j = await res.json(); m = j && j.message; } catch (e) { /* 본문 없음 */ }
      throw new Error(m && !/^(Forbidden|Access Denied)$/i.test(m) ? m : '이 기능을 사용할 권한이 없습니다.');
    }
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
    dashboard: (params) => request('/dashboard' + qs(params)),
    listContracts: (params) => request('/contracts' + qs(params)),
    downloadExcel: (params, reason) => download('/contracts/excel' + qs({ ...params, reason }), '외부위탁현황.xlsx'),
    getContract: (id) => request('/contracts/' + id),
    sameVendorContracts: (id) => request('/contracts/' + id + '/same-vendor'),
    vendorContracts: (vendorName) => request('/vendor-contracts' + qs({ vendorName })),
    contactHistory: (id, vendorWide) => request('/contracts/' + id + '/contact-history' + qs({ vendorWide })),
    createContract: (body) => request('/contracts', { method: 'POST', body }),
    updateContract: (id, body) => request('/contracts/' + id, { method: 'PUT', body }),
    updateContractFlags: (id, body) => request('/contracts/' + id + '/flags', { method: 'PATCH', body }),
    terminateContract: (id, body) => request('/contracts/' + id + '/terminate', { method: 'POST', body }),
    reopenContract: (id) => request('/contracts/' + id + '/reopen', { method: 'POST' }),
    deleteContract: (id) => request('/contracts/' + id, { method: 'DELETE' }),
    uploadFile: (id, form) => request('/contracts/' + id + '/files', { method: 'POST', form }),
    checkFile: (id, form) => request('/contracts/' + id + '/files/check', { method: 'POST', form }),
    analyzeContractDoc: (form) => request('/contract-doc/analyze', { method: 'POST', form }),
    updateFileDocumentDate: (fileId, documentDate, documentNote) => request('/files/' + fileId + '/document-date',
      { method: 'PATCH', body: documentNote === undefined ? { documentDate } : { documentDate, documentNote } }),
    periodicDestruction: (id, year) => request('/contracts/' + id + '/periodic-destruction' + qs({ year })),
    updateFileDestructionPeriod: (fileId, destructionPeriod) => request('/files/' + fileId + '/destruction-period', { method: 'PATCH', body: { destructionPeriod } }),
    changeExpiryDocsRemind: (id, remindYn) => request('/contracts/' + id + '/expiry-docs-remind', { method: 'PATCH', body: { remindYn } }),
    // 접근 기록 (관리자)
    accessLogActionTypes: () => request('/access-logs/action-types'),
    listAccessLogs: (params) => request('/access-logs' + qs(params)),
    downloadAccessLogs: (params, reason) => download('/access-logs/excel' + qs({ ...params, reason }), '접근기록.xlsx'),
    updateFileSignedDate: (fileId, signedDate) => request('/files/' + fileId + '/signed-date', { method: 'PATCH', body: { signedDate } }),
    downloadFile: (fileId, name) => download('/files/' + fileId + '/download', name),
    deleteFile: (fileId) => request('/files/' + fileId, { method: 'DELETE' }),
    downloadBlankTemplate: () => download('/checklist-template', '점검체크리스트.xlsx'),
    downloadTemplate: (id) => download('/contracts/' + id + '/checklist-template', '점검체크리스트.xlsx'),
    // 계약만료시 점검 양식: checklist | security-pledge | destruction-confirm
    downloadBlankExpiryForm: (type) => download('/expiry-forms/' + type, type + '.docx'),
    downloadExpiryForm: (id, type) => download('/contracts/' + id + '/expiry-forms/' + type, type + (type === 'checklist' ? '.xlsx' : '.docx')),
    // 양식 관리 (보안팀 관리자·플랫폼 관리자)
    listFormTemplates: () => request('/form-templates'),
    checkFormTemplate: (type, form) => request('/form-templates/' + type + '/check', { method: 'POST', form }),
    uploadFormTemplate: (type, form) => request('/form-templates/' + type, { method: 'POST', form }),
    activateFormTemplate: (versionId) => request('/form-templates/versions/' + versionId + '/activate', { method: 'POST' }),
    useDefaultFormTemplate: (type) => request('/form-templates/' + type + '/use-default', { method: 'POST' }),
    deleteFormTemplate: (versionId) => request('/form-templates/versions/' + versionId, { method: 'DELETE' }),
    downloadFormTemplate: (versionId) => download('/form-templates/versions/' + versionId + '/download', '양식'),
    previewFormTemplate: (versionId) => download('/form-templates/versions/' + versionId + '/preview', '양식_미리보기'),
    downloadDefaultFormTemplate: (type) => download('/form-templates/' + type + '/default/download', '기본양식'),
    previewDefaultFormTemplate: (type) => download('/form-templates/' + type + '/default/preview', '기본양식_미리보기'),
    uploadInspection: (id, form) => request('/contracts/' + id + '/inspections', { method: 'POST', form }),
    getInspection: (inspectionId) => request('/inspections/' + inspectionId),
    updateInspection: (inspectionId, body) => request('/inspections/' + inspectionId, { method: 'PUT', body }),
    downloadInspection: (inspectionId, name) => download('/inspections/' + inspectionId + '/download', name),
    deleteInspection: (inspectionId) => request('/inspections/' + inspectionId, { method: 'DELETE' }),
    sendAlert: (id, body) => request('/contracts/' + id + '/alerts', { method: 'POST', body }),
    runAlerts: () => request('/alerts/run', { method: 'POST' }),
    listVendors: (params) => request('/vendors' + qs(params)),
    previewVendorChecklist: (form) => request('/vendors/checklist-preview', { method: 'POST', form }),
    uploadVendorChecklist: (form) => request('/vendors/checklist', { method: 'POST', form }),
  };
})();
