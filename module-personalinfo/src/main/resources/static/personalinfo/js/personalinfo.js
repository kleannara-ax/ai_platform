/* =========================================================================
 * 개인정보 위수탁 관리 화면 (해시 라우팅 SPA, 무빌드 순수 JS)
 *   #/contracts            목록 화면
 *   #/contracts/new        등록 화면
 *   #/contracts/{id}       상세 화면 (계약서 업로드, 체크리스트 다운로드/업로드, 메일 발송)
 *   #/contracts/{id}/edit  수정 화면
 *   #/access-logs          접근 기록 (관리자: 조회·다운로드 기록, 엑셀 사유)
 *   #/forms                양식 관리 (보안팀 관리자·플랫폼 관리자: 체크리스트·확약서·파기 확인서 버전 관리)
 * 업체별 점검 체크리스트 업로드는 팝업(vendor-checklist-modal)으로 연다:
 *   목록의 업체명 클릭, 또는 상세의 [업체 계약 일괄 업로드] 버튼
 * 버튼 노출은 /personalinfo-api/me/permissions 결과로 제어하고,
 * 실제 권한 판정은 서버(@PreAuthorize)가 한다.
 * ========================================================================= */
(() => {
  'use strict';

  const view = document.getElementById('pi-view');
  const crumb = document.getElementById('pi-breadcrumb');
  const alertArea = document.getElementById('pi-alert-area');
  let perm = { canRead: false, canWrite: false, canDelete: false, canSendMail: false };
  /** 목록 기본 조건: 처음 들어오면(또는 초기화하면) [계약중] 카드가 선택된 상태 */
  const defaultCond = () => ({ yearStatus: 'ACTIVE' });
  let listState = { page: 0, size: 50, sort: 'contractEndDate,asc', cond: defaultCond(), baseYear: new Date().getFullYear() };
  let currentContract = null;

  // ──────────────── 공통 유틸 ────────────────
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDate = (d) => (d ? String(d).substring(0, 10).replace(/-/g, '.') : '-');
  const fmtDateTime = (d) => (d ? String(d).substring(0, 16).replace('T', ' ') : '-');
  const yn = (v) => (v === 'Y' ? 'Y' : v === 'N' ? 'N' : '-');
  const fmtSize = (n) => (n == null ? '-' : n < 1024 ? n + ' B' : n < 1048576 ? (n / 1024).toFixed(1) + ' KB' : (n / 1048576).toFixed(1) + ' MB');
  const badge = (code, label) => `<span class="pi-status ${esc(code || 'NONE')}">${esc(label || code || '-')}</span>`;
  /** 계약 표시 이름: 계약명 → 없으면 관련 서비스 */
  const contractLabel = (r) => r.contractName || r.serviceName || '-';
  /** 비관리자: 소속부서 계약만 조회 가능. 소속부서가 없으면 볼 수 있는 계약 없음 */
  const deptScoped = () => !perm.allDepartments;
  const canCreate = () => perm.canWrite && (perm.allDepartments || !!perm.deptName);
  const deptScopeBanner = () => {
    if (!deptScoped()) return '';
    return perm.deptName
      ? `<div class="pi-dept-scope alert alert-info py-2 px-3 mb-3" id="dept-scope-banner" role="status"><i class="fas fa-building-user me-1"></i>
          <b>${esc(perm.deptName)}</b> 소속부서의 계약만 표시됩니다. 다른 부서 계약은 보안팀(관리자)에게 문의하세요.</div>`
      : `<div class="pi-dept-scope alert alert-warning py-2 px-3 mb-3" id="dept-scope-banner" role="alert"><i class="fas fa-triangle-exclamation me-1"></i>
          사용자 정보에 <b>소속부서</b>가 지정되지 않아 볼 수 있는 계약이 없습니다. 관리자에게 사용자 관리에서 부서 지정을 요청하세요.</div>`;
  };
  const dLabel = (days) => (days == null ? '-' : days < 0 ? `만료 ${-days}일 경과` : days === 0 ? '오늘 만료' : `D-${days}`);

  function notify(message, type = 'success') {
    const id = 'n' + Date.now();
    alertArea.insertAdjacentHTML('beforeend',
      `<div id="${id}" class="alert alert-${type} alert-dismissible fade show" role="alert" style="white-space:pre-line">${esc(message)}
         <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="닫기"></button></div>`);
    setTimeout(() => document.getElementById(id)?.remove(), type === 'danger' ? 8000 : 4000);
  }
  const fail = (e) => notify(e.message || String(e), 'danger');
  const go = (hash) => { location.hash = hash; };
  const modal = (id) => bootstrap.Modal.getOrCreateInstance(document.getElementById(id));

  // ──────────────── 라우터 ────────────────
  async function route() {
    const hash = location.hash || '#/contracts';
    let m;
    try {
      if (hash === '#/contracts' || hash === '#/' || hash === '') return await renderList();
      if (hash === '#/contracts/new') return renderForm(null);
      if ((m = hash.match(/^#\/contracts\/new\?vendor=(.*)$/))) return renderForm(null, decodeURIComponent(m[1]));
      if ((m = hash.match(/^#\/contracts\/(\d+)\/edit$/))) return renderForm(await PI_API.getContract(m[1]));
      if ((m = hash.match(/^#\/contracts\/(\d+)$/))) return await renderDetail(m[1]);
      if (hash === '#/forms') return await renderForms();
      if (hash === '#/access-logs') return await renderAccessLogs();
      go('#/contracts');
    } catch (e) {
      view.innerHTML = `<div class="pi-card pi-empty"><i class="fas fa-triangle-exclamation me-1"></i>${esc(e.message)}</div>`;
    }
  }

  // ──────────────── 1. 목록 화면 ────────────────
  async function renderList() {
    crumb.innerHTML = '계약 목록';
    const c = listState.cond;
    view.innerHTML = `${deptScopeBanner()}
      <section id="pi-dashboard" class="pi-stats"></section>
      <section class="pi-card" id="contract-search-section">
        <form id="contract-search-form" class="row g-2 align-items-end">
          <div class="col-md-3"><label class="form-label small" for="s-keyword">통합검색</label>
            <input class="form-control form-control-sm" id="s-keyword" name="keyword" placeholder="수탁업체/계약명/서비스/위탁업무/재위탁업체" value="${esc(c.keyword)}"></div>
          <div class="col-md-2"><label class="form-label small" for="s-dept">담당부서</label>
            ${deptScoped()
              ? `<input class="form-control form-control-sm" id="s-dept" value="${esc(perm.deptName || '-')}" disabled title="소속부서 계약만 조회됩니다">`
              : `<input class="form-control form-control-sm" id="s-dept" name="deptName" value="${esc(c.deptName)}">`}</div>
          <div class="col-md-1"><label class="form-label small" for="s-manager">담당자</label>
            <input class="form-control form-control-sm" id="s-manager" name="managerName" value="${esc(c.managerName)}"></div>
          <div class="col-md-1"><label class="form-label small" for="s-status">계약상태</label>
            <select class="form-select form-select-sm" id="s-status" name="status">
              ${opt('', '전체', c.status)}${opt('ACTIVE', '계약중', c.status)}${opt('EXPIRING', '만료임박', c.status)}
              ${opt('EXPIRED', '만료', c.status)}${opt('TERMINATED', '종료', c.status)}${opt('NO_END_DATE', '기간미정', c.status)}
            </select></div>
          <div class="col-md-1"><label class="form-label small" for="s-isms">ISMS</label>
            <select class="form-select form-select-sm" id="s-isms" name="ismsScopeYn">${opt('', '전체', c.ismsScopeYn)}${opt('Y', 'Y', c.ismsScopeYn)}${opt('N', 'N', c.ismsScopeYn)}</select></div>
          <div class="col-md-1"><label class="form-label small" for="s-insp">점검결과</label>
            <select class="form-select form-select-sm" id="s-insp" name="inspectionResult">
              ${opt('', '전체', c.inspectionResult)}${opt('PASS', '적합', c.inspectionResult)}${opt('NEED_ACTION', '조치 필요', c.inspectionResult)}
              ${opt('INCOMPLETE', '평가 미완료', c.inspectionResult)}${opt('NONE', '미점검', c.inspectionResult)}
            </select></div>
          <div class="col-md-3"><label class="form-label small">계약 종료일</label>
            <div class="input-group input-group-sm">
              <input type="date" class="form-control" name="endDateFrom" aria-label="종료일 시작" value="${esc(c.endDateFrom)}">
              <span class="input-group-text">~</span>
              <input type="date" class="form-control" name="endDateTo" aria-label="종료일 끝" value="${esc(c.endDateTo)}"></div></div>
          <div class="col-12 d-flex justify-content-between mt-2">
            <div class="pi-actions">
              <button type="submit" class="btn btn-sm btn-primary"><i class="fas fa-search me-1"></i>검색</button>
              <button type="button" class="btn btn-sm btn-light" id="btn-reset">초기화</button>
            </div>
            <div class="pi-actions">
              <button type="button" class="btn btn-sm btn-outline-secondary" id="btn-template"><i class="fas fa-file-excel me-1"></i>점검 체크리스트 양식</button>
              ${perm.canManageForms ? '<button type="button" class="btn btn-sm btn-outline-dark" id="btn-access-logs" title="계약·점검 조회와 계약서·엑셀 다운로드 기록"><i class="fas fa-user-shield me-1"></i>접근 기록</button>' : ''}
              ${perm.canManageForms ? '<button type="button" class="btn btn-sm btn-outline-dark" id="btn-forms" title="보안팀 양식(체크리스트·확약서·파기 확인서) 등록·교체"><i class="fas fa-folder-tree me-1"></i>양식 관리</button>' : ''}
              <button type="button" class="btn btn-sm btn-outline-success" id="btn-excel"><i class="fas fa-download me-1"></i>엑셀 다운로드</button>
              ${perm.canDelete ? '<button type="button" class="btn btn-sm btn-outline-warning" id="btn-run-alert"><i class="fas fa-bell me-1"></i>알림 실행</button>' : ''}
              ${canCreate() ? '<button type="button" class="btn btn-sm btn-primary" id="btn-new"><i class="fas fa-plus me-1"></i>계약 등록</button>' : ''}
            </div>
          </div>
        </form>
      </section>
      <section class="pi-card" id="contract-list-section">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h2 class="mb-0"><i class="fas fa-list"></i>위수탁 계약 목록 <span class="pi-year-filter" id="list-year-filter">${esc(yearFilterLabel())}</span> <small class="text-muted fw-normal" id="list-count"></small></h2>
          <div class="d-flex gap-2 align-items-center">
          <span id="contract-col-tools"></span>
          <select class="form-select form-select-sm w-auto" id="s-sort" aria-label="정렬">
            ${opt('contractEndDate,asc', '종료일 빠른순', listState.sort)}${opt('contractEndDate,desc', '종료일 늦은순', listState.sort)}
            ${opt('vendorName,asc', '업체명순', listState.sort)}${opt('contractName,asc', '계약명순', listState.sort)}${opt('createdAt,desc', '최근 등록순', listState.sort)}
            ${opt('lastInspectionDate,asc', '점검일 오래된순', listState.sort)}
          </select>
          </div>
        </div>
        <p class="pi-col-hint text-muted mb-1"><i class="fas fa-circle-info me-1"></i>열 제목을 끌어 <b>좌우 순서</b>를 바꾸고, 제목 오른쪽 경계를 끌어 <b>너비</b>를 조정할 수 있습니다(경계 더블클릭: 그 열만 기본값). <b>[열 설정]</b>에서 ◀ ▶ 이동·너비 입력·초기화도 됩니다. 설정은 이 브라우저에 사용자별로 저장됩니다.</p>
        <div class="table-responsive pi-resizable-wrap"><table class="pi-table pi-resizable" id="contract-table">
          <thead><tr>${listCols().map((c) => `<th data-col="${c.key}" data-label="${esc(c.label)}" title="${esc(c.label)}"${c.key === 'action' ? ' class="pi-col-sticky-end"' : ''}><span class="pi-th-label">${esc(c.label)}</span></th>`).join('')}</tr></thead>
          <tbody><tr><td colspan="${listCols().length}" class="pi-empty">불러오는 중...</td></tr></tbody>
        </table></div>
        <nav class="d-flex justify-content-center mt-3" id="contract-pager" aria-label="페이지"></nav>
      </section>`;

    const form = document.getElementById('contract-search-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const keep = { yearStatus: listState.cond.yearStatus, yearCheck: listState.cond.yearCheck, followUp: listState.cond.followUp };
      listState.cond = { ...Object.fromEntries(new FormData(form).entries()), ...keep };
      listState.page = 0;
      loadList();
    });
    document.getElementById('btn-reset').onclick = () => { listState.cond = defaultCond(); listState.page = 0; renderList(); };
    document.getElementById('btn-excel').onclick = () => askDownloadReason('계약 목록 엑셀',
      (reason) => PI_API.downloadExcel({ ...listState.cond, baseYear: listState.baseYear, sort: listState.sort }, reason));
    document.getElementById('btn-template').onclick = () => PI_API.downloadBlankTemplate().catch(fail);
    document.getElementById('s-sort').onchange = (e) => { listState.sort = e.target.value; listState.page = 0; loadList(); };
    document.getElementById('btn-new')?.addEventListener('click', () => go('#/contracts/new'));
    document.getElementById('btn-run-alert')?.addEventListener('click', runAlerts);
    document.getElementById('btn-forms')?.addEventListener('click', () => go('#/forms'));
    document.getElementById('btn-access-logs')?.addEventListener('click', () => go('#/access-logs'));
    PiTable.enhance(document.getElementById('contract-table'),
      { id: 'contracts', cols: listCols(), tools: 'contract-col-tools', legacyWidthKey: COL_WIDTH_KEY });

    loadDashboard();
    await loadList();
  }

  function opt(value, label, selected) {
    return `<option value="${esc(value)}" ${String(selected ?? '') === value ? 'selected' : ''}>${esc(label)}</option>`;
  }

  /**
   * 목록 상단 요약 (기준 연도 기준)
   *  - 기준일   : 지난 연도 12/31, 올해는 오늘(조회 시점), 앞으로 올 연도 1/1
   *  - 전체 계약 : 시작일 ≤ 연말, 종료 기준일(종료 처리일 또는 원계약 종료일) ≥ 전년도 12/31
   *  - 계약중   : 기준일에 계약기간 안 + 수탁사 정기 점검 완료 건수
   *  - 종료     : 기준일에 계약기간 밖 + 계약종결 체크(계약변경·만료 점검) 업로드 여부
   *    (예: 2025.12.31 종료 → 2025년 계약중, 2026년 종료)
   * 카드·작은 숫자를 누르면 그 조건으로 목록을 거른다.
   */
  async function loadDashboard() {
    try {
      const d = await PI_API.dashboard({ baseYear: listState.baseYear });
      const el = document.getElementById('pi-dashboard');
      if (!el) return;
      const cur = listState.cond;
      const on = (ys, yc) => (cur.yearStatus || '') === ys && (cur.yearCheck || '') === yc && !cur.status && !cur.inspectionResult && !cur.followUp;
      const years = (d.availableYears || [d.baseYear]);
      el.className = 'pi-stats pi-year-stats';
      el.innerHTML = `
        <div class="pi-stat pi-year-select" id="base-year-card">
          <label class="label" for="base-year">기준 연도</label>
          <select class="form-select form-select-sm fw-bold" id="base-year" aria-label="기준 연도">
            ${years.map((y) => `<option value="${y}" ${y === d.baseYear ? 'selected' : ''}>${y}년</option>`).join('')}</select>
          <div class="pi-sub text-muted" title="지난 연도는 12월 31일, 올해는 오늘 기준으로 계약기간 안이면 계약중, 밖이면 종료">기준일 ${fmtDate(d.refDate)} · 계약기간 안이면 계약중</div>
        </div>
        <div class="pi-stat ${on('ACTIVE', '') ? 'active' : ''}" data-ys="ACTIVE" data-yc="" role="button" tabindex="0" id="stat-year-active">
          <div class="label">계약중</div><div class="value" style="color:#166534">${d.yearActiveCount}</div>
          <div class="pi-sub"><button type="button" class="pi-sub-link ${on('ACTIVE', 'INSPECTED') ? 'active' : ''}" data-ys="ACTIVE" data-yc="INSPECTED"
              title="${d.baseYear}년 수탁사 정기 점검을 완료한 계약">수탁사 점검완료 <b>${d.yearActiveInspectedCount}</b>건</button>
            <span class="text-muted">/ ${d.yearActiveCount}</span>
            ${d.yearActiveCount - d.yearActiveInspectedCount > 0 ? `<button type="button" class="pi-sub-link warn ${on('ACTIVE', 'NOT_INSPECTED') ? 'active' : ''}" data-ys="ACTIVE" data-yc="NOT_INSPECTED" title="${d.baseYear}년 수탁사 정기 점검 미완료">미점검 ${d.yearActiveCount - d.yearActiveInspectedCount}</button>` : ''}</div>
        </div>
        <div class="pi-stat ${on('', '') ? 'active' : ''}" data-ys="" data-yc="" role="button" tabindex="0" id="stat-year-total">
          <div class="label">전체 계약</div><div class="value" style="color:#111827">${d.yearTotalCount}</div>
          <div class="pi-sub text-muted">계약중 ${d.yearActiveCount} · 종료 ${d.yearEndedCount}</div>
        </div>
        <div class="pi-stat ${on('ENDED', '') ? 'active' : ''}" data-ys="ENDED" data-yc="" role="button" tabindex="0" id="stat-year-ended">
          <div class="label">종료</div><div class="value" style="color:#374151">${d.yearEndedCount}</div>
          <div class="pi-sub">계약종결 체크
            <button type="button" class="pi-sub-link ok ${on('ENDED', 'TERM_UPLOADED') ? 'active' : ''}" data-ys="ENDED" data-yc="TERM_UPLOADED" title="계약종결 체크(계약변경·만료 점검) 업로드">업로드 <b>${d.yearEndedTermCheckCount}</b></button>
            <button type="button" class="pi-sub-link ${d.yearEndedTermMissingCount ? 'warn' : ''} ${on('ENDED', 'TERM_MISSING') ? 'active' : ''}" data-ys="ENDED" data-yc="TERM_MISSING" title="계약종결 체크(계약변경·만료 점검) 미업로드">미업로드 <b>${d.yearEndedTermMissingCount}</b></button></div>
        </div>`;
      // 보안팀 확인 필요 카드 (기준 연도와 상관없이 현재 기준)
      const fuCards = [
        ['EXPIRY_DOCS_MISSING', '만료 서류 미수취', d.expiryDocsMissingCount, 'fa-file-circle-exclamation', '종료·만료(임박) 계약 중 만료 점검 체크리스트·확약서·파기 확인서를 다 받지 못한 계약'],
        ['ACTION_OVERDUE', '조치 지연', d.actionOverdueCount, 'fa-hourglass-end', '점검 결과 조치 필요 항목의 조치 예정일이 지났는데 조치 완료가 아닌 계약'],
        ['CONSENT_MISSING', '재위탁 동의서 누락', d.consentMissingCount, 'fa-file-contract', '재위탁 Y 인데 재위탁 동의서 파일이 없는 계약'],
        ['DESTRUCTION_LATE', '파기 지연', d.destructionLateCount, 'fa-clock-rotate-left', '파기 확인서의 파기 완료일이 파기 기한(계약 종료 + 5일, 표준 개인정보 보호지침 제10조)보다 늦은 계약'],
      ];
      document.getElementById('pi-followup-bar')?.remove();
      el.insertAdjacentHTML('afterend', `<section class="pi-followup-bar" id="pi-followup-bar" aria-label="보안팀 확인 필요">
        <span class="pi-fu-title"><i class="fas fa-shield-halved me-1"></i>확인 필요</span>
        ${fuCards.map(([k, label, n, icon, tip]) => `<button type="button" class="pi-fu-card ${n ? 'warn' : ''} ${cur.followUp === k ? 'active' : ''}" data-fu="${k}" title="${esc(tip)}">
          <i class="fas ${icon}"></i>${esc(label)} <b>${n}</b></button>`).join('')}
        <small class="text-muted ms-auto">기준 연도와 상관없이 오늘 기준</small></section>`);
      document.getElementById('pi-followup-bar').querySelectorAll('[data-fu]').forEach((b) => b.addEventListener('click', () => {
        listState.cond = cur.followUp === b.dataset.fu ? defaultCond() : { followUp: b.dataset.fu };
        listState.page = 0;
        renderList();
      }));
      const apply = (ys, yc) => {
        listState.cond = {};
        if (ys) listState.cond.yearStatus = ys;
        if (yc) listState.cond.yearCheck = yc;
        listState.page = 0;
        renderList();
      };
      el.querySelectorAll('.pi-stat[data-ys]').forEach((card) => {
        card.addEventListener('click', () => apply(card.dataset.ys, card.dataset.yc));
        card.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); apply(card.dataset.ys, card.dataset.yc); } });
      });
      el.querySelectorAll('.pi-sub-link').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); apply(b.dataset.ys, b.dataset.yc); }));
      document.getElementById('base-year').addEventListener('change', (e) => {
        listState.baseYear = Number(e.target.value);
        listState.page = 0;
        renderList();
      });
    } catch (e) { /* 요약 실패는 목록 사용에 영향 없음 */ }
  }

  /** 현재 카드 필터 안내 (목록 제목 옆) */
  const FOLLOW_UP_LABEL = { EXPIRY_DOCS_MISSING: '만료 서류 미수취', ACTION_OVERDUE: '조치 지연', CONSENT_MISSING: '재위탁 동의서 누락', DESTRUCTION_LATE: '파기 지연' };
  function yearFilterLabel() {
    const c = listState.cond; const y = listState.baseYear;
    if (c.followUp) return `확인 필요 · ${FOLLOW_UP_LABEL[c.followUp] || c.followUp} (오늘 기준)`;
    const map = { '': '전체', ACTIVE: '계약중', ENDED: '종료' };
    const check = { INSPECTED: '수탁사 점검완료', NOT_INSPECTED: '수탁사 미점검', TERM_UPLOADED: '계약종결 체크 업로드', TERM_MISSING: '계약종결 체크 미업로드' };
    return `${y}년 · ${map[c.yearStatus || '']}${c.yearCheck ? ' · ' + check[c.yearCheck] : ''}`;
  }

  // ──────────────── 목록 열 정의 (너비·순서 편집은 personalinfo-table.js) ────────────────
  // key: 저장 키, label: 제목, width: 기본 너비(px), min: 최소 너비(px)
  const CONTRACT_COLS = [
    { key: 'no', label: 'No', width: 52, min: 40 },
    { key: 'isms', label: 'ISMS', width: 78, min: 64 },
    { key: 'vendor', label: '수탁업체명', width: 170, min: 90 },
    { key: 'cname', label: '계약명', width: 170, min: 80 },
    { key: 'service', label: '관련 서비스', width: 110, min: 70 },
    { key: 'task', label: '위탁업무 내용', width: 240, min: 90 },
    { key: 'dept', label: '담당부서', width: 110, min: 70 },
    { key: 'manager', label: '담당자', width: 80, min: 72 },
    { key: 'period', label: '계약기간', width: 190, min: 90 },
    { key: 'dday', label: '만료', width: 72, min: 60 },
    { key: 'status', label: '계약상태', width: 88, min: 70 },
    { key: 'sub', label: '재위탁', width: 78, min: 64 },
    { key: 'insp', label: '최근 점검', width: 160, min: 80 },
  ];
  /** 삭제 권한(관리자)에게만 보이는 '관리' 열 */
  const DELETE_COL = { key: 'action', label: '관리', width: 64, min: 56 };
  const listCols = () => (perm.canDelete ? CONTRACT_COLS.concat(DELETE_COL) : CONTRACT_COLS);
  /** 이전 버전 계약 목록 열 너비 저장 키 (처음 한 번 이어받기) */
  const COL_WIDTH_KEY = 'personalinfo.contractList.colWidths.v1';

  async function loadList() {
    const tbody = document.querySelector('#contract-table tbody');
    try {
      const page = await PI_API.listContracts({ ...listState.cond, baseYear: listState.baseYear, page: listState.page, size: listState.size, sort: listState.sort });
      document.getElementById('list-count').textContent = `(총 ${page.totalElements}건)`;
      if (!page.content.length) {
        tbody.innerHTML = `<tr><td colspan="${listCols().length}" class="pi-empty">조회된 계약이 없습니다.</td></tr>`;
      } else {
        tbody.innerHTML = page.content.map((r, i) => `
          <tr data-id="${r.contractId}">
            <td class="pi-cell">${page.page * page.size + i + 1}</td>
            <td class="pi-cell pi-flag-cell">${flagCell(r, 'ismsScopeYn', 'ISMS 인증 여부')}</td>
            <td class="pi-cell fw-semibold" title="${esc(r.vendorName)}"><div class="pi-vendor-cell">${perm.canWrite
              ? `<button type="button" class="pi-vendor-link" data-vendor="${esc(r.vendorName)}" title="업체별 점검 체크리스트 업로드">${esc(r.vendorName)}</button>`
              : `<span class="pi-vendor-name">${esc(r.vendorName)}</span>`}${r.sameVendorContractCount > 1
              ? `<button type="button" class="pi-vendor-count" data-vendor="${esc(r.vendorName)}" title="이 업체의 계약 ${r.sameVendorContractCount}건 보기">계약 ${r.sameVendorContractCount}건</button>` : ''}</div></td>
            <td class="pi-cell" title="${esc(r.contractName || '')}">${r.contractName ? esc(r.contractName) : '<span class="text-muted">-</span>'}</td>
            <td class="pi-cell" title="${esc(r.serviceName || '')}">${esc(r.serviceName || '-')}</td>
            <td class="pi-cell" title="${esc(r.entrustedTask)}">${esc(r.entrustedTask)}</td>
            <td class="pi-cell" title="${esc(r.deptName || '')}">${esc(r.deptName || '-')}</td>
            <td class="pi-cell" title="${esc(r.managerName || '')}">${esc(r.managerName || '-')}</td>
            <td class="pi-cell" ${r.autoRenewing && r.autoRenewCount > 0 ? `title="원계약 기간 ${fmtDate(r.contractStartDate)} ~ ${fmtDate(r.contractEndDate)} · ${renewLabel(r.autoRenewPeriodMonths)} 단위 자동연장 ${r.autoRenewCount}회"` : ''}>${r.contractEndDate || r.contractStartDate
              ? fmtDate(r.contractStartDate) + ' ~ ' + fmtDate(r.currentEndDate || r.contractEndDate)
                + (r.autoRenewing && r.autoRenewCount > 0 ? ` <span class="pi-term-chip renew" title="자동연장 ${r.autoRenewCount}회 반영"><i class="fas fa-rotate"></i>연장${r.autoRenewCount}</span>` : '')
              : esc(r.contractPeriodNote || '-')}</td>
            <td class="pi-cell">${r.status === 'TERMINATED' ? '-' : dLabel(r.daysLeft)}${r.autoRenewing ? ' <small class="text-info-emphasis" title="다음 자동연장일까지 — 만료 대상 아님">자동연장</small>' : ''}</td>
            <td class="pi-cell">${badge(r.status, r.statusLabel)}${(r.status === 'TERMINATED' || r.status === 'EXPIRED') && r.terminationCheckYn
              ? (r.terminationCheckYn === 'Y'
                ? ' <span class="pi-term-chip ok" title="계약종결 체크(계약변경·만료 점검) 업로드됨"><i class="fas fa-check"></i>종결체크</span>'
                : ' <span class="pi-term-chip miss" title="계약종결 체크(계약변경·만료 점검) 미업로드"><i class="fas fa-xmark"></i>종결체크</span>')
              : ''}${followUpChips(r)}</td>
            <td class="pi-cell pi-flag-cell">${flagCell(r, 'subcontractYn', '재위탁 여부')}</td>
            <td class="pi-cell">${badge(r.lastInspectionResult || 'NONE', r.lastInspectionResultLabel)} <small class="text-muted">${r.lastInspectionDate ? fmtDate(r.lastInspectionDate) : ''}</small></td>
            ${perm.canDelete ? `<td class="pi-cell text-center pi-col-sticky-end"><button type="button" class="btn btn-sm btn-light text-danger pi-row-delete"
                data-id="${r.contractId}" data-vendor="${esc(r.vendorName)}" data-service="${esc(r.serviceName || '')}"
                title="계약 삭제" aria-label="${esc(r.vendorName)} 계약 삭제"><i class="fas fa-trash"></i></button></td>` : ''}
          </tr>`).join('');
        tbody.querySelectorAll('tr[data-id]').forEach((tr) => tr.addEventListener('click', () => go('#/contracts/' + tr.dataset.id)));
        tbody.querySelectorAll('.pi-row-delete').forEach((btn) => btn.addEventListener('click', (e) => {
          e.stopPropagation();   // 행 클릭(상세 이동) 막기
          deleteContractFromList(btn);
        }));
        tbody.querySelectorAll('.pi-flag').forEach((el) => {
          el.addEventListener('click', (e) => e.stopPropagation());   // 행 클릭(상세 이동) 막기
          el.addEventListener('change', () => toggleFlag(el));
        });
        tbody.querySelectorAll('.pi-vendor-link').forEach((b) => b.addEventListener('click', (e) => {
          e.stopPropagation();   // 행 클릭(상세 이동) 대신 팝업
          openVendorChecklist(b.dataset.vendor);
        }));
        tbody.querySelectorAll('.pi-vendor-count').forEach((b) => b.addEventListener('click', (e) => {
          e.stopPropagation();   // 같은 업체 계약 목록 팝업
          openVendorContracts(b.dataset.vendor);
        }));
      }
      PiTable.refresh(document.getElementById('contract-table'));   // 사용자 열 순서 적용
      renderPager(page);
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="${listCols().length}" class="pi-empty">${esc(e.message)}</td></tr>`;
    }
  }

  /** 목록 상태 칸의 확인 필요 표시 */
  function followUpChips(r) {
    const out = [];
    if (r.expiryDocsNeeded && r.expiryDocsReceived != null) {
      out.push(r.expiryDocsReceived >= 3
        ? '<span class="pi-term-chip ok" title="만료 서류 3종(만료 점검 체크리스트·확약서·파기 확인서) 모두 수취"><i class="fas fa-check"></i>만료서류 3/3</span>'
        : `<span class="pi-term-chip miss" title="만료 서류 3종 중 ${r.expiryDocsReceived}종 수취"><i class="fas fa-file-circle-exclamation"></i>만료서류 ${r.expiryDocsReceived}/3</span>`);
    }
    if (r.actionOverdueCount) out.push(`<span class="pi-term-chip miss" title="조치 예정일이 지난 점검 ${r.actionOverdueCount}건"><i class="fas fa-hourglass-end"></i>조치지연</span>`);
    if (r.consentMissing) out.push('<span class="pi-term-chip warn" title="재위탁 Y 인데 재위탁 동의서 파일 없음"><i class="fas fa-file-contract"></i>동의서 누락</span>');
    if (r.confirmReplyOverdue) out.push('<span class="pi-term-chip miss" title="계약 종료 + 30일(확인서 회신 기한)이 지났는데 파기 확인서 미수취"><i class="fas fa-envelope-open-text"></i>파기확인서 회신지연</span>');
    if (r.destructionLate) out.push('<span class="pi-term-chip warn" title="파기 완료일이 파기 기한(계약 종료 + 5일)보다 늦음"><i class="fas fa-clock-rotate-left"></i>파기지연</span>');
    return out.length ? '<div class="pi-fu-chips">' + out.join('') + '</div>' : '';
  }

  /** 다운로드 사유 입력 → 사유를 넘겨 다운로드 (서버가 사유와 함께 접근 기록을 남김) */
  let reasonAction = null;
  function askDownloadReason(title, action) {
    reasonAction = action;
    document.getElementById('download-reason-title').innerHTML = `<i class="fas fa-file-shield me-2 text-primary"></i>${esc(title)} — 다운로드 사유`;
    const inp = document.getElementById('download-reason');
    inp.value = ''; inp.classList.remove('is-invalid');
    modal('download-reason-modal').show();
    setTimeout(() => inp.focus(), 300);
  }
  document.getElementById('download-reason-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const inp = document.getElementById('download-reason');
    const reason = inp.value.trim();
    if (reason.length < 2) { inp.classList.add('is-invalid'); inp.focus(); return; }
    const btn = document.getElementById('download-reason-ok'); btn.disabled = true;
    try { await reasonAction(reason); modal('download-reason-modal').hide(); notify('다운로드했습니다. 사유가 접근 기록에 남았습니다.'); }
    catch (err) { fail(err); } finally { btn.disabled = false; }
  });

  // 목록에서 계약 삭제 (삭제 권한자만 버튼 노출, 서버도 CAN_DELETE 로 재확인)
  async function deleteContractFromList(btn) {
    const name = btn.dataset.vendor + (btn.dataset.service ? ` (${btn.dataset.service})` : '');
    if (!confirm(`'${name}' 계약을 삭제합니다.\n첨부파일·점검 이력도 함께 숨겨지며, 목록에서 사라집니다.`)) return;
    btn.disabled = true;
    try {
      await PI_API.deleteContract(btn.dataset.id);
      notify(`'${name}' 계약을 삭제했습니다.`);
      loadDashboard();
      await loadList();
    } catch (e) {
      btn.disabled = false;
      fail(e);
    }
  }

  // ISMS 인증 여부 / 재위탁 여부: 등록 권한이 있으면 목록에서 바로 Y/N 전환
  function flagCell(r, field, label) {
    const v = r[field] === 'Y';
    const tip = field === 'subcontractYn' && v && r.subcontractorName ? ` title="재위탁: ${esc(r.subcontractorName)}"` : '';
    if (!perm.canWrite) return `<span${tip}>${yn(r[field])}</span>`;
    return `<label class="pi-flag form-check form-switch mb-0"${tip}>
        <input class="form-check-input" type="checkbox" role="switch" data-id="${r.contractId}" data-field="${field}"
          data-vendor="${esc(r.vendorName)}" data-label="${label}" ${v ? 'checked' : ''} aria-label="${esc(r.vendorName)} ${label}">
        <span class="pi-flag-text">${v ? 'Y' : 'N'}</span></label>`;
  }

  async function toggleFlag(label) {
    const input = label.querySelector('input');
    const value = input.checked ? 'Y' : 'N';
    const text = label.querySelector('.pi-flag-text');
    input.disabled = true;
    try {
      const r = await PI_API.updateContractFlags(input.dataset.id, { [input.dataset.field]: value });
      text.textContent = value;
      notify(`${input.dataset.vendor} · ${input.dataset.label}: ${value} 로 변경했습니다.`);
      if (input.dataset.field === 'subcontractYn') {
        if (value === 'Y' && !r.subcontractorName) {
          notify(`${input.dataset.vendor}: 재위탁 업체명·업무가 비어 있습니다. 계약 수정 화면에서 입력하세요.`, 'warning');
        }
        label.title = value === 'Y' && r.subcontractorName ? '재위탁: ' + r.subcontractorName : '';
      }
    } catch (e) {
      input.checked = !input.checked;   // 실패하면 원래 값으로 되돌림
      text.textContent = input.checked ? 'Y' : 'N';
      fail(e);
    } finally {
      input.disabled = false;
    }
  }

  function renderPager(page) {
    const nav = document.getElementById('contract-pager');
    if (page.totalPages <= 1) { nav.innerHTML = ''; return; }
    const start = Math.max(0, page.page - 4);
    const end = Math.min(page.totalPages - 1, start + 9);
    let html = '<ul class="pagination pagination-sm mb-0">';
    html += `<li class="page-item ${page.first ? 'disabled' : ''}"><a class="page-link" href="#" data-p="${page.page - 1}">이전</a></li>`;
    for (let p = start; p <= end; p++) html += `<li class="page-item ${p === page.page ? 'active' : ''}"><a class="page-link" href="#" data-p="${p}">${p + 1}</a></li>`;
    html += `<li class="page-item ${page.last ? 'disabled' : ''}"><a class="page-link" href="#" data-p="${page.page + 1}">다음</a></li></ul>`;
    nav.innerHTML = html;
    nav.querySelectorAll('a[data-p]').forEach((a) => a.addEventListener('click', (e) => {
      e.preventDefault();
      const p = Number(a.dataset.p);
      if (p >= 0 && p < page.totalPages) { listState.page = p; loadList(); }
    }));
  }

  async function runAlerts() {
    if (!confirm('오늘 기준 알림 메일을 실행합니다.\n(만료 알림, 만료 서류 독촉, 조치 지연, 정기 점검 요청, 자동연장 확인 — 이미 보낸 알림은 다시 보내지 않습니다)')) return;
    try {
      const r = await PI_API.runAlerts();
      notify(`알림 실행 완료\n· 만료 알림: 대상 ${r.candidateCount}건 / 발송 ${r.sentCount} / 실패 ${r.failedCount} / 미발송 ${r.skippedCount} / 기발송 ${r.alreadySentCount}\n· 만료 서류 독촉 ${r.docsReminderCount}건 · 조치 지연 ${r.actionOverdueCount}건 · 정기 점검 요청 ${r.regularInspectionCount}건${r.regularSummaryCount ? ' (관리자 요약 발송)' : ''} · 자동연장 확인 ${r.autoRenewNoticeCount}건`);
    } catch (e) { fail(e); }
  }

  /** 자동연장 기간(개월) → 입력칸 값/단위 (12의 배수면 년) */
  function renewParts(months) {
    const m = Number(months) || 12;
    return m % 12 === 0 ? { value: m / 12, unit: 'Y' } : { value: m, unit: 'M' };
  }
  /** 상세 '현재 계약기간' (자동연장 반영) */
  function currentPeriodText(c) {
    if (!c.contractEndDate) return '<span class="text-muted">-</span>';
    const p = `${fmtDate(c.contractStartDate)} ~ <b>${fmtDate(c.currentEndDate || c.contractEndDate)}</b>`;
    if (c.terminatedYn === 'Y') return p + ` <small class="text-muted">(종료 처리 ${fmtDate(c.terminatedDate)})</small>`;
    if (c.autoRenewing && c.autoRenewCount > 0) return p + ` <span class="pi-term-chip renew"><i class="fas fa-rotate"></i>자동연장 ${c.autoRenewCount}회 반영</span>`;
    if (c.autoRenewing) return p + ` <small class="text-muted">(원계약 기간과 같음 · ${fmtDate(c.contractEndDate)} 이후 ${renewLabel(c.autoRenewPeriodMonths)} 단위 자동연장)</small>`;
    return p + ' <small class="text-muted">(원계약 기간과 같음)</small>';
  }
  /** 자동연장 기간 표시 (예: 1년, 6개월) */
  function renewLabel(months) {
    const p = renewParts(months);
    return p.value + (p.unit === 'Y' ? '년' : '개월');
  }

  // ──────────────── 2·4. 등록 / 수정 화면 ────────────────
  function renderForm(c, presetVendor) {
    if (!perm.canWrite) { notify('등록/수정 권한이 없습니다.', 'warning'); go(c ? '#/contracts/' + c.contractId : '#/contracts'); return; }
    if (!canCreate()) { notify('소속부서가 지정되지 않아 계약을 등록·수정할 수 없습니다. 관리자에게 부서 지정을 요청하세요.', 'warning'); go('#/contracts'); return; }
    const isEdit = !!c;
    const v = c || { ismsScopeYn: 'Y', autoRenewYn: 'N', autoRenewPeriodMonths: 12, contractDocYn: 'N', subcontractYn: 'N', alertYn: 'Y', alertDaysBefore: 30, vendorName: presetVendor || '' };
    crumb.innerHTML = `<a href="#/contracts">계약 목록</a> &gt; ${isEdit ? `<a href="#/contracts/${c.contractId}">${esc(c.vendorName)}</a> &gt; 수정` : '계약 등록'}`;
    const radio = (name, value) => `
      <div class="btn-group btn-group-sm" role="group" aria-label="${name}">
        <input type="radio" class="btn-check" name="${name}" id="${name}-Y" value="Y" ${value === 'Y' ? 'checked' : ''}><label class="btn btn-outline-primary" for="${name}-Y">Y</label>
        <input type="radio" class="btn-check" name="${name}" id="${name}-N" value="N" ${value !== 'Y' ? 'checked' : ''}><label class="btn btn-outline-primary" for="${name}-N">N</label>
      </div>`;
    const input = (name, label, value, attrs = '') =>
      `<label class="form-label ${attrs.includes('required') ? 'pi-required' : ''}" for="f-${name}">${label}</label>
       <input class="form-control" id="f-${name}" name="${name}" value="${esc(value)}" ${attrs}>`;

    view.innerHTML = `
      <section class="pi-card" id="contract-form-section">
        <h2><i class="fas ${isEdit ? 'fa-pen' : 'fa-plus'}"></i>${isEdit ? '위수탁 계약 수정' : '위수탁 계약 등록'}</h2>
        <form id="contract-form" novalidate>
          <div class="row g-3">
            <div class="col-md-4">${input('vendorName', '수탁업체명', v.vendorName, 'required maxlength="200"')}</div>
            <div class="col-md-4">${input('contractName', '계약명', v.contractName, 'required maxlength="200" placeholder="예: 본사 물류 위탁계약, 부산지사 물류 위탁계약"')}</div>
            <div class="col-md-2">${input('serviceName', '관련 서비스', v.serviceName, 'maxlength="200" placeholder="예: 자사몰, OMS"')}</div>
            <div class="col-md-2"><label class="form-label d-block">ISMS 범위</label>${radio('ismsScopeYn', v.ismsScopeYn)}</div>
            <div class="col-12 d-none" id="same-vendor-hint" role="status" aria-live="polite"></div>
            <div class="col-12"><label class="form-label pi-required" for="f-entrustedTask">위탁업무 내용</label>
              <textarea class="form-control" id="f-entrustedTask" name="entrustedTask" rows="2" maxlength="1000" required>${esc(v.entrustedTask)}</textarea></div>
          </div>

          <div class="pi-form-section mt-3"><h3>당사 담당 / 수탁사 담당</h3>
            <div class="row g-3">
              <div class="col-md-3">${deptScoped()
                ? input('deptName', '담당부서', v.deptName || perm.deptName, 'maxlength="100" readonly aria-describedby="dept-lock-help"')
                  + '<div class="form-text" id="dept-lock-help"><i class="fas fa-lock fa-xs me-1"></i>소속부서로 고정됩니다. 다른 부서 계약은 관리자에게 요청하세요.</div>'
                : input('deptName', '담당부서', v.deptName, 'maxlength="100"')}</div>
              <div class="col-md-3">${input('managerName', '계약 담당자', v.managerName, 'maxlength="100"')}</div>
              <div class="col-md-6">${input('managerEmail', '담당자 이메일 (만료 알림 수신)', v.managerEmail, 'type="email" maxlength="200"')}</div>
              <div class="col-md-3">${input('vendorContactName', '수탁사 담당자', v.vendorContactName, 'maxlength="100"')}</div>
              <div class="col-md-3">${input('vendorContactPhone', '수탁사 연락처', v.vendorContactPhone, 'maxlength="50"')}</div>
              <div class="col-md-6">${input('vendorContactEmail', '수탁사 이메일', v.vendorContactEmail, 'type="email" maxlength="200"')}</div>
            </div></div>

          <div class="pi-form-section"><h3>계약</h3>
            <div class="pi-form-sub" id="original-contract-block">
              <h4><i class="fas fa-file-signature me-1"></i>원계약 <small class="text-muted fw-normal">계약기간·만료 알림은 원계약 기준</small></h4>
              <div class="row g-3">
                <div class="col-md-3">${input('contractStartDate', '계약 시작일', v.contractStartDate, 'type="date" required')}</div>
                <div class="col-md-3">${input('contractEndDate', '계약 종료일', v.contractEndDate, 'type="date" required')}</div>
                <div class="col-md-6">${input('contractPeriodNote', '계약기간 비고', v.contractPeriodNote, 'maxlength="500" placeholder="예: 체결일로부터 1년, 이용약관"')}</div>
                <div class="col-md-2"><label class="form-label d-block">자동연장</label>${radio('autoRenewYn', v.autoRenewYn)}</div>
                <div class="col-md-3"><label class="form-label" for="f-renewValue">자동연장 기간</label>
                  <div class="input-group">
                    <input type="number" class="form-control" id="f-renewValue" min="1" max="120" step="1" value="${renewParts(v.autoRenewPeriodMonths).value}" aria-label="자동연장 기간 숫자">
                    <select class="form-select" id="f-renewUnit" style="max-width:90px" aria-label="자동연장 기간 단위">
                      <option value="Y" ${renewParts(v.autoRenewPeriodMonths).unit === 'Y' ? 'selected' : ''}>년</option>
                      <option value="M" ${renewParts(v.autoRenewPeriodMonths).unit === 'M' ? 'selected' : ''}>개월</option></select>
                  </div></div>
                <div class="col-md-4">${input('autoRenewNote', '자동연장 비고', v.autoRenewNote, 'maxlength="200" placeholder="예: 별도 해지 통보 없으면 1년씩 연장"')}</div>
                <div class="col-12"><small class="text-muted" id="auto-renew-hint"><i class="fas fa-circle-info me-1"></i>자동연장 Y 이면 원계약 종료일이 지나도 추가 계약 없이 위 기간 단위로 계약기간이 이어지는 것으로 보고 <b>만료임박·만료·만료 서류·만료 알림 대상에서 뺍니다</b>. 대신 연장일이 만료 알림 기준일(기본 30일) 안에 들어오면 담당자에게 <b>자동연장 예정 확인 메일</b>을 연장 주기마다 1회 보냅니다. 실제로 끝나면 [계약 종료 처리]를 하세요.</small></div>
              </div>
            </div>
            <div class="pi-form-sub" id="pi-entrust-contract-block">
              <h4><i class="fas fa-user-shield me-1"></i>개인정보처리 위탁계약</h4>
              <div class="row g-3">
                <div class="col-md-3"><label class="form-label d-block">개인정보처리 위탁계약서 작성</label>${radio('contractDocYn', v.contractDocYn)}</div>
                <div class="col-md-3">${input('contractSignedDate', '개인정보처리 위탁계약 체결일', v.contractSignedDate, 'type="date"')}</div>
                <div class="col-md-6 d-flex align-items-end"><small class="text-muted" id="doc-analyze-guide">
                  <i class="fas fa-wand-magic-sparkles me-1"></i>아래 <b>위수탁 계약서 파일</b>을 선택하면 파일에 적힌 계약일자(없으면 계약기간 시작일)를 체결일에 넣고 작성 여부를 Y 로 바꿉니다. 위수탁 계약서가 여러 건이면 <b>가장 최근 계약서의 날짜</b>가 체결일입니다. 직접 입력해도 됩니다.</small></div>
                <div class="col-12 d-none" id="doc-analyze-result" role="status" aria-live="polite"></div>
              </div>
            </div></div>

          <div class="pi-form-section"><h3>재위탁</h3>
            <div class="row g-3">
              <div class="col-md-2"><label class="form-label d-block">재위탁 여부</label>${radio('subcontractYn', v.subcontractYn)}
                <small class="text-muted d-block mt-1" id="subcontract-hint">Y 선택 시 입력</small></div>
              <div class="col-md-4">${input('subcontractorName', '재위탁 업체명', v.subcontractorName, 'maxlength="500"')}</div>
              <div class="col-md-4">${input('subcontractTask', '재위탁 업무', v.subcontractTask, 'maxlength="1000"')}</div>
              <div class="col-md-2"><label class="form-label" for="f-subcontractConsentYn">재위탁 동의서</label>
                <select class="form-select" id="f-subcontractConsentYn" name="subcontractConsentYn">
                  ${opt('', '-', v.subcontractConsentYn)}${opt('Y', 'Y', v.subcontractConsentYn)}${opt('N', 'N', v.subcontractConsentYn)}</select></div>
            </div></div>

          <div class="pi-form-section"><h3>만료 알림</h3>
            <div class="row g-3">
              <div class="col-md-2"><label class="form-label d-block">알림 사용</label>${radio('alertYn', v.alertYn)}</div>
              <div class="col-md-2">${input('alertDaysBefore', '만료 N일 전부터', v.alertDaysBefore, 'type="number" min="1" max="365"')}</div>
              <div class="col-md-8">${input('alertExtraEmails', '추가 수신자 (쉼표 구분)', v.alertExtraEmails, 'maxlength="1000" placeholder="security@company.com"')}</div>
              <div class="col-12"><small class="text-muted">알림 기준일(기본 30일), D-7, D-1, 당일, 만료 후 단계별로 담당자·추가 수신자에게 1회씩 메일이 발송됩니다.</small></div>
            </div></div>

          <div class="pi-form-section"><h3>비고</h3>
            <textarea class="form-control" id="f-remark" name="remark" rows="3" maxlength="2000" aria-label="비고">${esc(v.remark)}</textarea>
            <div class="mt-3"><label class="form-label" for="f-contractFile">위수탁 계약서 파일 <small class="text-muted">(선택, 저장 시 첨부됩니다. 상세 화면에서도 추가 가능 · 계약일자 자동 읽기: pdf, docx, doc, hwp, hwpx, xlsx, xls)</small></label>
              <input type="file" class="form-control" id="f-contractFile" accept=".pdf,.doc,.docx,.hwp,.hwpx,.xls,.xlsx,.jpg,.jpeg,.png,.zip"></div>
          </div>

          <div class="d-flex justify-content-end gap-2 mt-4">
            <button type="button" class="btn btn-light" id="btn-cancel">취소</button>
            <button type="submit" class="btn btn-primary" id="btn-save"><i class="fas fa-save me-1"></i>저장</button>
          </div>
        </form>
      </section>`;

    document.getElementById('btn-cancel').onclick = () => go(isEdit ? '#/contracts/' + c.contractId : '#/contracts');
    const form = document.getElementById('contract-form');

    // 자동연장 기간: 자동연장 Y 일 때만 입력 (기본 1년)
    const syncRenew = () => {
      const on = form.querySelector('input[name="autoRenewYn"]:checked')?.value === 'Y';
      ['f-renewValue', 'f-renewUnit'].forEach((id) => { document.getElementById(id).disabled = !on; });
      if (on && !document.getElementById('f-renewValue').value) { document.getElementById('f-renewValue').value = 1; document.getElementById('f-renewUnit').value = 'Y'; }
    };
    form.querySelectorAll('input[name="autoRenewYn"]').forEach((r) => r.addEventListener('change', syncRenew));
    syncRenew();

    // 재위탁 업체명·업무·동의서: 재위탁 여부가 Y 일 때만 입력 가능
    const SUB_FIELDS = ['f-subcontractorName', 'f-subcontractTask', 'f-subcontractConsentYn'];
    const subcontractOn = () => form.querySelector('input[name="subcontractYn"]:checked')?.value === 'Y';
    const syncSubcontract = (focus) => {
      const on = subcontractOn();
      SUB_FIELDS.forEach((id) => { document.getElementById(id).disabled = !on; });
      document.getElementById('subcontract-hint').classList.toggle('d-none', on);
      if (on && focus && !document.getElementById('f-subcontractorName').value) document.getElementById('f-subcontractorName').focus();
    };
    form.querySelectorAll('input[name="subcontractYn"]').forEach((r) => r.addEventListener('change', () => syncSubcontract(true)));
    syncSubcontract(false);

    // 같은 업체 계약 안내: 업체명을 입력하면 이미 등록된 그 업체의 계약을 보여준다 (한 업체 여러 계약)
    initSameVendorHint(isEdit ? c.contractId : null);
    if (!isEdit && presetVendor) document.getElementById('f-contractName').focus();

    // 위수탁 계약서 파일 선택 → 파일에 적힌 계약일자를 체결일에 넣고 작성 여부 Y
    const latestDoc = isEdit ? (c.files || []).find((f) => f.latestContractDoc) : null;
    const docState = initContractDocAnalyze(form, latestDoc ? latestDoc.signedDate : null);

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!form.reportValidity()) return;
      const body = Object.fromEntries(new FormData(form).entries());
      // 비활성(재위탁 N) 칸은 FormData 에 빠지므로 입력값을 그대로 보내 보존한다 (다시 Y 로 바꾸면 그대로 사용)
      body.subcontractorName = document.getElementById('f-subcontractorName').value;
      body.subcontractTask = document.getElementById('f-subcontractTask').value;
      body.subcontractConsentYn = document.getElementById('f-subcontractConsentYn').value;
      Object.keys(body).forEach((k) => { if (body[k] === '') body[k] = null; });
      body.alertDaysBefore = body.alertDaysBefore ? Number(body.alertDaysBefore) : 30;
      {
        const n = Number(document.getElementById('f-renewValue').value || 0);
        const months = document.getElementById('f-renewUnit').value === 'Y' ? n * 12 : n;
        if (body.autoRenewYn === 'Y' && (!Number.isInteger(n) || months < 1 || months > 120)) {
          notify('자동연장 기간은 1개월 ~ 10년 사이로 입력하세요.', 'warning'); document.getElementById('f-renewValue').focus(); return;
        }
        body.autoRenewPeriodMonths = months >= 1 && months <= 120 ? months : 12;
      }
      if (body.subcontractYn === 'Y' && !body.subcontractorName && !body.subcontractTask) {
        notify('재위탁이 Y 이면 재위탁 업체명 또는 재위탁 업무를 입력하세요.', 'warning');
        document.getElementById('f-subcontractorName').focus();
        return;
      }
      if (body.contractStartDate && body.contractEndDate && body.contractEndDate < body.contractStartDate) {
        notify('원계약 종료일은 시작일보다 빠를 수 없습니다.', 'warning'); document.getElementById('f-contractEndDate').focus(); return;
      }
      if (body.alertYn === 'Y' && !body.contractEndDate) { notify('만료 알림을 사용하려면 계약 종료일을 입력하세요. (종료일이 없으면 알림 사용 N)', 'warning'); return; }
      const btn = document.getElementById('btn-save');
      btn.disabled = true;
      try {
        const saved = isEdit ? await PI_API.updateContract(c.contractId, body) : await PI_API.createContract(body);
        const file = document.getElementById('f-contractFile')?.files?.[0];
        if (file) {
          const fd = new FormData();
          fd.append('file', file); fd.append('fileType', 'CONTRACT');
          if (docState && docState.docDate) fd.append('signedDate', docState.docDate);
          try { await PI_API.uploadFile(saved.contractId, fd); } catch (err) { notify('계약은 저장되었으나 파일 업로드 실패: ' + err.message, 'warning'); }
        }
        notify(isEdit ? '수정되었습니다.' : '등록되었습니다.');
        go('#/contracts/' + saved.contractId);
      } catch (err) { fail(err); } finally { btn.disabled = false; }
    });
  }

  /**
   * 위수탁 계약서 파일을 고르면 서버에서 글자를 읽어 계약일자를 찾는다(파일은 이 단계에서 저장하지 않음).
   *  - 파일에 적힌 계약일자를 '개인정보처리 위탁계약 체결일'에 그대로 넣고 '개인정보처리 위탁계약서 작성'을 Y 로 바꾼다.
   *    (개인정보처리 위탁계약은 원계약과 따로 체결될 수 있으므로 원계약·올해 날짜와 비교하지 않는다)
   *  - [되돌리기] 로 파일 선택 전 값으로 돌아가고, 다른 날짜 후보를 고르거나 직접 입력할 수 있다.
   */
  function initContractDocAnalyze(form, existingLatest) {
    const state = { docDate: null }; // 새로 고른 계약서 파일의 체결일 (저장 시 첨부 파일에 함께 저장)
    const fileInput = document.getElementById('f-contractFile');
    const box = document.getElementById('doc-analyze-result');
    const dateInput = document.getElementById('f-contractSignedDate');
    if (!fileInput || !box || !dateInput) return state;
    const docYn = () => form.querySelector('input[name="contractDocYn"]:checked')?.value || 'N';
    const setDocYn = (val) => { const r = document.getElementById('contractDocYn-' + val); if (r) r.checked = true; };
    const flash = (el) => { el.classList.remove('pi-autofilled'); void el.offsetWidth; el.classList.add('pi-autofilled'); };
    let seq = 0;

    const show = (cls, html) => { box.className = 'col-12 pi-doc-result alert py-2 px-3 mb-0 ' + cls; box.innerHTML = html; };
    const hide = () => { box.className = 'col-12 d-none'; box.innerHTML = ''; };

    fileInput.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      const my = ++seq;
      state.docDate = null;
      if (!file) { hide(); return; }
      if (!perm.canWrite) return;
      const before = { date: dateInput.value, yn: docYn() };
      show('alert-secondary', '<i class="fas fa-spinner fa-spin me-1"></i>계약서에서 계약일자를 찾는 중입니다…');
      let r;
      try {
        const fd = new FormData(); fd.append('file', file);
        r = await PI_API.analyzeContractDoc(fd);
      } catch (err) {
        if (my !== seq) return;
        show('alert-warning', `<i class="fas fa-triangle-exclamation me-1"></i>계약서를 읽지 못했습니다: ${esc(err.message)} <span class="text-muted">· 체결일과 작성 여부를 직접 입력하세요.</span>`);
        return;
      }
      if (my !== seq) return; // 그 사이 다른 파일을 고른 경우 이전 결과는 버린다

      const apply = (date) => {
        state.docDate = date;
        // 이미 등록된 위수탁 계약서가 더 최근이면 체결일은 그대로 (체결일 = 가장 최근 계약서 날짜)
        const older = existingLatest && String(date) < String(existingLatest);
        if (!older) { dateInput.value = date; flash(dateInput); }
        setDocYn('Y'); flash(document.querySelector('label[for="contractDocYn-Y"]'));
        render(date, older);
      };
      const render = (date, older) => {
        const others = (r.candidates || []).filter((c) => c.date !== date);
        const fromStart = r.candidates?.find((c) => c.date === date)?.kind === 'PERIOD';
        const what = fromStart ? '체결일 표기가 없어 계약기간 시작일' : '계약서에 적힌 계약일자';
        const head = older
          ? `<i class="fas fa-circle-info me-1"></i>이 계약서의 체결일은 <b>${fmtDate(date)}</b>(${what})입니다. 이미 등록된 위수탁 계약서(<b>${fmtDate(existingLatest)}</b>)가 더 최근이라 계약의 체결일은 그대로 둡니다.`
          : `<i class="fas fa-circle-check me-1"></i>${what} <b>${fmtDate(date)}</b> 를 체결일에 넣고 <b>작성 여부를 Y</b>로 바꿨습니다.`;
        show(older ? 'alert-info' : 'alert-success', `
          <div>${head}</div>
          <div class="small text-muted mt-1">근거: ${esc(r.candidates?.find((c) => c.date === date)?.kindLabel || '직접 선택')}${r.candidates?.find((c) => c.date === date)?.context ? ` · "${esc(r.candidates.find((c) => c.date === date).context)}"` : ''} · 파일: ${esc(r.fileName)}</div>
          <div class="d-flex flex-wrap gap-2 mt-2 align-items-center">
            ${others.length ? `<span class="small text-muted">다른 날짜:</span>${others.map((c) => `<button type="button" class="btn btn-sm btn-light border" data-act="pick" data-date="${esc(c.date)}" title="${esc(c.kindLabel)} · ${esc(c.context)}">${fmtDate(c.date)} <small class="text-muted">${esc(c.kindLabel)}</small></button>`).join('')}` : ''}
            <button type="button" class="btn btn-sm btn-link text-secondary" data-act="undo"><i class="fas fa-rotate-left me-1"></i>되돌리기</button>
          </div>`);
        box.querySelectorAll('[data-act="pick"]').forEach((b) => b.addEventListener('click', () => apply(b.dataset.date)));
        box.querySelector('[data-act="undo"]').addEventListener('click', () => {
          dateInput.value = before.date; setDocYn(before.yn); state.docDate = null;
          show('alert-secondary', '<i class="fas fa-rotate-left me-1"></i>파일을 고르기 전 값으로 되돌렸습니다. 첨부할 파일은 그대로 저장되며, 파일의 체결일은 상세 화면 첨부 목록에서 고칠 수 있습니다.');
        });
      };

      if (r.detectedDate) { apply(r.detectedDate); return; }
      const cands = (r.candidates || []);
      show(r.textReadable ? 'alert-warning' : 'alert-secondary', `
        <div><i class="fas fa-circle-info me-1"></i>${esc(r.message)}</div>
        ${cands.length ? `<div class="d-flex flex-wrap gap-2 mt-2 align-items-center"><span class="small text-muted">문서 안 날짜:</span>${cands.map((c) => `<button type="button" class="btn btn-sm btn-light border" data-act="pick" data-date="${esc(c.date)}" title="${esc(c.context)}">${fmtDate(c.date)} <small class="text-muted">${esc(c.kindLabel)}</small></button>`).join('')}</div>` : ''}`);
      box.querySelectorAll('[data-act="pick"]').forEach((b) => b.addEventListener('click', () => apply(b.dataset.date)));
    });
    return state;
  }

  /** 첨부 정렬: 위수탁 계약서(체결일 최신순, 날짜 없는 것은 뒤) → 그 밖의 파일(최근 등록순) */
  function sortFiles(files) {
    const docs = files.filter((f) => f.fileType === 'CONTRACT')
      .sort((a, b) => String(b.signedDate || '').localeCompare(String(a.signedDate || '')) || b.fileId - a.fileId);
    return docs.concat(files.filter((f) => f.fileType !== 'CONTRACT'));
  }

  function signedDateCell(f) {
    const d = f.signedDate ? `<b>${fmtDate(f.signedDate)}</b>` : '<span class="text-muted">미입력</span>';
    const latest = f.latestContractDoc ? ' <span class="badge text-bg-primary" title="계약의 개인정보처리 위탁계약 체결일로 표시되는 계약서">최신</span>' : '';
    const edit = perm.canWrite ? ` <button class="btn btn-sm btn-link p-0 ms-1" data-edit-signed="${f.fileId}" data-date="${esc(f.signedDate || '')}" title="체결일 수정" aria-label="체결일 수정"><i class="fas fa-pen fa-xs"></i></button>` : '';
    return d + latest + edit;
  }

  /**
   * 서류 일자 입력 팝업 (확약서 서명일 / 파기 확인서 파기 완료일).
   * 파기 확인서는 파기 기한(종료 + 5일)·확인서 회신 기한(종료 + 30일)을 보여주고,
   * 파기 기한보다 늦은 날짜면 지연 사유 칸을 열고 저장 전에 한 번 더 확인한다.
   * @returns Promise<{date, note}|null> (취소하면 null)
   */
  function askDocDate({ fileType, fileName, current, note }) {
    return new Promise((resolve) => {
      const isDest = fileType === 'DESTRUCTION_CONFIRM';
      const label = isDest ? '파기 완료일' : '서명일';
      const f = (currentContract && currentContract.followUp) || {};
      const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
      const $ = (id) => document.getElementById(id);
      const input = $('doc-date-input'); const noteEl = $('doc-date-note'); const form = $('doc-date-form');
      $('doc-date-title').textContent = (isDest ? '개인정보 파기 확인서' : '정보보호 및 비밀유지 확약서') + ' — ' + label;
      $('doc-date-file').textContent = fileName ? '파일: ' + fileName : '';
      $('doc-date-label').textContent = label;
      $('doc-date-help').textContent = isDest
        ? '파기 확인서에 적힌 실제 파기 일자를 입력하세요(업로드한 날이 아님). 모르면 비워 두고 나중에 첨부 목록의 연필 버튼으로 입력할 수 있습니다.'
        : '확약서에 서명한 날짜를 입력하세요. 모르면 비워 두고 나중에 첨부 목록의 연필 버튼으로 입력할 수 있습니다.';
      const box = $('doc-date-deadline');
      if (isDest && f.destructionDeadline) {
        box.innerHTML = `<i class="fas fa-calendar-check me-1"></i>계약 종료일 <b>${fmtDate(f.endBaseDate)}</b> 기준<br>
          · <b>파기 기한</b> ${fmtDate(f.destructionDeadline)}까지 <span class="text-muted">(종료 + ${f.destructionDays}일, 표준 개인정보 보호지침 제10조 '5일 이내 파기')</span><br>
          · <b>확인서 회신 기한</b> ${fmtDate(f.confirmReplyDeadline)}까지 <span class="text-muted">(종료 + ${f.confirmReplyDays}일, 협력사가 파기 확인서를 보내오는 기간)</span>`;
        box.classList.remove('d-none');
      } else if (isDest) {
        box.innerHTML = '<i class="fas fa-circle-info me-1"></i>원계약 종료일이 없어 파기 기한을 계산할 수 없습니다.';
        box.classList.remove('d-none');
      } else {
        box.classList.add('d-none');
      }
      input.value = current === undefined ? today : (current || '');
      input.max = today;
      input.classList.remove('is-invalid');
      noteEl.value = note || '';
      let late = false;
      const check = () => {
        late = isDest && !!input.value && !!f.destructionDeadline && input.value > f.destructionDeadline;
        $('doc-date-late').classList.toggle('d-none', !late);
        if (late) {
          const days = Math.round((new Date(input.value) - new Date(f.destructionDeadline)) / 86400000);
          $('doc-date-late-msg').innerHTML = `<i class="fas fa-triangle-exclamation me-1"></i>파기 기한(${fmtDate(f.destructionDeadline)})보다 <b>${days}일 늦습니다.</b> 저장하면 이 계약은 <b>파기 지연</b>으로 표시됩니다.`;
        }
      };
      input.oninput = check; check();
      let done = false;
      form.onsubmit = (e) => {
        e.preventDefault();
        if (input.value && input.value > today) { input.classList.add('is-invalid'); return; }
        input.classList.remove('is-invalid');
        if (late && !confirm(`${label} ${fmtDate(input.value)}은(는) 파기 기한 ${fmtDate(f.destructionDeadline)}보다 늦어 '파기 지연'으로 표시됩니다.\n날짜가 맞습니까?`)) return;
        done = true;
        modal('doc-date-modal').hide();
        resolve({ date: input.value || null, note: isDest ? (late ? noteEl.value.trim() : '') : null });
      };
      document.getElementById('doc-date-modal').addEventListener('hidden.bs.modal', () => { if (!done) resolve(null); }, { once: true });
      modal('doc-date-modal').show();
      setTimeout(() => input.focus(), 300);
    });
  }

  /** 확약서 서명일 / 파기 확인서 파기 완료일 칸 */
  function documentDateCell(f) {
    const d = f.documentDate ? `<b>${fmtDate(f.documentDate)}</b>` : '<span class="text-warning-emphasis">미입력</span>';
    const edit = perm.canWrite ? ` <button class="btn btn-sm btn-link p-0 ms-1" data-edit-docdate="${f.fileId}" data-date="${esc(f.documentDate || '')}" data-label="${esc(f.documentDateLabel)}"
      data-filetype="${esc(f.fileType)}" data-name="${esc(f.originalName)}" data-note="${esc(f.documentNote || '')}" title="${esc(f.documentDateLabel)} 입력·수정" aria-label="${esc(f.documentDateLabel)} 수정"><i class="fas fa-pen fa-xs"></i></button>` : '';
    const noteLine = f.documentNote ? `<small class="d-block text-danger pi-ellipsis" style="max-width:260px" title="${esc(f.documentNote)}">지연 사유: ${esc(f.documentNote)}</small>` : '';
    return `<small class="text-muted">${esc(f.documentDateLabel)}</small> ` + d + edit + noteLine;
  }

  /** 상세 상단 처리 필요 항목 안내 (해당 사항이 있을 때만) */
  function followUpPanel(c) {
    const f = c.followUp;
    if (!f) return '';
    const items = [];
    if (f.expiryDocsMissing) {
      items.push(`<li><b>만료 서류 미수취 ${3 - f.expiryDocsReceived}종</b>: ${esc(f.missingDocs.join(', '))}
        <a href="#expiry-check-section" class="ms-1 pi-fu-jump" data-target="expiry-check-section">[계약만료시 점검으로]</a>
        ${c.terminatedYn === 'Y' ? `<span class="text-muted ms-1">· 독촉 메일 ${c.expiryDocsRemindYn === 'N' ? '<b>꺼짐</b>' : '주 1회 발송 중'}</span>
          ${perm.canWrite ? `<button class="btn btn-link btn-sm p-0 ms-1 align-baseline" id="btn-toggle-remind" data-yn="${c.expiryDocsRemindYn === 'N' ? 'Y' : 'N'}">${c.expiryDocsRemindYn === 'N' ? '켜기' : '끄기'}</button>` : ''}` : ''}</li>`);
    }
    (f.overdueInspections || []).forEach((o) => items.push(`<li><b>조치 지연</b>: ${fmtDate(o.inspectionDate)} 점검의 N 항목 ${o.nCount}건 — 조치 예정일 ${fmtDate(o.actionDueDate)}에서 <b class="text-danger">${o.overdueDays}일 지남</b>
      <a href="#" class="ms-1" data-open-ins="${o.inspectionId}">[점검 상세에서 조치 완료 처리]</a></li>`));
    if (f.consentMissing) {
      items.push(`<li><b>재위탁 동의서 누락</b>: 재위탁이 Y 인데 재위탁 동의서 파일이 없습니다. <a href="#contract-file-section" class="pi-fu-jump" data-target="contract-file-section" data-filetype="SUBCONTRACT_CONSENT">[재위탁 동의서 올리기]</a></li>`);
    }
    if (f.destructionLate) {
      items.push(`<li><b>파기 지연</b>: 파기 완료일 ${fmtDate(f.destructionDate)} 이(가) 파기 기한 ${fmtDate(f.destructionDeadline)}(계약 종료 + ${f.destructionDays}일)보다 늦습니다.
        ${f.destructionDelayReason ? `<span class="text-muted">지연 사유: ${esc(f.destructionDelayReason)}</span>` : '협력사에 지연 사유를 확인해 첨부 목록 연필 버튼으로 기록하세요.'}</li>`);
    }
    if (f.confirmReplyOverdue) {
      items.push(`<li><b>파기 확인서 회신 기한 지남</b>: 회신 기한 ${fmtDate(f.confirmReplyDeadline)}(계약 종료 + ${f.confirmReplyDays}일)이 지났는데 파기 확인서를 받지 못했습니다.
        <a href="#expiry-check-section" class="ms-1 pi-fu-jump" data-target="expiry-check-section">[계약만료시 점검으로]</a></li>`);
    }
    (f.documentDateMissing || []).forEach((m) => items.push(`<li class="text-muted"><b>${esc(m)} 미입력</b>: 첨부 목록의 연필 버튼으로 입력하세요.</li>`));
    if (!items.length) return '';
    const serious = f.expiryDocsMissing || f.actionOverdueCount || f.consentMissing || f.destructionLate || f.confirmReplyOverdue;
    return `<section class="pi-card pi-followup-panel ${serious ? 'serious' : ''}" id="follow-up-section" role="status">
      <h2 class="mb-1"><i class="fas fa-clipboard-list"></i>처리 필요 항목</h2>
      <p class="pi-followup-desc">계약 만료·점검 이후 아직 끝나지 않은 일입니다. 담당자가 협력사로부터 서류를 받아 올리거나 조치를 완료하면 이 목록에서 자동으로 사라집니다.</p>
      <ul class="mb-0 small">${items.join('')}</ul></section>`;
  }

  /** 같은 업체 계약 표 (상세 섹션·팝업·등록 안내 공통) */
  function sameVendorTable(list, currentId, opts = {}) {
    return `<div class="table-responsive"><table class="pi-table pi-same-vendor-table"><thead><tr>
        <th class="pi-sv-name">계약명</th><th>관련 서비스</th><th>위탁업무 내용</th><th class="pi-sv-dept">담당부서 / 담당자</th><th>원계약 기간</th><th>상태</th><th>최근 점검</th></tr></thead>
      <tbody>${list.map((r) => `
        <tr data-id="${r.contractId}" class="${r.contractId === currentId ? 'pi-same-current' : ''}" tabindex="0" role="button" title="${r.contractId === currentId ? '지금 보고 있는 계약' : '이 계약 상세로 이동'}">
          <td class="fw-semibold pi-sv-name">${esc(r.contractName || '-')}${r.contractId === currentId ? ' <span class="badge text-bg-primary">이 계약</span>' : ''}${r.vendorName && opts.showVendor ? `<small class="text-muted d-block">${esc(r.vendorName)}</small>` : ''}</td>
          <td>${esc(r.serviceName || '-')}</td>
          <td class="pi-ellipsis" style="max-width:260px" title="${esc(r.entrustedTask)}">${esc(r.entrustedTask)}</td>
          <td class="pi-sv-dept">${esc([r.deptName, r.managerName].filter(Boolean).join(' / ') || '-')}</td>
          <td class="text-nowrap">${r.contractStartDate || r.contractEndDate ? fmtDate(r.contractStartDate) + ' ~ ' + fmtDate(r.contractEndDate) : esc(r.contractPeriodNote || '-')}</td>
          <td class="text-nowrap">${badge(r.status, r.statusLabel)}</td>
          <td class="text-nowrap">${badge(r.lastInspectionResult || 'NONE', r.lastInspectionResultLabel)} <small class="text-muted">${r.lastInspectionDate ? fmtDate(r.lastInspectionDate) : ''}</small></td>
        </tr>`).join('')}</tbody></table></div>`;
  }

  function bindSameVendorRows(root, currentId, onGo) {
    root.querySelectorAll('.pi-same-vendor-table tr[data-id]').forEach((tr) => {
      const id = Number(tr.dataset.id);
      if (id === currentId) { tr.style.cursor = 'default'; return; }
      const open = () => { if (onGo) onGo(); go('#/contracts/' + id); };
      tr.addEventListener('click', open);
      tr.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); open(); } });
    });
  }

  /** 상세: 같은 업체 계약 섹션 */
  async function loadSameVendor(c) {
    const body = document.getElementById('same-vendor-body');
    if (!body) return;
    try {
      const list = await PI_API.sameVendorContracts(c.contractId);
      const active = list.filter((r) => r.status !== 'TERMINATED').length;
      document.getElementById('same-vendor-count').textContent = `(${list.length}건 · 진행 ${active})`;
      body.innerHTML = sameVendorTable(list, c.contractId) +
        `<small class="text-muted">업체명이 같은(㈜·(주)·주식회사·공백 차이 무시) 계약을 모두 보여줍니다. 본사·지사처럼 날짜나 업무 범위가 다른 계약은 <b>계약명</b>으로 구분해 각각 등록하세요. 행을 누르면 그 계약으로 이동합니다.</small>`;
      PiTable.enhance(body.querySelector('table'), { id: 'sameVendor' });
      bindSameVendorRows(body, c.contractId);
    } catch (e) {
      body.innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  /** 목록 '계약 N건' → 같은 업체 계약 팝업 */
  async function openVendorContracts(vendorName) {
    const body = document.getElementById('vendor-contracts-modal-body');
    document.getElementById('vendor-contracts-modal-title').innerHTML =
      `<i class="fas fa-layer-group me-2 text-primary"></i>${esc(vendorName)} <small class="text-muted fw-normal">업체 계약 목록</small>`;
    body.innerHTML = '<div class="pi-empty">불러오는 중...</div>';
    const addBtn = document.getElementById('btn-vc-add');
    const ckBtn = document.getElementById('btn-vc-checklist');
    addBtn.classList.toggle('d-none', !canCreate());
    ckBtn.classList.toggle('d-none', !perm.canWrite);
    addBtn.onclick = () => { modal('vendor-contracts-modal').hide(); go('#/contracts/new?vendor=' + encodeURIComponent(vendorName)); };
    ckBtn.onclick = () => { modal('vendor-contracts-modal').hide(); openVendorChecklist(vendorName); };
    modal('vendor-contracts-modal').show();
    try {
      const list = await PI_API.vendorContracts(vendorName);
      const active = list.filter((r) => r.status !== 'TERMINATED').length;
      body.innerHTML = `<p class="small text-muted mb-2">계약 ${list.length}건 (진행 ${active}) · 행을 누르면 계약 상세로 이동합니다.</p>` +
        sameVendorTable(list, null, { showVendor: true });
      PiTable.enhance(body.querySelector('table'), { id: 'sameVendor' });
      bindSameVendorRows(body, null, () => modal('vendor-contracts-modal').hide());
    } catch (e) {
      body.innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  /** 등록/수정 폼: 업체명 입력 시 같은 업체의 기존 계약 안내 */
  function initSameVendorHint(currentId) {
    const inp = document.getElementById('f-vendorName');
    const box = document.getElementById('same-vendor-hint');
    if (!inp || !box) return;
    let timer = null; let seq = 0;
    const check = async () => {
      const name = inp.value.trim(); const my = ++seq;
      if (!name) { box.className = 'col-12 d-none'; return; }
      try {
        const list = (await PI_API.vendorContracts(name)).filter((r) => r.contractId !== currentId);
        if (my !== seq) return;
        if (!list.length) { box.className = 'col-12 d-none'; return; }
        box.className = 'col-12 pi-same-vendor-hint alert alert-info py-2 px-3 mb-0';
        box.innerHTML = `<div class="mb-1"><i class="fas fa-layer-group me-1"></i><b>${esc(list[0].vendorName)}</b> 업체에 이미 계약이 <b>${list.length}건</b> 있습니다.
            같은 업체의 다른 계약(본사·지사 등)이면 그대로 저장하고, <b>계약명</b>으로 구분해 주세요.</div>
          <div class="d-flex flex-wrap gap-1">${list.map((r) => `<a class="pi-same-chip" href="#/contracts/${r.contractId}" target="_self" title="${esc(r.entrustedTask)}">${esc(contractLabel(r))}
            <small>${r.contractStartDate || r.contractEndDate ? fmtDate(r.contractStartDate) + '~' + fmtDate(r.contractEndDate) : ''}</small> ${badge(r.status, r.statusLabel)}</a>`).join('')}</div>`;
        box.querySelectorAll('a.pi-same-chip').forEach((a) => a.addEventListener('click', (e) => {
          if (!confirm('입력 중인 내용은 저장되지 않습니다. 이 계약으로 이동할까요?')) e.preventDefault();
        }));
      } catch (e) { box.className = 'col-12 d-none'; }
    };
    inp.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(check, 350); });
    if (inp.value.trim()) check();
  }

  // ──────────────── 3. 상세 화면 ────────────────
  // ──────────────── 접근 기록 (#/access-logs, 관리자) ────────────────
  const accessState = { page: 0, size: 50, cond: {} };
  let accessActionTypes = null;

  async function renderAccessLogs() {
    crumb.innerHTML = '<a href="#/contracts">계약 목록</a> &gt; 접근 기록';
    if (!perm.canManageForms) {
      view.innerHTML = '<div class="pi-card pi-empty"><i class="fas fa-lock me-1"></i>접근 기록은 보안팀 관리자(ROLE_PI_ADMIN)와 플랫폼 관리자만 볼 수 있습니다.</div>';
      return;
    }
    if (!accessActionTypes) accessActionTypes = await PI_API.accessLogActionTypes();
    const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    const monthAgo = new Date(Date.now() - 30 * 86400000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    const c = accessState.cond;
    if (!c.from) { c.from = monthAgo; c.to = today; }
    view.innerHTML = `
      <section class="pi-card" id="access-log-search-section">
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-2">
          <h2 class="mb-0"><i class="fas fa-user-shield"></i>접근 기록 <small class="text-muted fw-normal">조회·다운로드 기록 (수정·삭제 불가)</small></h2>
          <button class="btn btn-sm btn-light" id="btn-access-back"><i class="fas fa-list me-1"></i>계약 목록</button>
        </div>
        <p class="small text-muted mb-2"><i class="fas fa-circle-info me-1"></i>계약 상세·점검 상세 조회, 계약서·증빙·점검 결과 다운로드, 계약 목록 엑셀(사유 필수) 다운로드를 누가·언제·어디서(IP) 했는지 남깁니다. 이 화면의 엑셀 다운로드도 사유와 함께 기록됩니다.</p>
        <form id="access-log-form" class="row g-2 align-items-end">
          <div class="col-md-3"><label class="form-label small" for="al-from">기간</label>
            <div class="input-group input-group-sm"><input type="date" class="form-control" id="al-from" name="from" value="${esc(c.from)}" aria-label="시작일">
              <span class="input-group-text">~</span><input type="date" class="form-control" id="al-to" name="to" value="${esc(c.to)}" aria-label="종료일"></div></div>
          <div class="col-md-2"><label class="form-label small" for="al-action">행위</label>
            <select class="form-select form-select-sm" id="al-action" name="actionType">${opt('', '전체', c.actionType)}${accessActionTypes.map((a) => opt(a.code, a.label, c.actionType)).join('')}</select></div>
          <div class="col-md-2"><label class="form-label small" for="al-user">사용자</label>
            <input class="form-control form-control-sm" id="al-user" name="user" value="${esc(c.user)}" placeholder="이름 또는 로그인 ID"></div>
          <div class="col-md-3"><label class="form-label small" for="al-vendor">업체 / 대상</label>
            <input class="form-control form-control-sm" id="al-vendor" name="vendor" value="${esc(c.vendor)}" placeholder="수탁업체명·계약명·파일명"></div>
          <div class="col-md-2 d-flex gap-1">
            <button class="btn btn-sm btn-primary" type="submit"><i class="fas fa-search me-1"></i>검색</button>
            <button class="btn btn-sm btn-outline-success" type="button" id="btn-access-excel"><i class="fas fa-download me-1"></i>엑셀</button></div>
        </form>
      </section>
      <section class="pi-card" id="access-log-list-section">
        <h2 class="mb-2"><i class="fas fa-list"></i>기록 <small class="text-muted fw-normal" id="access-log-count"></small></h2>
        <div class="table-responsive"><table class="pi-table" id="access-log-table"><thead><tr>
          <th>일시</th><th>행위</th><th>수탁업체 / 계약</th><th>대상</th><th>행 수</th><th>사유</th><th>사용자</th><th>접속 IP</th></tr></thead>
          <tbody><tr><td colspan="8" class="pi-empty">불러오는 중...</td></tr></tbody></table></div>
        <nav class="d-flex justify-content-center mt-3" id="access-log-pager" aria-label="페이지"></nav>
      </section>`;
    PiTable.enhance(document.getElementById('access-log-table'), { id: 'accessLogs' });
    document.getElementById('btn-access-back').onclick = () => go('#/contracts');
    const form = document.getElementById('access-log-form');
    const readCond = () => Object.fromEntries([...new FormData(form).entries()].filter(([, v]) => v !== ''));
    form.addEventListener('submit', (e) => { e.preventDefault(); accessState.cond = readCond(); accessState.page = 0; loadAccessLogs(); });
    document.getElementById('btn-access-excel').onclick = () => askDownloadReason('접근 기록 엑셀', (reason) => PI_API.downloadAccessLogs(readCond(), reason));
    await loadAccessLogs();
  }

  async function loadAccessLogs() {
    const tbody = document.querySelector('#access-log-table tbody');
    try {
      const p = await PI_API.listAccessLogs({ ...accessState.cond, page: accessState.page, size: accessState.size });
      document.getElementById('access-log-count').textContent = `(총 ${p.totalElements}건)`;
      tbody.innerHTML = p.content.length ? p.content.map((a) => `
        <tr style="cursor:default" class="${a.actionType.startsWith('DOWNLOAD_EXCEL') || a.actionType === 'DOWNLOAD_ACCESS_LOG' ? 'pi-access-excel' : ''}">
          <td class="text-nowrap">${fmtDateTime(a.accessedAt)}</td>
          <td class="text-nowrap"><span class="pi-status ${a.actionType.startsWith('DOWNLOAD') ? 'EXPIRING' : 'NONE'}">${esc(a.actionTypeLabel)}</span></td>
          <td>${a.contractId ? `<a href="#/contracts/${a.contractId}">${esc(a.vendorName || '')}</a><small class="d-block text-muted">${esc(a.contractName || '')}</small>` : '<span class="text-muted">-</span>'}</td>
          <td class="pi-ellipsis" style="max-width:280px" title="${esc(a.targetName || '')}">${esc(a.targetName || '-')}</td>
          <td class="text-end">${a.recordCount == null ? '' : a.recordCount}</td>
          <td class="pi-ellipsis" style="max-width:220px" title="${esc(a.reason || '')}">${esc(a.reason || '')}</td>
          <td class="text-nowrap">${esc(a.userName || '-')}<small class="d-block text-muted">${esc(a.userLoginId || '')}</small></td>
          <td class="text-nowrap small" title="${esc(a.userAgent || '')}">${esc(a.clientIp || '-')}</td></tr>`).join('')
        : '<tr><td colspan="8" class="pi-empty">조건에 맞는 기록이 없습니다.</td></tr>';
      PiTable.refresh(document.getElementById('access-log-table'));
      const pager = document.getElementById('access-log-pager');
      pager.innerHTML = p.totalPages > 1 ? `<ul class="pagination pagination-sm mb-0">
        <li class="page-item ${p.page === 0 ? 'disabled' : ''}"><button class="page-link" data-p="${p.page - 1}">이전</button></li>
        <li class="page-item disabled"><span class="page-link">${p.page + 1} / ${p.totalPages}</span></li>
        <li class="page-item ${p.page + 1 >= p.totalPages ? 'disabled' : ''}"><button class="page-link" data-p="${p.page + 1}">다음</button></li></ul>` : '';
      pager.querySelectorAll('[data-p]').forEach((b) => b.addEventListener('click', () => { accessState.page = Number(b.dataset.p); loadAccessLogs(); }));
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="8" class="pi-empty">${esc(e.message)}</td></tr>`;
    }
  }

  // ──────────────── 양식 관리 (#/forms) ────────────────
  const FORM_ICON = { checklist: 'fa-file-excel text-success', 'security-pledge': 'fa-file-signature text-primary', 'destruction-confirm': 'fa-file-shield text-danger' };
  let formGroups = [];

  async function renderForms() {
    crumb.innerHTML = '<a href="#/contracts">계약 목록</a> &gt; 양식 관리';
    if (!perm.canManageForms) {
      view.innerHTML = '<div class="pi-card pi-empty"><i class="fas fa-lock me-1"></i>양식 관리는 보안팀 관리자(ROLE_PI_ADMIN)와 플랫폼 관리자만 사용할 수 있습니다.</div>';
      return;
    }
    formGroups = await PI_API.listFormTemplates();
    view.innerHTML = `
      <section class="pi-card" id="form-guide-section">
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2">
          <h2 class="mb-1"><i class="fas fa-folder-tree"></i>양식 관리 <small class="text-muted fw-normal">보안팀 양식 버전 관리</small></h2>
          <button class="btn btn-sm btn-light" id="btn-forms-back"><i class="fas fa-list me-1"></i>계약 목록</button>
        </div>
        <ul class="small text-muted mb-0 ps-3">
          <li><b>사용 중</b>인 양식이 계약 화면의 양식 다운로드, 계약별 자동 입력 양식, <b>만료 알림 메일 첨부</b>에 모두 쓰입니다. 배포 없이 바로 반영됩니다.</li>
          <li>새 버전을 올리면 형식을 검사합니다. <b>체크리스트</b>는 협력사 작성본 자동 판정이 깨지지 않도록 시스템이 실제로 읽어 보고, 읽지 못하면 등록하지 않습니다.</li>
          <li><b>확약서·파기 확인서</b>는 docx·doc·hwp·hwpx·pdf 를 올릴 수 있습니다. <b>docx 만 계약 정보가 자동으로 채워지고</b>, 나머지 형식은 올린 파일 그대로 나갑니다.</li>
          <li>이전 버전은 남아 있어 언제든 <b>[사용 지정]</b>으로 되돌릴 수 있고, <b>[기본 양식 사용]</b>을 누르면 시스템 기본 양식으로 돌아갑니다. 점검 이력과 메일 발송 이력에 사용한 양식 버전이 기록됩니다.</li>
        </ul>
      </section>
      ${formGroups.map(formGroupCard).join('')}`;
    formGroups.forEach((g) => PiTable.enhance(document.getElementById('form-versions-' + g.formType), { id: 'formVersions' }));
    bindForms();
  }

  function formGroupCard(g) {
    const vrow = (v) => `
      <tr class="${v.active ? 'pi-latest-doc' : ''}" style="cursor:default">
        <td class="text-nowrap"><b>${esc(v.versionLabel)}</b> ${v.active ? '<span class="pi-status PASS ms-1">사용 중</span>' : ''}</td>
        <td class="pi-ellipsis" style="max-width:240px" title="${esc(v.originalName)}">${esc(v.originalName)}
          <small class="d-block text-muted">${esc((v.fileExt || '').toUpperCase())} · ${fmtSize(v.fileSize)} · ${v.autofill ? '자동 입력' : '<span class="text-warning-emphasis">원본 그대로</span>'}</small></td>
        <td class="pi-ellipsis" style="max-width:260px" title="${esc(v.changeNote)}">${esc(v.changeNote || '-')}</td>
        <td class="small" style="max-width:300px;white-space:pre-line">${esc(v.validationNote || '-')}</td>
        <td class="text-nowrap small">${fmtDateTime(v.createdAt)}<span class="d-block text-muted">${esc(v.createdByName || '-')}</span></td>
        <td class="text-nowrap small">${v.activatedAt ? fmtDateTime(v.activatedAt) + `<span class="d-block text-muted">${esc(v.activatedByName || '-')}</span>` : '-'}</td>
        <td class="text-end text-nowrap">
          <button class="btn btn-sm btn-light" data-ft-dl="${v.formTemplateId}" title="원본 다운로드" aria-label="${esc(v.versionLabel)} 원본 다운로드"><i class="fas fa-download"></i></button>
          ${v.autofill ? `<button class="btn btn-sm btn-light" data-ft-preview="${v.formTemplateId}" title="샘플 계약 정보로 채운 미리보기" aria-label="${esc(v.versionLabel)} 미리보기"><i class="fas fa-eye"></i></button>` : ''}
          ${v.active ? '' : `<button class="btn btn-sm btn-outline-primary" data-ft-activate="${v.formTemplateId}" data-label="${esc(v.versionLabel)}" data-type="${esc(g.label)}"><i class="fas fa-check me-1"></i>사용 지정</button>
            <button class="btn btn-sm btn-light text-danger" data-ft-del="${v.formTemplateId}" data-label="${esc(v.versionLabel)}" aria-label="${esc(v.versionLabel)} 삭제"><i class="fas fa-trash"></i></button>`}
        </td></tr>`;
    const ph = Object.entries(g.placeholders || {});
    return `
      <section class="pi-card pi-form-group" id="form-group-${esc(g.formType)}">
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-2">
          <div>
            <h2 class="mb-1"><i class="fas ${FORM_ICON[g.formType] || 'fa-file'}"></i>${esc(g.label)}</h2>
            <div class="small">사용 중: <b class="pi-form-current">${esc(g.currentVersionLabel)}</b>
              ${g.usingDefault ? '<span class="pi-status NONE ms-1">시스템 기본 양식</span>' : ''}
              <span class="text-muted ms-1">${esc(g.currentOriginalName)}</span>
              ${g.currentAutofill ? '' : '<span class="pi-status INCOMPLETE ms-1" title="계약 정보가 자동으로 채워지지 않습니다">원본 그대로 사용</span>'}</div>
          </div>
          <div class="pi-actions">
            <button class="btn btn-sm btn-outline-secondary" data-ft-default-dl="${esc(g.formType)}" title="시스템 기본 양식 원본"><i class="fas fa-download me-1"></i>기본 양식</button>
            <button class="btn btn-sm btn-outline-secondary" data-ft-default-preview="${esc(g.formType)}" title="기본 양식에 샘플 계약 정보를 채운 미리보기"><i class="fas fa-eye me-1"></i>기본 미리보기</button>
            ${g.usingDefault ? '' : `<button class="btn btn-sm btn-outline-warning" data-ft-use-default="${esc(g.formType)}" data-type="${esc(g.label)}"><i class="fas fa-rotate-left me-1"></i>기본 양식 사용</button>`}
            <button class="btn btn-sm btn-primary" data-ft-upload="${esc(g.formType)}"><i class="fas fa-upload me-1"></i>새 버전 등록</button>
          </div>
        </div>
        <p class="small text-muted mb-2">${esc(g.description)} <span class="text-nowrap">허용 형식: <b>${esc(g.extensions.join(', '))}</b></span></p>
        ${g.rules && g.rules.length ? `<details class="small mb-2"><summary class="text-primary">체크리스트 양식이 지켜야 하는 구조</summary>
          <ul class="mb-0 mt-1">${g.rules.map((r) => `<li>${esc(r)}</li>`).join('')}</ul></details>` : ''}
        ${ph.length ? `<details class="small mb-2"><summary class="text-primary">docx 자리표시자 (양식 문서에 그대로 적으면 계약 정보로 바뀝니다)</summary>
          <table class="table table-sm mt-1 mb-0 pi-ph-table"><tbody>${ph.map(([k, d]) => `<tr><td class="text-nowrap"><code>${esc(k)}</code></td><td>${esc(d)}</td></tr>`).join('')}</tbody></table></details>` : ''}
        <div class="table-responsive"><table class="pi-table" id="form-versions-${esc(g.formType)}"><thead><tr>
          <th>버전</th><th>파일</th><th>변경 메모</th><th>검사 결과</th><th>등록</th><th>사용 지정</th><th></th></tr></thead>
          <tbody>${g.versions.length ? g.versions.map(vrow).join('')
            : '<tr><td colspan="7" class="pi-empty">등록한 양식이 없어 시스템 기본 양식을 쓰고 있습니다. [새 버전 등록]으로 보안팀 양식을 올리세요.</td></tr>'}</tbody></table></div>
      </section>`;
  }

  function bindForms() {
    const reload = () => renderForms().catch(fail);
    document.getElementById('btn-forms-back').onclick = () => go('#/contracts');
    view.querySelectorAll('[data-ft-dl]').forEach((b) => b.addEventListener('click', () => PI_API.downloadFormTemplate(b.dataset.ftDl).catch(fail)));
    view.querySelectorAll('[data-ft-preview]').forEach((b) => b.addEventListener('click', () => PI_API.previewFormTemplate(b.dataset.ftPreview).catch(fail)));
    view.querySelectorAll('[data-ft-default-dl]').forEach((b) => b.addEventListener('click', () => PI_API.downloadDefaultFormTemplate(b.dataset.ftDefaultDl).catch(fail)));
    view.querySelectorAll('[data-ft-default-preview]').forEach((b) => b.addEventListener('click', () => PI_API.previewDefaultFormTemplate(b.dataset.ftDefaultPreview).catch(fail)));
    view.querySelectorAll('[data-ft-activate]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm(`${b.dataset.type} ${b.dataset.label} 을(를) 사용 중 양식으로 지정합니다.\n계약 화면 다운로드와 만료 알림 메일 첨부에 바로 반영됩니다.`)) return;
      try { await PI_API.activateFormTemplate(b.dataset.ftActivate); notify(`${b.dataset.type} ${b.dataset.label} 을(를) 사용 중으로 지정했습니다.`); reload(); } catch (e) { fail(e); }
    }));
    view.querySelectorAll('[data-ft-use-default]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm(`${b.dataset.type}을(를) 시스템 기본 양식으로 되돌립니다.\n등록한 버전은 지워지지 않아 다시 사용 지정할 수 있습니다.`)) return;
      try { await PI_API.useDefaultFormTemplate(b.dataset.ftUseDefault); notify('기본 양식으로 되돌렸습니다.'); reload(); } catch (e) { fail(e); }
    }));
    view.querySelectorAll('[data-ft-del]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm(`${b.dataset.label} 버전을 삭제합니다. (이력에서 숨겨지며 다시 사용할 수 없습니다)`)) return;
      try { await PI_API.deleteFormTemplate(b.dataset.ftDel); notify('양식 버전을 삭제했습니다.'); reload(); } catch (e) { fail(e); }
    }));
    view.querySelectorAll('[data-ft-upload]').forEach((b) => b.addEventListener('click', () => openFormUpload(b.dataset.ftUpload)));
  }

  function openFormUpload(type) {
    const g = formGroups.find((x) => x.formType === type);
    const f = document.getElementById('form-template-form');
    f.reset();
    document.getElementById('ft-type').value = type;
    document.getElementById('form-template-modal-title').innerHTML = `<i class="fas ${FORM_ICON[type] || 'fa-file'} me-2"></i>${esc(g.label)} — 새 버전 등록`;
    document.getElementById('ft-guide').textContent = g.description;
    document.getElementById('ft-ext').textContent = '(' + g.extensions.join(', ') + ')';
    document.getElementById('ft-file').accept = g.extensions.map((e) => '.' + e).join(',');
    document.getElementById('ft-check').innerHTML = '';
    document.getElementById('ft-submit').disabled = true;
    modal('form-template-modal').show();
  }

  /** 파일을 고르면 저장 전에 검사 결과를 보여주고, 통과해야 [등록] 버튼이 켜진다 */
  let ftSeq = 0;
  document.getElementById('ft-file').addEventListener('change', async (e) => {
    const box = document.getElementById('ft-check');
    const submit = document.getElementById('ft-submit');
    const file = e.target.files?.[0]; const my = ++ftSeq;
    submit.disabled = true;
    if (!file) { box.innerHTML = ''; return; }
    box.innerHTML = '<div class="pi-preview small"><i class="fas fa-spinner fa-spin me-1"></i>양식을 검사하는 중...</div>';
    try {
      const fd = new FormData(); fd.append('file', file);
      const r = await PI_API.checkFormTemplate(document.getElementById('ft-type').value, fd);
      if (my !== ftSeq) return;
      box.innerHTML = `<div class="pi-preview small" id="ft-check-result">
        <div class="fw-bold text-success mb-1"><i class="fas fa-circle-check me-1"></i>등록할 수 있는 양식입니다${r.autofill ? ' (계약 정보 자동 입력)' : ' (원본 그대로 사용)'}</div>
        ${r.info.length ? `<ul class="mb-1">${r.info.map((x) => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}
        ${r.warnings.length ? `<div class="text-warning-emphasis fw-bold mt-1"><i class="fas fa-triangle-exclamation me-1"></i>확인하세요</div>
          <ul class="mb-0 text-warning-emphasis">${r.warnings.map((x) => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}</div>`;
      submit.disabled = false;
    } catch (err) {
      if (my !== ftSeq) return;
      box.innerHTML = `<div class="alert alert-danger small mb-0" id="ft-check-result" style="white-space:pre-line"><i class="fas fa-circle-xmark me-1"></i>${esc(err.message)}</div>`;
    }
  });

  document.getElementById('form-template-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    const fd = new FormData();
    fd.append('file', document.getElementById('ft-file').files[0]);
    if (f.changeNote.value.trim()) fd.append('changeNote', f.changeNote.value.trim());
    fd.append('activate', document.getElementById('ft-activate').checked ? 'true' : 'false');
    const btn = document.getElementById('ft-submit'); btn.disabled = true;
    try {
      const r = await PI_API.uploadFormTemplate(document.getElementById('ft-type').value, fd);
      modal('form-template-modal').hide();
      notify(`${r.versionLabel} 양식을 등록했습니다${r.active ? ' (사용 중으로 지정)' : ''}.` + ((r.warnings || []).length ? '\n' + r.warnings.join('\n') : ''),
        (r.warnings || []).length ? 'warning' : 'success');
      renderForms().catch(fail);
    } catch (err) { fail(err); btn.disabled = false; }
  });

  // ──────────────── 계약만료시 점검 ────────────────
  // 만료 점검 결과(엑셀) = TERMINATION 점검 이력, 확약서/파기확인서 = 첨부파일(fileType)
  const EXPIRY_ITEMS = [
    { key: 'checklist', label: '만료 점검 결과 (엑셀)', icon: 'fa-file-excel text-success', form: '만료 점검 체크리스트', accept: '.xlsx' },
    { key: 'security-pledge', fileType: 'SECURITY_PLEDGE', label: '정보보호 및 비밀유지 확약서', icon: 'fa-file-signature text-primary', form: '확약서 양식', accept: '.pdf,.doc,.docx,.hwp,.hwpx,.jpg,.jpeg,.png,.zip' },
    { key: 'destruction-confirm', fileType: 'DESTRUCTION_CONFIRM', label: '개인정보 파기 확인서', icon: 'fa-file-shield text-danger', form: '파기 확인서 양식', accept: '.pdf,.doc,.docx,.hwp,.hwpx,.jpg,.jpeg,.png,.zip' },
  ];

  function expirySection(c) {
    const byDateDesc = (a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
    const rows = EXPIRY_ITEMS.map((it) => {
      let status; let latest = ''; let count = 0;
      if (it.key === 'checklist') {
        const list = c.inspections.filter((i) => i.inspectionType === 'TERMINATION');
        count = list.length;
        const i = list[0];
        status = i ? `${badge(i.result, i.resultLabel)} <small class="text-muted ms-1">${fmtDate(i.inspectionDate)}</small>` : '<span class="pi-status NONE">미수취</span>';
        if (i) latest = `<button class="btn btn-link btn-sm p-0 text-truncate pi-expiry-file" data-dl-ins="${i.inspectionId}" data-name="${esc(i.originalName)}" title="${esc(i.originalName)}"><i class="fas fa-download me-1"></i>${esc(i.originalName)}</button>`;
      } else {
        const list = c.files.filter((f) => f.fileType === it.fileType).sort(byDateDesc);
        count = list.length;
        const f = list[0];
        status = f ? `<span class="pi-status PASS">수취 완료</span> <small class="text-muted ms-1">${fmtDate(f.createdAt)}</small>
          <small class="d-block ${f.documentDate ? 'text-muted' : 'text-warning-emphasis'}">${esc(f.documentDateLabel || '')} ${f.documentDate ? fmtDate(f.documentDate) : '미입력'}</small>` : '<span class="pi-status NONE">미수취</span>';
        if (f) latest = `<button class="btn btn-link btn-sm p-0 text-truncate pi-expiry-file" data-dl-file="${f.fileId}" data-name="${esc(f.originalName)}" title="${esc(f.originalName)}"><i class="fas fa-download me-1"></i>${esc(f.originalName)}</button>`;
      }
      return `<tr data-expiry="${it.key}" style="cursor:default">
        <td class="text-nowrap"><i class="fas ${it.icon} me-2"></i><b>${esc(it.label)}</b></td>
        <td class="text-nowrap"><button class="btn btn-sm btn-outline-secondary" data-expiry-form="${it.key}" title="이 계약 정보가 채워진 양식"><i class="fas fa-download me-1"></i>${esc(it.form)}</button></td>
        <td class="text-nowrap">${status}${count > 1 ? ` <small class="text-muted">(총 ${count}건)</small>` : ''}</td>
        <td class="pi-ellipsis" style="max-width:260px">${latest || '<span class="text-muted">-</span>'}</td>
        <td class="text-end text-nowrap">${perm.canWrite ? `<button class="btn btn-sm btn-primary" data-expiry-upload="${it.key}"><i class="fas fa-upload me-1"></i>업로드</button>
          ${it.fileType ? `<input type="file" class="d-none" data-expiry-input="${it.key}" accept="${it.accept}" aria-label="${esc(it.label)} 파일">` : ''}` : ''}</td></tr>`;
    });
    const done = EXPIRY_ITEMS.filter((it) => it.key === 'checklist'
      ? c.inspections.some((i) => i.inspectionType === 'TERMINATION')
      : c.files.some((f) => f.fileType === it.fileType)).length;
    return `
      <section class="pi-card" id="expiry-check-section">
        <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
          <h2 class="mb-0"><i class="fas fa-file-circle-check"></i>계약만료시 점검
            <small class="fw-normal ${done === 3 ? 'text-success' : 'text-muted'}">(수취 ${done} / 3)</small></h2>
          <button class="btn btn-sm btn-outline-success" id="btn-expiry-forms-all"><i class="fas fa-file-arrow-down me-1"></i>양식 3종 모두 다운로드</button>
        </div>
        <p class="small text-muted mb-2"><i class="fas fa-circle-info me-1"></i>계약 만료 전에 협력사로부터 아래 3가지 자료를 받아 올려 주세요. 만료 알림 메일에는 이 3가지 양식이 계약 정보가 채워진 상태로 첨부되어 담당자에게 발송됩니다.
          만료 점검 결과는 체크리스트의 <b>3.계약변경 만료시 체크</b> 시트를 읽어 판정합니다.</p>
        <div class="table-responsive"><table class="pi-table" id="expiry-check-table"><thead><tr><th>점검 항목</th><th>양식</th><th>제출 현황</th><th>최근 제출 파일</th><th></th></tr></thead>
          <tbody>${rows.join('')}</tbody></table></div>
      </section>`;
  }

  function bindExpiry(c, reload) {
    const id = c.contractId;
    const sec = document.getElementById('expiry-check-section');
    if (!sec) return;
    sec.querySelectorAll('[data-expiry-form]').forEach((b) => b.addEventListener('click', () => PI_API.downloadExpiryForm(id, b.dataset.expiryForm).catch(fail)));
    document.getElementById('btn-expiry-forms-all').onclick = async () => {
      try { for (const it of EXPIRY_ITEMS) await PI_API.downloadExpiryForm(id, it.key); } catch (e) { fail(e); }
    };
    sec.querySelectorAll('[data-expiry-upload]').forEach((b) => b.addEventListener('click', () => {
      const key = b.dataset.expiryUpload;
      if (key === 'checklist') {
        document.getElementById('inspection-form').reset();
        document.getElementById('ins-type').value = 'TERMINATION';
        modal('inspection-modal').show();
        return;
      }
      sec.querySelector(`[data-expiry-input="${key}"]`).click();
    }));
    sec.querySelectorAll('[data-expiry-input]').forEach((inp) => inp.addEventListener('change', async () => {
      const f = inp.files?.[0]; if (!f) return;
      const it = EXPIRY_ITEMS.find((x) => x.key === inp.dataset.expiryInput);
      const ans = await askDocDate({ fileType: it.fileType, fileName: f.name });
      if (!ans) { inp.value = ''; return; }
      const fd = new FormData();
      fd.append('file', f); fd.append('fileType', it.fileType); fd.append('description', '계약만료시 점검 제출');
      if (ans.date) fd.append('documentDate', ans.date);
      if (ans.note) fd.append('documentNote', ans.note);
      try { await PI_API.uploadFile(id, fd); notify(`${it.label}을(를) 업로드했습니다.`); reload(); } catch (e) { fail(e); inp.value = ''; }
    }));
  }

  async function renderDetail(id) {
    const c = await PI_API.getContract(id);
    currentContract = c;
    crumb.innerHTML = `<a href="#/contracts">계약 목록</a> &gt; ${esc(c.vendorName)}${c.contractName ? ' &gt; ' + esc(c.contractName) : ''}`;
    const row = (k, v) => `<dt>${esc(k)}</dt><dd>${v}</dd>`;
    view.innerHTML = `
      <section class="pi-card" id="contract-detail-section">
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
          <h2 class="mb-0"><i class="fas fa-building"></i>${esc(c.vendorName)}${c.contractName ? ` <span class="pi-contract-name">${esc(c.contractName)}</span>` : ''} ${badge(c.status, c.statusLabel)}
            <small class="text-muted fw-normal ms-1">${c.status === 'TERMINATED' ? '' : dLabel(c.daysLeft)}${c.autoRenewing ? ' · 자동연장' : ''}</small></h2>
          <div class="pi-actions">
            <button class="btn btn-sm btn-light" id="btn-back"><i class="fas fa-list me-1"></i>목록</button>
            ${perm.canSendMail ? '<button class="btn btn-sm btn-outline-primary" id="btn-mail"><i class="fas fa-envelope me-1"></i>만료 안내 메일</button>' : ''}
            ${perm.canWrite ? (c.terminatedYn === 'Y'
              ? '<button class="btn btn-sm btn-outline-secondary" id="btn-reopen"><i class="fas fa-rotate-left me-1"></i>종료 취소</button>'
              : '<button class="btn btn-sm btn-outline-secondary" id="btn-terminate"><i class="fas fa-ban me-1"></i>계약 종료 처리</button>') : ''}
            ${perm.canWrite ? '<button class="btn btn-sm btn-primary" id="btn-edit"><i class="fas fa-pen me-1"></i>수정</button>' : ''}
            ${perm.canDelete ? '<button class="btn btn-sm btn-outline-danger" id="btn-delete"><i class="fas fa-trash me-1"></i>삭제</button>' : ''}
          </div>
        </div>
        <dl class="pi-dl two">
          ${row('계약명', esc(c.contractName || '-'))}${row('관련 서비스', esc(c.serviceName || '-'))}
          ${row('ISMS 범위', yn(c.ismsScopeYn))}${row('', '')}
          ${row('위탁업무 내용', esc(c.entrustedTask))}${row('담당부서 / 담당자', esc((c.deptName || '-') + ' / ' + (c.managerName || '-')))}
          ${row('담당자 이메일', esc(c.managerEmail || '-'))}${row('수탁사 담당자', esc([c.vendorContactName, c.vendorContactPhone, c.vendorContactEmail].filter(Boolean).join(' / ') || '-'))}
          ${row('원계약 기간', fmtDate(c.contractStartDate) + ' ~ ' + fmtDate(c.contractEndDate))}${row('원계약 기간 비고', esc(c.contractPeriodNote || '-'))}
          ${row('현재 계약기간', currentPeriodText(c))}${row('', '')}
          ${row('원계약 자동연장', c.autoRenewYn === 'Y'
            ? `Y · <b>${renewLabel(c.autoRenewPeriodMonths)} 단위</b>${c.autoRenewNote ? ' (' + esc(c.autoRenewNote) + ')' : ''}${c.autoRenewing
              ? (c.autoRenewCount > 0
                ? `<br><span class="pi-term-chip renew"><i class="fas fa-rotate"></i>자동연장 ${c.autoRenewCount}회</span>`
                : '<br><small class="text-muted">원계약 종료 후 자동연장 — 만료 대상 아님</small>')
                + (c.alertYn === 'Y' ? `<br><small class="text-muted"><i class="fas fa-envelope me-1"></i>연장일 ${c.alertDaysBefore}일 전 담당자에게 유지 여부 확인 메일</small>` : '<br><small class="text-warning-emphasis">만료 알림 미사용 — 자동연장 확인 메일도 보내지 않음</small>')
              : ''}`
            : yn(c.autoRenewYn) + (c.autoRenewNote ? ' (' + esc(c.autoRenewNote) + ')' : ''))}${row('개인정보처리 위탁계약서 작성 / 체결일', yn(c.contractDocYn) + ' / ' + fmtDate(c.contractSignedDate))}
          ${row('재위탁', c.subcontractYn === 'Y' ? esc(c.subcontractorName || '') + (c.subcontractTask ? ' — ' + esc(c.subcontractTask) : '') : 'N')}${row('재위탁 동의서', yn(c.subcontractConsentYn))}
          ${row('만료 알림', c.alertYn === 'Y' ? `사용 (만료 ${c.alertDaysBefore}일 전부터)` : '미사용')}${row('추가 수신자', esc(c.alertExtraEmails || '-'))}
          ${c.terminatedYn === 'Y' ? row('종료일 / 사유', fmtDate(c.terminatedDate) + ' / ' + esc(c.terminationReason || '-'))
            + row('만료 서류 미수취 사유', c.expiryDocsNote ? `<span class="text-danger">${esc(c.expiryDocsNote)}</span>` : '-') : ''}
          ${row('비고', esc(c.remark || '-'))}${row('등록 / 수정', fmtDateTime(c.createdAt) + ' / ' + fmtDateTime(c.updatedAt))}
        </dl>
      </section>

      ${followUpPanel(c)}

      <section class="pi-card" id="same-vendor-section">
        <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
          <h2 class="mb-0"><i class="fas fa-layer-group"></i>같은 업체 계약 <small class="text-muted fw-normal" id="same-vendor-count"></small></h2>
          ${canCreate() ? '<button class="btn btn-sm btn-outline-primary" id="btn-add-same-vendor"><i class="fas fa-plus me-1"></i>이 업체로 계약 추가</button>' : ''}
        </div>
        <div id="same-vendor-body"><div class="pi-empty">불러오는 중...</div></div>
      </section>

      <section class="pi-card" id="contact-history-section">
        <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
          <h2 class="mb-0"><i class="fas fa-user-clock"></i>협력사 담당자 이력 <small class="text-muted fw-normal">(위탁사 담당자)</small> <small class="text-muted fw-normal" id="contact-history-count"></small></h2>
          <div class="btn-group btn-group-sm" role="group" aria-label="이력 범위">
            <input type="radio" class="btn-check" name="ch-scope" id="ch-scope-vendor" value="vendor" checked>
            <label class="btn btn-outline-secondary" for="ch-scope-vendor">업체 전체 계약</label>
            <input type="radio" class="btn-check" name="ch-scope" id="ch-scope-contract" value="contract">
            <label class="btn btn-outline-secondary" for="ch-scope-contract">이 계약만</label>
          </div>
        </div>
        <div id="contact-history-body"><div class="pi-empty">불러오는 중...</div></div>
      </section>

      <section class="pi-card" id="contract-file-section">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h2 class="mb-0"><i class="fas fa-paperclip"></i>위수탁 계약서 / 증빙 (${c.files.length})</h2>
          ${perm.canWrite ? `<form id="file-upload-form" class="d-flex gap-2 align-items-center">
            <select class="form-select form-select-sm w-auto" name="fileType" aria-label="파일 구분">
              <option value="CONTRACT">위수탁 계약서</option><option value="SUBCONTRACT_CONSENT">재위탁 동의서</option>
              <option value="SECURITY_PLEDGE">정보보호 및 비밀유지 확약서</option><option value="DESTRUCTION_CONFIRM">개인정보 파기 확인서</option>
              <option value="ETC">기타</option></select>
            <input type="file" class="form-control form-control-sm" name="file" required aria-label="파일" accept=".pdf,.doc,.docx,.hwp,.hwpx,.xls,.xlsx,.jpg,.jpeg,.png,.zip">
            <input type="date" class="form-control form-control-sm w-auto" name="signedDate" id="upload-signed-date" title="계약서 체결일 (위수탁 계약서만, 파일을 고르면 자동 입력)" aria-label="계약서 체결일">
            <button class="btn btn-sm btn-primary text-nowrap" type="submit"><i class="fas fa-upload me-1"></i>업로드</button></form>` : ''}
        </div>
        <p class="small text-muted mb-2" id="contract-doc-rule"><i class="fas fa-circle-info me-1"></i>위수탁 계약서는 체결일자별로 여러 건 올릴 수 있으며, 계약의 <b>개인정보처리 위탁계약 체결일</b>은 <b>체결일이 가장 최근인 위수탁 계약서</b>의 날짜로 표시됩니다. 업로드할 때 계약서에서 날짜를 읽고(체결일 표기가 없으면 계약기간 시작일), 직접 고칠 수도 있습니다.</p>
        <div class="table-responsive"><table class="pi-table" id="file-table"><thead><tr><th>구분</th><th>파일명</th><th>계약서 체결일 / 서류 일자</th><th>크기</th><th>설명</th><th>등록일시</th><th></th></tr></thead>
          <tbody>${c.files.length ? sortFiles(c.files).map((f) => `
            <tr class="${f.latestContractDoc ? 'pi-latest-doc' : ''}"><td>${esc(f.fileTypeLabel)}</td><td>${esc(f.originalName)}</td>
              <td class="text-nowrap">${f.fileType === 'CONTRACT' ? signedDateCell(f) : (f.documentDateLabel ? documentDateCell(f) : '<span class="text-muted">-</span>')}</td>
              <td>${fmtSize(f.fileSize)}</td><td>${esc(f.description || '')}</td>
              <td>${fmtDateTime(f.createdAt)}</td>
              <td class="text-end text-nowrap"><button class="btn btn-sm btn-light" data-dl-file="${f.fileId}" data-name="${esc(f.originalName)}"><i class="fas fa-download"></i></button>
                ${perm.canDelete ? `<button class="btn btn-sm btn-light text-danger" data-del-file="${f.fileId}" aria-label="삭제"><i class="fas fa-trash"></i></button>` : ''}</td></tr>`).join('')
            : '<tr><td colspan="7" class="pi-empty">등록된 계약서가 없습니다.</td></tr>'}</tbody></table></div>
      </section>

      <section class="pi-card" id="inspection-section">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h2 class="mb-0"><i class="fas fa-clipboard-check"></i>위수탁 점검 이력 (${c.inspections.length})</h2>
          <div class="pi-actions">
            <button class="btn btn-sm btn-outline-success" id="btn-dl-template"><i class="fas fa-file-excel me-1"></i>체크리스트 양식 다운로드</button>
            ${perm.canWrite ? '<button class="btn btn-sm btn-outline-primary" id="btn-vendor-upload" title="같은 업체의 다른 계약에도 함께 등록"><i class="fas fa-building me-1"></i>업체 계약 일괄 업로드</button>' : ''}
            ${perm.canWrite ? '<button class="btn btn-sm btn-primary" id="btn-upload-inspection"><i class="fas fa-upload me-1"></i>작성된 체크리스트 업로드</button>' : ''}
          </div>
        </div>
        <div class="table-responsive"><table class="pi-table" id="inspection-table"><thead><tr><th>점검일</th><th>구분</th><th>평가자</th><th>결과</th><th>Y / N / NA / 미기재</th><th>조치 예정일</th><th>조치 완료</th><th>파일</th><th></th></tr></thead>
          <tbody>${c.inspections.length ? c.inspections.map((i) => `
            <tr data-ins="${i.inspectionId}"><td>${fmtDate(i.inspectionDate)}</td><td>${esc(i.inspectionTypeLabel)}${i.uploadGroupId ? ' <span class="badge text-bg-light border" title="업체별 일괄 업로드로 등록">업체 일괄</span>' : ''}</td><td>${esc(i.inspectorName || '-')}</td>
              <td>${badge(i.result, i.resultLabel)}</td>
              <td><span class="pi-eval-Y">${i.yCount}</span> / <span class="pi-eval-N">${i.nCount}</span> / <span class="pi-eval-NA">${i.naCount}</span> / ${i.blankCount}</td>
              <td>${fmtDate(i.actionDueDate)}</td><td>${yn(i.actionCompletedYn)}</td>
              <td class="pi-ellipsis" style="max-width:180px">${esc(i.originalName)}</td>
              <td class="text-end text-nowrap"><button class="btn btn-sm btn-light" data-dl-ins="${i.inspectionId}" data-name="${esc(i.originalName)}" aria-label="다운로드"><i class="fas fa-download"></i></button>
                ${perm.canDelete ? `<button class="btn btn-sm btn-light text-danger" data-del-ins="${i.inspectionId}" aria-label="삭제"><i class="fas fa-trash"></i></button>` : ''}</td></tr>`).join('')
            : '<tr><td colspan="9" class="pi-empty">점검 이력이 없습니다. 체크리스트 양식을 내려받아 협력사에 전달한 뒤, 작성본을 업로드하세요.</td></tr>'}</tbody></table></div>
      </section>

      ${expirySection(c)}

      <section class="pi-card" id="alert-log-section">
        <h2><i class="fas fa-bell"></i>알림 발송 이력 <small class="text-muted fw-normal">(최근 20건)</small></h2>
        <div class="table-responsive"><table class="pi-table" id="alert-log-table"><thead><tr><th>일시</th><th>종류</th><th>경로</th><th>단계</th><th>수신자</th><th>상태</th><th>첨부 양식</th><th>사유</th></tr></thead>
          <tbody>${c.alerts.length ? c.alerts.map((a) => `
            <tr style="cursor:default"><td>${fmtDateTime(a.sentAt || a.createdAt)}</td><td class="text-nowrap">${esc(a.alertTypeLabel || '만료 알림')}</td><td>${a.triggerType === 'AUTO' ? '자동' : '수동'}</td><td>${esc(a.alertStage)}</td>
              <td class="pi-ellipsis" title="${esc(a.recipients)}">${esc(a.recipients || '-')}</td><td>${badge(a.sendStatus, a.sendStatus)}</td>
              <td class="pi-ellipsis small" title="${esc(a.formVersions)}">${esc(a.formVersions || '-')}</td>
              <td class="pi-ellipsis" title="${esc(a.errorMessage)}">${esc(a.errorMessage || '')}</td></tr>`).join('')
            : '<tr><td colspan="8" class="pi-empty">발송 이력이 없습니다.</td></tr>'}</tbody></table></div>
      </section>`;

    PiTable.enhance(document.getElementById('file-table'), { id: 'files' });
    PiTable.enhance(document.getElementById('inspection-table'), { id: 'inspections' });
    PiTable.enhance(document.getElementById('expiry-check-table'), { id: 'expiryCheck' });
    PiTable.enhance(document.getElementById('alert-log-table'), { id: 'alerts' });
    bindDetail(c);
  }

  // ──────────────── 협력사 담당자 이력 ────────────────
  const periodText = (h) => (h.contractStartDate || h.contractEndDate)
    ? `${fmtDate(h.contractStartDate)} ~ ${fmtDate(h.contractEndDate)}`
    : (h.contractPeriodNote || '-');

  async function loadContactHistory(contractId, vendorWide) {
    const body = document.getElementById('contact-history-body');
    if (!body) return;
    try {
      const list = await PI_API.contactHistory(contractId, vendorWide);
      document.getElementById('contact-history-count').textContent = `(${list.length}건)`;
      if (!list.length) { body.innerHTML = '<div class="pi-empty">담당자 이력이 없습니다.</div>'; return; }
      // 바뀐 항목은 강조. 등록/이관 건은 강조 없음
      const cell = (h, field, value) => {
        const changed = h.changedFields.includes(field);
        return `<span class="${changed ? 'pi-ch-changed' : ''}"${changed ? ' title="이 이력에서 변경된 항목"' : ''}>${esc(value || '-')}</span>`;
      };
      body.innerHTML = `
        <div class="table-responsive"><table class="pi-table" id="contact-history-table"><thead><tr>
          <th>변경일시</th><th>구분</th>${vendorWide ? '<th>계약 (관련 서비스)</th>' : ''}
          <th>계약기간</th><th>담당부서</th><th>담당자</th><th>담당자 이메일</th><th>변경자</th></tr></thead>
          <tbody>${list.map((h, i) => `
            <tr class="pi-ch-row ${h.currentContract ? '' : 'pi-ch-other'}" data-idx="${i}" tabindex="0" role="button" title="클릭하면 이 이력의 내역을 봅니다">
              <td class="text-nowrap">${fmtDateTime(h.changedAt)}</td>
              <td class="text-nowrap"><span class="pi-status ${h.changeType === 'UPDATE' ? 'EXPIRING' : 'NONE'}">${esc(h.changeTypeLabel)}</span></td>
              ${vendorWide ? `<td>${h.currentContract ? '<b>이 계약</b>' : `<span class="pi-ch-contract-link">${esc(h.vendorName || '')}</span>`}
                <small class="text-muted d-block">${esc(contractLabel(h))}</small></td>` : ''}
              <td class="text-nowrap">${esc(periodText(h))}${(h.contractStartDate || h.contractEndDate) && h.contractPeriodNote ? `<small class="text-muted d-block">${esc(h.contractPeriodNote)}</small>` : ''}</td>
              <td>${cell(h, 'DEPT_NAME', h.deptName)}</td>
              <td>${cell(h, 'MANAGER_NAME', h.managerName)}</td>
              <td>${cell(h, 'MANAGER_EMAIL', h.managerEmail)}</td>
              <td class="text-nowrap">${esc(h.changedByName || (h.changeType === 'INITIAL' ? '시스템' : '-'))}</td>
            </tr>`).join('')}</tbody></table></div>
        <small class="text-muted">위탁사(당사) 담당자가 바뀔 때만 기록합니다(수탁사 담당자·연락처 변경은 기록하지 않음). 각 행은 변경 <b>후</b> 담당자와 그 시점의 계약기간이며, <span class="pi-ch-changed">노란색</span>은 그 이력에서 바뀐 항목입니다.${vendorWide ? ' 같은 업체(㈜·(주)·공백 차이 무시)의 다른 계약 이력도 함께 보여줍니다.' : ''} <b>행을 누르면 그 시점의 내역(변경 전·후, 계약 정보)을 팝업으로 봅니다.</b></small>`;
      PiTable.enhance(document.getElementById('contact-history-table'), { id: 'contactHistory' });
      body.querySelectorAll('tr.pi-ch-row').forEach((tr) => {
        const open = () => openContactHistoryDetail(list, Number(tr.dataset.idx));
        tr.addEventListener('click', open);
        tr.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
      });
    } catch (e) {
      body.innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  /**
   * 담당자 이력 행 클릭 → 그 시점 내역 팝업
   *  - 변경 전 → 변경 후 (같은 계약의 바로 이전 이력과 비교)
   *  - 그 시점의 계약기간·변경자·변경일시
   *  - 해당 계약의 현재 정보 (이전/다른 계약이면 [이 계약 상세로 이동])
   */
  const contractCache = new Map();
  async function openContactHistoryDetail(list, idx) {
    const h = list[idx];
    if (!h) return;
    const prev = list.slice(idx + 1).find((x) => x.contractId === h.contractId) || null;
    const body = document.getElementById('contact-history-modal-body');
    const gotoBtn = document.getElementById('btn-ch-goto');
    const isCurrent = h.currentContract;
    gotoBtn.classList.toggle('d-none', isCurrent);
    gotoBtn.onclick = () => { modal('contact-history-modal').hide(); go('#/contracts/' + h.contractId); };
    document.getElementById('contact-history-modal-title').innerHTML =
      `<i class="fas fa-clock-rotate-left me-2 text-primary"></i>담당자 이력 내역 <small class="text-muted fw-normal ms-1">${fmtDateTime(h.changedAt)} · ${esc(h.changeTypeLabel)}</small>`;

    const rows = [
      ['계약기간', prev ? periodText(prev) : null, periodText(h)],
      ['계약기간 비고', prev ? prev.contractPeriodNote : null, h.contractPeriodNote],
      ['담당부서', prev ? prev.deptName : null, h.deptName],
      ['담당자', prev ? prev.managerName : null, h.managerName],
      ['담당자 이메일', prev ? prev.managerEmail : null, h.managerEmail],
    ];
    const diffTable = `
      <table class="pi-table pi-ch-diff mb-2"><thead><tr><th style="width:140px">항목</th><th>변경 전</th><th></th><th>변경 후 (이 이력)</th></tr></thead>
        <tbody>${rows.map(([k, b, a]) => {
          const changed = prev && String(b || '') !== String(a || '');
          return `<tr class="${changed ? 'pi-ch-diff-changed' : ''}"><th>${esc(k)}</th>
            <td class="text-muted">${prev ? esc(b || '-') : '<span class="text-muted">(처음 기록)</span>'}</td>
            <td class="text-center">${changed ? '<i class="fas fa-arrow-right text-warning"></i>' : ''}</td>
            <td>${changed ? `<span class="pi-ch-changed">${esc(a || '-')}</span>` : esc(a || '-')}</td></tr>`;
        }).join('')}</tbody></table>
      <p class="small text-muted mb-3">변경자 <b>${esc(h.changedByName || (h.changeType === 'INITIAL' ? '시스템' : '-'))}</b> · 변경일시 ${fmtDateTime(h.changedAt)}
        ${prev ? ` · 비교 대상: ${fmtDateTime(prev.changedAt)} 이력` : ' · 이 계약의 첫 이력입니다'}</p>`;
    body.innerHTML = diffTable + '<div id="ch-modal-contract"><div class="pi-empty">계약 정보를 불러오는 중...</div></div>';
    modal('contact-history-modal').show();

    try {
      let c = contractCache.get(h.contractId);
      if (!c) { c = await PI_API.getContract(h.contractId); contractCache.set(h.contractId, c); }
      const row = (k, v) => `<dt>${esc(k)}</dt><dd>${v}</dd>`;
      const docs = (c.files || []).filter((f) => f.fileType === 'CONTRACT');
      const latestDoc = docs.find((f) => f.latestContractDoc);
      document.getElementById('ch-modal-contract').innerHTML = `
        <h6 class="fw-bold mb-2"><i class="fas fa-building me-1"></i>${isCurrent ? '이 계약' : '해당 계약'} 정보
          <small class="text-muted fw-normal">(현재 기준)</small> ${badge(c.status, c.statusLabel)}</h6>
        <dl class="pi-dl pi-dl-compact">
          ${row('수탁업체 / 계약명', esc(c.vendorName) + ' / ' + esc(contractLabel(c)))}
          ${row('위탁업무 내용', esc(c.entrustedTask || '-'))}
          ${row('원계약 기간', (c.contractStartDate || c.contractEndDate) ? fmtDate(c.contractStartDate) + ' ~ ' + fmtDate(c.contractEndDate) : esc(c.contractPeriodNote || '-'))}
          ${row('현재 담당부서 / 담당자', esc(c.deptName || '-') + ' / ' + esc(c.managerName || '-') + (c.managerEmail ? ' <small class="text-muted">(' + esc(c.managerEmail) + ')</small>' : ''))}
          ${row('개인정보처리 위탁계약서 작성 / 체결일', yn(c.contractDocYn) + ' / ' + fmtDate(c.contractSignedDate) + (docs.length ? ` <small class="text-muted">(위수탁 계약서 ${docs.length}건${latestDoc ? ', 최신 ' + esc(latestDoc.originalName) : ''})</small>` : ''))}
          ${row('최근 점검', badge(c.lastInspectionResult || 'NONE', c.lastInspectionResultLabel) + (c.lastInspectionDate ? ' ' + fmtDate(c.lastInspectionDate) : ''))}
          ${c.terminatedYn === 'Y' ? row('종료일 / 사유', fmtDate(c.terminatedDate) + ' / ' + esc(c.terminationReason || '-')) : ''}
        </dl>`;
    } catch (e) {
      document.getElementById('ch-modal-contract').innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  function bindDetail(c) {
    const id = c.contractId;
    loadSameVendor(c);
    document.getElementById('btn-add-same-vendor')?.addEventListener('click', () => go('#/contracts/new?vendor=' + encodeURIComponent(c.vendorName)));
    loadContactHistory(id, true);
    document.querySelectorAll('input[name="ch-scope"]').forEach((r) =>
      r.addEventListener('change', () => loadContactHistory(id, document.getElementById('ch-scope-vendor').checked)));
    const reload = () => renderDetail(id).catch(fail);
    document.getElementById('btn-back').onclick = () => go('#/contracts');
    document.getElementById('btn-edit')?.addEventListener('click', () => go(`#/contracts/${id}/edit`));
    document.getElementById('btn-delete')?.addEventListener('click', async () => {
      if (!confirm(`'${c.vendorName}' 계약을 삭제합니다. (첨부/점검 이력도 함께 숨겨집니다)`)) return;
      try { await PI_API.deleteContract(id); notify('삭제되었습니다.'); go('#/contracts'); } catch (e) { fail(e); }
    });
    document.getElementById('btn-terminate')?.addEventListener('click', () => {
      // 종료일 기본값 = 원계약 종료일 (없으면 오늘)
      const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
      const dateEl = document.getElementById('term-date');
      dateEl.value = c.currentEndDate || c.contractEndDate || today;
      dateEl.min = c.contractStartDate || '';
      document.getElementById('term-reason').value = '계약 만료';
      const missing = (c.followUp && c.followUp.missingDocs) || [];
      document.getElementById('term-docs-box').classList.toggle('d-none', !missing.length);
      document.getElementById('term-docs-note').value = '';
      document.getElementById('term-docs-note').required = missing.length > 0;
      document.getElementById('term-docs-remind').checked = true;
      document.getElementById('term-docs-missing').innerHTML = missing.length
        ? `<i class="fas fa-triangle-exclamation me-1"></i>아직 받지 못한 만료 서류 <b>${missing.length}종</b>: ${esc(missing.join(', '))}<br>미수취 사유를 남겨야 종료 처리할 수 있습니다.` : '';
      document.getElementById('term-date-hint').textContent = c.autoRenewing && c.autoRenewCount > 0
        ? `기본값은 자동연장된 현재 계약 종료일(${fmtDate(c.currentEndDate)})입니다. 원계약 종료일은 ${fmtDate(c.contractEndDate)}. 실제 종료일로 바꾸세요.`
        : c.contractEndDate
        ? `기본값은 원계약 종료일(${fmtDate(c.contractEndDate)})입니다. 중도 해지 등은 실제 종료일로 바꾸세요.`
        : '원계약 종료일이 없어 오늘 날짜를 기본값으로 넣었습니다.';
      modal('terminate-modal').show();
    });
    document.getElementById('btn-reopen')?.addEventListener('click', async () => {
      if (!confirm('계약 종료 처리를 취소합니다.')) return;
      try { await PI_API.reopenContract(id); notify('종료 처리가 취소되었습니다.'); reload(); } catch (e) { fail(e); }
    });

    // 계약서 업로드
    const fileForm = document.getElementById('file-upload-form');
    if (fileForm) {
      const typeSel = fileForm.querySelector('[name="fileType"]');
      const fileSel = fileForm.querySelector('[name="file"]');
      const dateSel = document.getElementById('upload-signed-date');
      const syncDateBox = () => { const on = typeSel.value === 'CONTRACT'; dateSel.disabled = !on; if (!on) dateSel.value = ''; };
      typeSel.addEventListener('change', syncDateBox); syncDateBox();
      // 위수탁 계약서를 고르면 파일에서 체결일을 읽어 채움 (직접 고칠 수 있음)
      let aseq = 0;
      fileSel.addEventListener('change', async () => {
        const f = fileSel.files?.[0]; const my = ++aseq;
        if (!f || typeSel.value !== 'CONTRACT') return;
        dateSel.value = ''; dateSel.placeholder = '읽는 중';
        try {
          const fd = new FormData(); fd.append('file', f);
          const r = await PI_API.analyzeContractDoc(fd);
          if (my !== aseq) return;
          if (r.detectedDate) { dateSel.value = r.detectedDate; dateSel.classList.remove('pi-autofilled'); void dateSel.offsetWidth; dateSel.classList.add('pi-autofilled'); }
          notify(r.detectedDate ? `${r.message} (업로드 전에 고칠 수 있습니다)` : r.message, r.detectedDate ? 'info' : 'warning');
        } catch (err) { if (my === aseq) notify('계약서 날짜를 읽지 못했습니다. 체결일을 직접 입력하세요.', 'warning'); }
      });
      fileForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fd = new FormData(fileForm);
        if (!fd.get('signedDate')) fd.delete('signedDate');
        const ft = fd.get('fileType');
        if (ft === 'SECURITY_PLEDGE' || ft === 'DESTRUCTION_CONFIRM') {
          const ans = await askDocDate({ fileType: ft, fileName: fileSel.files?.[0]?.name });
          if (!ans) return;
          if (ans.date) fd.append('documentDate', ans.date);
          if (ans.note) fd.append('documentNote', ans.note);
        }
        try { await PI_API.uploadFile(id, fd); notify('업로드되었습니다.'); reload(); } catch (err) { fail(err); }
      });
    }
    // 위수탁 계약서 체결일 수정
    document.querySelectorAll('[data-edit-signed]').forEach((b) => b.addEventListener('click', () => {
      const td = b.closest('td'); const cur = b.dataset.date || '';
      td.innerHTML = `<div class="d-flex gap-1 align-items-center"><input type="date" class="form-control form-control-sm pi-signed-edit" value="${esc(cur)}" aria-label="계약서 체결일 수정" style="width:150px">
        <button class="btn btn-sm btn-primary" data-act="save" aria-label="저장"><i class="fas fa-check"></i></button>
        <button class="btn btn-sm btn-light" data-act="cancel" aria-label="취소"><i class="fas fa-xmark"></i></button></div>`;
      const inp = td.querySelector('input'); inp.focus();
      td.querySelector('[data-act="cancel"]').onclick = () => reload();
      td.querySelector('[data-act="save"]').onclick = async () => {
        try { await PI_API.updateFileSignedDate(b.dataset.editSigned, inp.value || null); notify('계약서 체결일을 저장했습니다. 계약 체결일은 가장 최근 계약서 기준으로 다시 맞췄습니다.'); reload(); }
        catch (err) { fail(err); }
      };
    }));
    // 확약서 서명일 / 파기 확인서 파기 완료일 (+ 파기 지연 사유) 입력·수정
    document.querySelectorAll('[data-edit-docdate]').forEach((b) => b.addEventListener('click', async () => {
      const isDest = b.dataset.filetype === 'DESTRUCTION_CONFIRM';
      const ans = await askDocDate({ fileType: b.dataset.filetype, fileName: b.dataset.name, current: b.dataset.date || '', note: b.dataset.note || '' });
      if (!ans) return;
      try {
        await PI_API.updateFileDocumentDate(b.dataset.editDocdate, ans.date, isDest ? ans.note : undefined);
        notify(`${b.dataset.label}을 저장했습니다.`); reload();
      } catch (err) { fail(err); }
    }));
    // 확인 필요 안내의 바로가기
    document.querySelectorAll('.pi-fu-jump').forEach((a) => a.addEventListener('click', (e) => {
      e.preventDefault();
      const sec = document.getElementById(a.dataset.target);
      sec?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      if (a.dataset.filetype) { const sel = document.querySelector('#file-upload-form [name="fileType"]'); if (sel) { sel.value = a.dataset.filetype; sel.dispatchEvent(new Event('change')); } }
    }));
    document.querySelectorAll('[data-open-ins]').forEach((a) => a.addEventListener('click', (e) => { e.preventDefault(); showInspection(a.dataset.openIns); }));
    document.getElementById('btn-toggle-remind')?.addEventListener('click', async (e) => {
      const yn2 = e.currentTarget.dataset.yn;
      try { await PI_API.changeExpiryDocsRemind(id, yn2); notify(yn2 === 'Y' ? '독촉 메일을 다시 보냅니다(주 1회).' : '이 계약의 독촉 메일을 껐습니다.'); reload(); } catch (err) { fail(err); }
    });
    document.querySelectorAll('[data-dl-file]').forEach((b) => b.addEventListener('click', () => PI_API.downloadFile(b.dataset.dlFile, b.dataset.name).catch(fail)));
    document.querySelectorAll('[data-del-file]').forEach((b) => b.addEventListener('click', async () => {
      if (!confirm('첨부파일을 삭제합니다.')) return;
      try { await PI_API.deleteFile(b.dataset.delFile); notify('삭제되었습니다.'); reload(); } catch (e) { fail(e); }
    }));

    // 체크리스트
    document.getElementById('btn-dl-template').onclick = () => PI_API.downloadTemplate(id).catch(fail);
    document.getElementById('btn-vendor-upload')?.addEventListener('click', () => openVendorChecklist(c.vendorName));
    document.getElementById('btn-upload-inspection')?.addEventListener('click', () => {
      const f = document.getElementById('inspection-form');
      f.reset();
      document.getElementById('ins-type').value = c.terminatedYn === 'Y' ? 'TERMINATION' : 'REGULAR';
      modal('inspection-modal').show();
    });
    document.querySelectorAll('[data-dl-ins]').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); PI_API.downloadInspection(b.dataset.dlIns, b.dataset.name).catch(fail); }));
    document.querySelectorAll('[data-del-ins]').forEach((b) => b.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (!confirm('점검 이력을 삭제합니다.')) return;
      try { await PI_API.deleteInspection(b.dataset.delIns); notify('삭제되었습니다.'); reload(); } catch (err) { fail(err); }
    }));
    document.querySelectorAll('tr[data-ins]').forEach((tr) => tr.addEventListener('click', () => showInspection(tr.dataset.ins)));
    bindExpiry(c, reload);

    // 메일
    document.getElementById('btn-mail')?.addEventListener('click', () => {
      document.getElementById('mail-form').reset();
      const def = [c.managerEmail, c.alertExtraEmails].filter(Boolean).join(', ');
      document.getElementById('mail-default-recipients').textContent = '기본 수신자: ' + (def || '(없음 — 추가 수신자를 입력하세요)') + ' + 서버 설정 기본 수신자';
      modal('mail-modal').show();
    });
  }

  async function showInspection(inspectionId) {
    const body = document.getElementById('inspection-detail-body');
    body.innerHTML = '<div class="pi-empty">불러오는 중...</div>';
    modal('inspection-detail-modal').show();
    try {
      const i = await PI_API.getInspection(inspectionId);
      let lastSheet = '';
      const rows = i.items.map((it) => {
        const head = it.sheetLabel !== lastSheet ? `<tr><th colspan="7" class="bg-light">${esc(it.sheetLabel)}</th></tr>` : '';
        lastSheet = it.sheetLabel;
        return head + `<tr style="cursor:default"><td class="text-nowrap">${esc(it.category || '')}</td><td>${it.importantYn === 'Y' ? '●' : ''}</td>
          <td>${esc(it.question)}</td><td style="white-space:pre-wrap">${esc(it.currentStatus || '')}</td>
          <td class="text-center pi-eval-${esc(it.evaluation || '')}">${esc(it.evaluation || (it.evaluationRaw ? '?' + it.evaluationRaw : '-'))}</td>
          <td style="white-space:pre-wrap">${esc(it.actionPlan || '')}</td><td>${esc(it.actionSchedule || (it.confirmDate ? fmtDate(it.confirmDate) : ''))}</td></tr>`;
      }).join('');
      body.innerHTML = `
        <dl class="pi-dl two mb-3">
          <dt>점검일 / 구분</dt><dd>${fmtDate(i.inspectionDate)} / ${esc(i.inspectionTypeLabel)}</dd>
          <dt>결과</dt><dd>${badge(i.result, i.resultLabel)} &nbsp;Y ${i.yCount} · N ${i.nCount} · NA ${i.naCount} · 미기재 ${i.blankCount}</dd>
          <dt>체크리스트 회사명</dt><dd>${esc(i.sheetCompanyName || '-')}</dd><dt>처리시스템</dt><dd>${i.systemOwnedYn === 'N' ? '미 보유' : esc(i.systemName || (i.systemOwnedYn === 'Y' ? '보유' : '-'))}</dd>
        </dl>
        ${perm.canWrite ? `<form id="follow-up-form" class="row g-2 align-items-end mb-3">
          <div class="col-md-2"><label class="form-label small" for="fu-date">점검일</label><input type="date" class="form-control form-control-sm" id="fu-date" name="inspectionDate" value="${esc(i.inspectionDate)}"></div>
          <div class="col-md-2"><label class="form-label small" for="fu-inspector">평가자</label><input class="form-control form-control-sm" id="fu-inspector" name="inspectorName" value="${esc(i.inspectorName)}"></div>
          <div class="col-md-2"><label class="form-label small" for="fu-due">조치 예정일</label><input type="date" class="form-control form-control-sm" id="fu-due" name="actionDueDate" value="${esc(i.actionDueDate)}"></div>
          <div class="col-md-2"><label class="form-label small" for="fu-done">조치 완료</label><select class="form-select form-select-sm" id="fu-done" name="actionCompletedYn">${opt('N', 'N', i.actionCompletedYn)}${opt('Y', 'Y', i.actionCompletedYn)}</select></div>
          <div class="col-md-3"><label class="form-label small" for="fu-remark">비고</label><input class="form-control form-control-sm" id="fu-remark" name="remark" value="${esc(i.remark)}"></div>
          <div class="col-md-1"><button class="btn btn-sm btn-primary w-100" type="submit">저장</button></div></form>` : ''}
        <div class="table-responsive"><table class="pi-table" id="inspection-item-table"><thead><tr><th>구분</th><th>중요</th><th>평가 항목</th><th>현황</th><th>평가</th><th>조치 계획</th><th>일정</th></tr></thead>
          <tbody>${rows}</tbody></table></div>`;
      PiTable.enhance(document.getElementById('inspection-item-table'), { id: 'inspectionItems' });
      document.getElementById('follow-up-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const b = Object.fromEntries(new FormData(e.target).entries());
        Object.keys(b).forEach((k) => { if (b[k] === '') b[k] = null; });
        try {
          await PI_API.updateInspection(inspectionId, b);
          notify('저장되었습니다.');
          modal('inspection-detail-modal').hide();
          renderDetail(currentContract.contractId);
        } catch (err) { fail(err); }
      });
    } catch (e) { body.innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`; }
  }


  // ──────────────── 5. 업체별 체크리스트 업로드 팝업 ────────────────
  // 업체명(목록) 또는 [업체 계약 일괄 업로드](상세) → 해당 업체를 고정한 팝업을 연다.
  const vendorState = { vendor: null, preview: null, file: null, changed: false };
  const normalizeVendor = (n) => String(n || '').replace(/\s+/g, '').replace(/㈜|\(주\)|（주）|\(株\)|주식회사/g, '').toLowerCase();
  const vEl = (id) => document.getElementById(id);

  async function openVendorChecklist(vendorName) {
    if (!perm.canWrite) { notify('업로드 권한이 없습니다.', 'warning'); return; }
    Object.assign(vendorState, { vendor: null, preview: null, file: null, changed: false });
    vEl('vendor-modal-form').reset();
    vEl('vendor-modal-title').textContent = vendorName;
    vEl('vendor-modal-preview').innerHTML = '';
    vEl('vendor-modal-result').innerHTML = '';
    vEl('vendor-modal-contracts').innerHTML = '<div class="pi-empty">불러오는 중...</div>';
    updateSubmitState();
    modal('vendor-checklist-modal').show();
    try {
      // 업체 키 기준으로 같은 업체의 계약을 모두 가져온다 (종료 계약 포함, 기본 선택은 미종료만)
      const list = await PI_API.listVendors({ keyword: vendorName, includeTerminated: true });
      const key = normalizeVendor(vendorName);
      vendorState.vendor = list.find((v) => v.vendorKey === key) || list[0] || null;
      renderVendorContracts();
    } catch (e) {
      vEl('vendor-modal-contracts').innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  function renderVendorContracts() {
    const box = vEl('vendor-modal-contracts');
    const v = vendorState.vendor;
    if (!v) { box.innerHTML = '<div class="pi-empty">업체의 계약을 찾을 수 없습니다.</div>'; updateSubmitState(); return; }
    vEl('vendor-modal-title').textContent = v.vendorName;
    vEl('vendor-modal-summary').innerHTML =
      `계약 ${v.contractCount}건 (진행 ${v.activeContractCount}) · 올해 미점검 ${v.notInspectedThisYearCount}건 · 최근 점검 ${badge(v.lastInspectionResult || 'NONE', v.lastInspectionResultLabel)}${v.lastInspectionDate ? ' ' + fmtDate(v.lastInspectionDate) : ''}`;
    box.innerHTML = `
      <div class="table-responsive"><table class="pi-table" id="vendor-contract-table"><thead><tr>
        <th style="width:36px"><input type="checkbox" class="form-check-input" id="v-check-all" aria-label="전체 선택"></th>
        <th>업체명</th><th>계약명</th><th>관련 서비스</th><th>위탁업무 내용</th><th>담당</th><th>종료일</th><th>상태</th><th>최근 점검</th></tr></thead>
        <tbody>${v.contracts.map((c) => `
          <tr style="cursor:default">
            <td><input type="checkbox" class="form-check-input v-check" value="${c.contractId}" ${c.status !== 'TERMINATED' ? 'checked' : ''} aria-label="선택"></td>
            <td>${esc(c.vendorName)}</td><td>${esc(c.contractName || '-')}</td><td>${esc(c.serviceName || '-')}</td>
            <td class="pi-ellipsis" title="${esc(c.entrustedTask)}">${esc(c.entrustedTask)}</td>
            <td>${esc([c.deptName, c.managerName].filter(Boolean).join(' / ') || '-')}</td>
            <td>${fmtDate(c.contractEndDate)}</td><td>${badge(c.status, c.statusLabel)}</td>
            <td class="text-nowrap">${badge(c.lastInspectionResult || 'NONE', c.lastInspectionResultLabel)} <small class="text-muted">${c.lastInspectionDate ? fmtDate(c.lastInspectionDate) : ''}</small></td>
          </tr>`).join('')}</tbody></table></div>
      <small class="text-muted">기본으로 종료되지 않은 계약이 선택됩니다. 계약변경·만료 점검이면 종료 계약도 선택할 수 있습니다.</small>`;
    PiTable.enhance(document.getElementById('vendor-contract-table'), { id: 'vendorChecklist' });
    const all = vEl('v-check-all');
    const checks = () => [...box.querySelectorAll('.v-check')];
    all.checked = checks().length > 0 && checks().every((x) => x.checked);
    all.addEventListener('change', () => { checks().forEach((x) => { x.checked = all.checked; }); updateSubmitState(); });
    checks().forEach((x) => x.addEventListener('change', () => { all.checked = checks().every((y) => y.checked); updateSubmitState(); }));
    updateSubmitState();
  }

  function selectedContractIds() {
    return [...document.querySelectorAll('#vendor-modal-contracts .v-check:checked')].map((x) => x.value);
  }

  function updateSubmitState() {
    const btn = vEl('v-submit');
    const n = selectedContractIds().length;
    btn.disabled = !(vendorState.file && vendorState.vendor && n > 0);
    btn.innerHTML = `<i class="fas fa-upload me-1"></i>선택한 계약 ${n}건에 등록`;
  }

  async function previewVendorFile() {
    const box = vEl('vendor-modal-preview');
    vendorState.file = vEl('v-file').files[0] || null;
    vendorState.preview = null;
    vEl('vendor-modal-result').innerHTML = '';
    if (!vendorState.file) { box.innerHTML = ''; updateSubmitState(); return; }
    box.innerHTML = '<div class="pi-preview">체크리스트를 읽는 중...</div>';
    const fd = new FormData();
    fd.append('file', vendorState.file);
    fd.append('inspectionType', vEl('v-type').value);
    try {
      const p = await PI_API.previewVendorChecklist(fd);
      vendorState.preview = p;
      const v = vendorState.vendor;
      const mismatch = v && p.sheetCompanyName && normalizeVendor(p.sheetCompanyName) !== v.vendorKey;
      const warnings = (p.warnings || []).filter((w) => !w.includes('업체를 직접 선택') && !w.includes('같은 업체의 계약이 없습니다'));
      if (mismatch) warnings.unshift(`체크리스트 회사명('${p.sheetCompanyName}')이 이 업체('${v.vendorName}')와 다릅니다. 파일을 확인하세요.`);
      box.innerHTML = `
        <div class="pi-preview">
          <div class="d-flex flex-wrap gap-3 align-items-center">
            <span>회사명 <b>${esc(p.sheetCompanyName || '(없음)')}</b></span>
            <span>평가일 <b>${fmtDate(p.evaluationDate)}</b></span>
            <span>평가자 <b>${esc(p.evaluator || '-')}</b></span>
            <span>${badge(p.result, p.resultLabel)} Y <span class="pi-eval-Y">${p.yCount}</span> · N <span class="pi-eval-N">${p.nCount}</span> · NA ${p.naCount} · 미기재 ${p.blankCount} (총 ${p.totalCount})</span>
          </div>
          ${warnings.length ? `<ul class="small text-warning-emphasis mb-0 mt-1">${warnings.map((w) => `<li>${esc(w)}</li>`).join('')}</ul>` : ''}
        </div>`;
      if (p.evaluator && !vEl('v-inspector').value) vEl('v-inspector').value = p.evaluator;
    } catch (e) {
      vendorState.file = null;
      box.innerHTML = `<div class="pi-preview text-danger">${esc(e.message)}</div>`;
    }
    updateSubmitState();
  }

  async function submitVendorChecklist(e) {
    e.preventDefault();
    const ids = selectedContractIds();
    const v = vendorState.vendor;
    if (!vendorState.file || !v || !ids.length) return;
    const p = vendorState.preview;
    if (p && p.sheetCompanyName && normalizeVendor(p.sheetCompanyName) !== v.vendorKey
        && !confirm(`체크리스트 회사명('${p.sheetCompanyName}')과 업체('${v.vendorName}')가 다릅니다. 그래도 등록할까요?`)) return;
    if (!confirm(`'${v.vendorName}' 계약 ${ids.length}건에 체크리스트를 점검 이력으로 등록합니다.`)) return;

    const fd = new FormData();
    ['inspectionDate', 'actionDueDate', 'inspectorName', 'remark'].forEach((k) => {
      const val = vEl('vendor-modal-form').elements[k].value.trim();
      if (val) fd.append(k, val);
    });
    fd.append('vendorKey', v.vendorKey);
    fd.append('inspectionType', vEl('v-type').value);
    ids.forEach((id) => fd.append('contractIds', id));
    fd.append('file', vendorState.file);
    const btn = vEl('v-submit');
    btn.disabled = true;
    try {
      const r = await PI_API.uploadVendorChecklist(fd);
      vendorState.changed = true;
      vEl('vendor-modal-result').innerHTML = `
        <div class="alert alert-${r.result === 'PASS' ? 'success' : 'warning'} mb-2">
          <b>${esc(r.vendorName)}</b> 계약 ${r.registeredCount}건에 등록했습니다. 판정: ${esc(r.resultLabel)}
          ${(r.warnings || []).length ? `<ul class="mb-0 small mt-1">${r.warnings.map((w) => `<li>${esc(w)}</li>`).join('')}</ul>` : ''}
        </div>
        <div class="table-responsive"><table class="pi-table" id="vendor-result-table"><thead><tr><th>계약</th><th>관련 서비스</th><th>점검일</th><th>결과</th><th></th></tr></thead>
          <tbody>${r.inspections.map((i) => `<tr data-go="${i.contractId}" title="계약 상세로 이동"><td>${esc(i.vendorName)}</td><td>${esc(i.serviceName || '-')}</td>
            <td>${fmtDate(i.inspectionDate)}</td><td>${badge(i.result, i.resultLabel)}</td><td class="text-end"><i class="fas fa-chevron-right text-muted"></i></td></tr>`).join('')}</tbody></table></div>`;
      PiTable.enhance(document.getElementById('vendor-result-table'), { id: 'vendorUploadResult' });
      vEl('vendor-modal-result').querySelectorAll('tr[data-go]').forEach((tr) => tr.addEventListener('click', () => {
        const target = '#/contracts/' + tr.dataset.go;
        modal('vendor-checklist-modal').hide();
        if (location.hash === target) route(); else go(target);
      }));
      vEl('v-file').value = '';
      vendorState.file = null;
      vendorState.preview = null;
      vEl('vendor-modal-preview').innerHTML = '';
      // 팝업 안의 계약 목록(최근 점검)도 갱신
      const list = await PI_API.listVendors({ keyword: v.vendorName, includeTerminated: true });
      vendorState.vendor = list.find((x) => x.vendorKey === v.vendorKey) || v;
      renderVendorContracts();
      vEl('vendor-modal-result').scrollIntoView({ block: 'nearest' });
    } catch (err) { fail(err); } finally { updateSubmitState(); }
  }

  vEl('v-file').addEventListener('change', previewVendorFile);
  vEl('v-type').addEventListener('change', () => { if (vendorState.file) previewVendorFile(); });
  vEl('vendor-modal-form').addEventListener('submit', submitVendorChecklist);
  // 등록한 뒤 팝업을 닫으면 현재 화면(목록/상세)의 최근 점검을 다시 불러온다
  vEl('vendor-checklist-modal').addEventListener('hidden.bs.modal', () => {
    if (vendorState.changed) { vendorState.changed = false; route(); }
  });

  // ──────────────── 모달 폼 ────────────────
  document.getElementById('inspection-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    ['inspectionDate', 'actionDueDate', 'inspectorName', 'remark'].forEach((k) => { if (!fd.get(k)) fd.delete(k); });
    const btn = e.submitter; if (btn) btn.disabled = true;
    try {
      const r = await PI_API.uploadInspection(currentContract.contractId, fd);
      modal('inspection-modal').hide();
      const warn = (r.warnings || []).length ? '\n' + r.warnings.join('\n') : '';
      notify(`업로드 완료: ${r.resultLabel} (Y ${r.yCount} / N ${r.nCount} / NA ${r.naCount} / 미기재 ${r.blankCount})${warn}`, r.result === 'PASS' && !warn ? 'success' : 'warning');
      renderDetail(currentContract.contractId);
    } catch (err) { fail(err); } finally { if (btn) btn.disabled = false; }
  });

  document.getElementById('terminate-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    if (!f.reportValidity()) return;
    const docsBox = !document.getElementById('term-docs-box').classList.contains('d-none');
    const body = { terminatedDate: f.terminatedDate.value, reason: f.reason.value || null };
    if (docsBox) {
      const note = document.getElementById('term-docs-note');
      if (!note.value.trim()) { note.classList.add('is-invalid'); note.focus(); notify('만료 서류 미수취 사유를 입력하세요.', 'warning'); return; }
      note.classList.remove('is-invalid');
      body.expiryDocsNote = note.value.trim();
      body.expiryDocsRemindYn = document.getElementById('term-docs-remind').checked ? 'Y' : 'N';
    }
    const btn = document.getElementById('btn-term-save');
    btn.disabled = true;
    try {
      await PI_API.terminateContract(currentContract.contractId, body);
      modal('terminate-modal').hide();
      notify(`계약 종료 처리되었습니다. (종료일 ${fmtDate(body.terminatedDate)})`);
      renderDetail(currentContract.contractId);
    } catch (err) { fail(err); } finally { btn.disabled = false; }
  });

  document.getElementById('mail-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target).entries());
    try {
      const r = await PI_API.sendAlert(currentContract.contractId, body);
      modal('mail-modal').hide();
      if (r.sendStatus === 'SENT') notify('메일을 발송했습니다: ' + r.recipients);
      else notify(`메일이 발송되지 않았습니다 (${r.sendStatus}): ${r.errorMessage || ''}`, 'warning');
      renderDetail(currentContract.contractId);
    } catch (err) { fail(err); }
  });

  // ──────────────── 시작 ────────────────
  async function init() {
    if (!PI_API.requireAuth()) return;
    PiTable.setNotify(notify);
    try { perm = await PI_API.permissions(); } catch (e) { fail(e); }
    if (!perm.canRead) {
      view.innerHTML = '<div class="pi-card pi-empty"><i class="fas fa-lock me-1"></i>개인정보 위수탁 관리 메뉴의 조회 권한이 없습니다. 보안팀에 권한(ROLE_PI_VIEWER 이상)을 요청하세요.</div>';
      return;
    }
    window.addEventListener('hashchange', route);
    route();
  }
  init();
})();
