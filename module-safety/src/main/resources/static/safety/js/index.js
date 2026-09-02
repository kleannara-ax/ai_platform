/* 스택형 분류 트리(좌) + 분류 하위 매뉴얼 목록(우) + 매뉴얼 상세 모달 + 엑셀 업로드 모달 */

let tree = [];                 // 전체 분류 트리 (대분류 배열, 각 children 재귀)
let nodeIndex = new Map();     // categoryId -> 노드 (부모 참조 __parent 포함)
let expanded = new Set();      // 펼쳐진 분류 id
let selectedId = null;         // 선택한 분류 id (null = 전체)
let manuals = [];              // 선택한 분류 하위 매뉴얼 전체
let currentManualId = null;
let currentDetail = null;
let isAdminUser = false;
// 관리(수정) 모드. 기본은 꺼짐 — 관리 버튼/관리 칸은 이 모드에서만 나타난다.
let editMode = false;
let detailModal = null;

document.addEventListener('DOMContentLoaded', async () => {
  if (!SAFETY.requireAuth()) return;
  SAFETY.renderNav('index.html');
  detailModal = new bootstrap.Modal(document.getElementById('detailModal'));
  isAdminUser = await SAFETY.isAdmin();
  if (isAdminUser) document.getElementById('btn-edit-mode').classList.remove('d-none');
  bindLightbox();
  await loadTree();
  // 첫 화면은 전체 매뉴얼 목록
  await selectCategory(null);
});

// ================================================================
// 분류 트리 로딩 / 인덱싱
// ================================================================
async function loadTree() {
  try {
    tree = await SAFETY.api('/safety-api/categories') || [];
  } catch (e) {
    tree = [];
    SAFETY.toast(e.message, false);
  }
  indexTree();
}

function indexTree() {
  nodeIndex = new Map();
  const walk = (nodes, parent) => {
    sortNodes(nodes).forEach(n => {
      n.__parent = parent;
      nodeIndex.set(Number(n.categoryId), n);
      walk(n.children, n);
    });
  };
  walk(tree, null);
}

function sortNodes(nodes) {
  return (nodes || []).sort((a, b) => (a.sortOrder - b.sortOrder) || String(a.name).localeCompare(String(b.name)));
}

function findNode(id) {
  return (id == null) ? null : (nodeIndex.get(Number(id)) || null);
}

/** 루트에서 해당 분류까지의 이름 배열 (대분류 > 중분류 > 소분류) */
function pathOf(id) {
  const names = [];
  for (let n = findNode(id); n; n = n.__parent) names.unshift(n.name);
  return names;
}

function totalManualCount() {
  return tree.reduce((sum, n) => sum + (n.manualCount || 0), 0);
}

// ================================================================
// 좌측 스택형 트리 렌더링
// ================================================================
function renderTree() {
  const body = document.getElementById('treeBody');
  const rows = [treeRootRow()];
  if (!tree.length) {
    body.innerHTML = rows.join('') + `<div class="tree-empty">등록된 분류가 없습니다.</div>`;
    return;
  }
  rows.push(renderNodes(tree));
  body.innerHTML = rows.join('');
}

/** 최상단 "전체" 행 — 모든 분류의 매뉴얼을 한 번에 본다 */
function treeRootRow() {
  const on = (selectedId === null) ? 'selected' : '';
  return `<div class="tree-row ${on}" onclick="selectCategory(null)">
      <span class="tr-caret leaf"></span>
      <span class="tr-icon"><i class="fas fa-layer-group"></i></span>
      <span class="tr-name">전체</span>
      <span class="tr-count">${totalManualCount()}</span>
    </div>`;
}

