/* 분류(대/중/소) 드릴다운 + 매뉴얼 목록/상세 + 엑셀 업로드 모달 로직 */
let tree = [];              // 전체 분류 트리 (대분류 배열, 각 children 재귀)
let path = { majorId: null, middleId: null, minorId: null };
let currentManualId = null;
let isAdminUser = false;

document.addEventListener('DOMContentLoaded', async () => {
  if (!SAFETY.requireAuth()) return;
  SAFETY.renderNav('index.html');
  isAdminUser = await SAFETY.isAdmin();
  if (isAdminUser) {
    document.getElementById('btn-excel-upload').classList.remove('d-none');
    document.getElementById('btn-add-context').classList.remove('d-none');
  }
  await loadTree();
  render();
});

async function loadTree() {
  try {
    tree = await SAFETY.api('/safety-api/categories');
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 트리 탐색 유틸
// ================================================================
function findNode(id) {
  if (id == null) return null;
  let found = null;
  const walk = (nodes) => {
    (nodes || []).forEach(n => {
      if (Number(n.categoryId) === Number(id)) found = n;
      walk(n.children);
    });
  };
  walk(tree);
  return found;
}

function currentMajor() { return findNode(path.majorId); }
function currentMiddle() { return findNode(path.middleId); }
function currentMinor() { return findNode(path.minorId); }

// ================================================================
// 브레드크럼 + 상단 "추가" 버튼 컨텍스트
// ================================================================
function goTo(level) {
  if (level === 'root') { path = { majorId: null, middleId: null, minorId: null }; }
  else if (level === 'major') { path.middleId = null; path.minorId = null; }
  else if (level === 'middle') { path.minorId = null; }
  closeDetail();
  render();
}

function selectMajor(id) { path.majorId = id; path.middleId = null; path.minorId = null; closeDetail(); render(); }
function selectMiddle(id) { path.middleId = id; path.minorId = null; closeDetail(); render(); }
function selectMinor(id) { path.minorId = id; closeDetail(); render(); }

function renderCrumb() {
  const bar = document.getElementById('crumbBar');
  const items = [];
  items.push(`<button class="crumb-item ${!path.majorId ? 'active' : ''}" onclick="goTo('root')"><i class="fas fa-layer-group"></i>전체 분류</button>`);
  const maj = currentMajor();
  if (maj) {
    items.push(`<span class="crumb-sep"><i class="fas fa-chevron-right"></i></span>`);
    items.push(`<button class="crumb-item ${!path.middleId ? 'active' : ''}" onclick="goTo('major')">${SAFETY.escapeHtml(maj.name)}</button>`);
  }
  const mid = currentMiddle();
  if (mid) {
    items.push(`<span class="crumb-sep"><i class="fas fa-chevron-right"></i></span>`);
    items.push(`<button class="crumb-item ${!path.minorId ? 'active' : ''}" onclick="goTo('middle')">${SAFETY.escapeHtml(mid.name)}</button>`);
  }
  const min = currentMinor();
  if (min) {
    items.push(`<span class="crumb-sep"><i class="fas fa-chevron-right"></i></span>`);
    items.push(`<button class="crumb-item active" onclick="goTo('minor')">${SAFETY.escapeHtml(min.name)}<span class="level-tag">소분류</span></button>`);
  }
  bar.innerHTML = items.join('');
}

function updateAddButtonLabel() {
  const btn = document.getElementById('btn-add-context');
  const label = document.getElementById('btn-add-context-label');
  if (!isAdminUser) return;
  if (!path.majorId) label.textContent = '대분류 추가';
  else if (!path.middleId) label.textContent = '중분류 추가';
  else if (!path.minorId) label.textContent = '소분류 추가';
  else label.textContent = '매뉴얼 추가';
  btn.classList.remove('d-none');
}

function onAddContextClick() {
  if (!path.minorId) {
    openCategoryCreateModal();
  } else {
    openManualCreateModal();
  }
}

// ================================================================
// 메인 렌더링 — 분류 카드 그리드 or 매뉴얼 목록
// ================================================================
function render() {
  renderCrumb();
  updateAddButtonLabel();
  const pane = document.getElementById('browsePane');

  if (path.minorId) {
    renderManualPane(pane);
    return;
  }

  let nodes, levelLabel, icon, colors;
  if (!path.majorId) {
    nodes = tree; levelLabel = '대분류'; icon = 'fa-industry'; colors = ['#4f46e5', '#7c3aed'];
  } else if (!path.middleId) {
    nodes = currentMajor() ? currentMajor().children : []; levelLabel = '중분류'; icon = 'fa-cogs'; colors = ['#0ea5e9', '#06b6d4'];
  } else {
    nodes = currentMiddle() ? currentMiddle().children : []; levelLabel = '소분류'; icon = 'fa-tags'; colors = ['#f59e0b', '#f97316'];
  }

  if (!nodes || !nodes.length) {
    pane.innerHTML = `<div class="empty-state"><i class="fas fa-folder-open"></i>등록된 ${levelLabel}가 없습니다.${isAdminUser ? `<div class="mt-2"><button class="btn-modern btn-primary-modern" onclick="onAddContextClick()"><i class="fas fa-plus"></i>${levelLabel} 추가</button></div>` : ''}</div>`;
    return;
  }

  const clickFn = !path.majorId ? 'selectMajor' : (!path.middleId ? 'selectMiddle' : 'selectMinor');
  const sorted = [...nodes].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  pane.innerHTML = `<div class="cat-grid">` + sorted.map(n => `
    <div class="cat-card" onclick="${clickFn}(${n.categoryId})">
      <div class="cat-icon" style="background:linear-gradient(135deg,${colors[0]},${colors[1]})"><i class="fas ${icon}"></i></div>
      <div class="cat-name">${SAFETY.escapeHtml(n.name)}</div>
      <div class="cat-sub">${(n.children || []).length ? (n.children.length + '개 하위 분류') : (levelLabel === '소분류' ? '매뉴얼 보기' : '하위 분류 없음')}</div>
      ${isAdminUser ? `<div class="cat-tools" onclick="event.stopPropagation()">
        <button onclick="openCategoryEditModal(${n.categoryId})" title="수정"><i class="fas fa-pen"></i></button>
        <button class="danger" onclick="deleteCategory(${n.categoryId})" title="삭제"><i class="fas fa-trash"></i></button>
      </div>` : ''}
    </div>`).join('') + `</div>`;
}

async function renderManualPane(pane) {
  pane.innerHTML = `<div class="manual-card"><div class="mc-head"><h5>불러오는 중...</h5></div></div>`;
  const min = currentMinor();
  try {
    const manuals = await SAFETY.api('/safety-api/categories/' + path.minorId + '/manuals');
    const sorted = [...manuals].sort((a, b) => a.sortOrder - b.sortOrder);
    pane.innerHTML = `<div class="manual-card">
      <div class="mc-head"><h5>${SAFETY.escapeHtml(min ? min.name : '')} - 매뉴얼 목록</h5>
        <span class="text-muted small">${sorted.length}건</span></div>
      <div id="manualRowsHolder">${sorted.length ? sorted.map(m => `
        <a href="#" class="manual-row" onclick="openDetail(${m.manualId}); return false;">
          <span class="mr-title"><i class="fas fa-file-alt"></i>${SAFETY.escapeHtml(m.title)}</span>
          <span class="mr-date">${(m.updatedAt || '').replace('T', ' ').substring(0, 16)}</span>
        </a>`).join('') : `<div class="empty-state" style="padding:36px"><i class="fas fa-file-circle-question"></i>등록된 매뉴얼이 없습니다.</div>`}
      </div>
    </div>`;
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 매뉴얼 상세
// ================================================================
async function openDetail(manualId) {
  currentManualId = manualId;
  document.getElementById('browsePane').style.display = 'none';
  document.getElementById('manualDetailPane').style.display = '';
  if (isAdminUser) {
    document.getElementById('btn-add-step').classList.remove('d-none');
    document.getElementById('btn-delete-manual').classList.remove('d-none');
  }
  await loadDetail();
}

function closeDetail() {
  currentManualId = null;
  document.getElementById('browsePane').style.display = '';
  document.getElementById('manualDetailPane').style.display = 'none';
}

async function loadDetail() {
  try {
    const d = await SAFETY.api('/safety-api/manuals/' + currentManualId);
    document.getElementById('detailTitle').textContent = d.title;
    document.getElementById('detailMeta').textContent = d.categoryName ? ('분류: ' + d.categoryName) : '';
    renderSteps(d.steps || []);
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderSteps(steps) {
  const tbody = document.getElementById('stepRows');
  if (!steps.length) {
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">등록된 단계가 없습니다.</td></tr>';
    return;
  }
  tbody.innerHTML = steps.map(s => `
    <tr>
      <td class="text-center"><span class="step-no-badge">${s.stepNo}</span></td>
      <td>${(s.photos || []).map(p => `<img src="${p.url}" style="max-width:100%;max-height:110px;display:block;margin-bottom:4px;border:1px solid var(--line);border-radius:8px" alt="${SAFETY.escapeHtml(p.originalName)}">`).join('') || '<span class="text-muted small">-</span>'}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.description || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.hazard || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.safetyEquipment || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.remark || '')}</td>
      <td class="text-center">
        ${isAdminUser ? `
          <button class="btn btn-sm btn-outline-secondary mb-1" onclick="openStepModal(${s.stepId})"><i class="fas fa-pen"></i></button>
          <button class="btn btn-sm btn-outline-danger" onclick="deleteStep(${s.stepId})"><i class="fas fa-trash"></i></button>` : ''}
      </td>
    </tr>`).join('');
}

// ================================================================
// 분류 등록/수정 모달
// ================================================================
function openCategoryCreateModal() {
  document.getElementById('cat-id').value = '';
  document.getElementById('cat-name').value = '';
  document.getElementById('cat-sort').value = 0;
  let ctx = '최상위(대분류)';
  if (path.middleId) ctx = (currentMiddle() ? currentMiddle().name : '') + ' 아래 소분류';
  else if (path.majorId) ctx = (currentMajor() ? currentMajor().name : '') + ' 아래 중분류';
  document.getElementById('cat-context-text').textContent = ctx;
  document.getElementById('categoryModalTitle').textContent = '분류 추가';
  new bootstrap.Modal(document.getElementById('categoryModal')).show();
}

function openCategoryEditModal(categoryId) {
  const node = findNode(categoryId);
  if (!node) return;
  document.getElementById('cat-id').value = categoryId;
  document.getElementById('cat-name').value = node.name;
  document.getElementById('cat-sort').value = node.sortOrder;
  document.getElementById('cat-context-text').textContent = '이름/표시순서 수정';
  document.getElementById('categoryModalTitle').textContent = '분류 수정';
  new bootstrap.Modal(document.getElementById('categoryModal')).show();
}

async function saveCategory() {
  const id = document.getElementById('cat-id').value;
  const name = document.getElementById('cat-name').value.trim();
  if (!name) { SAFETY.toast('분류명을 입력하세요.', false); return; }
  const sortOrder = Number(document.getElementById('cat-sort').value) || 0;
  try {
    if (id) {
      await SAFETY.api('/safety-api/categories/' + id, { method: 'PUT', body: { name, sortOrder } });
    } else {
      let parentId = null;
      if (path.middleId) parentId = path.middleId;
      else if (path.majorId) parentId = path.majorId;
      await SAFETY.api('/safety-api/categories', { method: 'POST', body: { name, parentId, sortOrder } });
    }
    bootstrap.Modal.getInstance(document.getElementById('categoryModal')).hide();
    SAFETY.toast('저장되었습니다.');
    await loadTree();
    render();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteCategory(categoryId) {
  if (!confirm('이 분류를 삭제하시겠습니까? (하위 분류/매뉴얼이 있으면 삭제할 수 없습니다)')) return;
  try {
    await SAFETY.api('/safety-api/categories/' + categoryId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    await loadTree();
    render();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 매뉴얼 등록/수정 모달
// ================================================================
function openManualCreateModal() {
  if (!path.minorId) { SAFETY.toast('먼저 소분류까지 선택하세요.', false); return; }
  document.getElementById('man-id').value = '';
  document.getElementById('man-title').value = '';
  document.getElementById('man-sort').value = 0;
  const min = currentMinor();
  document.getElementById('man-context-note').textContent = '등록 분류: ' + (min ? min.name : '') + ' · 등록 후 상세 화면에서 단계(공정 순서)를 추가할 수 있습니다.';
  document.getElementById('manualModalTitle').textContent = '매뉴얼 추가';
  new bootstrap.Modal(document.getElementById('manualModal')).show();
}

async function saveManual() {
  const title = document.getElementById('man-title').value.trim();
  if (!title) { SAFETY.toast('매뉴얼 제목을 입력하세요.', false); return; }
  const sortOrder = Number(document.getElementById('man-sort').value) || 0;
  try {
    await SAFETY.api('/safety-api/manuals', {
      method: 'POST',
      body: { categoryId: path.minorId, title, sortOrder, steps: [] },
    });
    bootstrap.Modal.getInstance(document.getElementById('manualModal')).hide();
    SAFETY.toast('매뉴얼이 등록되었습니다.');
    render();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteManual() {
  if (!confirm('이 매뉴얼을 삭제하시겠습니까?')) return;
  try {
    await SAFETY.api('/safety-api/manuals/' + currentManualId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    closeDetail();
    render();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 단계 등록/수정/삭제
// ================================================================
function openStepModal(stepId) {
  document.getElementById('stepModalTitle').textContent = stepId ? '단계 수정' : '단계 추가';
  document.getElementById('step-id').value = stepId || '';
  document.getElementById('step-photo-upload-wrap').style.display = stepId ? '' : 'none';
  document.getElementById('step-photo-file').value = '';
  if (!stepId) {
    document.getElementById('step-no').value = '';
    document.getElementById('step-sort').value = 0;
    document.getElementById('step-desc').value = '';
    document.getElementById('step-hazard').value = '';
    document.getElementById('step-equip').value = '';
    document.getElementById('step-remark').value = '';
  } else {
    prefillStep(stepId);
  }
  new bootstrap.Modal(document.getElementById('stepModal')).show();
}

async function prefillStep(stepId) {
  try {
    const d = await SAFETY.api('/safety-api/manuals/' + currentManualId);
    const s = (d.steps || []).find(x => x.stepId === stepId);
    if (!s) return;
    document.getElementById('step-no').value = s.stepNo;
    document.getElementById('step-sort').value = s.sortOrder;
    document.getElementById('step-desc').value = s.description || '';
    document.getElementById('step-hazard').value = s.hazard || '';
    document.getElementById('step-equip').value = s.safetyEquipment || '';
    document.getElementById('step-remark').value = s.remark || '';
  } catch (e) { /* ignore */ }
}

async function saveStep() {
  const stepId = document.getElementById('step-id').value;
  const payload = {
    stepNo: Number(document.getElementById('step-no').value) || 0,
    description: document.getElementById('step-desc').value,
    hazard: document.getElementById('step-hazard').value,
    safetyEquipment: document.getElementById('step-equip').value,
    remark: document.getElementById('step-remark').value,
    sortOrder: Number(document.getElementById('step-sort').value) || 0,
  };
  try {
    let savedStepId = stepId;
    if (stepId) {
      await SAFETY.api('/safety-api/steps/' + stepId, { method: 'PUT', body: payload });
    } else {
      const created = await SAFETY.api('/safety-api/manuals/' + currentManualId + '/steps', { method: 'POST', body: payload });
      savedStepId = created.stepId;
    }
    const file = document.getElementById('step-photo-file').files[0];
    if (file && savedStepId) {
      await SAFETY.uploadMultipart('/safety-api/steps/' + savedStepId + '/photos', { file });
    }
    bootstrap.Modal.getInstance(document.getElementById('stepModal')).hide();
    SAFETY.toast('저장되었습니다.');
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteStep(stepId) {
  if (!confirm('이 단계를 삭제하시겠습니까?')) return;
  try {
    await SAFETY.api('/safety-api/steps/' + stepId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 엑셀 일괄 업로드 모달
// ================================================================
let euPreviewData = [];
let euSelected = { major: null, middle: null, minor: null };

function openExcelModal() {
  document.getElementById('eu-file').value = '';
  document.getElementById('euStep2').style.display = 'none';
  document.getElementById('euStep3').style.display = 'none';
  document.getElementById('eu-confirm-btn').classList.add('d-none');
  euPreviewData = [];
  euSelected = { major: null, middle: null, minor: null };
  document.getElementById('eu-add-major-form').classList.add('d-none');
  document.getElementById('eu-add-middle-form').classList.add('d-none');
  document.getElementById('eu-add-minor-form').classList.add('d-none');
  euFillMajorSelect();
  new bootstrap.Modal(document.getElementById('excelModal')).show();
}

function euFillMajorSelect() {
  const sel = document.getElementById('eu-major');
  const sorted = [...tree].sort((a, b) => a.sortOrder - b.sortOrder);
  sel.innerHTML = '<option value="">선택</option>' + sorted.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  sel.value = '';
  document.getElementById('eu-middle').innerHTML = '<option value="">대분류를 먼저 선택</option>';
  document.getElementById('eu-middle').disabled = true;
  document.getElementById('eu-minor').innerHTML = '<option value="">중분류를 먼저 선택</option>';
  document.getElementById('eu-minor').disabled = true;
}

function euOnMajorChange() {
  const majorId = document.getElementById('eu-major').value;
  euSelected.major = majorId || null;
  euSelected.middle = null; euSelected.minor = null;
  const midSel = document.getElementById('eu-middle');
  const minSel = document.getElementById('eu-minor');
  minSel.innerHTML = '<option value="">중분류를 먼저 선택</option>'; minSel.disabled = true;
  if (!majorId) { midSel.innerHTML = '<option value="">대분류를 먼저 선택</option>'; midSel.disabled = true; euUpdateSummary(); return; }
  const major = findNode(majorId);
  const children = (major && major.children) ? [...major.children].sort((a, b) => a.sortOrder - b.sortOrder) : [];
  midSel.innerHTML = '<option value="">선택</option>' + children.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  midSel.disabled = false;
  euUpdateSummary();
}

function euOnMiddleChange() {
  const middleId = document.getElementById('eu-middle').value;
  euSelected.middle = middleId || null;
  euSelected.minor = null;
  const minSel = document.getElementById('eu-minor');
  if (!middleId) { minSel.innerHTML = '<option value="">중분류를 먼저 선택</option>'; minSel.disabled = true; euUpdateSummary(); return; }
  const middle = findNode(middleId);
  const children = (middle && middle.children) ? [...middle.children].sort((a, b) => a.sortOrder - b.sortOrder) : [];
  minSel.innerHTML = '<option value="">선택</option>' + children.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  minSel.disabled = false;
  euUpdateSummary();
}

function euOnMinorChange() {
  euSelected.minor = document.getElementById('eu-minor').value || null;
  euUpdateSummary();
}

function euToggleAdd(level) {
  const el = document.getElementById('eu-add-' + level + '-form');
  el.classList.toggle('d-none');
}

async function euCreateCategory(level) {
  const nameInput = document.getElementById('eu-add-' + level + '-name');
  const name = nameInput.value.trim();
  if (!name) { SAFETY.toast('분류명을 입력하세요.', false); return; }
  let parentId = null;
  if (level === 'middle') parentId = euSelected.major;
  if (level === 'minor') parentId = euSelected.middle;
  if (level !== 'major' && !parentId) { SAFETY.toast('상위 분류를 먼저 선택하세요.', false); return; }
  try {
    const created = await SAFETY.api('/safety-api/categories', { method: 'POST', body: { name, parentId, sortOrder: 0 } });
    await loadTree();
    nameInput.value = '';
    document.getElementById('eu-add-' + level + '-form').classList.add('d-none');
    SAFETY.toast('분류가 추가되었습니다.');
    if (level === 'major') {
      euFillMajorSelect();
      document.getElementById('eu-major').value = created.categoryId;
      euOnMajorChange();
    } else if (level === 'middle') {
      euOnMajorChange();
      document.getElementById('eu-middle').value = created.categoryId;
      euOnMiddleChange();
    } else {
      euOnMiddleChange();
      document.getElementById('eu-minor').value = created.categoryId;
      euOnMinorChange();
    }
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function euUpdateSummary() {
  const box = document.getElementById('euSummary');
  const btn = document.getElementById('eu-confirm-btn');
  if (euSelected.minor) {
    const maj = findNode(euSelected.major), mid = findNode(euSelected.middle), min = findNode(euSelected.minor);
    box.innerHTML = `<i class="fas fa-folder-tree me-1"></i>등록 위치: <b>${SAFETY.escapeHtml(maj ? maj.name : '')} › ${SAFETY.escapeHtml(mid ? mid.name : '')} › ${SAFETY.escapeHtml(min ? min.name : '')}</b>`;
    box.style.display = '';
    if (euPreviewData.length) btn.classList.remove('d-none');
  } else {
    box.innerHTML = '소분류까지 선택하면 업로드 버튼이 활성화됩니다.';
    btn.classList.add('d-none');
  }
}

async function euDoPreview() {
  const file = document.getElementById('eu-file').files[0];
  if (!file) { SAFETY.toast('엑셀 파일을 선택하세요.', false); return; }
  try {
    euPreviewData = await SAFETY.uploadMultipart('/safety-api/excel-upload/preview', { file });
    euRenderPreview();
    document.getElementById('euStep2').style.display = '';
    document.getElementById('euStep3').style.display = 'none';
    euUpdateSummary();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function euRenderPreview() {
  const recognizedCount = euPreviewData.filter(s => s.recognized).length;
  document.getElementById('eu-check-all').checked = true;
  document.getElementById('euPreviewRows').innerHTML = euPreviewData.map((s, idx) => `
    <tr class="${s.recognized ? '' : 'excluded'}">
      <td><input type="checkbox" class="eu-sheet-chk" data-idx="${idx}" ${s.selected ? 'checked' : ''} ${s.recognized ? '' : 'disabled'}></td>
      <td>${SAFETY.escapeHtml(s.sheetName)}</td>
      <td>${s.recognized ? '<span class="badge-ok">인식됨</span>' : `<span class="badge-no">제외</span>`}</td>
      <td>${SAFETY.escapeHtml(s.detectedTitle || '')}${!s.recognized && s.reason ? `<div class="small text-muted">${SAFETY.escapeHtml(s.reason)}</div>` : ''}</td>
      <td class="text-center">${s.stepCount}</td>
      <td class="text-center">${s.photoCount}</td>
      <td class="small">${(s.stepPreviewLines || []).slice(0, 3).map(l => SAFETY.escapeHtml(l)).join('<br>')}</td>
    </tr>`).join('');
  const note = document.querySelector('#euStep2 .eu-note');
  if (note) note.textContent = `총 ${euPreviewData.length}개 시트 중 ${recognizedCount}개가 매뉴얼로 인식되어 기본 선택되었습니다. 필요 없는 시트만 체크 해제하세요.`;
}

function euToggleAll(box) {
  document.querySelectorAll('.eu-sheet-chk:not(:disabled)').forEach(c => { c.checked = box.checked; });
}

async function euDoConfirm() {
  if (!euSelected.minor) { SAFETY.toast('등록할 소분류를 선택하세요.', false); return; }
  const file = document.getElementById('eu-file').files[0];
  if (!file) { SAFETY.toast('엑셀 파일이 없습니다. 다시 선택 후 형식 확인을 눌러주세요.', false); return; }
  const selectedNames = Array.from(document.querySelectorAll('.eu-sheet-chk:checked'))
    .map(c => euPreviewData[Number(c.dataset.idx)].sheetName);
  if (!selectedNames.length) { SAFETY.toast('가져올 시트를 하나 이상 선택하세요.', false); return; }
  if (!confirm(`선택한 ${selectedNames.length}개 시트를 매뉴얼로 등록하시겠습니까?`)) return;

  try {
    const result = await SAFETY.uploadMultipart('/safety-api/excel-upload/confirm', {
      file, categoryId: euSelected.minor, sheetNames: selectedNames.join(','),
    });
    euRenderResult(result);
    SAFETY.toast(result.importedCount + '개 매뉴얼이 등록되었습니다.');
    if (path.minorId === Number(euSelected.minor)) render();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function euRenderResult(result) {
  document.getElementById('euStep3').style.display = '';
  let html = `<p class="mb-2">총 <b>${result.importedCount}</b>건 등록됨</p>`;
  if (result.manuals && result.manuals.length) {
    html += '<ul class="mb-2">' + result.manuals.map(m => `<li>${SAFETY.escapeHtml(m.title)}</li>`).join('') + '</ul>';
  }
  if (result.skipped && result.skipped.length) {
    html += '<div class="text-muted small">건너뜀:</div><ul class="small text-muted">' +
      result.skipped.map(s => `<li>${SAFETY.escapeHtml(s)}</li>`).join('') + '</ul>';
  }
  document.getElementById('euResultBody').innerHTML = html;
  document.getElementById('eu-confirm-btn').classList.add('d-none');
}
