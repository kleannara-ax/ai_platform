(() => {
  'use strict';

  const CFG = window.FACILITY_PAGE_CONFIG || {};
  const label = CFG.itemLabel || '설비';
  const apiBase = CFG.apiBase || '/facility-api/air-conditioners';
  const builtinMarkerImages = {
    airconSystem: '/images/facility/aircon_system.png',
    airconWall: '/images/facility/aircon_wall.png',
    airconStand: '/images/facility/aircon_stand.png',
    airconOutdoor: '/images/facility/aircon_outdoor_unit.png',
    aircon: '/images/facility/aircon_system.png',
    water: '/images/facility/water_purifier.png'
  };
  const state = {
    items: [], buildings: [], floors: [], buildingFloorMap: {},
    q: '', buildingId: '', floorId: '', status: null,
    sort: { key: 'serialNumber', direction: 'asc' },
    editingId: 0, inspectId: 0, selectedCoord: null,
    planImagePath: '', existingMarkers: [], planView: { scale: 1 }
  };

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? '').replace(/[&<>'"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[ch]));
  const today = () => new Date().toISOString().slice(0, 10);
  const monthValue = (v) => v ? String(v).slice(0, 7) : '';
  const monthStart = (v) => v ? `${v}-01` : '';
  const fmtDate = (v) => v ? String(v).slice(0, 10) : '-';
  const fmtMonth = (v) => v ? `${String(v).slice(0,4)}년 ${String(v).slice(5,7)}월` : '-';
  const num = (v) => { const n = Number(v); return Number.isFinite(n) ? n : null; };
  const coord2 = (v) => { if (v === '' || v == null) return null; const n = Number(v); return Number.isFinite(n) ? Number(n.toFixed(2)) : null; };
  const isInIframe = () => { try { return window.self !== window.top; } catch { return true; } };

  const API = (() => {
    const getUser = () => { try { return JSON.parse(localStorage.getItem('fireweb_user') || localStorage.getItem('fw_user') || 'null'); } catch { return null; } };
    const canManage = () => { const u = getUser(); return !!u && u.canManage !== false; };
    async function req(url, opts = {}) {
      const requestOpts = { ...opts };
      const headers = { ...(requestOpts.headers || {}) };
      if (window.FireWebCsrf?.isMutation?.(requestOpts.method)) await window.FireWebCsrf.ensureToken();
      if (requestOpts.body && typeof requestOpts.body === 'object' && !(requestOpts.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        requestOpts.body = JSON.stringify(requestOpts.body);
      }
      requestOpts.headers = window.FireWebCsrf?.headers(headers, requestOpts.method) || headers;
      const res = await fetch(url, requestOpts);
      if (res.status === 401) {
        localStorage.removeItem('fireweb_user'); localStorage.removeItem('fw_user');
        (window.FireWebCsrf?.goLogin || (() => { location.href = '/index.html'; }))();
        return null;
      }
      return res;
    }
    return { req, getUser, canManage };
  })();

  const initialQuery = new URLSearchParams(location.search || '');
  let hasOtherAdminPermission = false;
  const canEdit = () => hasOtherAdminPermission;
  const isCurrentUserListed = (extraValue1) => {
    const user = API.getUser();
    if (!user?.loginId) return false;
    return String(extraValue1 || '').split(',').map(s => s.trim()).filter(Boolean).includes(user.loginId);
  };
  async function loadOtherAdminPermission() {
    hasOtherAdminPermission = false;
    try {
      const res = await API.req('/common-api/codes/lookup/OTHER_PERM');
      const json = res && res.ok ? await res.json().catch(() => null) : null;
      const otherAdmin = Array.isArray(json?.data) ? json.data.find(d => d.code === 'OTHER_ADMIN') : null;
      hasOtherAdminPermission = isCurrentUserListed(otherAdmin?.extraValue1);
    } catch (e) {
      console.warn('OTHER_ADMIN 권한 조회 실패', e);
    }
  }
  function requireEditPermission() {
    if (canEdit()) return true;
    alert('기타시설관리 권한이 필요합니다.');
    return false;
  }
  const isFloorEmbed = () => initialQuery.get('returnTo') === 'floor' || ['embedAdd','embedEdit','embedInspect','embedDetails'].some(k => initialQuery.get(k) === '1');
  function postFloorMessage(type) {
    if (!isFloorEmbed()) return;
    try { window.parent?.postMessage(type, '*'); } catch (_) {}
  }
  function applyEmbeddedShell() {
    if (!isFloorEmbed()) return;
    document.body.style.background = 'transparent';
    document.querySelector('header')?.style.setProperty('display', 'none');
    document.querySelector('main.container-fluid')?.style.setProperty('display', 'none');
    const style = document.createElement('style');
    style.textContent = 'body{background:transparent!important}.modal-backdrop{display:none!important}.modal{background:transparent!important}.modal-dialog{margin:.35rem auto!important}.modal-content{box-shadow:0 14px 36px rgba(15,23,42,.18)!important}';
    document.head.appendChild(style);
  }
  const isAirconPage = () => CFG.menuCode === 'OTHER_AIRCON' || label === '에어컨';
  const isWaterPurifierPage = () => CFG.menuCode === 'OTHER_WATER_PURIFIER' || label === '정수기';
  const isSimpleWaterPurifier = () => Boolean(CFG.simpleFields) || isWaterPurifierPage();
  const fixedType = () => CFG.fixedType || (isWaterPurifierPage() ? '정수기' : '');
  const inspectStatusLabel = () => isAirconPage() ? '점검요청' : '점검필요';
  const installDateLabel = () => CFG.installDateLabel || (isSimpleWaterPurifier() ? '설치일' : '제조/설치월');
  const installDateInputType = () => isSimpleWaterPurifier() ? 'date' : 'month';
  const installDateValue = (v) => isSimpleWaterPurifier() ? (v ? String(v).slice(0, 10) : '') : monthValue(v);
  const dateFromInstallInput = (v) => { if (!v) return ''; return isSimpleWaterPurifier() ? v : monthStart(v); };
  const displayInstallDate = (v) => isSimpleWaterPurifier() ? fmtDate(v) : fmtMonth(v);
  const outdoorUnitCount = (v) => Math.min(2, Math.max(1, Number(v || 1) || 1));


  function bootstrapModal(id) {
    const el = $(id);
    return el ? bootstrap.Modal.getOrCreateInstance(el) : null;
  }

  function unwrap(json) { return json?.data ?? json; }

  function showToast(message, type = 'success') {
    const host = $('facilityToast');
    if (!host) return alert(message);
    host.innerHTML = `<div class="alert alert-${type} shadow mb-0" style="opacity:0;transition:opacity .25s">${esc(message)}</div>`;
    requestAnimationFrame(() => { host.firstElementChild.style.opacity = '1'; });
    setTimeout(() => { if (host.firstElementChild) host.firstElementChild.style.opacity = '0'; }, 2200);
    setTimeout(() => { host.innerHTML = ''; }, 2800);
  }

  function renderShell() {
    document.body.innerHTML = `
      <header ${isInIframe() ? 'style="display:none"' : ''}>
        <nav class="navbar navbar-expand-lg navbar-dark facility-navbar">
          <div class="container-fluid">
            <a class="navbar-brand fw-bold" href="/fire-map.html">설비관리시스템</a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#topNav"><span class="navbar-toggler-icon"></span></button>
            <div class="collapse navbar-collapse" id="topNav">
              <ul class="navbar-nav ms-3">
                <li class="nav-item dropdown">
                  <a class="nav-link dropdown-toggle fw-semibold" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">소방설비</a>
                  <ul class="dropdown-menu">
                    <li><a class="dropdown-item" href="/fire-map.html">전체 도면</a></li>
                    <li><a class="dropdown-item" href="/maps/floor.html?buildingName=%EB%B3%B5%EC%A7%80%EA%B4%80&floorName=1%EC%B8%B5">층별 도면</a></li>
                    <li><a class="dropdown-item" href="/extinguishers.html">소화기</a></li>
                    <li><a class="dropdown-item" href="/hydrants.html">소화전</a></li>
                    <li><a class="dropdown-item" href="/receivers.html">수신기</a></li>
                    <li><a class="dropdown-item" href="/pumps.html">소방펌프</a></li>
                    <li><a class="dropdown-item" href="/qr">QR코드</a></li>
                  </ul>
                </li>
                <li class="nav-item dropdown">
                  <a class="nav-link dropdown-toggle active fw-semibold" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">기타설비</a>
                  <ul class="dropdown-menu">
                    <li><a class="dropdown-item" href="/index.html?page=other_dashboard">대시보드</a></li>
                    <li><a class="dropdown-item" href="/facility-map.html">도면 (메인)</a></li>
                    <li><a class="dropdown-item" href="/facility/floor.html">층별 도면</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="/facility/air-conditioners">에어컨</a></li>
                    <li><a class="dropdown-item" href="/facility/water-purifiers">정수기</a></li>
                    <li><a class="dropdown-item" href="/facility/qr">QR코드</a></li>
                  </ul>
                </li>
              </ul>
              <ul class="navbar-nav ms-auto align-items-center" id="navAccountArea"></ul>
            </div>
          </div>
        </nav>
      </header>
      <main class="container-fluid py-4">
        <section class="card summary-gradient-card mb-4">
          <div class="card-body d-flex flex-wrap justify-content-between align-items-center gap-3 text-white">
            <div>
              <h3 class="mb-1 fw-bold">${esc(CFG.title || `${label} 관리`)}</h3>
              ${CFG.subtitle ? `<div class="text-white-50 fw-semibold">${esc(CFG.subtitle)}</div>` : ''}
              <div class="text-white-50 fw-semibold mt-1">총 <span id="totalCount">0</span> 건</div>
            </div>
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <div class="btn-group" role="group">
                <button type="button" class="btn btn-info text-white fw-bold" id="btnStatusInspect">${esc(inspectStatusLabel())} <span id="countInspect">(0)</span></button>
              </div>
              ${canEdit() ? `<button type="button" class="btn btn-light fw-bold" id="btnAdd">+ ${esc(label)} 추가</button>` : ''}
            </div>
          </div>
        </section>
        <section class="card shadow-sm mb-4">
          <div class="card-body">
            <div class="row g-2 align-items-center">
              <div class="col-lg-4"><input id="filterQ" class="form-control" placeholder="검색어(건물/층/종류/제조사/위치/비고)"></div>
              <div class="col-lg-3"><select id="filterBuildingId" class="form-select"><option value="">-- 건물 전체 --</option></select></div>
              <div class="col-lg-3"><select id="filterFloorId" class="form-select"><option value="">-- 층 전체 --</option></select></div>
              <div class="col-lg-2 d-flex gap-2"><button class="btn btn-primary w-100" id="btnSearch">검색</button><button class="btn btn-outline-secondary w-100" id="btnReset">초기화</button></div>
            </div>
          </div>
        </section>
        <section class="card shadow-sm"><div class="card-body p-0" id="mainTableWrap"><div class="text-center py-5 text-muted">데이터를 불러오는 중...</div></div></section>
      </main>
      ${renderModals()}
      <div id="facilityToast" style="position:fixed;top:16px;right:16px;z-index:3000"></div>`;
    renderNav();
  }

  function renderAirconFields() {
    if (!isAirconPage()) return '';
    return `<div class="col-md-4"><label class="form-label fw-bold">에어컨 식별 No. <span class="text-muted small">(선택)</span></label><input type="text" class="form-control" id="serialNumber" maxlength="50" placeholder="예: 1-1"><div class="form-text">현장 식별 No.가 있을 때만 입력하세요.</div></div><div class="col-md-4"><label class="form-label fw-bold">제조사</label><input type="text" class="form-control" id="manufacturer" maxlength="100" placeholder="예: LG, 삼성"></div><div class="col-md-4"><label class="form-label fw-bold">위치</label><input type="text" class="form-control" id="locationDescription" maxlength="200" placeholder="예: 사무실 출입문 상부"></div><div class="col-md-4"><label class="form-label fw-bold">실외기 대수</label><select class="form-select" id="outdoorUnitCount"><option value="1">1대</option><option value="2">2대</option></select></div><div class="col-md-4"><label class="form-label fw-bold">상태</label><select class="form-select" id="inspectionRequested"><option value="false">정상</option><option value="true">점검요청</option></select><div class="form-text">점검요청은 QR 점검 완료 대상이 됩니다.</div></div>`;
  }

  function renderTypeField() {
    if (isSimpleWaterPurifier()) {
      return `<input type="hidden" id="equipmentType" value="${esc(fixedType())}">`;
    }
    return `<div class="col-md-6"><label class="form-label fw-bold">${esc(CFG.typeLabel || '설비 종류')}</label><select class="form-select" id="equipmentType" required></select></div>`;
  }

  function renderCycleField() {
    if (isSimpleWaterPurifier()) return '';
    return '<div class="col-md-4"><label class="form-label fw-bold">관리 주기(년)</label><input type="number" class="form-control" id="cycleYears" min="1" value="10"></div>';
  }

  function renderNoteAndPhotoFields() {
    if (isSimpleWaterPurifier()) return '';
    return '<div class="col-12"><label class="form-label fw-bold">비고</label><textarea class="form-control" id="note" rows="2"></textarea></div><div class="col-12"><label class="form-label fw-bold">업로드 이미지</label><input type="file" class="form-control" id="photo" accept="image/*"><div class="form-text">저장 후 사용자가 업로드한 이미지로 저장되며, 기존 업로드 이미지는 1장 정책에 따라 갱신됩니다.</div></div>';
  }

  function renderInspectModal() {
    if (isAirconPage()) {
      return `<div class="modal fade" id="inspectModal" tabindex="-1" aria-hidden="true"><div class="modal-dialog modal-lg modal-dialog-centered"><div class="modal-content"><div class="modal-header"><h5 class="modal-title">${esc(label)} 점검 요청</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><div class="fw-edit-section"><div class="fw-edit-section-title"><strong>고장 접수</strong><span>접수자 이름, 소속, 고장내용만 입력합니다.</span></div><div class="row g-3"><div class="col-md-6"><label class="form-label fw-bold">접수자 이름</label><input type="text" class="form-control" id="faultReporterName" maxlength="100" placeholder="선택 입력"></div><div class="col-md-6"><label class="form-label fw-bold">소속</label><input type="text" class="form-control" id="faultReporterDepartment" maxlength="100" placeholder="선택 입력"></div><div class="col-12"><label class="form-label fw-bold">고장내용 <span class="text-danger">*</span></label><textarea class="form-control" id="faultDescription" rows="5" maxlength="1000" placeholder="예: 냉방이 되지 않음, 물 샘, 소음 발생 등" required></textarea></div></div></div></div><div class="modal-footer"><button class="btn btn-outline-secondary" data-bs-dismiss="modal">취소</button><button class="btn btn-primary" id="btnConfirmInspect">점검 요청 접수</button></div></div></div></div>`;
    }
    return `<div class="modal fade" id="inspectModal" tabindex="-1" aria-hidden="true"><div class="modal-dialog modal-lg modal-dialog-centered"><div class="modal-content"><div class="modal-header"><h5 class="modal-title">${esc(label)} 점검</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><div class="fw-edit-section"><div class="fw-edit-section-title"><strong>점검 결과</strong></div><div class="row g-3"><div class="col-md-4"><label class="form-label fw-bold">점검일</label><input type="date" class="form-control" id="inspectDate"></div><div class="col-md-8 d-flex align-items-end"><div class="alert alert-info mb-0 w-100 py-2">${esc(inspectStatusLabel())} 처리 후 상태는 정상으로 저장됩니다.</div><input type="radio" class="btn-check" name="inspectFaulty" id="inspectOk" value="false" checked><input type="radio" class="btn-check" name="inspectFaulty" id="inspectBad" value="true"><textarea class="d-none" id="inspectFaultReason"></textarea></div><div class="col-12"><label class="form-label fw-bold">점검 사진(선택)</label><input type="file" class="form-control" id="inspectPhoto" accept="image/*"><div class="form-text">업로드 시 사용자가 업로드한 이미지가 교체됩니다.</div></div></div></div></div><div class="modal-footer"><button class="btn btn-outline-secondary" data-bs-dismiss="modal">취소</button><button class="btn btn-success" id="btnConfirmInspect">점검 완료</button></div></div></div></div>`;
  }

  function renderModals() {
    return `
      <div class="modal fade" id="detailsModal" tabindex="-1" aria-hidden="true"><div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content"><div class="modal-header"><h5 class="modal-title">${esc(label)} 상세</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body" id="detailsModalBody"></div></div></div></div>
      <div class="modal fade" id="imageZoomModal" tabindex="-1" aria-hidden="true"><div class="modal-dialog modal-dialog-centered" style="max-width:min(96vw,1400px)"><div class="modal-content bg-dark"><div class="modal-header border-0"><button type="button" class="btn-close btn-close-white ms-auto" data-bs-dismiss="modal"></button></div><div class="modal-body text-center"><img id="zoomImage" alt="확대 이미지" style="max-width:100%;max-height:84vh;object-fit:contain"></div></div></div></div>
      ${renderInspectModal()}
      <div class="modal fade" id="upsertModal" tabindex="-1" aria-hidden="true"><div class="modal-dialog modal-xl modal-dialog-scrollable"><div class="modal-content"><div class="modal-header"><h5 class="modal-title" id="upsertTitle">${esc(label)} 등록</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><form id="upsertForm"><input type="hidden" id="equipmentId"><div class="fw-edit-section"><div class="fw-edit-section-title"><strong>기본 정보</strong></div><div class="row g-3"><div class="col-md-6"><label class="form-label fw-bold">건물</label><select class="form-select" id="buildingSel" required></select></div><div class="col-md-6"><label class="form-label fw-bold">층</label><select class="form-select" id="floorSel" required></select></div><div class="col-12"><div class="fw-edit-section-title mb-2"><strong>도면 위치 선택</strong><span>도면 클릭으로 좌표를 지정합니다.</span></div><div class="border rounded p-2 bg-light"><div id="planCanvas" class="position-relative" style="height:480px;overflow:hidden;background:#fff;border:1px solid #ddd;border-radius:8px"><div id="planContent" style="position:absolute;transform-origin:center center"><img id="planImg" alt="도면" style="position:absolute;display:none"><div id="markerLayer" style="position:absolute;inset:0;z-index:3"></div></div></div><div class="small text-muted mt-2 d-flex flex-wrap gap-3"><span>선택 좌표: X <span id="coordXText">-</span> / Y <span id="coordYText">-</span></span><span>마우스 휠로 도면을 확대/축소할 수 있습니다.</span></div></div></div><div class="col-md-4"><label class="form-label fw-bold">${esc(installDateLabel())}</label><input type="${installDateInputType()}" class="form-control" id="manufactureMonth" required></div>${renderCycleField()}${renderTypeField()}${renderAirconFields()}<div class="col-md-3"><label class="form-label fw-bold">X 좌표(%)</label><input type="number" step="0.01" class="form-control" id="coordX"></div><div class="col-md-3"><label class="form-label fw-bold">Y 좌표(%)</label><input type="number" step="0.01" class="form-control" id="coordY"></div>${renderNoteAndPhotoFields()}</div></div><div class="fw-edit-section" id="historySection"><div class="fw-edit-section-title"><strong>점검 이력</strong><span>최근 12건 수정 / 추가 / 삭제</span></div><div class="table-responsive fw-history-wrap"><table class="table table-sm fw-history-table mb-0"><thead><tr><th style="width:34%">점검일</th><th>점검자</th><th style="width:160px">관리</th></tr></thead><tbody id="historyBody"><tr><td colspan="3" class="text-center text-muted">점검 이력이 없습니다.</td></tr></tbody></table></div></div></form></div><div class="modal-footer"><button class="btn btn-outline-secondary" data-bs-dismiss="modal">닫기</button><button class="btn btn-primary" id="btnSave">저장</button></div></div></div></div>`;
  }

  function renderNav() {
    const user = API.getUser(); const area = $('navAccountArea'); if (!area) return;
    area.innerHTML = user ? `<li class="nav-item dropdown"><a class="nav-link dropdown-toggle fw-semibold" href="#" role="button" data-bs-toggle="dropdown">${esc(user.displayName || user.username || user.loginId || '사용자')}</a><ul class="dropdown-menu dropdown-menu-end"><li><a class="dropdown-item" href="/account/index.html">내 정보</a></li>${API.canManage() ? '<li><a class="dropdown-item" href="/account/users.html">계정관리</a></li>' : ''}<li><hr class="dropdown-divider"></li><li class="px-3 pb-2"><button type="button" class="btn btn-sm btn-danger w-100" id="btnLogout">로그아웃</button></li></ul></li>` : '<li class="nav-item"><a class="btn btn-sm btn-outline-light" href="/index.html">로그인</a></li>';
    $('btnLogout')?.addEventListener('click', () => { window.FireWebNav?.logout?.() || (window.FireWebCsrf?.goLogin || (() => { location.href = '/index.html'; }))(); });
  }

  async function loadOptions() {
    try {
      const br = await API.req('/fire-api/qr/buildings'); const bj = br && br.ok ? await br.json().catch(() => null) : null;
      state.buildings = Array.isArray(bj?.data) ? bj.data : [];
    } catch { state.buildings = []; }
    try {
      const fr = await API.req('/fire-api/qr/floors'); const fj = fr && fr.ok ? await fr.json().catch(() => null) : null;
      state.floors = Array.isArray(fj?.data) ? fj.data : [];
    } catch { state.floors = []; }
    try {
      const r = await API.req('/fire-api/maps/building-floors'); const j = r && r.ok ? await r.json().catch(() => null) : null;
      state.buildingFloorMap = {};
      (Array.isArray(j?.data) ? j.data : []).forEach(b => { state.buildingFloorMap[String(b.buildingId)] = Array.isArray(b.floors) ? b.floors : []; });
    } catch { state.buildingFloorMap = {}; }
    fillSelects();
  }

  function fillSelects() {
    const bOpts = '<option value="">-- 건물 전체 --</option>' + state.buildings.map(b => `<option value="${esc(b.buildingId)}">${esc(b.buildingName)}</option>`).join('');
    const fOpts = '<option value="">-- 층 전체 --</option>' + state.floors.map(f => `<option value="${esc(f.floorId)}">${esc(f.floorName)}</option>`).join('');
    if ($('filterBuildingId')) $('filterBuildingId').innerHTML = bOpts;
    if ($('filterFloorId')) $('filterFloorId').innerHTML = fOpts;
    if ($('buildingSel')) $('buildingSel').innerHTML = bOpts.replace('건물 전체', '건물 선택');
    updateModalFloorOptions();
  }

  function updateModalFloorOptions() {
    const sel = $('floorSel'); if (!sel) return;
    const b = $('buildingSel')?.value || '';
    const list = b && state.buildingFloorMap[String(b)]?.length ? state.buildingFloorMap[String(b)] : state.floors;
    sel.innerHTML = '<option value="">-- 층 선택 --</option>' + list.map(f => `<option value="${esc(f.floorId)}">${esc(f.floorName)}</option>`).join('');
  }

  async function loadList() {
    const params = new URLSearchParams({ page: '0', size: '500' });
    if (state.q) params.set('q', state.q);
    if (state.buildingId) params.set('buildingId', state.buildingId);
    if (state.floorId) params.set('floorId', state.floorId);
    const res = await API.req(`${apiBase}?${params}`); if (!res) return;
    const json = await res.json().catch(() => null);
    if (res.ok && json?.success !== false) state.items = unwrap(json)?.content || [];
    else state.items = [];
    renderTable(filteredItems()); updateCounts();
  }

  function bucket(item) {
    if (isAirconPage()) return { inspect: Boolean(item.inspectionRequested) };
    const now = new Date(); now.setHours(0,0,0,0);
    const last = item.lastInspectionDate ? new Date(item.lastInspectionDate) : null;
    const inspect = !last || new Date(last.getTime() + 30*24*3600*1000) <= now;
    return { inspect };
  }

  function filteredItems() {
    if (!state.status) return state.items;
    return state.items.filter(it => bucket(it)[state.status]);
  }

  function updateCounts() {
    $('totalCount').textContent = String(state.items.length);
    $('countInspect').textContent = `(${state.items.filter(i => bucket(i).inspect).length})`;
  }

  function sortValue(item, key) {
    switch (key) {
      case 'rowNo': case 'serialNumber': return item.serialNumber || '';
      case 'buildingName': return item.buildingName || '';
      case 'floorName': return item.floorName || '';
      case 'equipmentType': return item.equipmentType || '';
      case 'manufactureDate': return item.manufactureDate || '';
      case 'lastInspectionDate': return item.lastInspectionDate || '';
      case 'lastInspectorName': return item.lastInspectorName || '';
      case 'note': return item.note || '';
      default: return '';
    }
  }

  function sorted(items) {
    const dir = state.sort.direction === 'desc' ? -1 : 1;
    return items.slice().sort((a, b) => {
      const av = sortValue(a, state.sort.key), bv = sortValue(b, state.sort.key);
      const blankA = String(av).trim() === '', blankB = String(bv).trim() === '';
      if (blankA && !blankB) return 1; if (!blankA && blankB) return -1;
      const c = String(av).localeCompare(String(bv), 'ko', { numeric: true, sensitivity: 'base' });
      if (c !== 0) return c * dir;
      return String(a.serialNumber || '').localeCompare(String(b.serialNumber || ''), 'ko', { numeric: true });
    });
  }

  function th(text, key, style = '') {
    const active = state.sort.key === key;
    const ind = active ? (state.sort.direction === 'asc' ? '▲' : '▼') : '↕';
    return `<th class="sortable-header js-sort" data-key="${key}" style="${style}">${esc(text)}<span class="sort-indicator">${ind}</span></th>`;
  }

  function renderTable(items) {
    const wrap = $('mainTableWrap'); if (!wrap) return;
    if (!items.length) { wrap.innerHTML = '<div class="text-center text-muted py-5">조회 결과가 없습니다.</div>'; return; }
    const rows = sorted(items).map((it, idx) => {
      const typeCell = isSimpleWaterPurifier() ? '' : `<td><div class="fw-semibold text-truncate" title="${esc(it.equipmentType || '')}">${esc(it.equipmentType || '-')}</div>${isAirconPage() ? `<div class="small text-muted mt-1">${esc([it.manufacturer, it.locationDescription, it.outdoorUnitCount ? '실외기 ' + outdoorUnitCount(it.outdoorUnitCount) + '대' : ''].filter(Boolean).join(' · ') || '-')}</div>` : ''}</td>`;
      const noteCell = isSimpleWaterPurifier() ? '' : `<td class="text-truncate" title="${esc(it.note || '')}">${esc(it.note || '-')}</td>`;
      const inspectionCells = isAirconPage() ? '' : `<td>${fmtDate(it.lastInspectionDate)}</td><td class="text-truncate" title="${esc(it.lastInspectorName || '')}">${esc(it.lastInspectorName || '-')}</td>`;
      const requestButton = isAirconPage()
        ? `<button class="btn btn-sm btn-fw-inspect js-inspect" data-id="${it.equipmentId}">점검 요청</button>`
        : `<button class="btn btn-sm btn-fw-inspect js-inspect" data-id="${it.equipmentId}">점검</button>`;
      return `<tr class="clickable-row js-detail" data-id="${it.equipmentId}">
        <td class="text-center">${idx + 1}</td><td class="text-center fw-bold">${esc(it.serialNumber || '-')}</td>
        <td class="text-truncate" title="${esc(it.buildingName || '')}">${esc(it.buildingName || '-')}</td>
        <td class="text-truncate" title="${esc(it.floorName || '')}">${esc(it.floorName || '-')}</td>
        ${typeCell}<td>${displayInstallDate(it.manufactureDate)}</td>${inspectionCells}
        <td>${bucket(it).inspect ? `<span class="fw-status fw-warn">${esc(inspectStatusLabel())}</span>` : '<span class="fw-status fw-ok">정상</span>'}</td>
        ${noteCell}<td class="text-center"><div class="facility-actions">${canEdit() ? `<button class="btn btn-sm btn-fw-edit js-edit" data-id="${it.equipmentId}">수정</button>${requestButton}<button class="btn btn-sm btn-fw-delete js-delete" data-id="${it.equipmentId}" data-serial="${esc(it.serialNumber || '')}">삭제</button>` : ''}</div></td>
      </tr>`;
    }).join('');
    const typeHead = isSimpleWaterPurifier() ? '' : th('종류', 'equipmentType', 'width:170px');
    const noteHead = isSimpleWaterPurifier() ? '' : th('비고', 'note', 'width:160px');
    wrap.innerHTML = `<div class="fw-table-wrap"><div class="table-responsive fw-list-scroll"><table class="table table-hover mb-0 facility-list-table"><thead class="table-dark"><tr>${th('No.', 'rowNo', 'width:50px;text-align:center')}${th(CFG.serialLabel || '설비 ID', 'serialNumber', 'width:92px;text-align:center')}${th('건물', 'buildingName', 'width:110px')}${th('층', 'floorName', 'width:80px')}${typeHead}${th(installDateLabel(), 'manufactureDate', 'width:110px')}${isAirconPage() ? '' : `${th('최종 점검일', 'lastInspectionDate', 'width:110px')}${th('점검자', 'lastInspectorName', 'width:100px')}`}${th('상태', 'lastInspectionDate', 'width:100px')}${noteHead}<th style="width:160px;text-align:center">관리</th></tr></thead><tbody>${rows}</tbody></table></div></div>`;
  }

  function markerImage(type) {
    const t = String(type || '').replace(/\s+/g, '').replace(/[.,_\-]/g, '').toLowerCase();
    const fallback = isWaterPurifierPage() ? builtinMarkerImages.water : builtinMarkerImages.aircon;
    const builtIn = (() => {
      if (isWaterPurifierPage()) return builtinMarkerImages.water;
      if (t.includes('실외') || t.includes('outdoor')) return builtinMarkerImages.airconOutdoor;
      if (t.includes('벽걸') || t.includes('wall')) return builtinMarkerImages.airconWall;
      if (t.includes('스탠드') || t.includes('stand')) return builtinMarkerImages.airconStand;
      if (t.includes('시스템') || t.includes('system')) return builtinMarkerImages.airconSystem;
      return fallback;
    })();
    return CFG.markerImages?.[type] || CFG.markerImages?.default || builtIn;
  }

  function airconExtraDetail(d) {
    if (!isAirconPage()) return '';
    return detailItem('제조사', d.manufacturer || '-') +
      detailItem('상세 위치', d.locationDescription || '-') +
      detailItem('실외기 대수', `${outdoorUnitCount(d.outdoorUnitCount)}대`);
  }

  async function getDetail(id) {
    const res = await API.req(`${apiBase}/${encodeURIComponent(id)}`); if (!res) return null;
    const json = await res.json().catch(() => null);
    if (!res.ok || json?.success === false || !json?.data) { alert(json?.message || '상세 정보를 불러오지 못했습니다.'); return null; }
    return json.data;
  }

  function detailQrType() {
    return isWaterPurifierPage() ? 'water' : 'aircon';
  }

  function detailQrImageUrl(qrKey) {
    return `/fire-api/qr/image?type=${encodeURIComponent(detailQrType())}&id=${encodeURIComponent(qrKey || '')}`;
  }

  function renderDetailPhotoSection(d) {
    const imageAlt = `${label} 사진`;
    const body = d.imagePath
      ? `<div class="fw-media-box text-center"><img src="${esc(d.imagePath)}" alt="${esc(imageAlt)}" class="img-fluid rounded shadow js-zoomable" style="max-height:300px;object-fit:contain" onerror="this.closest('.fw-media-box').innerHTML='<div class=&quot;fw-empty-box&quot;>사진을 불러오지 못했습니다.</div>'"></div>`
      : '<div class="fw-empty-box">등록된 사진이 없습니다.</div>';
    return `<div class="fw-edit-section"><div class="fw-edit-section-title"><strong>사진</strong><span>사용자가 업로드한 사진</span></div>${body}</div>`;
  }

  function renderDetailQrSection(d) {
    if (!canEdit()) return '';
    const qrKey = d.qrKey || d.serialNumber || d.equipmentId;
    if (!qrKey) {
      return '<div class="fw-edit-section"><div class="fw-edit-section-title"><strong>QR코드</strong></div><div class="fw-empty-box">QR 키 정보가 없습니다.</div></div>';
    }
    return `<div class="fw-edit-section"><div class="fw-edit-section-title"><strong>QR코드</strong></div><div class="fw-media-box text-center"><img src="${detailQrImageUrl(qrKey)}" data-zoom-kind="qr" alt="${esc(label)} QR코드" class="img-fluid rounded js-zoomable" style="width:220px;height:220px;object-fit:contain;background:#fff" loading="lazy"><div class="small text-muted fw-bold mt-2">${esc(qrKey)}</div></div></div>`;
  }

  async function openDetails(id) {
    const d = await getDetail(id); if (!d) return;
    const plan = await fetchPlanImage(d.buildingId, d.floorId, d.buildingName, d.floorName);
    const inspRows = (d.inspections || []).length ? d.inspections.map(r => `<tr><td>${fmtDate(r.inspectionDate)}</td><td>${esc(r.inspectorName || '-')}</td></tr>`).join('') : '<tr><td colspan="2" class="text-center text-muted">점검 이력이 없습니다.</td></tr>';
    const typeDetail = isSimpleWaterPurifier() ? '' : detailItem(CFG.typeLabel || '종류', d.equipmentType);
    const noteBox = (!isSimpleWaterPurifier() && d.note) ? `<div class="fw-media-box mt-3"><strong>비고</strong><div class="mt-2">${esc(d.note)}</div></div>` : '';
    const secondarySection = isAirconPage() ? noteBox : `<div class="fw-edit-section"><div class="fw-edit-section-title"><strong>점검 정보</strong><span>최근 12건</span></div><div class="fw-history-wrap table-responsive"><table class="table table-sm fw-history-table mb-0"><thead><tr><th>점검일</th><th>점검자</th></tr></thead><tbody>${inspRows}</tbody></table></div>${noteBox}</div>`;
    const photoSection = renderDetailPhotoSection(d);
    const qrSection = renderDetailQrSection(d);
    $('detailsModalBody').innerHTML = `<div class="row g-4"><div class="col-lg-6 d-flex flex-column gap-3"><div class="fw-edit-section"><div class="fw-edit-section-title"><strong>기본 정보</strong></div><div class="fw-detail-grid">${detailItem(CFG.serialLabel || '설비 ID', d.serialNumber)}${detailItem('건물', d.buildingName)}${detailItem('층', d.floorName)}${typeDetail}${detailItem(installDateLabel(), displayInstallDate(d.manufactureDate))}${detailItem('좌표', d.x != null && d.y != null ? `X ${Number(d.x).toFixed(2)} / Y ${Number(d.y).toFixed(2)}` : '-')}${airconExtraDetail(d)}</div></div>${secondarySection}</div><div class="col-lg-6 d-flex flex-column gap-3">${photoSection}${qrSection}<div class="fw-edit-section"><div class="fw-edit-section-title"><strong>도면 위치</strong><span>클릭하면 확대됩니다.</span></div>${plan ? `<div class="fw-media-box position-relative js-zoomable" id="detailPlanWrap" data-zoom-src="${esc(plan)}" data-zoom-kind="plan" style="height:320px;overflow:hidden;background:#fff;display:flex;align-items:center;justify-content:center"><img id="detailPlanImg" src="${esc(plan)}" alt="도면" style="max-width:100%;max-height:100%;object-fit:contain;display:block"><div id="detailMarkerLayer" style="position:absolute;inset:0;z-index:3;pointer-events:none"></div></div>` : '<div class="fw-empty-box">도면 정보가 없습니다.</div>'}</div></div></div>`;
    const modalEl = $('detailsModal');
    const drawDetailPlanMarker = () => drawSingleMarker('detailPlanWrap', 'detailPlanImg', 'detailMarkerLayer', d.x, d.y, markerImage(d.equipmentType || fixedType()));
    if (plan && modalEl) modalEl.addEventListener('shown.bs.modal', drawDetailPlanMarker, { once: true });
    bootstrapModal('detailsModal')?.show();
    if (plan) [80, 250, 600].forEach(delay => setTimeout(drawDetailPlanMarker, delay));
  }

  function detailItem(k, v) { return `<div class="fw-detail-item"><div class="fw-detail-item-label">${esc(k)}</div><div class="fw-detail-item-value">${esc(v || '-')}</div></div>`; }

  async function fetchPlanImage(buildingId, floorId, buildingName, floorName) {
    if (buildingId && floorId) {
      try { const r = await API.req(`/fire-api/maps/floor-data?${new URLSearchParams({ buildingId, floorId })}`); const j = r && r.ok ? await r.json().catch(() => null) : null; if (j?.data?.planImagePath) return j.data.planImagePath; } catch {}
    }
    return '';
  }

  // 도면 이미지는 기존 층별 도면과 동일하게 /fire-api/maps/floor-data DB 응답만 사용한다.

  function drawSingleMarker(wrapId, imgId, layerId, x, y, icon) {
    const wrap = $(wrapId), img = $(imgId), layer = $(layerId); if (!wrap || !img || !layer) return;
    const draw = () => {
      layer.innerHTML = ''; const cw = wrap.clientWidth, ch = wrap.clientHeight, iw = img.naturalWidth, ih = img.naturalHeight; if (!cw || !ch || !iw || !ih) return;
      const scale = Math.min(cw/iw, ch/ih), w = iw*scale, h = ih*scale, ox = (cw-w)/2, oy = (ch-h)/2;
      img.style.cssText = `position:absolute;left:${ox}px;top:${oy}px;width:${w}px;height:${h}px;display:block;object-fit:contain`;
      const nx = num(x), ny = num(y); if (nx == null || ny == null) return;
      const m = document.createElement('div');
      m.className = 'facility-map-marker';
      m.style.cssText = `position:absolute;left:${ox+w*nx/100}px;top:${oy+h*ny/100}px;width:28px;height:28px;transform:translate(-50%,-50%);z-index:3`;
      m.innerHTML = `<img src="${esc(icon)}" alt="" class="facility-marker-img">`;
      layer.appendChild(m);
    };
    if (img.complete) draw(); img.onload = draw; setTimeout(draw, 0);
  }

  async function openUpsert(id = 0, preset = {}) {
    if (!requireEditPermission()) return;
    state.editingId = Number(id || 0); state.selectedCoord = null;
    $('upsertTitle').textContent = state.editingId ? `${label} 수정` : `${label} 등록`;
    $('equipmentId').value = state.editingId || '';
    $('historySection').style.display = state.editingId && !isAirconPage() ? '' : 'none';
    if ($('photo')) $('photo').value = ''; fillTypeOptions(); resetFormDefaults();
    if (state.editingId) {
      const d = await getDetail(state.editingId); if (!d) return;
      $('buildingSel').value = d.buildingId || ''; updateModalFloorOptions(); $('floorSel').value = d.floorId || '';
      if ($('equipmentType')) $('equipmentType').value = d.equipmentType || fixedType(); $('manufactureMonth').value = installDateValue(d.manufactureDate);
      if ($('cycleYears')) $('cycleYears').value = d.replacementCycleYears || 10; if ($('note')) $('note').value = d.note || '';
      if (isAirconPage()) {
        if ($('serialNumber')) $('serialNumber').value = d.serialNumber || '';
        if ($('manufacturer')) $('manufacturer').value = d.manufacturer || '';
        if ($('locationDescription')) $('locationDescription').value = d.locationDescription || '';
        if ($('outdoorUnitCount')) $('outdoorUnitCount').value = String(outdoorUnitCount(d.outdoorUnitCount));
        if ($('inspectionRequested')) $('inspectionRequested').value = String(Boolean(d.inspectionRequested));
      }
      setCoord(d.x, d.y); renderHistory(d.inspections || []);
    } else {
      renderHistory([]);
      if (preset.buildingId) { $('buildingSel').value = preset.buildingId; updateModalFloorOptions(); }
      if (preset.floorId) $('floorSel').value = preset.floorId;
      setCoord(preset.x ?? '', preset.y ?? '');
    }
    await loadPlanForModal();
    bootstrapModal('upsertModal')?.show();
  }

  function resetFormDefaults() {
    $('upsertForm')?.reset(); $('manufactureMonth').value = isSimpleWaterPurifier() ? today() : new Date().toISOString().slice(0,7); if ($('cycleYears')) $('cycleYears').value = '10'; if ($('equipmentType')) $('equipmentType').value = fixedType();
    if (isAirconPage()) {
      if ($('serialNumber')) $('serialNumber').value = '';
      if ($('manufacturer')) $('manufacturer').value = '';
      if ($('locationDescription')) $('locationDescription').value = '';
      if ($('outdoorUnitCount')) $('outdoorUnitCount').value = '1';
      if ($('inspectionRequested')) $('inspectionRequested').value = 'false';
    }
  }
  function fillTypeOptions() { if (!$('equipmentType')) return; if (isSimpleWaterPurifier()) { $('equipmentType').value = fixedType(); return; } $('equipmentType').innerHTML = '<option value="">-- 종류 선택 --</option>' + (CFG.typeOptions || ['기타']).map(t => `<option value="${esc(t)}">${esc(t)}</option>`).join(''); }
  function setCoord(x, y) { const rx = coord2(x); const ry = coord2(y); $('coordX').value = rx ?? ''; $('coordY').value = ry ?? ''; $('coordXText').textContent = rx != null ? rx.toFixed(2) : '-'; $('coordYText').textContent = ry != null ? ry.toFixed(2) : '-'; state.selectedCoord = rx != null && ry != null ? { x: rx, y: ry } : null; renderPlanMarkers(); }

  async function loadPlanForModal() {
    const b = $('buildingSel')?.value, f = $('floorSel')?.value;
    const img = $('planImg'); state.existingMarkers = []; state.planImagePath = ''; state.planView.scale = 1;
    if (!b || !f || !img) { img?.removeAttribute('src'); if (img) img.style.display = 'none'; renderPlanMarkers(); return; }
    state.planImagePath = await fetchPlanImage(b, f, $('buildingSel')?.selectedOptions?.[0]?.textContent, $('floorSel')?.selectedOptions?.[0]?.textContent);
    state.existingMarkers = state.items.filter(it => String(it.buildingId) === String(b) && String(it.floorId) === String(f) && Number(it.equipmentId) !== Number(state.editingId) && it.x != null && it.y != null);
    if (state.planImagePath) { img.src = state.planImagePath; img.style.display = 'block'; img.onload = renderPlanMarkers; setTimeout(renderPlanMarkers, 80); }
    else { img.removeAttribute('src'); img.style.display = 'none'; renderPlanMarkers(); }
  }

  function renderPlanMarkers() {
    const canvas = $('planCanvas'), content = $('planContent'), img = $('planImg'), layer = $('markerLayer'); if (!canvas || !content || !img || !layer) return;
    layer.innerHTML = ''; if (!img.naturalWidth || !img.naturalHeight) return;
    const cw = canvas.clientWidth, ch = canvas.clientHeight, iw = img.naturalWidth, ih = img.naturalHeight;
    const scale = Math.min(cw/iw, ch/ih), w = iw*scale, h = ih*scale, ox = (cw-w)/2, oy = (ch-h)/2;
    content.style.cssText = `position:absolute;left:${ox}px;top:${oy}px;width:${w}px;height:${h}px;transform-origin:center center;transform:scale(${state.planView.scale})`;
    img.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;display:block;object-fit:fill';
    const add = (x, y, icon, selected, title) => { const nx = num(x), ny = num(y); if (nx == null || ny == null) return; const m = document.createElement('div'); m.className = `facility-map-marker${selected ? ' selected' : ''}`; m.title = title || ''; m.style.cssText = `position:absolute;left:${w*nx/100}px;top:${h*ny/100}px;width:${selected ? 30 : 22}px;height:${selected ? 30 : 22}px;transform:translate(-50%,-50%);opacity:${selected ? 1 : .7};cursor:${selected ? 'default':'pointer'};z-index:${selected ? 4 : 2}`; m.innerHTML = `<img src="${esc(icon)}" alt="" class="facility-marker-img">`; if (!selected) m.addEventListener('click', e => { e.stopPropagation(); setCoord(nx, ny); }); layer.appendChild(m); };
    state.existingMarkers.forEach(it => add(it.x, it.y, markerImage(it.equipmentType), false, it.serialNumber));
    if (state.selectedCoord) add(state.selectedCoord.x, state.selectedCoord.y, markerImage($('equipmentType')?.value || fixedType()), true, '선택 위치');
  }

  function zoomPlanWithWheel(event) {
    const canvas = $('planCanvas'), img = $('planImg'); if (!canvas || !img?.naturalWidth) return;
    event.preventDefault();
    const next = Math.max(1, Math.min(4, state.planView.scale * (event.deltaY < 0 ? 1.16 : 1 / 1.16)));
    if (next === state.planView.scale) return;
    state.planView.scale = next;
    renderPlanMarkers();
  }

  function renderHistory(rows) {
    const body = $('historyBody'); if (!body) return;
    const list = rows.slice(0, 12);
    const blank = `<tr class="js-new-history"><td><input type="date" class="form-control form-control-sm js-h-date" value="${today()}"><input type="hidden" class="js-h-faulty" value="false"><input type="hidden" class="js-h-reason" value=""></td><td class="text-muted">신규</td><td><button type="button" class="btn btn-sm btn-primary js-h-add">추가</button></td></tr>`;
    body.innerHTML = list.map(r => `<tr data-id="${r.inspectionId}"><td><input type="date" class="form-control form-control-sm js-h-date" value="${fmtDate(r.inspectionDate)}"><input type="hidden" class="js-h-faulty" value="false"><input type="hidden" class="js-h-reason" value=""></td><td class="fw-history-name">${esc(r.inspectorName || '-')}</td><td><div class="fw-history-actions"><button type="button" class="btn btn-sm btn-outline-primary js-h-save">저장</button><button type="button" class="btn btn-sm btn-outline-danger js-h-del">삭제</button></div></td></tr>`).join('') + blank;
  }

  async function saveEquipment() {
    if (!requireEditPermission()) return;
    const payload = {
      equipmentId: state.editingId || null,
      buildingId: Number($('buildingSel').value),
      floorId: Number($('floorSel').value),
      equipmentType: fixedType() || $('equipmentType')?.value || '',
      manufactureDate: dateFromInstallInput($('manufactureMonth').value),
      replacementCycleYears: Number($('cycleYears')?.value || 10),
      x: coord2($('coordX').value),
      y: coord2($('coordY').value),
      note: $('note')?.value || ''
    };
    if (isAirconPage()) {
      payload.serialNumber = $('serialNumber')?.value?.trim() || null;
      payload.manufacturer = $('manufacturer')?.value?.trim() || null;
      payload.locationDescription = $('locationDescription')?.value?.trim() || null;
      payload.outdoorUnitCount = outdoorUnitCount($('outdoorUnitCount')?.value);
      if (state.editingId) payload.inspectionRequested = $('inspectionRequested')?.value === 'true';
    }
    if (isSimpleWaterPurifier()) {
      payload.equipmentType = fixedType();
      payload.replacementCycleYears = 10;
      payload.note = null;
    }
    if (!payload.buildingId || !payload.floorId || !payload.equipmentType || !payload.manufactureDate) return alert('필수 항목을 입력하세요.');
    const res = await API.req(apiBase, { method: 'POST', body: payload }); if (!res) return;
    const json = await res.json().catch(() => null);
    if (!res.ok || json?.success === false) return alert(json?.message || '저장 실패');
    const saved = json.data; const file = $('photo')?.files?.[0];
    if (file && saved?.equipmentId) await uploadImage(saved.equipmentId, file);
    postFloorMessage('fireweb:floor-action-saved');
    bootstrapModal('upsertModal')?.hide(); showToast(`${label} 저장 완료`); await loadList();
  }

  async function uploadImage(id, file) {
    if (!requireEditPermission()) return;
    const fd = new FormData(); fd.append('file', file);
    const res = await API.req(`${apiBase}/${encodeURIComponent(id)}/image`, { method: 'POST', body: fd });
    if (!res || !res.ok) { const t = await res?.text().catch(() => ''); alert(t || '이미지 업로드 실패'); }
  }

  async function inspectEquipment() {
    if (isAirconPage()) return submitAirconFaultRequest();
    if (!requireEditPermission()) return;
    const isFaulty = $('inspectBad').checked; const faultReason = $('inspectFaultReason').value.trim();
    if (isFaulty && !faultReason) return alert('점검 사유를 입력하세요.');
    const res = await API.req(`${apiBase}/inspect`, { method: 'POST', body: { equipmentId: state.inspectId, inspectionDate: $('inspectDate').value || today(), faulty: isFaulty, faultReason } }); if (!res) return;
    const json = await res.json().catch(() => null); if (!res.ok || json?.success === false) return alert(json?.message || '점검 저장 실패');
    const file = $('inspectPhoto')?.files?.[0]; if (file) await uploadImage(state.inspectId, file);
    postFloorMessage('fireweb:floor-action-saved');
    bootstrapModal('inspectModal')?.hide(); showToast('점검이 완료되었습니다.'); await loadList();
  }

  async function submitAirconFaultRequest() {
    if (!requireEditPermission()) return;
    const item = state.items.find(it => Number(it.equipmentId) === Number(state.inspectId));
    let qrKey = item?.qrKey || '';
    if (!qrKey && state.inspectId) {
      const detail = await getDetail(state.inspectId);
      qrKey = detail?.qrKey || '';
    }
    if (!qrKey) return alert('점검 요청에 사용할 QR 키 정보가 없습니다.');
    const description = $('faultDescription')?.value?.trim() || '';
    if (!description) { $('faultDescription')?.focus(); return alert('고장내용을 입력하세요.'); }
    const fd = new FormData();
    fd.append('reporterName', $('faultReporterName')?.value?.trim() || '');
    fd.append('reporterDepartment', $('faultReporterDepartment')?.value?.trim() || '');
    fd.append('faultDescription', description);
    const res = await API.req(`/facility-api/mobile/air-conditioners/${encodeURIComponent(qrKey)}/fault-reports`, { method: 'POST', body: fd }); if (!res) return;
    const json = await res.json().catch(() => null);
    if (!res.ok || json?.success === false) return alert(json?.message || '점검 요청 접수 실패');
    postFloorMessage('fireweb:floor-action-saved');
    bootstrapModal('inspectModal')?.hide();
    showToast('점검 요청이 접수되었습니다.');
    await loadList();
  }

  async function saveHistoryRow(tr, mode) {
    if (!requireEditPermission()) return;
    const inspectionDate = tr.querySelector('.js-h-date')?.value; const isFaulty = tr.querySelector('.js-h-faulty')?.value === 'true'; const faultReason = tr.querySelector('.js-h-reason')?.value || '';
    if (!inspectionDate) return alert('점검일을 입력하세요.'); if (isFaulty && !faultReason.trim()) return alert('점검 사유를 입력하세요.');
    const url = mode === 'add' ? `${apiBase}/${state.editingId}/inspections` : `${apiBase}/${state.editingId}/inspections/${tr.dataset.id}`;
    const method = mode === 'add' ? 'POST' : 'PATCH';
    const res = await API.req(url, { method, body: { inspectionDate, isFaulty, faultReason } }); const json = res && await res.json().catch(() => null);
    if (!res || !res.ok || json?.success === false) return alert(json?.message || '점검 이력 저장 실패');
    const d = await getDetail(state.editingId); renderHistory(d?.inspections || []); showToast('점검 이력을 저장했습니다.'); await loadList();
  }

  async function deleteHistoryRow(tr) {
    if (!requireEditPermission()) return;
    if (!confirm('점검 이력을 삭제할까요?')) return;
    const res = await API.req(`${apiBase}/${state.editingId}/inspections/${tr.dataset.id}`, { method: 'DELETE' }); const json = res && await res.json().catch(() => null);
    if (!res || !res.ok || json?.success === false) return alert(json?.message || '점검 이력 삭제 실패');
    const d = await getDetail(state.editingId); renderHistory(d?.inspections || []); showToast('점검 이력을 삭제했습니다.'); await loadList();
  }

  async function deleteEquipment(id, serial) {
    if (!requireEditPermission()) return;
    if (!confirm(`${serial || label} 항목을 삭제할까요?`)) return;
    const res = await API.req(`${apiBase}/${encodeURIComponent(id)}`, { method: 'DELETE' }); const json = res && await res.json().catch(() => null);
    if (!res || !res.ok || json?.success === false) return alert(json?.message || '삭제 실패');
    postFloorMessage('fireweb:floor-action-saved');
    showToast('삭제되었습니다.'); await loadList();
  }

  function openZoomImage(sourceEl) {
    const img = $('zoomImage');
    if (!img || !sourceEl) return;
    const src = sourceEl.dataset?.zoomSrc || sourceEl.currentSrc || sourceEl.src;
    if (!src) return;
    const kind = sourceEl.dataset?.zoomKind || 'photo';
    img.src = src;
    img.alt = sourceEl.getAttribute('alt') || '확대 이미지';
    img.style.width = kind === 'qr' ? 'min(82vw, 720px)' : (kind === 'plan' ? 'min(92vw, 1200px)' : 'auto');
    img.style.height = 'auto';
    img.style.maxWidth = '100%';
    img.style.maxHeight = '84vh';
    img.style.objectFit = 'contain';
    img.style.background = kind === 'qr' ? '#fff' : 'transparent';
    img.style.padding = kind === 'qr' ? '24px' : '0';
    return bootstrapModal('imageZoomModal')?.show();
  }

  function bindEvents() {
    document.addEventListener('click', async (e) => {
      const sort = e.target.closest('.js-sort'); if (sort) { const key = sort.dataset.key; if (state.sort.key === key) state.sort.direction = state.sort.direction === 'asc' ? 'desc' : 'asc'; else state.sort = { key, direction: 'asc' }; renderTable(filteredItems()); return; }
      const detail = e.target.closest('.js-detail'); if (detail && !e.target.closest('button')) return openDetails(detail.dataset.id);
      const edit = e.target.closest('.js-edit'); if (edit) { e.stopPropagation(); if (!requireEditPermission()) return; return openUpsert(edit.dataset.id); }
      const insp = e.target.closest('.js-inspect'); if (insp) { e.stopPropagation(); if (!requireEditPermission()) return; state.inspectId = Number(insp.dataset.id); if (isAirconPage()) { if ($('faultReporterName')) $('faultReporterName').value = ''; if ($('faultReporterDepartment')) $('faultReporterDepartment').value = ''; if ($('faultDescription')) $('faultDescription').value = ''; return bootstrapModal('inspectModal')?.show(); } $('inspectDate').value = today(); $('inspectOk').checked = true; $('inspectFaultReason').value = ''; $('inspectPhoto').value = ''; return bootstrapModal('inspectModal')?.show(); }
      const del = e.target.closest('.js-delete'); if (del) { e.stopPropagation(); if (!requireEditPermission()) return; return deleteEquipment(del.dataset.id, del.dataset.serial); }
      const zoom = e.target.closest('.js-zoomable'); if (zoom) return openZoomImage(zoom);
      const addH = e.target.closest('.js-h-add'); if (addH) return saveHistoryRow(addH.closest('tr'), 'add');
      const saveH = e.target.closest('.js-h-save'); if (saveH) return saveHistoryRow(saveH.closest('tr'), 'save');
      const delH = e.target.closest('.js-h-del'); if (delH) return deleteHistoryRow(delH.closest('tr'));
    });
    $('btnAdd')?.addEventListener('click', () => openUpsert(0));
    $('btnSearch')?.addEventListener('click', () => { state.q = $('filterQ').value.trim(); state.buildingId = $('filterBuildingId').value; state.floorId = $('filterFloorId').value; loadList(); });
    $('btnReset')?.addEventListener('click', () => { state.q = state.buildingId = state.floorId = ''; $('filterQ').value = ''; $('filterBuildingId').value = ''; $('filterFloorId').value = ''; loadList(); });
    $('btnStatusInspect')?.addEventListener('click', () => { state.status = state.status === 'inspect' ? null : 'inspect'; renderTable(filteredItems()); });
    $('btnSave')?.addEventListener('click', saveEquipment);
    $('btnConfirmInspect')?.addEventListener('click', inspectEquipment);
    $('buildingSel')?.addEventListener('change', () => { updateModalFloorOptions(); loadPlanForModal(); });
    $('floorSel')?.addEventListener('change', loadPlanForModal);
    $('equipmentType')?.addEventListener('change', renderPlanMarkers);
    $('coordX')?.addEventListener('input', () => setCoord($('coordX').value, $('coordY').value));
    $('coordY')?.addEventListener('input', () => setCoord($('coordX').value, $('coordY').value));
    $('planCanvas')?.addEventListener('click', (e) => { const content = $('planContent'), img = $('planImg'); if (!content || !img || !img.naturalWidth) return; const rect = content.getBoundingClientRect(); if (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom) return; const x = ((e.clientX - rect.left) / rect.width) * 100; const y = ((e.clientY - rect.top) / rect.height) * 100; setCoord(x, y); });
    $('planCanvas')?.addEventListener('wheel', zoomPlanWithWheel, { passive: false });
  }

  async function handleInitialAction() {
    const params = initialQuery;
    const addRequested = params.get('add') === '1' || params.get('embedAdd') === '1';
    const detailsId = params.get('details');
    const qrKey = params.get('qrKey');
    const editId = params.get('edit');
    const inspectId = params.get('inspect');
    if (addRequested) {
      if (!requireEditPermission()) return;
      await openUpsert(0, {
        buildingId: params.get('buildingId') || '',
        floorId: params.get('floorId') || '',
        x: params.get('x') || '',
        y: params.get('y') || ''
      });
      return;
    }
    if (detailsId) {
      await openDetails(detailsId);
      return;
    }
    if (qrKey) {
      const matched = state.items.find(it => String(it.qrKey || '') === String(qrKey));
      if (matched?.equipmentId) {
        await openDetails(matched.equipmentId);
        return;
      }
      alert('해당 QR 설비를 찾을 수 없습니다.');
    }
    if (editId) {
      if (!requireEditPermission()) return;
      await openUpsert(editId);
      return;
    }
    if (inspectId) {
      if (!requireEditPermission()) return;
      state.inspectId = Number(inspectId);
      if (isAirconPage()) {
        if ($('faultReporterName')) $('faultReporterName').value = '';
        if ($('faultReporterDepartment')) $('faultReporterDepartment').value = '';
        if ($('faultDescription')) $('faultDescription').value = '';
        bootstrapModal('inspectModal')?.show();
        return;
      }
      $('inspectDate').value = today();
      $('inspectOk').checked = true;
      $('inspectFaultReason').value = '';
      $('inspectPhoto').value = '';
      bootstrapModal('inspectModal')?.show();
    }
  }

  async function init() {
    await loadOtherAdminPermission();
    renderShell(); applyEmbeddedShell(); bindEvents(); fillTypeOptions();
    await loadOptions(); await loadList();
    await handleInitialAction();
    ['upsertModal','inspectModal','detailModal'].forEach(id => {
      $(id)?.addEventListener('hidden.bs.modal', () => postFloorMessage('fireweb:floor-action-close'));
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