function renderNodes(nodes) {
  const icons = { 1: 'fa-industry', 2: 'fa-cogs', 3: 'fa-tags' };
  return sortNodes(nodes).map(n => {
    const id = Number(n.categoryId);
    const children = n.children || [];
    const hasChildren = children.length > 0;
    const isOpen = expanded.has(id);
    const on = (Number(selectedId) === id) ? 'selected' : '';
    const caret = hasChildren
      ? `<button class="tr-caret ${isOpen ? 'open' : ''}" onclick="event.stopPropagation(); toggleExpand(${id})" title="${isOpen ? '접기' : '펼치기'}"><i class="fas fa-chevron-right"></i></button>`
      : `<span class="tr-caret leaf"></span>`;
    const tools = (isAdminUser && editMode)
      ? `<span class="tr-tools" onclick="event.stopPropagation()">
           <button onclick="openCategoryEditModal(${id})" title="분류 수정"><i class="fas fa-pen"></i></button>
           <button class="danger" onclick="deleteCategory(${id})" title="분류 삭제"><i class="fas fa-trash"></i></button>
         </span>`
      : '';
    return `<div class="tree-node">
        <div class="tree-row ${on}" onclick="selectCategory(${id})" title="${SAFETY.escapeHtml(n.name)}">
          ${caret}
          <span class="tr-icon"><i class="fas ${icons[n.levelNo] || 'fa-folder'}"></i></span>
          <span class="tr-name">${SAFETY.escapeHtml(n.name)}</span>
          <span class="tr-count">${n.manualCount || 0}</span>
          ${tools}
        </div>
        ${hasChildren && isOpen ? `<div class="tree-children">${renderNodes(children)}</div>` : ''}
      </div>`;
  }).join('');
}

function toggleExpand(id) {
  const key = Number(id);
  if (expanded.has(key)) expanded.delete(key); else expanded.add(key);
  renderTree();
}

function expandAll(open) {
  expanded = new Set();
  if (open) nodeIndex.forEach((node, id) => { if ((node.children || []).length) expanded.add(id); });
  renderTree();
}

/** 선택한 분류가 트리에서 보이도록 조상 노드를 모두 펼친다 */
function expandAncestors(id) {
  for (let n = findNode(id); n; n = n.__parent) {
    if (n.__parent) expanded.add(Number(n.__parent.categoryId));
  }
}

// ================================================================
// 분류 선택 → 하위 매뉴얼 전체 조회
// ================================================================
async function selectCategory(id) {
  selectedId = (id == null) ? null : Number(id);
  if (selectedId !== null) {
    expandAncestors(selectedId);
    // 하위 분류가 있으면 선택과 동시에 한 단계 펼쳐 준다
    const node = findNode(selectedId);
    if (node && (node.children || []).length) expanded.add(selectedId);
  }
  renderTree();
  updateAddButtonLabel();
  await loadManuals();
}

async function loadManuals() {
  const holder = document.getElementById('manualRows');
  holder.innerHTML = `<div class="empty-state"><i class="fas fa-spinner fa-spin"></i>불러오는 중...</div>`;
  try {
    const query = (selectedId === null) ? '' : ('?categoryId=' + selectedId);
    manuals = await SAFETY.api('/safety-api/manuals/by-category' + query) || [];
  } catch (e) {
    manuals = [];
    SAFETY.toast(e.message, false);
  }
  renderListHead();
  renderManualList();
}

function renderListHead() {
  const names = (selectedId === null) ? [] : pathOf(selectedId);
  document.getElementById('listTitle').textContent =
    (selectedId === null) ? '전체 매뉴얼' : (names[names.length - 1] || '매뉴얼');
  document.getElementById('listPath').textContent =
    (selectedId === null) ? '모든 분류' : names.join(' > ');
}

