/* =========================================================================
 * 개인정보 위수탁 관리 화면 (해시 라우팅 SPA, 무빌드 순수 JS)
 *   #/contracts            목록 화면
 *   #/contracts/new        등록 화면
 *   #/contracts/{id}       상세 화면 (계약서 업로드, 체크리스트 다운로드/업로드, 메일 발송)
 *   #/contracts/{id}/edit  수정 화면
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
  let listState = { page: 0, size: 50, sort: 'contractEndDate,asc', cond: {} };
  let currentContract = null;

  // ──────────────── 공통 유틸 ────────────────
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDate = (d) => (d ? String(d).substring(0, 10).replace(/-/g, '.') : '-');
  const fmtDateTime = (d) => (d ? String(d).substring(0, 16).replace('T', ' ') : '-');
  const yn = (v) => (v === 'Y' ? 'Y' : v === 'N' ? 'N' : '-');
  const fmtSize = (n) => (n == null ? '-' : n < 1024 ? n + ' B' : n < 1048576 ? (n / 1024).toFixed(1) + ' KB' : (n / 1048576).toFixed(1) + ' MB');
  const badge = (code, label) => `<span class="pi-status ${esc(code || 'NONE')}">${esc(label || code || '-')}</span>`;
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
      if ((m = hash.match(/^#\/contracts\/(\d+)\/edit$/))) return renderForm(await PI_API.getContract(m[1]));
      if ((m = hash.match(/^#\/contracts\/(\d+)$/))) return await renderDetail(m[1]);
      go('#/contracts');
    } catch (e) {
      view.innerHTML = `<div class="pi-card pi-empty"><i class="fas fa-triangle-exclamation me-1"></i>${esc(e.message)}</div>`;
    }
  }

  // ──────────────── 1. 목록 화면 ────────────────
  async function renderList() {
    crumb.innerHTML = '계약 목록';
    const c = listState.cond;
    view.innerHTML = `
      <section id="pi-dashboard" class="pi-stats"></section>
      <section class="pi-card" id="contract-search-section">
        <form id="contract-search-form" class="row g-2 align-items-end">
          <div class="col-md-3"><label class="form-label small" for="s-keyword">통합검색</label>
            <input class="form-control form-control-sm" id="s-keyword" name="keyword" placeholder="수탁업체/서비스/위탁업무/재위탁업체" value="${esc(c.keyword)}"></div>
          <div class="col-md-2"><label class="form-label small" for="s-dept">담당부서</label>
            <input class="form-control form-control-sm" id="s-dept" name="deptName" value="${esc(c.deptName)}"></div>
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
              <button type="button" class="btn btn-sm btn-outline-success" id="btn-excel"><i class="fas fa-download me-1"></i>엑셀 다운로드</button>
              ${perm.canDelete ? '<button type="button" class="btn btn-sm btn-outline-warning" id="btn-run-alert"><i class="fas fa-bell me-1"></i>만료 알림 실행</button>' : ''}
              ${perm.canWrite ? '<button type="button" class="btn btn-sm btn-primary" id="btn-new"><i class="fas fa-plus me-1"></i>계약 등록</button>' : ''}
            </div>
          </div>
        </form>
      </section>
      <section class="pi-card" id="contract-list-section">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <h2 class="mb-0"><i class="fas fa-list"></i>위수탁 계약 목록 <small class="text-muted fw-normal" id="list-count"></small></h2>
          <div class="d-flex gap-2 align-items-center">
          <button type="button" class="btn btn-sm btn-light text-nowrap" id="btn-reset-cols" title="열 너비를 기본값으로 되돌립니다">
            <i class="fas fa-arrows-left-right me-1"></i>열 너비 초기화</button>
          <select class="form-select form-select-sm w-auto" id="s-sort" aria-label="정렬">
            ${opt('contractEndDate,asc', '종료일 빠른순', listState.sort)}${opt('contractEndDate,desc', '종료일 늦은순', listState.sort)}
            ${opt('vendorName,asc', '업체명순', listState.sort)}${opt('createdAt,desc', '최근 등록순', listState.sort)}
            ${opt('lastInspectionDate,asc', '점검일 오래된순', listState.sort)}
          </select>
          </div>
        </div>
        <p class="pi-col-hint text-muted mb-1"><i class="fas fa-circle-info me-1"></i>열 제목 오른쪽 경계를 끌어서 너비를 조정할 수 있습니다. 경계를 더블클릭하면 해당 열만 기본값으로 돌아갑니다. 조정한 너비는 이 브라우저에 저장됩니다.</p>
        <div class="table-responsive pi-resizable-wrap"><table class="pi-table pi-resizable" id="contract-table">
          <colgroup>${listCols().map((c) => `<col data-col="${c.key}">`).join('')}</colgroup>
          <thead><tr>${listCols().map((c) => `<th data-col="${c.key}" title="${esc(c.label)}"${c.key === 'action' ? ' class="pi-col-sticky-end"' : ''}><span class="pi-th-label">${esc(c.label)}</span></th>`).join('')}</tr></thead>
          <tbody><tr><td colspan="${listCols().length}" class="pi-empty">불러오는 중...</td></tr></tbody>
        </table></div>
        <nav class="d-flex justify-content-center mt-3" id="contract-pager" aria-label="페이지"></nav>
      </section>`;

    const form = document.getElementById('contract-search-form');
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      listState.cond = Object.fromEntries(new FormData(form).entries());
      listState.page = 0;
      loadList();
    });
    document.getElementById('btn-reset').onclick = () => { listState.cond = {}; listState.page = 0; renderList(); };
    document.getElementById('btn-excel').onclick = () => PI_API.downloadExcel({ ...listState.cond, sort: listState.sort }).catch(fail);
    document.getElementById('btn-template').onclick = () => PI_API.downloadBlankTemplate().catch(fail);
    document.getElementById('s-sort').onchange = (e) => { listState.sort = e.target.value; listState.page = 0; loadList(); };
    document.getElementById('btn-new')?.addEventListener('click', () => go('#/contracts/new'));
    document.getElementById('btn-run-alert')?.addEventListener('click', runAlerts);
    initColumnResize(document.getElementById('contract-table'), listCols(), COL_WIDTH_KEY);
    document.getElementById('btn-reset-cols').onclick = () => {
      localStorage.removeItem(COL_WIDTH_KEY);
      applyColumnWidths(document.getElementById('contract-table'), listCols(), {});
      notify('열 너비를 기본값으로 되돌렸습니다.');
    };

    loadDashboard();
    await loadList();
  }

  function opt(value, label, selected) {
    return `<option value="${esc(value)}" ${String(selected ?? '') === value ? 'selected' : ''}>${esc(label)}</option>`;
  }

  async function loadDashboard() {
    try {
      const d = await PI_API.dashboard();
      const card = (label, value, color, status, extra) =>
        `<div class="pi-stat" data-status="${status || ''}" data-extra="${extra || ''}" role="button" tabindex="0">
           <div class="label">${esc(label)}</div><div class="value" style="color:${color}">${value}</div></div>`;
      const el = document.getElementById('pi-dashboard');
      if (!el) return;
      el.innerHTML =
        card('전체 계약', d.totalCount, '#111827', '') +
        card('계약중', d.activeCount, '#166534', 'ACTIVE') +
        card('만료임박', d.expiringCount, '#b45309', 'EXPIRING') +
        card('만료', d.expiredCount, '#b91c1c', 'EXPIRED') +
        card('종료', d.terminatedCount, '#374151', 'TERMINATED') +
        card(`${d.year}년 점검완료`, d.inspectedThisYearCount, '#4f46e5', '', '') +
        card(`${d.year}년 미점검`, d.notInspectedThisYearCount, '#9333ea', '', 'NONE') +
        card('조치 필요', d.needActionCount, '#dc2626', '', 'NEED_ACTION');
      el.querySelectorAll('.pi-stat').forEach((s) => s.addEventListener('click', () => {
        listState.cond = {};
        if (s.dataset.status) listState.cond.status = s.dataset.status;
        if (s.dataset.extra) listState.cond.inspectionResult = s.dataset.extra;
        listState.page = 0;
        renderList();
      }));
    } catch (e) { /* 요약 실패는 목록 사용에 영향 없음 */ }
  }

  // ──────────────── 목록 열 너비 조정 ────────────────
  // key: 저장 키, label: 제목, width: 기본 너비(px), min: 최소 너비(px)
  const CONTRACT_COLS = [
    { key: 'no', label: 'No', width: 52, min: 40 },
    { key: 'isms', label: 'ISMS', width: 78, min: 64 },
    { key: 'vendor', label: '수탁업체명', width: 150, min: 80 },
    { key: 'service', label: '관련 서비스', width: 120, min: 70 },
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
  const COL_WIDTH_KEY = 'personalinfo.contractList.colWidths.v1';

  function loadColumnWidths(storageKey) {
    try { return JSON.parse(localStorage.getItem(storageKey) || '{}') || {}; } catch (e) { return {}; }
  }

  function applyColumnWidths(table, cols, saved) {
    let total = 0;
    cols.forEach((c) => {
      const w = Math.max(c.min, Number(saved[c.key]) || c.width);
      total += w;
      const col = table.querySelector(`col[data-col="${c.key}"]`);
      if (col) col.style.width = w + 'px';
    });
    table.style.width = total + 'px';   // 고정 레이아웃: 합계 너비 지정 (넘치면 가로 스크롤)
    table.style.minWidth = '100%';
  }

  /** 헤더 오른쪽 경계에 끌기 핸들을 붙인다. 마우스/터치 모두 지원, 결과는 localStorage 에 저장 */
  function initColumnResize(table, cols, storageKey) {
    if (!table) return;
    const saved = loadColumnWidths(storageKey);
    applyColumnWidths(table, cols, saved);
    table.querySelectorAll('thead th[data-col]').forEach((th) => {
      const key = th.dataset.col;
      const def = cols.find((c) => c.key === key);
      const handle = document.createElement('span');
      handle.className = 'pi-col-resizer';
      handle.setAttribute('role', 'separator');
      handle.setAttribute('aria-orientation', 'vertical');
      handle.setAttribute('aria-label', def.label + ' 열 너비 조정');
      handle.tabIndex = 0;
      handle.title = '끌어서 너비 조정 · 더블클릭: 기본값';
      th.appendChild(handle);

      const col = table.querySelector(`col[data-col="${key}"]`);
      const current = () => parseInt(col.style.width, 10) || def.width;
      const setWidth = (w) => {
        const width = Math.max(def.min, Math.min(800, Math.round(w)));
        saved[key] = width;
        applyColumnWidths(table, cols, saved);
        return width;
      };
      const persist = () => localStorage.setItem(storageKey, JSON.stringify(saved));

      const start = (startX) => {
        const startW = current();
        table.classList.add('pi-resizing');
        handle.classList.add('active');
        const move = (x) => setWidth(startW + (x - startX));
        const onMouse = (e) => { e.preventDefault(); move(e.clientX); };
        const onTouch = (e) => { if (e.touches[0]) move(e.touches[0].clientX); };
        const end = () => {
          table.classList.remove('pi-resizing');
          handle.classList.remove('active');
          document.removeEventListener('mousemove', onMouse);
          document.removeEventListener('mouseup', end);
          document.removeEventListener('touchmove', onTouch);
          document.removeEventListener('touchend', end);
          persist();
        };
        document.addEventListener('mousemove', onMouse);
        document.addEventListener('mouseup', end);
        document.addEventListener('touchmove', onTouch, { passive: true });
        document.addEventListener('touchend', end);
      };
      handle.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation(); start(e.clientX); });
      handle.addEventListener('touchstart', (e) => { e.stopPropagation(); if (e.touches[0]) start(e.touches[0].clientX); }, { passive: true });
      handle.addEventListener('click', (e) => e.stopPropagation());
      // 더블클릭: 이 열만 기본값
      handle.addEventListener('dblclick', (e) => {
        e.stopPropagation();
        delete saved[key];
        applyColumnWidths(table, cols, saved);
        persist();
      });
      // 키보드: ←/→ 10px, Shift 와 함께 50px
      handle.addEventListener('keydown', (e) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
        e.preventDefault();
        const step = (e.shiftKey ? 50 : 10) * (e.key === 'ArrowLeft' ? -1 : 1);
        setWidth(current() + step);
        persist();
      });
    });
  }

  async function loadList() {
    const tbody = document.querySelector('#contract-table tbody');
    try {
      const page = await PI_API.listContracts({ ...listState.cond, page: listState.page, size: listState.size, sort: listState.sort });
      document.getElementById('list-count').textContent = `(총 ${page.totalElements}건)`;
      if (!page.content.length) {
        tbody.innerHTML = `<tr><td colspan="${listCols().length}" class="pi-empty">조회된 계약이 없습니다.</td></tr>`;
      } else {
        tbody.innerHTML = page.content.map((r, i) => `
          <tr data-id="${r.contractId}">
            <td class="pi-cell">${page.page * page.size + i + 1}</td>
            <td class="pi-cell pi-flag-cell">${flagCell(r, 'ismsScopeYn', 'ISMS 인증 여부')}</td>
            <td class="pi-cell fw-semibold" title="${esc(r.vendorName)}">${perm.canWrite
              ? `<button type="button" class="pi-vendor-link" data-vendor="${esc(r.vendorName)}" title="업체별 점검 체크리스트 업로드">${esc(r.vendorName)}</button>`
              : esc(r.vendorName)}</td>
            <td class="pi-cell" title="${esc(r.serviceName || '')}">${esc(r.serviceName || '-')}</td>
            <td class="pi-cell" title="${esc(r.entrustedTask)}">${esc(r.entrustedTask)}</td>
            <td class="pi-cell" title="${esc(r.deptName || '')}">${esc(r.deptName || '-')}</td>
            <td class="pi-cell" title="${esc(r.managerName || '')}">${esc(r.managerName || '-')}</td>
            <td class="pi-cell">${r.contractEndDate || r.contractStartDate ? fmtDate(r.contractStartDate) + ' ~ ' + fmtDate(r.contractEndDate) : esc(r.contractPeriodNote || '-')}</td>
            <td class="pi-cell">${r.status === 'TERMINATED' ? '-' : dLabel(r.daysLeft)}</td>
            <td class="pi-cell">${badge(r.status, r.statusLabel)}</td>
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
      }
      renderPager(page);
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="${listCols().length}" class="pi-empty">${esc(e.message)}</td></tr>`;
    }
  }

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
    if (!confirm('오늘 기준 만료 알림 메일을 실행합니다. (이미 보낸 단계는 다시 보내지 않습니다)')) return;
    try {
      const r = await PI_API.runAlerts();
      notify(`만료 알림 실행 완료: 대상 ${r.candidateCount}건 / 발송 ${r.sentCount} / 실패 ${r.failedCount} / 미발송 ${r.skippedCount} / 기발송 ${r.alreadySentCount}`);
    } catch (e) { fail(e); }
  }

  // ──────────────── 2·4. 등록 / 수정 화면 ────────────────
  function renderForm(c) {
    if (!perm.canWrite) { notify('등록/수정 권한이 없습니다.', 'warning'); go(c ? '#/contracts/' + c.contractId : '#/contracts'); return; }
    const isEdit = !!c;
    const v = c || { ismsScopeYn: 'Y', autoRenewYn: 'N', contractDocYn: 'N', subcontractYn: 'N', alertYn: 'Y', alertDaysBefore: 30 };
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
            <div class="col-md-3">${input('serviceName', '관련 서비스', v.serviceName, 'maxlength="200" placeholder="예: 자사몰, 임직원몰, OMS"')}</div>
            <div class="col-md-2"><label class="form-label d-block">ISMS 범위</label>${radio('ismsScopeYn', v.ismsScopeYn)}</div>
            <div class="col-12"><label class="form-label pi-required" for="f-entrustedTask">위탁업무 내용</label>
              <textarea class="form-control" id="f-entrustedTask" name="entrustedTask" rows="2" maxlength="1000" required>${esc(v.entrustedTask)}</textarea></div>
          </div>

          <div class="pi-form-section mt-3"><h3>당사 담당 / 수탁사 담당</h3>
            <div class="row g-3">
              <div class="col-md-3">${input('deptName', '담당부서', v.deptName, 'maxlength="100"')}</div>
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
                <div class="col-md-3">${input('contractStartDate', '계약 시작일', v.contractStartDate, 'type="date"')}</div>
                <div class="col-md-3">${input('contractEndDate', '계약 종료일', v.contractEndDate, 'type="date"')}</div>
                <div class="col-md-6">${input('contractPeriodNote', '계약기간 비고', v.contractPeriodNote, 'maxlength="500" placeholder="예: 체결일로부터 1년, 이용약관"')}</div>
                <div class="col-md-2"><label class="form-label d-block">자동연장</label>${radio('autoRenewYn', v.autoRenewYn)}</div>
                <div class="col-md-4">${input('autoRenewNote', '자동연장 비고', v.autoRenewNote, 'maxlength="200" placeholder="예: 1개월 단위"')}</div>
              </div>
            </div>
            <div class="pi-form-sub" id="pi-entrust-contract-block">
              <h4><i class="fas fa-user-shield me-1"></i>개인정보처리 위탁계약</h4>
              <div class="row g-3">
                <div class="col-md-3"><label class="form-label d-block">개인정보처리 위탁계약서 작성</label>${radio('contractDocYn', v.contractDocYn)}</div>
                <div class="col-md-3">${input('contractSignedDate', '개인정보처리 위탁계약 체결일', v.contractSignedDate, 'type="date"')}</div>
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
            ${isEdit ? '' : `<div class="mt-3"><label class="form-label" for="f-contractFile">위수탁 계약서 파일 <small class="text-muted">(선택, 등록 후 상세 화면에서도 추가 가능)</small></label>
              <input type="file" class="form-control" id="f-contractFile" accept=".pdf,.doc,.docx,.hwp,.hwpx,.xls,.xlsx,.jpg,.jpeg,.png,.zip"></div>`}
          </div>

          <div class="d-flex justify-content-end gap-2 mt-4">
            <button type="button" class="btn btn-light" id="btn-cancel">취소</button>
            <button type="submit" class="btn btn-primary" id="btn-save"><i class="fas fa-save me-1"></i>저장</button>
          </div>
        </form>
      </section>`;

    document.getElementById('btn-cancel').onclick = () => go(isEdit ? '#/contracts/' + c.contractId : '#/contracts');
    const form = document.getElementById('contract-form');

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
      if (body.subcontractYn === 'Y' && !body.subcontractorName && !body.subcontractTask) {
        notify('재위탁이 Y 이면 재위탁 업체명 또는 재위탁 업무를 입력하세요.', 'warning');
        document.getElementById('f-subcontractorName').focus();
        return;
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
          try { await PI_API.uploadFile(saved.contractId, fd); } catch (err) { notify('계약은 저장되었으나 파일 업로드 실패: ' + err.message, 'warning'); }
        }
        notify(isEdit ? '수정되었습니다.' : '등록되었습니다.');
        go('#/contracts/' + saved.contractId);
      } catch (err) { fail(err); } finally { btn.disabled = false; }
    });
  }

  // ──────────────── 3. 상세 화면 ────────────────
  async function renderDetail(id) {
    const c = await PI_API.getContract(id);
    currentContract = c;
    crumb.innerHTML = `<a href="#/contracts">계약 목록</a> &gt; ${esc(c.vendorName)}`;
    const row = (k, v) => `<dt>${esc(k)}</dt><dd>${v}</dd>`;
    view.innerHTML = `
      <section class="pi-card" id="contract-detail-section">
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
          <h2 class="mb-0"><i class="fas fa-building"></i>${esc(c.vendorName)} ${badge(c.status, c.statusLabel)}
            <small class="text-muted fw-normal ms-1">${c.status === 'TERMINATED' ? '' : dLabel(c.daysLeft)}</small></h2>
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
          ${row('ISMS 범위', yn(c.ismsScopeYn))}${row('관련 서비스', esc(c.serviceName || '-'))}
          ${row('위탁업무 내용', esc(c.entrustedTask))}${row('담당부서 / 담당자', esc((c.deptName || '-') + ' / ' + (c.managerName || '-')))}
          ${row('담당자 이메일', esc(c.managerEmail || '-'))}${row('수탁사 담당자', esc([c.vendorContactName, c.vendorContactPhone, c.vendorContactEmail].filter(Boolean).join(' / ') || '-'))}
          ${row('원계약 기간', fmtDate(c.contractStartDate) + ' ~ ' + fmtDate(c.contractEndDate))}${row('원계약 기간 비고', esc(c.contractPeriodNote || '-'))}
          ${row('원계약 자동연장', yn(c.autoRenewYn) + (c.autoRenewNote ? ' (' + esc(c.autoRenewNote) + ')' : ''))}${row('개인정보처리 위탁계약서 작성 / 체결일', yn(c.contractDocYn) + ' / ' + fmtDate(c.contractSignedDate))}
          ${row('재위탁', c.subcontractYn === 'Y' ? esc(c.subcontractorName || '') + (c.subcontractTask ? ' — ' + esc(c.subcontractTask) : '') : 'N')}${row('재위탁 동의서', yn(c.subcontractConsentYn))}
          ${row('만료 알림', c.alertYn === 'Y' ? `사용 (만료 ${c.alertDaysBefore}일 전부터)` : '미사용')}${row('추가 수신자', esc(c.alertExtraEmails || '-'))}
          ${c.terminatedYn === 'Y' ? row('종료일 / 사유', fmtDate(c.terminatedDate) + ' / ' + esc(c.terminationReason || '-')) + row('', '') : ''}
          ${row('비고', esc(c.remark || '-'))}${row('등록 / 수정', fmtDateTime(c.createdAt) + ' / ' + fmtDateTime(c.updatedAt))}
        </dl>
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
            <button class="btn btn-sm btn-primary text-nowrap" type="submit"><i class="fas fa-upload me-1"></i>업로드</button></form>` : ''}
        </div>
        <table class="pi-table"><thead><tr><th>구분</th><th>파일명</th><th>크기</th><th>설명</th><th>등록일시</th><th></th></tr></thead>
          <tbody>${c.files.length ? c.files.map((f) => `
            <tr><td>${esc(f.fileTypeLabel)}</td><td>${esc(f.originalName)}</td><td>${fmtSize(f.fileSize)}</td><td>${esc(f.description || '')}</td>
              <td>${fmtDateTime(f.createdAt)}</td>
              <td class="text-end text-nowrap"><button class="btn btn-sm btn-light" data-dl-file="${f.fileId}" data-name="${esc(f.originalName)}"><i class="fas fa-download"></i></button>
                ${perm.canDelete ? `<button class="btn btn-sm btn-light text-danger" data-del-file="${f.fileId}" aria-label="삭제"><i class="fas fa-trash"></i></button>` : ''}</td></tr>`).join('')
            : '<tr><td colspan="6" class="pi-empty">등록된 계약서가 없습니다.</td></tr>'}</tbody></table>
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
        <table class="pi-table"><thead><tr><th>점검일</th><th>구분</th><th>평가자</th><th>결과</th><th>Y / N / NA / 미기재</th><th>조치 예정일</th><th>조치 완료</th><th>파일</th><th></th></tr></thead>
          <tbody>${c.inspections.length ? c.inspections.map((i) => `
            <tr data-ins="${i.inspectionId}"><td>${fmtDate(i.inspectionDate)}</td><td>${esc(i.inspectionTypeLabel)}${i.uploadGroupId ? ' <span class="badge text-bg-light border" title="업체별 일괄 업로드로 등록">업체 일괄</span>' : ''}</td><td>${esc(i.inspectorName || '-')}</td>
              <td>${badge(i.result, i.resultLabel)}</td>
              <td><span class="pi-eval-Y">${i.yCount}</span> / <span class="pi-eval-N">${i.nCount}</span> / <span class="pi-eval-NA">${i.naCount}</span> / ${i.blankCount}</td>
              <td>${fmtDate(i.actionDueDate)}</td><td>${yn(i.actionCompletedYn)}</td>
              <td class="pi-ellipsis" style="max-width:180px">${esc(i.originalName)}</td>
              <td class="text-end text-nowrap"><button class="btn btn-sm btn-light" data-dl-ins="${i.inspectionId}" data-name="${esc(i.originalName)}" aria-label="다운로드"><i class="fas fa-download"></i></button>
                ${perm.canDelete ? `<button class="btn btn-sm btn-light text-danger" data-del-ins="${i.inspectionId}" aria-label="삭제"><i class="fas fa-trash"></i></button>` : ''}</td></tr>`).join('')
            : '<tr><td colspan="9" class="pi-empty">점검 이력이 없습니다. 체크리스트 양식을 내려받아 협력사에 전달한 뒤, 작성본을 업로드하세요.</td></tr>'}</tbody></table>
      </section>

      <section class="pi-card" id="alert-log-section">
        <h2><i class="fas fa-bell"></i>만료 알림 발송 이력 <small class="text-muted fw-normal">(최근 20건)</small></h2>
        <table class="pi-table"><thead><tr><th>일시</th><th>경로</th><th>단계</th><th>수신자</th><th>상태</th><th>사유</th></tr></thead>
          <tbody>${c.alerts.length ? c.alerts.map((a) => `
            <tr style="cursor:default"><td>${fmtDateTime(a.sentAt || a.createdAt)}</td><td>${a.triggerType === 'AUTO' ? '자동' : '수동'}</td><td>${esc(a.alertStage)}</td>
              <td class="pi-ellipsis" title="${esc(a.recipients)}">${esc(a.recipients || '-')}</td><td>${badge(a.sendStatus, a.sendStatus)}</td>
              <td class="pi-ellipsis" title="${esc(a.errorMessage)}">${esc(a.errorMessage || '')}</td></tr>`).join('')
            : '<tr><td colspan="6" class="pi-empty">발송 이력이 없습니다.</td></tr>'}</tbody></table>
      </section>`;

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
          <th>계약기간</th><th>위탁사 담당부서</th><th>위탁사 담당자</th><th>담당자 이메일</th><th>변경자</th></tr></thead>
          <tbody>${list.map((h) => `
            <tr class="${h.currentContract ? '' : 'pi-ch-other'}" style="cursor:default">
              <td class="text-nowrap">${fmtDateTime(h.changedAt)}</td>
              <td class="text-nowrap"><span class="pi-status ${h.changeType === 'UPDATE' ? 'EXPIRING' : 'NONE'}">${esc(h.changeTypeLabel)}</span></td>
              ${vendorWide ? `<td>${h.currentContract ? '<b>이 계약</b>' : `<a href="#/contracts/${h.contractId}">${esc(h.vendorName || '')}</a>`}
                <small class="text-muted d-block">${esc(h.serviceName || '-')}</small></td>` : ''}
              <td class="text-nowrap">${esc(periodText(h))}${(h.contractStartDate || h.contractEndDate) && h.contractPeriodNote ? `<small class="text-muted d-block">${esc(h.contractPeriodNote)}</small>` : ''}</td>
              <td>${cell(h, 'DEPT_NAME', h.deptName)}</td>
              <td>${cell(h, 'MANAGER_NAME', h.managerName)}</td>
              <td>${cell(h, 'MANAGER_EMAIL', h.managerEmail)}</td>
              <td class="text-nowrap">${esc(h.changedByName || (h.changeType === 'INITIAL' ? '시스템' : '-'))}</td>
            </tr>`).join('')}</tbody></table></div>
        <small class="text-muted">위탁사(당사) 담당자가 바뀔 때만 기록합니다(수탁사 담당자·연락처 변경은 기록하지 않음). 각 행은 변경 <b>후</b> 담당자와 그 시점의 계약기간이며, <span class="pi-ch-changed">노란색</span>은 그 이력에서 바뀐 항목입니다.${vendorWide ? ' 같은 업체(㈜·(주)·공백 차이 무시)의 다른 계약 이력도 함께 보여줍니다.' : ''}</small>`;
    } catch (e) {
      body.innerHTML = `<div class="pi-empty">${esc(e.message)}</div>`;
    }
  }

  function bindDetail(c) {
    const id = c.contractId;
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
    document.getElementById('btn-terminate')?.addEventListener('click', async () => {
      const reason = prompt('계약 종료 사유를 입력하세요. (종료 처리하면 만료 알림이 중단됩니다)\n종료 시 "계약변경·만료 점검" 체크리스트 업로드를 권장합니다.', '계약 만료');
      if (reason === null) return;
      try { await PI_API.terminateContract(id, { reason }); notify('계약 종료 처리되었습니다.'); reload(); } catch (e) { fail(e); }
    });
    document.getElementById('btn-reopen')?.addEventListener('click', async () => {
      if (!confirm('계약 종료 처리를 취소합니다.')) return;
      try { await PI_API.reopenContract(id); notify('종료 처리가 취소되었습니다.'); reload(); } catch (e) { fail(e); }
    });

    // 계약서 업로드
    const fileForm = document.getElementById('file-upload-form');
    fileForm?.addEventListener('submit', async (e) => {
      e.preventDefault();
      try { await PI_API.uploadFile(id, new FormData(fileForm)); notify('업로드되었습니다.'); reload(); } catch (err) { fail(err); }
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
        <div class="table-responsive"><table class="pi-table"><thead><tr><th>구분</th><th>중요</th><th>평가 항목</th><th>현황</th><th>평가</th><th>조치 계획</th><th>일정</th></tr></thead>
          <tbody>${rows}</tbody></table></div>`;
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
        <th>업체명</th><th>관련 서비스</th><th>위탁업무 내용</th><th>담당</th><th>종료일</th><th>상태</th><th>최근 점검</th></tr></thead>
        <tbody>${v.contracts.map((c) => `
          <tr style="cursor:default">
            <td><input type="checkbox" class="form-check-input v-check" value="${c.contractId}" ${c.status !== 'TERMINATED' ? 'checked' : ''} aria-label="선택"></td>
            <td>${esc(c.vendorName)}</td><td>${esc(c.serviceName || '-')}</td>
            <td class="pi-ellipsis" title="${esc(c.entrustedTask)}">${esc(c.entrustedTask)}</td>
            <td>${esc([c.deptName, c.managerName].filter(Boolean).join(' / ') || '-')}</td>
            <td>${fmtDate(c.contractEndDate)}</td><td>${badge(c.status, c.statusLabel)}</td>
            <td class="text-nowrap">${badge(c.lastInspectionResult || 'NONE', c.lastInspectionResultLabel)} <small class="text-muted">${c.lastInspectionDate ? fmtDate(c.lastInspectionDate) : ''}</small></td>
          </tr>`).join('')}</tbody></table></div>
      <small class="text-muted">기본으로 종료되지 않은 계약이 선택됩니다. 계약변경·만료 점검이면 종료 계약도 선택할 수 있습니다.</small>`;
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
        <table class="pi-table"><thead><tr><th>계약</th><th>관련 서비스</th><th>점검일</th><th>결과</th><th></th></tr></thead>
          <tbody>${r.inspections.map((i) => `<tr data-go="${i.contractId}" title="계약 상세로 이동"><td>${esc(i.vendorName)}</td><td>${esc(i.serviceName || '-')}</td>
            <td>${fmtDate(i.inspectionDate)}</td><td>${badge(i.result, i.resultLabel)}</td><td class="text-end"><i class="fas fa-chevron-right text-muted"></i></td></tr>`).join('')}</tbody></table>`;
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