function renderManualList() {
  const holder = document.getElementById('manualRows');
  const keyword = document.getElementById('listFilter').value.trim().toLowerCase();
  const shown = keyword
    ? manuals.filter(m => String(m.title || '').toLowerCase().includes(keyword)
        || String(m.categoryPath || '').toLowerCase().includes(keyword))
    : manuals;

  document.getElementById('listCount').textContent =
    keyword ? `${shown.length}건 / 전체 ${manuals.length}건` : `${manuals.length}건`;

  if (!shown.length) {
    holder.innerHTML = `<div class="empty-state"><i class="fas fa-file-circle-question"></i>${
      keyword ? '검색 결과가 없습니다.' : '이 분류에 등록된 매뉴얼이 없습니다.'}</div>`;
    return;
  }

  holder.innerHTML = shown.map(m => `
    <div class="manual-row" onclick="openDetail(${m.manualId})">
      <div class="mr-left">
        <span class="mr-ico"><i class="fas fa-file-lines"></i></span>
        <div style="min-width:0">
          <div class="mr-title">${SAFETY.escapeHtml(m.title)}</div>
          <div class="mr-path">${SAFETY.escapeHtml(m.categoryPath || m.categoryName || '')}</div>
        </div>
      </div>
      <div class="mr-right">
        <span class="mr-date">${formatDate(m.updatedAt)}</span>
        <span class="mr-chevron"><i class="fas fa-chevron-right"></i></span>
      </div>
    </div>`).join('');
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').substring(0, 16) : '';
}

// ================================================================
// 관리(수정) 모드 — 기본 꺼짐. 켜야 관리 버튼과 관리 칸이 나타난다.
// ================================================================
function toggleEditMode() {
  if (!isAdminUser) return;
  editMode = !editMode;
  const btn = document.getElementById('btn-edit-mode');
  btn.classList.toggle('on', editMode);
  document.getElementById('btn-edit-mode-label').textContent = editMode ? '수정 종료' : '수정';
  document.getElementById('btn-excel-upload').classList.toggle('d-none', !editMode);
  renderTree();
  updateAddButtonLabel();
  if (currentDetail) renderDetail();
}

function updateAddButtonLabel() {
  const btn = document.getElementById('btn-add-context');
  if (!isAdminUser || !editMode) { btn.classList.add('d-none'); return; }
  const node = findNode(selectedId);
  const level = node ? node.levelNo : 0;
  const labels = { 0: '대분류 추가', 1: '중분류 추가', 2: '소분류 추가', 3: '매뉴얼 추가' };
  document.getElementById('btn-add-context-label').textContent = labels[level];
  btn.classList.remove('d-none');
}

function onAddContextClick() {
  const node = findNode(selectedId);
  if (node && node.levelNo === 3) openManualCreateModal();
  else openCategoryCreateModal();
}

// ================================================================
// 매뉴얼 상세 모달 — 목록에서 선택하면 수칙(단계)을 모달로 보여준다
// ================================================================
async function openDetail(manualId) {
  currentManualId = manualId;
  currentDetail = null;
  document.getElementById('detailTitle').textContent = '불러오는 중...';
  document.getElementById('detailPath').textContent = '';
  document.getElementById('stepCols').innerHTML = '';
  document.getElementById('stepHead').innerHTML = '';
  document.getElementById('stepRows').innerHTML = '';
  document.getElementById('detailTools').innerHTML = '';
  detailModal.show();
  await loadDetail();
}

async function loadDetail() {
  try {
    currentDetail = await SAFETY.api('/safety-api/manuals/' + currentManualId);
    renderDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderDetail() {
  const d = currentDetail;
  if (!d) return;
  document.getElementById('detailTitle').textContent = d.title || '';
  const summary = manuals.find(m => Number(m.manualId) === Number(currentManualId));
  document.getElementById('detailPath').textContent =
    (summary && summary.categoryPath) ? summary.categoryPath : (d.categoryName || '');
  renderDetailTools();
  renderSteps(d.steps || []);
}

function renderDetailTools() {
  const el = document.getElementById('detailTools');
  if (!isAdminUser) { el.innerHTML = ''; return; }
  el.innerHTML = `
    <button class="btn-modern btn-edit-toggle ${editMode ? 'on' : ''}" onclick="toggleEditMode()">
      <i class="fas fa-pen"></i>${editMode ? '수정 종료' : '수정'}</button>
    ${editMode ? `
      <button class="btn-modern btn-outline-modern" onclick="openStepModal()"><i class="fas fa-plus"></i>단계 추가</button>
      <button class="btn-modern btn-danger-soft" onclick="deleteManual()"><i class="fas fa-trash"></i>매뉴얼 삭제</button>` : ''}`;
}

/** 관리 칸은 수정 모드에서만 만들고, 평상시에는 열 자체를 없애 본문을 넓게 쓴다. */
function renderSteps(steps) {
  const manage = isAdminUser && editMode;
  document.getElementById('stepCols').innerHTML = manage
    ? `<col style="width:46px"><col style="width:170px"><col><col style="width:15%"><col style="width:15%"><col style="width:13%"><col style="width:86px">`
    : `<col style="width:46px"><col style="width:190px"><col><col style="width:17%"><col style="width:17%"><col style="width:15%">`;
  document.getElementById('stepHead').innerHTML =
    `<tr><th>No.</th><th>공정 순서(사진)</th><th>공정 순서(설명)</th><th>위험요인</th><th>안전 보호구</th><th>비고</th>${
      manage ? '<th>관리</th>' : ''}</tr>`;

  const tbody = document.getElementById('stepRows');
  const colCount = manage ? 7 : 6;
  if (!steps.length) {
    tbody.innerHTML = `<tr><td colspan="${colCount}" class="text-center text-muted py-4">등록된 단계가 없습니다.</td></tr>`;
    return;
  }
  tbody.innerHTML = steps.map(s => `
    <tr>
      <td class="text-center"><span class="step-no-badge">${s.stepNo}</span></td>
      <td>${renderPhotos(s.photos)}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.description || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.hazard || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.safetyEquipment || '')}</td>
      <td style="white-space:pre-wrap">${SAFETY.escapeHtml(s.remark || '')}</td>
      ${manage ? `<td>
        <div class="step-manage">
          <button class="btn btn-sm btn-outline-secondary" onclick="openStepModal(${s.stepId})" title="단계 수정"><i class="fas fa-pen"></i></button>
          <button class="btn btn-sm btn-outline-danger" onclick="deleteStep(${s.stepId})" title="단계 삭제"><i class="fas fa-trash"></i></button>
        </div>
      </td>` : ''}
    </tr>`).join('');
}

function renderPhotos(photos) {
  if (!photos || !photos.length) return '<span class="text-muted small">-</span>';
  return photos.map(p => `<img class="step-photo" src="${SAFETY.escapeHtml(p.url)}"
      alt="${SAFETY.escapeHtml(p.originalName || '')}" data-name="${SAFETY.escapeHtml(p.originalName || '')}"
      onclick="openLightboxFrom(this)" title="클릭하면 크게 볼 수 있습니다">`).join('');
}

// ================================================================
// 이미지 확대 뷰어(라이트박스)
// ================================================================
let lbScale = null;   // null = 화면 맞춤, 숫자 = 원본 대비 배율
let lbUrl = '';

function bindLightbox() {
  const box = document.getElementById('lightbox');
  const stage = document.getElementById('lbStage');
  const img = document.getElementById('lbImg');

  // 이미지 바깥(빈 공간)을 누르면 닫기
  stage.addEventListener('click', (e) => { if (e.target === stage) closeLightbox(); });
  // 이미지를 누르면 100% <-> 화면 맞춤 전환
  img.addEventListener('click', () => { if (lbScale === null) lbSetScale(1); else lbFit(); });
  // 휠로 배율 조절
  stage.addEventListener('wheel', (e) => {
    if (!box.classList.contains('open')) return;
    e.preventDefault();
    lbZoom(e.deltaY < 0 ? 1 : -1);
  }, { passive: false });
  // Esc — 부트스트랩 모달보다 먼저 가로채서 라이트박스만 닫는다
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape' || !box.classList.contains('open')) return;
    e.preventDefault();
    e.stopPropagation();
    closeLightbox();
  }, true);
}

function openLightboxFrom(el) {
  openLightbox(el.getAttribute('src'), el.dataset.name || '');
}

function openLightbox(url, name) {
  lbUrl = url;
  const img = document.getElementById('lbImg');
  img.src = url;
  document.getElementById('lbName').textContent = name || '';
  lbFit();
  document.getElementById('lightbox').classList.add('open');
}

function closeLightbox() {
  document.getElementById('lightbox').classList.remove('open');
  document.getElementById('lbImg').src = '';
  lbUrl = '';
}

function lbFit() {
  lbScale = null;
  const img = document.getElementById('lbImg');
  img.classList.add('fit');
  img.classList.remove('zoomed');
  img.style.width = '';
  document.getElementById('lbZoomLabel').textContent = '맞춤';
}

function lbSetScale(scale) {
  const img = document.getElementById('lbImg');
  if (!img.naturalWidth) return;
  lbScale = Math.min(8, Math.max(0.1, scale));
  img.classList.remove('fit');
  img.classList.add('zoomed');
  img.style.width = Math.round(img.naturalWidth * lbScale) + 'px';
  document.getElementById('lbZoomLabel').textContent = Math.round(lbScale * 100) + '%';
}

function lbZoom(direction) {
  const img = document.getElementById('lbImg');
  if (!img.naturalWidth) return;
  // 맞춤 상태에서는 현재 화면에 표시된 비율을 시작점으로 삼는다
  const base = (lbScale === null) ? (img.clientWidth / img.naturalWidth) : lbScale;
  lbSetScale(direction > 0 ? base * 1.25 : base / 1.25);
}

function lbOpenRaw() {
  if (lbUrl) window.open(lbUrl, '_blank', 'noopener');
}

// ================================================================
// 분류 등록/수정
// ================================================================
function openCategoryCreateModal() {
  const parent = findNode(selectedId);
  if (parent && parent.levelNo === 3) { SAFETY.toast('소분류 아래에는 분류를 추가할 수 없습니다.', false); return; }
  document.getElementById('cat-id').value = '';
  document.getElementById('cat-name').value = '';
  document.getElementById('cat-sort').value = 0;
  const labels = { 1: '아래 중분류', 2: '아래 소분류' };
  document.getElementById('cat-context-text').textContent =
    parent ? (pathOf(parent.categoryId).join(' > ') + ' ' + labels[parent.levelNo]) : '최상위(대분류)';
  document.getElementById('categoryModalTitle').textContent = '분류 추가';
  new bootstrap.Modal(document.getElementById('categoryModal')).show();
}

function openCategoryEditModal(categoryId) {
  const node = findNode(categoryId);
  if (!node) return;
  document.getElementById('cat-id').value = categoryId;
  document.getElementById('cat-name').value = node.name;
  document.getElementById('cat-sort').value = node.sortOrder;
  document.getElementById('cat-context-text').textContent = pathOf(categoryId).join(' > ');
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
      await SAFETY.api('/safety-api/categories', { method: 'POST', body: { name, parentId: selectedId, sortOrder } });
      if (selectedId !== null) expanded.add(Number(selectedId));
    }
    bootstrap.Modal.getInstance(document.getElementById('categoryModal')).hide();
    SAFETY.toast('저장되었습니다.');
    await refreshAll();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteCategory(categoryId) {
  if (!confirm('이 분류를 삭제하시겠습니까? (하위 분류/매뉴얼이 있으면 삭제할 수 없습니다)')) return;
  try {
    await SAFETY.api('/safety-api/categories/' + categoryId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    if (Number(selectedId) === Number(categoryId)) selectedId = null;
    await refreshAll();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 분류/매뉴얼이 바뀌면 트리(건수 포함)와 목록을 함께 다시 읽는다 */
async function refreshAll() {
  await loadTree();
  if (selectedId !== null && !findNode(selectedId)) selectedId = null;
  renderTree();
  updateAddButtonLabel();
  await loadManuals();
}

// ================================================================
// 매뉴얼 등록/삭제
// ================================================================
function openManualCreateModal() {
  const node = findNode(selectedId);
  if (!node || node.levelNo !== 3) { SAFETY.toast('먼저 소분류를 선택하세요.', false); return; }
  document.getElementById('man-id').value = '';
  document.getElementById('man-title').value = '';
  document.getElementById('man-sort').value = 0;
  document.getElementById('man-context-note').textContent =
    '등록 분류: ' + pathOf(selectedId).join(' > ') + ' · 등록 후 목록에서 열어 단계(공정 순서)를 추가할 수 있습니다.';
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
      body: { categoryId: selectedId, title, sortOrder, steps: [] },
    });
    bootstrap.Modal.getInstance(document.getElementById('manualModal')).hide();
    SAFETY.toast('매뉴얼이 등록되었습니다.');
    await refreshAll();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteManual() {
  if (!confirm('이 매뉴얼을 삭제하시겠습니까?')) return;
  try {
    await SAFETY.api('/safety-api/manuals/' + currentManualId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    detailModal.hide();
    currentManualId = null;
    currentDetail = null;
    await refreshAll();
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
  const step = stepId ? (currentDetail && (currentDetail.steps || []).find(x => Number(x.stepId) === Number(stepId))) : null;
  document.getElementById('step-no').value = step ? step.stepNo : '';
  document.getElementById('step-sort').value = step ? step.sortOrder : 0;
  document.getElementById('step-desc').value = step ? (step.description || '') : '';
  document.getElementById('step-hazard').value = step ? (step.hazard || '') : '';
  document.getElementById('step-equip').value = step ? (step.safetyEquipment || '') : '';
  document.getElementById('step-remark').value = step ? (step.remark || '') : '';
  new bootstrap.Modal(document.getElementById('stepModal')).show();
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
  euPreselectFromTree();
  new bootstrap.Modal(document.getElementById('excelModal')).show();
}

/** 좌측 트리에서 이미 고른 분류가 있으면 업로드 모달의 대/중/소 선택을 미리 채워 준다 */
function euPreselectFromTree() {
  const node = findNode(selectedId);
  if (!node) return;
  const chain = [];
  for (let n = node; n; n = n.__parent) chain.unshift(n);
  if (chain[0]) {
    document.getElementById('eu-major').value = chain[0].categoryId;
    euOnMajorChange();
  }
  if (chain[1]) {
    document.getElementById('eu-middle').value = chain[1].categoryId;
    euOnMiddleChange();
  }
  if (chain[2]) {
    document.getElementById('eu-minor').value = chain[2].categoryId;
    euOnMinorChange();
  }
}

function euFillMajorSelect() {
  const sel = document.getElementById('eu-major');
  const sorted = sortNodes(tree);
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
  const children = sortNodes((findNode(majorId) || {}).children);
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
  const children = sortNodes((findNode(middleId) || {}).children);
  minSel.innerHTML = '<option value="">선택</option>' + children.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  minSel.disabled = false;
  euUpdateSummary();
}

function euOnMinorChange() {
  euSelected.minor = document.getElementById('eu-minor').value || null;
  euUpdateSummary();
}

function euToggleAdd(level) {
  document.getElementById('eu-add-' + level + '-form').classList.toggle('d-none');
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
    renderTree();
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
    box.innerHTML = `<i class="fas fa-folder-tree me-1"></i>등록 위치: <b>${SAFETY.escapeHtml(pathOf(euSelected.minor).join(' > '))}</b>`;
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
    await refreshAll();
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
