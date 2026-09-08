/* 스택형 분류 트리(좌) + 분류 하위 매뉴얼 목록(우) + 매뉴얼 상세 모달 + 엑셀 업로드 모달 */

let tree = [];                 // 전체 분류 트리 (대분류 배열, 각 children 재귀)
let nodeIndex = new Map();     // categoryId -> 노드 (부모 참조 __parent 포함)
let expanded = new Set();      // 펼쳐진 분류 id
let selectedId = null;         // 선택한 분류 id (null = 전체)
let manuals = [];              // 선택한 분류 하위 매뉴얼 전체
/** 서버에 걸어 둔 내용 검색어. 비어 있으면 분류 전체 목록을 본다. */
let contentKeyword = '';
let currentManualId = null;
let currentDetail = null;
let isAdminUser = false;
// 관리(수정) 모드. 기본은 꺼짐 — 관리 버튼/관리 칸은 이 모드에서만 나타난다.
let editMode = false;
let detailModal = null;

// ── 단계 표 컬럼 폭 ──
// 열 구성은 매뉴얼마다 다르므로(서식/사용자 정의) 서버에서 받은 열 정의를 그대로 쓴다.
// 폭은 px 가 아니라 "비중"이라 모달 폭이 얼마든 가로로 꽉 찬다.
/** No. 열은 항상 맨 앞에 붙는 고정 열이다 (열 정의에는 들어 있지 않다) */
const STEP_NO_WIDTH = 46;
/** 관리 열도 고정 (수정 모드에서만) */
const STEP_MANAGE_WIDTH = 80;
const STEP_COL_MIN = 44;
/**
 * 사진 칸의 최소 폭 = 사진 상자 폭(CSS .step-photo 180px) + 칸 좌우 여백(12px x 2).
 * 사진은 어느 매뉴얼에서든 같은 크기로 고정이라, 칸이 이보다 좁아지면 사진이 칸을 넘친다.
 */
const STEP_PHOTO_COL_MIN = 180 + 24;
/** 이 폭보다 좁으면 표 대신 항목별 카드로 쌓아 보여준다 */
const STEP_STACK_BREAKPOINT = 760;
const STEP_COL_STORAGE_KEY = 'safety.stepColWeights';
/** 매뉴얼별로 사용자가 조절한 폭 비중 { manualId: { columnKey: weight } } */
let stepColWeights = loadStepColWeights();
/** 지금 그려진 표의 열 목록 (No./관리 포함, 화면 기준) */
let stepColLayout = [];

document.addEventListener('DOMContentLoaded', async () => {
  if (!SAFETY.requireAuth()) return;
  SAFETY.renderNav('index.html');
  detailModal = new bootstrap.Modal(document.getElementById('detailModal'));
  isAdminUser = await SAFETY.isAdmin();
  if (isAdminUser) {
    // 수정 모드와 무관하게 항상 보이는 버튼들 (추가 계열)
    document.getElementById('btn-edit-mode').classList.remove('d-none');
    document.getElementById('btn-add-major').classList.remove('d-none');
    document.getElementById('btn-add-notice').classList.remove('d-none');
  }
  bindLightbox();
  bindStepColResize();
  await loadNotices();
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

/** 루트에서 해당 분류까지의 이름 배열 (대분류 > 중분류) */
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
  const icons = { 1: 'fa-industry', 2: 'fa-cogs' };
  return sortNodes(nodes).map(n => {
    const id = Number(n.categoryId);
    const children = n.children || [];
    const hasChildren = children.length > 0;
    const isOpen = expanded.has(id);
    const on = (Number(selectedId) === id) ? 'selected' : '';
    const caret = hasChildren
      ? `<button class="tr-caret ${isOpen ? 'open' : ''}" onclick="event.stopPropagation(); toggleExpand(${id})" title="${isOpen ? '접기' : '펼치기'}"><i class="fas fa-chevron-right"></i></button>`
      : `<span class="tr-caret leaf"></span>`;
    // 분류 추가는 이 트리 안에서만 한다 — 중분류 아래에는 더 만들 수 없으므로 버튼도 내지 않는다.
    // 추가 버튼은 항상 보이고, 수정/삭제는 수정 모드에서만 보인다.
    const childLabel = { 1: '중분류 추가' }[n.levelNo];
    const addButton = (isAdminUser && childLabel)
      ? `<button onclick="openCategoryCreateModal(${id})" title="${childLabel}"><i class="fas fa-plus"></i></button>` : '';
    const editButtons = (isAdminUser && editMode)
      ? `<button onclick="openCategoryEditModal(${id})" title="분류 수정"><i class="fas fa-pen"></i></button>
         <button class="danger" onclick="deleteCategory(${id})" title="분류 삭제"><i class="fas fa-trash"></i></button>` : '';
    const tools = (addButton || editButtons)
      ? `<span class="tr-tools" onclick="event.stopPropagation()">${addButton}${editButtons}</span>` : '';
    return `<div class="tree-node">
        <div class="tree-row ${on} ${n.levelNo === 2 ? 'drop-ok' : ''}" onclick="selectCategory(${id})"
             title="${SAFETY.escapeHtml(n.name)}"
             ondragover="onCategoryDragOver(event, ${id}, ${n.levelNo})"
             ondragleave="onCategoryDragLeave(event)"
             ondrop="onCategoryDrop(event, ${id}, ${n.levelNo})">
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
    const params = new URLSearchParams();
    if (selectedId !== null) params.set('categoryId', selectedId);
    if (contentKeyword) params.set('content', contentKeyword);
    const query = params.toString();
    manuals = await SAFETY.api('/safety-api/manuals/by-category' + (query ? '?' + query : '')) || [];
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

// ================================================================
// 검색 — 매뉴얼명(화면 필터) / 내용(서버 조회) 두 가지
// ================================================================
function onContentFilterInput() {
  const value = document.getElementById('contentFilter').value.trim();
  document.getElementById('contentFilterClear').style.display = value ? '' : 'none';
  // 입력만으로는 조회하지 않는다 (내용 검색은 서버를 타므로 Enter/버튼으로 실행)
  if (!value && contentKeyword) clearContentSearch();
}

async function runContentSearch() {
  const value = document.getElementById('contentFilter').value.trim();
  if (value === contentKeyword) return;
  contentKeyword = value;
  await loadManuals();
}

async function clearContentSearch() {
  document.getElementById('contentFilter').value = '';
  document.getElementById('contentFilterClear').style.display = 'none';
  if (!contentKeyword) return;
  contentKeyword = '';
  await loadManuals();
}

function clearTitleFilter() {
  document.getElementById('listFilter').value = '';
  renderManualList();
}

/** 발췌에서 검색어에 해당하는 부분만 표시를 입힌다 */
function highlight(text, keyword) {
  const safe = SAFETY.escapeHtml(text);
  if (!keyword) return safe;
  const needle = SAFETY.escapeHtml(keyword);
  const pattern = needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return safe.replace(new RegExp(pattern, 'gi'), m => `<mark>${m}</mark>`);
}

function renderManualList() {
  const holder = document.getElementById('manualRows');
  const titleInput = document.getElementById('listFilter');
  const keyword = titleInput.value.trim().toLowerCase();
  document.getElementById('listFilterClear').style.display = keyword ? '' : 'none';

  const shown = keyword
    ? manuals.filter(m => String(m.title || '').toLowerCase().includes(keyword)
        || String(m.categoryPath || '').toLowerCase().includes(keyword))
    : manuals;

  document.getElementById('listCount').textContent =
    keyword ? `${shown.length}건 / ${manuals.length}건` : `${manuals.length}건`;
  renderSearchHint(shown.length);
  renderDragHint();

  if (!shown.length) {
    const message = contentKeyword
      ? `'${SAFETY.escapeHtml(contentKeyword)}' 이(가) 들어간 매뉴얼이 없습니다.`
      : (keyword ? '검색 결과가 없습니다.' : '이 분류에 등록된 매뉴얼이 없습니다.');
    holder.innerHTML = `<div class="empty-state"><i class="fas fa-file-circle-question"></i>${message}</div>`;
    return;
  }

  const canDrag = isAdminUser && editMode;
  holder.innerHTML = shown.map(m => {
    const snippets = (m.matchSnippets || []);
    const more = (m.matchCount || 0) - snippets.length;
    return `
    <div class="manual-row" onclick="openDetail(${m.manualId})"
         ${canDrag ? `draggable="true" ondragstart="onManualDragStart(event, ${m.manualId})" ondragend="onManualDragEnd()"` : ''}>
      <div class="mr-left">
        <span class="mr-ico"><i class="fas fa-file-lines"></i></span>
        <div style="min-width:0">
          <div class="mr-title">${SAFETY.escapeHtml(m.title)}</div>
          <div class="mr-path">${SAFETY.escapeHtml(m.categoryPath || m.categoryName || '')}</div>
          ${snippets.length ? `<div class="mr-snippets">
            ${snippets.map(t => `<div class="mr-snippet">${highlight(t, contentKeyword)}</div>`).join('')}
            ${more > 0 ? `<div class="mr-match-more">외 ${more}개 단계에서 더 발견</div>` : ''}
          </div>` : ''}
        </div>
      </div>
      <div class="mr-right">
        <span class="mr-date">${formatDate(m.updatedAt)}</span>
        <span class="mr-chevron"><i class="fas fa-chevron-right"></i></span>
      </div>
    </div>`;
  }).join('');
}

/** 지금 어떤 검색이 걸려 있는지 한 줄로 알려 준다 */
function renderSearchHint(shownCount) {
  const hint = document.getElementById('searchHint');
  if (!contentKeyword) { hint.innerHTML = ''; return; }
  const scope = (selectedId === null) ? '전체 분류' : pathOf(selectedId).join(' > ');
  hint.innerHTML = `<i class="fas fa-magnifying-glass me-1"></i>${SAFETY.escapeHtml(scope)}에서 `
    + `내용에 <b>${SAFETY.escapeHtml(contentKeyword)}</b> 이(가) 들어간 매뉴얼 ${shownCount}건`;
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').substring(0, 16) : '';
}

/** 날짜만 (공지 목록은 시간까지 보여줄 만큼 폭이 넓지 않다) */
function formatDateOnly(value) {
  return value ? String(value).substring(0, 10) : '';
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
  if (!editMode) {
    const hint = document.getElementById('searchHint');
    if (hint && !contentKeyword) hint.innerHTML = '';
  }
  renderNotices();
  renderTree();
  renderManualList();   // 드래그 가능 여부가 바뀌므로 목록도 다시 그린다
  updateAddButtonLabel();
  if (currentDetail) renderDetail();
}

/**
 * 관리자에게만 보이는 버튼들의 노출을 맞춘다.
 * 추가 계열(대분류 추가)은 수정 모드와 무관하게 보이고, "내용 추가"는 수정 모드에서만 보인다.
 */
function updateAddButtonLabel() {
  document.getElementById('btn-add-major').classList.toggle('d-none', !isAdminUser);
  document.getElementById('btn-add-notice').classList.toggle('d-none', !isAdminUser);
  document.getElementById('btn-add-manual').classList.toggle('d-none', !(isAdminUser && editMode));
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
  const path = (summary && summary.categoryPath) ? summary.categoryPath : (d.categoryName || '');
  document.getElementById('detailPath').textContent =
    d.formTypeName ? `${path} · ${d.formTypeName}` : path;
  renderDetailTools();
  renderDetailMeta(d.meta || []);
  renderSteps(d.steps || []);
  renderColumnAdmin();
}

/** 위험성 평가서의 부서명/작업인원/목적 같은 머리말 항목 */
function renderDetailMeta(meta) {
  const box = document.getElementById('detailMetaBox');
  if (!meta.length) { box.innerHTML = ''; return; }
  box.innerHTML = `<div class="detail-meta-grid">${meta.map(m => `
    <div class="dm-item">
      <span class="dm-label">${SAFETY.escapeHtml(m.label)}</span>
      <span class="dm-value">${SAFETY.escapeHtml(m.value || '')}</span>
    </div>`).join('')}</div>`;
}

/** 표의 열 관리 — 수정 모드에서만 보인다 */
/**
 * 표 아래에 있던 '표의 열' 패널은 없앴다.
 * 열 이름·순서·유형·삭제를 모두 표 머리글에서 직접 다루는 편이 직관적이라,
 * 여기서는 수정 모드일 때 쓰는 법만 한 줄 안내한다.
 */
function renderColumnAdmin() {
  const box = document.getElementById('columnAdmin');
  if (!isAdminUser || !editMode || !currentDetail) { box.innerHTML = ''; return; }
  box.innerHTML = `<div class="edit-hint">
    <i class="fas fa-circle-info"></i>
    칸을 눌러 내용을 고치고(Esc 취소), 사진은 끌어서 다른 칸으로 옮기거나 사진 파일을 칸에 바로 떨어뜨릴 수 있습니다.
    머리글은 끌어서 순서를 바꾸고, <i class="fas fa-pen"></i> 로 이름을 고칩니다.
  </div>`;
}

// ================================================================
// 표에서 바로 고치기 — 칸 클릭 편집 / 머리글 이름·순서 / 사진 끌어다 놓기
//  (모두 수정 모드 + SAFETY 관리자일 때만 동작한다)
// ================================================================

/** 칸을 눌러 그 자리에서 내용을 고친다. 사진이나 버튼을 누른 것이면 편집하지 않는다. */
function startCellEdit(event, stepId, columnId) {
  if (!isAdminUser || !editMode) return;
  if (event.target.closest('img, button, a, textarea')) return;
  const td = event.currentTarget;
  if (td.querySelector('.cell-input')) return;          // 이미 편집 중

  const step = (currentDetail.steps || []).find(s => Number(s.stepId) === Number(stepId));
  const value = ((step && step.values) || []).find(v => Number(v.columnId) === Number(columnId));
  const before = (value && value.text) ? value.text : '';

  // 입력창을 새로 띄우지 않고 칸 글자 자리를 그대로 고쳐 쓴다 — 표 모양이 흔들리지 않는다
  const span = td.querySelector('.cell-text');
  if (!span) return;
  td.classList.add('cell-editing');
  span.classList.remove('empty');
  span.textContent = before;
  span.contentEditable = 'true';
  span.spellcheck = false;
  span.focus();
  placeCaretAtEnd(span);

  let done = false;
  const finish = async (save) => {
    if (done) return;
    done = true;
    span.contentEditable = 'false';
    td.classList.remove('cell-editing');
    const after = span.innerText.replace(/\u00a0/g, ' ').replace(/\n+$/, '');
    if (!save || after === before) { await loadDetail(); return; }
    try {
      await saveCellValue(stepId, columnId, after);
    } catch (e) {
      SAFETY.toast(e.message, false);
    }
    await loadDetail();
  };
  span.addEventListener('blur', () => finish(true));
  span.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { e.preventDefault(); finish(false); }
    // 줄바꿈이 필요한 칸이 많아 Enter 는 줄바꿈으로 두고, 저장은 Ctrl+Enter 로 한다
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); span.blur(); }
  });
  // 서식이 딸려 오지 않게 글은 항상 평문으로 붙인다 (사진 붙여넣기는 칸의 onpaste 가 맡는다)
  span.addEventListener('paste', (e) => {
    const items = [...((e.clipboardData && e.clipboardData.items) || [])];
    if (items.some(i => i.kind === 'file' && i.type.startsWith('image/'))) return;
    e.preventDefault();
    document.execCommand('insertText', false, (e.clipboardData || window.clipboardData).getData('text'));
  });
}

/** 커서를 글자 끝으로 */
function placeCaretAtEnd(el) {
  const range = document.createRange();
  range.selectNodeContents(el);
  range.collapse(false);
  const sel = window.getSelection();
  sel.removeAllRanges();
  sel.addRange(range);
}

/** 칸 하나만 바뀌어도 서버는 행 전체를 받으므로, 나머지 값은 그대로 실어 보낸다. */
async function saveCellValue(stepId, columnId, text) {
  const step = (currentDetail.steps || []).find(s => Number(s.stepId) === Number(stepId));
  if (!step) return;
  const columns = currentDetail.columns || [];
  const byColumn = {};
  (step.values || []).forEach(v => { byColumn[v.columnId] = v; });

  const values = columns.filter(c => c.columnType !== 'PHOTO').map(c => {
    const v = byColumn[c.columnId];
    if (Number(c.columnId) === Number(columnId)) {
      return { columnId: c.columnId, text, checked: false };
    }
    return { columnId: c.columnId, text: (v && v.text) || null, checked: !!(v && v.checked) };
  });
  await SAFETY.api('/safety-api/steps/' + stepId, {
    method: 'PUT',
    body: { stepNo: step.stepNo, sortOrder: step.sortOrder, values },
  });
}

/** 머리글 이름을 그 자리에서 고친다 */
function startColumnRename(event, columnId) {
  event.stopPropagation();
  const th = event.target.closest('th');
  const span = th && th.querySelector('.col-title');
  if (!span || th.querySelector('.col-input')) return;
  const column = (currentDetail.columns || []).find(c => Number(c.columnId) === Number(columnId));
  if (!column) return;

  const input = document.createElement('input');
  input.className = 'col-input';
  input.value = column.label;
  input.maxLength = 100;
  th.setAttribute('draggable', 'false');     // 편집 중에는 끌리지 않게
  span.replaceWith(input);
  input.focus();
  input.select();

  let done = false;
  const finish = async (save) => {
    if (done) return;
    done = true;
    const name = input.value.trim();
    if (!save || !name || name === column.label) { await loadDetail(); return; }
    try {
      await SAFETY.api('/safety-api/columns/' + columnId, {
        method: 'PUT',
        body: {
          label: name, columnType: column.columnType,
          sortOrder: column.sortOrder, widthWeight: column.widthWeight,
        },
      });
      SAFETY.toast('열 이름을 바꿨습니다.');
    } catch (e) {
      SAFETY.toast(e.message, false);
    }
    await loadDetail();
  };
  input.addEventListener('blur', () => finish(true));
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); input.blur(); }
    if (e.key === 'Escape') { e.preventDefault(); finish(false); }
  });
}

// ── 머리글을 끌어 열 순서 바꾸기 ────────────────────────────────
let draggingColumnId = null;

function onColDragStart(event, columnId) {
  if (event.target.closest('.col-resizer, .col-tools, .col-input')) { event.preventDefault(); return; }
  draggingColumnId = columnId;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', 'column:' + columnId);
  event.currentTarget.classList.add('col-dragging');
}

function onColDragOver(event, columnId) {
  if (draggingColumnId == null || draggingColumnId === columnId) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = 'move';
  event.currentTarget.classList.add('col-drop-target');
}

function onColDragLeave(event) { event.currentTarget.classList.remove('col-drop-target'); }

function onColDragEnd() {
  draggingColumnId = null;
  document.querySelectorAll('.col-dragging, .col-drop-target')
    .forEach(el => el.classList.remove('col-dragging', 'col-drop-target'));
}

async function onColDrop(event, targetColumnId) {
  event.preventDefault();
  event.currentTarget.classList.remove('col-drop-target');
  const moved = draggingColumnId;
  onColDragEnd();
  if (moved == null || moved === targetColumnId) return;

  const ids = (currentDetail.columns || []).map(c => Number(c.columnId));
  const from = ids.indexOf(Number(moved));
  const to = ids.indexOf(Number(targetColumnId));
  if (from < 0 || to < 0) return;
  ids.splice(to, 0, ids.splice(from, 1)[0]);
  await saveColumnOrder(ids);
}

// ── 사진을 다른 칸으로 끌어다 놓기 / 파일을 칸에 떨어뜨려 올리기 ──────
let draggingPhotoId = null;

function onPhotoDragStart(event, photoId) {
  draggingPhotoId = photoId;
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData('text/plain', 'photo:' + photoId);
  event.target.classList.add('photo-dragging');
  event.stopPropagation();
}

function onPhotoDragEnd() {
  draggingPhotoId = null;
  document.querySelectorAll('.photo-dragging').forEach(el => el.classList.remove('photo-dragging'));
  document.querySelectorAll('.cell-drop-target').forEach(el => el.classList.remove('cell-drop-target'));
}

/** 표 안의 사진이든, 바탕화면에서 끌어온 파일이든 받는다 */
function onCellDragOver(event) {
  const fromOutside = [...(event.dataTransfer.types || [])].includes('Files');
  if (draggingPhotoId == null && !fromOutside) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = draggingPhotoId != null ? 'move' : 'copy';
  event.currentTarget.classList.add('cell-drop-target');
}

function onCellDragLeave(event) { event.currentTarget.classList.remove('cell-drop-target'); }

async function onCellDrop(event, stepId, columnId) {
  event.preventDefault();
  event.currentTarget.classList.remove('cell-drop-target');
  const photoId = draggingPhotoId;
  onPhotoDragEnd();

  try {
    // 표 안의 사진을 끄는 중이면 '이동'이다.
    // 브라우저가 페이지 안의 <img> 를 끌 때 dataTransfer.files 에도 그 이미지를 넣어 주기 때문에,
    // 파일부터 확인하면 이동이 아니라 새 업로드(=복사)가 되어 버린다. 이동을 먼저 본다.
    if (photoId != null) {
      await SAFETY.api('/safety-api/photos/' + photoId + '/column',
        { method: 'PUT', body: { stepId, columnId } });
    } else {
      const files = [...((event.dataTransfer && event.dataTransfer.files) || [])]
        .filter(f => f.type.startsWith('image/'));
      if (!files.length) return;
      for (const file of files) {
        await SAFETY.uploadMultipart('/safety-api/steps/' + stepId + '/photos', { file, columnId });
      }
      SAFETY.toast(files.length + '장을 올렸습니다.');
    }
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 클립보드의 사진을 그 칸에 붙여 넣는다 (캡처 후 Ctrl+V) */
async function onCellPaste(event, stepId, columnId) {
  if (!isAdminUser || !editMode) return;
  const items = [...((event.clipboardData && event.clipboardData.items) || [])];
  const images = items.filter(i => i.kind === 'file' && i.type.startsWith('image/'));
  if (!images.length) return;      // 글 붙여넣기는 그대로 두고 사진일 때만 가로챈다

  event.preventDefault();
  try {
    for (const item of images) {
      const blob = item.getAsFile();
      if (!blob) continue;
      const ext = (blob.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
      const file = new File([blob], 'clipboard-' + Date.now() + '.' + ext, { type: blob.type });
      await SAFETY.uploadMultipart('/safety-api/steps/' + stepId + '/photos', { file, columnId });
    }
    SAFETY.toast(images.length + '장을 붙여 넣었습니다.');
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** "행 추가" — 모달 없이 빈 행을 바로 만들고 첫 칸을 편집 상태로 연다 */
async function addStepInline() {
  try {
    const no = nextStepNo();
    const created = await SAFETY.api('/safety-api/manuals/' + currentManualId + '/steps', {
      method: 'POST',
      body: { stepNo: no, sortOrder: no, values: [] },
    });
    await loadDetail();
    const rows = [...document.querySelectorAll('#stepRows tr')];
    const row = rows.find(tr => tr.innerHTML.includes('startCellEdit(event, ' + created.stepId + ','));
    const cell = row && row.querySelector('.cell-editable');
    if (cell) {
      cell.scrollIntoView({ block: 'nearest' });
      cell.click();
    }
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 표의 열 추가/수정/삭제/순서 변경
// ================================================================
function openColumnModal(columnId) {
  const column = columnId ? (currentDetail.columns || []).find(c => Number(c.columnId) === Number(columnId)) : null;
  document.getElementById('col-id').value = column ? column.columnId : '';
  document.getElementById('col-label').value = column ? column.label : '';
  document.getElementById('col-type').value = column ? column.columnType : 'TEXT';
  document.getElementById('col-width').value = column ? column.widthWeight : 200;
  document.getElementById('columnModalTitle').textContent = column ? '열 수정' : '열 추가';
  new bootstrap.Modal(document.getElementById('columnModal')).show();
}

async function saveColumn() {
  const id = document.getElementById('col-id').value;
  const label = document.getElementById('col-label').value.trim();
  if (!label) { SAFETY.toast('열 이름을 입력하세요.', false); return; }
  const body = {
    label,
    columnType: document.getElementById('col-type').value,
    widthWeight: Number(document.getElementById('col-width').value) || 200,
    sortOrder: 0,
  };
  try {
    if (id) await SAFETY.api('/safety-api/columns/' + id, { method: 'PUT', body });
    else await SAFETY.api(`/safety-api/manuals/${currentManualId}/columns`, { method: 'POST', body });
    bootstrap.Modal.getInstance(document.getElementById('columnModal')).hide();
    SAFETY.toast('저장되었습니다.');
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteColumn(columnId) {
  if (!confirm('이 열을 삭제하시겠습니까? 열에 들어 있던 내용도 함께 사라집니다.')) return;
  try {
    await SAFETY.api('/safety-api/columns/' + columnId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 열 순서를 저장한다 (머리글을 끌어다 놓으면 onColDrop 이 부른다) */
async function saveColumnOrder(ids) {
  try {
    await SAFETY.api(`/safety-api/manuals/${currentManualId}/columns/order`, { method: 'PUT', body: ids });
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderDetailTools() {
  const el = document.getElementById('detailTools');
  const resetBtn = `<button class="btn-modern btn-outline-modern" onclick="resetStepColWidths()"
      title="열 너비를 기본값으로"><i class="fas fa-table-columns"></i>열 너비 초기화</button>`;
  if (!isAdminUser) { el.innerHTML = resetBtn; return; }
  el.innerHTML = resetBtn + `
    <button class="btn-modern btn-edit-toggle ${editMode ? 'on' : ''}" onclick="toggleEditMode()">
      <i class="fas fa-pen"></i>${editMode ? '수정 종료' : '수정'}</button>
    ${editMode ? `
      <button class="btn-modern btn-outline-modern" onclick="addStepInline()"
              title="빈 행을 바로 추가합니다. 칸을 눌러 내용을 넣으세요."><i class="fas fa-plus"></i>행 추가</button>
      <button class="btn-modern btn-outline-modern" onclick="openColumnModal()"
              title="표에 열을 추가합니다"><i class="fas fa-table-columns"></i>열 추가</button>
      <button class="btn-modern btn-danger-soft" onclick="deleteManual()"><i class="fas fa-trash"></i>매뉴얼 삭제</button>` : ''}`;
}

/**
 * 상세 표를 그린다.
 * 열 구성은 매뉴얼마다 다르므로 서버가 준 열 정의(currentDetail.columns)대로 만들고,
 * 각 칸은 열 ID 로 값을 찾아 채운다. 체크 열은 체크버튼으로 그린다.
 * 사진이 하나도 없는 매뉴얼이면 사진 열 자체를 만들지 않는다(buildStepColLayout).
 */
function renderSteps(steps) {
  stepColLayout = buildStepColLayout();

  const canEditColumns = isAdminUser && editMode;
  document.getElementById('stepHead').innerHTML = '<tr>' + stepColLayout.map((col, i) => {
    // No./관리 열은 서버의 열 정의가 아니라 화면이 붙인 것이라 이름·순서를 바꿀 수 없다
    const movable = canEditColumns && col.columnId != null;
    const dnd = movable ? ` draggable="true"
       ondragstart="onColDragStart(event, ${col.columnId})" ondragend="onColDragEnd(event)"
       ondragover="onColDragOver(event, ${col.columnId})" ondragleave="onColDragLeave(event)"
       ondrop="onColDrop(event, ${col.columnId})"` : '';
    const tools = movable ? `<span class="col-tools">
         <button type="button" onclick="startColumnRename(event, ${col.columnId})" title="열 이름 수정"><i class="fas fa-pen"></i></button>
         <button type="button" onclick="openColumnModal(${col.columnId})" title="열 유형 변경"><i class="fas fa-sliders"></i></button>
         <button type="button" class="danger" onclick="deleteColumn(${col.columnId})" title="열 삭제"><i class="fas fa-trash"></i></button>
       </span>` : '';
    return `<th class="${col.type === 'CHECK' ? 'col-check' : ''}${movable ? ' col-movable' : ''}"
       data-col="${col.key}" data-column-id="${col.columnId != null ? col.columnId : ''}"
       title="${SAFETY.escapeHtml(col.label)}${movable ? ' — 끌어서 순서 변경' : ''}"${dnd}
       ><span class="col-title">${SAFETY.escapeHtml(col.label)}</span>${tools}${i < stepColLayout.length - 1
         ? `<span class="col-resizer" data-col="${col.key}" title="경계를 끌어 폭 조절"></span>` : ''}</th>`;
  }).join('') + '</tr>';

  const tbody = document.getElementById('stepRows');
  if (!steps.length) {
    tbody.innerHTML = `<tr><td colspan="${stepColLayout.length}" class="text-center text-muted py-4">등록된 행이 없습니다.</td></tr>`;
    applyStepColWidths();
    return;
  }

  tbody.innerHTML = steps.map(step => {
    const byColumn = {};
    (step.values || []).forEach(v => { byColumn[v.columnId] = v; });
    return '<tr>' + stepColLayout.map(col => renderStepCell(col, step, byColumn[col.columnId])).join('') + '</tr>';
  }).join('');
  applyStepColWidths();
}

function renderStepCell(col, step, value) {
  const label = ` data-label="${SAFETY.escapeHtml(col.label)}"`;
  const canEdit = isAdminUser && editMode;
  if (col.type === 'NO') {
    return `<td class="text-center"${label}><span class="step-no-badge">${step.stepNo}</span></td>`;
  }
  if (col.type === 'MANAGE') {
    return `<td${label}><div class="step-manage">
      <button class="btn btn-sm btn-outline-secondary" onclick="openStepModal(${step.stepId})"
              title="행 번호·정렬, 사진 목록"><i class="fas fa-sliders"></i></button>
      <button class="btn btn-sm btn-outline-danger" onclick="deleteStep(${step.stepId})" title="행 삭제"><i class="fas fa-trash"></i></button>
    </div></td>`;
  }
  // 사진을 끌어다 놓거나, 파일을 바로 떨어뜨려 올릴 수 있는 칸
  const drop = canEdit ? ` ondragover="onCellDragOver(event)" ondragleave="onCellDragLeave(event)"
      ondrop="onCellDrop(event, ${step.stepId}, ${col.columnId})"
      onpaste="onCellPaste(event, ${step.stepId}, ${col.columnId})"` : '';
  if (col.type === 'PHOTO') {
    // 사진 칸에는 글자가 없어 포커스를 받을 수 없다 — 붙여넣기를 받으려면 tabindex 가 필요하다
    return `<td class="cell-droppable${canEdit ? ' cell-pastable' : ''}"${
      canEdit ? ' tabindex="0" title="사진을 끌어다 놓거나 Ctrl+V 로 붙여넣기"' : ''}${label}${drop}>${
      renderPhotos(photosOfColumn(step, col.columnId, true), canEdit)}</td>`;
  }
  if (col.type === 'CHECK') {
    const checked = value && value.checked;
    const clickable = isAdminUser && editMode;
    return `<td class="text-center"${label}>
      <button class="check-btn ${checked ? 'on' : ''}" ${clickable ? '' : 'disabled'}
              title="${clickable ? '눌러서 체크' : (checked ? '체크됨' : '미체크')}"
              onclick="toggleStepCheck(${step.stepId}, ${col.columnId}, ${!checked})">
        <i class="fas ${checked ? 'fa-square-check' : 'fa-square'}"></i>
      </button></td>`;
  }
  // 글 열이라도 그 칸에 지정된 사진이 있으면 글 아래에 함께 보여준다 (예: 비고 칸의 사진)
  const text = (value && value.text) ? value.text : '';
  const photos = photosOfColumn(step, col.columnId, false);
  const photoHtml = photos.length ? `<div class="cell-photos">${renderPhotos(photos, canEdit)}</div>` : '';
  const editable = canEdit
    ? ` class="cell-droppable cell-editable" onclick="startCellEdit(event, ${step.stepId}, ${col.columnId})"
        title="눌러서 내용 수정"`
    : ' class="cell-droppable"';
  return `<td style="white-space:pre-wrap"${editable}${label}${drop}><span class="cell-text${
    text ? '' : ' empty'}">${text ? SAFETY.escapeHtml(text) : (canEdit ? '내용 입력' : '')}</span>${photoHtml}</td>`;
}

/**
 * 이 칸에 보여줄 사진들.
 * columnId 가 없는(예전에 올렸거나 엑셀에서 열을 못 정한) 사진은 기본 사진 열이 떠맡는다.
 */
function photosOfColumn(step, columnId, isDefaultPhotoColumn) {
  return (step.photos || []).filter(p =>
    p.columnId === columnId || (isDefaultPhotoColumn && p.columnId == null));
}

/** 체크버튼 토글 — 관리자만, 수정 모드에서만 */
async function toggleStepCheck(stepId, columnId, checked) {
  if (!isAdminUser || !editMode) return;
  try {
    await SAFETY.api(`/safety-api/steps/${stepId}/checks/${columnId}?checked=${checked}`, { method: 'PUT' });
    await loadDetail();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 단계 표 컬럼 폭 — px 절대값, 머리글 경계 드래그로 조절
// ================================================================
function stepColKeys() {
  const keys = ['no', 'photo', 'desc', 'hazard', 'equip', 'remark'];
  if (isAdminUser && editMode) keys.push('manage');
  return keys;
}

function loadStepColWeights() {
  try {
    return JSON.parse(localStorage.getItem(STEP_COL_STORAGE_KEY) || '{}') || {};
  } catch (e) {
    return {}; // 저장값을 못 읽는 환경이면 서버의 기본 비중을 쓴다
  }
}

function saveStepColWeights() {
  try { localStorage.setItem(STEP_COL_STORAGE_KEY, JSON.stringify(stepColWeights)); }
  catch (e) { /* 저장 못해도 이번 세션 동안은 그대로 쓴다 */ }
}

/**
 * 지금 매뉴얼에 맞는 화면 열 목록을 만든다.
 * 서버 열 정의 앞에 No., (수정 모드면) 뒤에 관리 열을 붙인다.
 * 사진 열은 실제 사진이 하나도 없으면 빼서 그만큼 다른 열을 넓게 쓴다.
 */
function buildStepColLayout() {
  const detail = currentDetail || {};
  const columns = detail.columns || [];
  // 사진 열에 실제로 들어갈 사진(열 지정이 없거나 사진 열을 가리키는 것)이 있는지 본다.
  // 비고 칸에만 사진이 있는 매뉴얼에서 빈 사진 열이 생기지 않도록 한다.
  const photoColumnIds = columns.filter(c => c.columnType === 'PHOTO').map(c => c.columnId);
  const hasAnyPhoto = (detail.steps || []).some(step =>
    (step.photos || []).some(p => p.columnId == null || photoColumnIds.includes(p.columnId)));
  const manualId = detail.manualId;
  const saved = (manualId != null && stepColWeights[manualId]) ? stepColWeights[manualId] : {};

  const layout = [{ key: 'no', label: 'No.', type: 'NO', weight: STEP_NO_WIDTH, fixed: true }];
  columns.forEach(column => {
    if (column.columnType === 'PHOTO' && !hasAnyPhoto) return;   // 사진이 없으면 사진 열은 아예 만들지 않는다
    const key = 'c' + column.columnId;
    // 사진이 들어갈 수 있는 칸(사진 열 + 사진이 붙은 글 열)은 사진 상자보다 좁아지지 않게 한다
    const holdsPhoto = column.columnType === 'PHOTO'
      || (detail.steps || []).some(step => (step.photos || []).some(p => p.columnId === column.columnId));
    layout.push({
      key,
      columnId: column.columnId,
      label: column.label,
      type: column.columnType,
      weight: Number(saved[key]) > 0 ? Number(saved[key]) : (column.widthWeight || 200),
      minWidth: holdsPhoto ? STEP_PHOTO_COL_MIN : STEP_COL_MIN,
      fixed: false,
    });
  });
  if (isAdminUser && editMode) {
    layout.push({ key: 'manage', label: '관리', type: 'MANAGE', weight: STEP_MANAGE_WIDTH, fixed: true });
  }
  return layout;
}

/** 표가 들어갈 실제 가용 폭. 모달이 보이기 전에는 0 이 나올 수 있다. */
function stepColAvailableWidth() {
  const holder = document.querySelector('#detailModal .table-responsive');
  return holder ? Math.floor(holder.clientWidth) : 0;
}

/** 고정 폭을 뺀 나머지를 비중대로 나눈다. 마지막 칸이 반올림 오차를 흡수한다. */
function fitStepColWidths(layout, available) {
  const widths = {};
  let remaining = available;
  layout.forEach(col => { if (col.fixed) { widths[col.key] = col.weight; remaining -= col.weight; } });

  const flex = layout.filter(col => !col.fixed);
  const totalWeight = flex.reduce((sum, col) => sum + col.weight, 0) || 1;
  let used = 0;
  flex.forEach((col, i) => {
    const min = col.minWidth || STEP_COL_MIN;
    if (i === flex.length - 1) {
      widths[col.key] = Math.max(min, remaining - used);
    } else {
      widths[col.key] = Math.max(min, Math.round(remaining * col.weight / totalWeight));
      used += widths[col.key];
    }
  });
  return widths;
}

function paintStepCols(widths) {
  document.getElementById('stepCols').innerHTML =
    stepColLayout.map(col => `<col style="width:${widths[col.key]}px">`).join('');
  const table = document.querySelector('#detailModal .step-table');
  if (table) {
    table.style.width = stepColLayout.reduce((sum, col) => sum + widths[col.key], 0) + 'px';
  }
}

/**
 * 현재 모달 폭에 맞춰 열 폭을 다시 계산해 적용한다 (행 내용은 건드리지 않는다).
 * 표로 읽을 수 없을 만큼 좁으면 항목별 카드 형태로 전환한다.
 */
function applyStepColWidths() {
  const table = document.querySelector('#detailModal .step-table');
  if (!table || !stepColLayout.length) return;
  const available = stepColAvailableWidth();

  if (available > 0 && available < STEP_STACK_BREAKPOINT) {
    table.classList.add('stacked');
    table.style.width = '';
    document.getElementById('stepCols').innerHTML = '';
    return;
  }
  table.classList.remove('stacked');
  paintStepCols(fitStepColWidths(stepColLayout, (available > 0 ? available : 1100) - 1));
}

function bindStepColResize() {
  // 모달이 완전히 열린 뒤에야 표 영역의 실제 폭을 알 수 있으므로 그때 한 번 더 맞춘다.
  document.getElementById('detailModal').addEventListener('shown.bs.modal', () => applyStepColWidths());

  // 표 영역 폭이 바뀔 때마다 다시 맞춘다 — 세로 스크롤바 등장, 사이드바 접기, 창 크기 변경까지 포함.
  const holder = document.querySelector('#detailModal .table-responsive');
  if (holder && window.ResizeObserver) {
    let lastWidth = 0;
    new ResizeObserver(() => {
      const width = holder.clientWidth;
      if (width > 0 && width !== lastWidth) { lastWidth = width; applyStepColWidths(); }
    }).observe(holder);
  } else {
    window.addEventListener('resize', () => {
      if (document.getElementById('detailModal').classList.contains('show')) applyStepColWidths();
    });
  }

  // 경계를 끌면 양옆 두 칸이 폭을 주고받는다. 합계가 그대로라 표는 항상 화면에 꼭 맞는다.
  document.getElementById('stepHead').addEventListener('mousedown', (e) => {
    const handle = e.target.closest('.col-resizer');
    if (!handle) return;
    const index = stepColLayout.findIndex(col => col.key === handle.dataset.col);
    if (index < 0 || index + 1 >= stepColLayout.length) return;
    e.preventDefault();

    const widths = fitStepColWidths(stepColLayout, stepColAvailableWidth() - 1);
    const key = stepColLayout[index].key;
    const nextKey = stepColLayout[index + 1].key;
    const startX = e.clientX;
    const startWidth = widths[key];
    const startNext = widths[nextKey];
    document.body.classList.add('col-resizing');

    const onMove = (ev) => {
      const delta = Math.max(STEP_COL_MIN - startWidth,
                    Math.min(startNext - STEP_COL_MIN, ev.clientX - startX));
      widths[key] = startWidth + delta;
      widths[nextKey] = startNext - delta;
      // 현재 px 값을 그대로 비중으로 삼는다 — 다른 폭의 화면에서도 같은 비율로 재현된다.
      stepColLayout.forEach(col => { if (!col.fixed) col.weight = widths[col.key]; });
      paintStepCols(widths);
    };
    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.classList.remove('col-resizing');
      const manualId = currentDetail && currentDetail.manualId;
      if (manualId != null) {
        stepColWeights[manualId] = {};
        stepColLayout.forEach(col => { if (!col.fixed) stepColWeights[manualId][col.key] = col.weight; });
        saveStepColWeights();
      }
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

function resetStepColWidths() {
  const manualId = currentDetail && currentDetail.manualId;
  if (manualId != null) delete stepColWeights[manualId];
  saveStepColWeights();
  stepColLayout = buildStepColLayout();
  applyStepColWidths();
  SAFETY.toast('열 너비를 기본 비율로 되돌렸습니다.');
}

function renderPhotos(photos, draggable) {
  if (!photos || !photos.length) {
    return draggable ? '<span class="cell-photo-hint">사진을 끌어다 놓기</span>' : '<span class="text-muted small">-</span>';
  }
  return photos.map(p => {
    const img = `<img class="step-photo" src="${SAFETY.escapeHtml(p.url)}"
      alt="${SAFETY.escapeHtml(p.originalName || '')}" data-name="${SAFETY.escapeHtml(p.originalName || '')}"
      ${draggable ? `draggable="true" ondragstart="onPhotoDragStart(event, ${p.photoId})" ondragend="onPhotoDragEnd(event)"` : ''}
      onclick="openLightboxFrom(this)"
      title="${draggable ? '끌어서 다른 칸으로 옮기기 · 클릭하면 크게 보기' : '클릭하면 크게 볼 수 있습니다'}">`;
    if (!draggable) return img;
    return `<span class="photo-chip">${img}<button type="button" class="photo-del"
      onclick="deleteStepPhoto(${p.photoId})" title="사진 삭제"><i class="fas fa-xmark"></i></button></span>`;
  }).join('');
}

// ================================================================
// 매뉴얼을 분류로 끌어다 놓기 (수정 모드에서만)
// ================================================================
let draggingManualId = null;

/** 목록에서 매뉴얼을 집었을 때 */
function onManualDragStart(event, manualId) {
  if (!isAdminUser || !editMode) { event.preventDefault(); return; }
  draggingManualId = Number(manualId);
  event.dataTransfer.effectAllowed = 'move';
  // 일부 브라우저는 데이터가 없으면 드래그를 시작하지 않는다
  event.dataTransfer.setData('text/plain', String(manualId));
  event.currentTarget.classList.add('dragging');
  document.body.classList.add('dnd-active');
}

function onManualDragEnd() {
  draggingManualId = null;
  document.body.classList.remove('dnd-active');
  document.querySelectorAll('.manual-row.dragging').forEach(el => el.classList.remove('dragging'));
  document.querySelectorAll('.tree-row.drop-over').forEach(el => el.classList.remove('drop-over'));
}

/** 매뉴얼은 중분류에만 붙일 수 있으므로 대분류 위에서는 받지 않는다 */
function onCategoryDragOver(event, categoryId, levelNo) {
  if (draggingManualId == null || levelNo !== 2) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = 'move';
  event.currentTarget.classList.add('drop-over');
}

function onCategoryDragLeave(event) {
  event.currentTarget.classList.remove('drop-over');
}

async function onCategoryDrop(event, categoryId, levelNo) {
  if (draggingManualId == null) return;
  event.preventDefault();
  event.currentTarget.classList.remove('drop-over');
  if (levelNo !== 2) {
    SAFETY.toast('매뉴얼은 중분류에만 넣을 수 있습니다.', false);
    return;
  }

  const manualId = draggingManualId;
  const manual = manuals.find(m => Number(m.manualId) === manualId);
  if (manual && Number(manual.categoryId) === Number(categoryId)) return;   // 제자리
  onManualDragEnd();

  try {
    await SAFETY.api(`/safety-api/manuals/${manualId}/category?categoryId=${categoryId}`, { method: 'PUT' });
    SAFETY.toast(`'${manual ? manual.title : '매뉴얼'}' 을(를) ${pathOf(categoryId).join(' > ')} (으)로 옮겼습니다.`);
    await refreshAll();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 수정 모드에서 목록 위에 사용법을 한 줄 띄운다 */
function renderDragHint() {
  const hint = document.getElementById('searchHint');
  if (!hint || !(isAdminUser && editMode) || contentKeyword) return;
  hint.innerHTML = '<i class="fas fa-hand-pointer me-1"></i>매뉴얼을 왼쪽 분류(중분류)로 끌어다 놓으면 그 분류로 옮겨집니다.';
}

// ================================================================
// 공지사항 (좌측 패널)
// ================================================================
let notices = [];
let noticeViewModal = null;
/** 전체 보기 모달 상태 — 한 페이지 5건, 펼쳐 둔 공지는 하나만 */
const NOTICE_PAGE_SIZE = 5;
let noticeViewPage = 0;
let noticeOpenId = null;

async function loadNotices() {
  try {
    notices = await SAFETY.api('/safety-api/notices') || [];
  } catch (e) {
    notices = [];
    console.warn('공지사항 조회 실패', e);
  }
  renderNotices();
}

function renderNotices() {
  const holder = document.getElementById('noticeList');
  if (!notices.length) {
    holder.innerHTML = '<div class="notice-empty">등록된 공지사항이 없습니다.</div>';
    return;
  }
  const manage = isAdminUser && editMode;
  holder.innerHTML = notices.map(n => `
    <div class="notice-row ${n.pinned ? 'pinned' : ''}" onclick="openNoticeView(${n.noticeId})"
         title="눌러서 전체 내용 보기">
      <div class="notice-top">
        ${n.pinned ? '<span class="notice-pin" title="상단 고정"><i class="fas fa-thumbtack"></i></span>' : ''}
        <div class="notice-title">${SAFETY.escapeHtml(n.title)}</div>
        <span class="notice-date">${formatDateOnly(n.createdAt)}</span>
        ${manage ? `<span class="notice-tools" onclick="event.stopPropagation()">
          <button onclick="openNoticeModal(${n.noticeId})" title="공지 수정"><i class="fas fa-pen"></i></button>
          <button class="danger" onclick="deleteNotice(${n.noticeId})" title="공지 삭제"><i class="fas fa-trash"></i></button>
        </span>` : ''}
      </div>
      ${n.content ? `<div class="notice-preview">${SAFETY.escapeHtml(n.content)}</div>` : ''}
    </div>`).join('');
}

/**
 * 공지 전체 보기 모달. 한 페이지에 5건씩 보여주고, 누른 공지만 펼쳐 둔다.
 * (누른 공지가 몇 번째든 그 공지가 있는 페이지를 열어 준다)
 */
function openNoticeView(noticeId) {
  if (!notices.length) { SAFETY.toast('등록된 공지사항이 없습니다.', false); return; }

  const index = notices.findIndex(n => Number(n.noticeId) === Number(noticeId));
  noticeViewPage = index >= 0 ? Math.floor(index / NOTICE_PAGE_SIZE) : 0;
  noticeOpenId = index >= 0 ? Number(notices[index].noticeId) : null;

  renderNoticeView();
  if (!noticeViewModal) noticeViewModal = new bootstrap.Modal(document.getElementById('noticeViewModal'));
  noticeViewModal.show();
}

function renderNoticeView() {
  const totalPages = Math.max(1, Math.ceil(notices.length / NOTICE_PAGE_SIZE));
  noticeViewPage = Math.min(Math.max(0, noticeViewPage), totalPages - 1);
  const from = noticeViewPage * NOTICE_PAGE_SIZE;
  const page = notices.slice(from, from + NOTICE_PAGE_SIZE);

  document.getElementById('noticeViewMeta').textContent = `전체 ${notices.length}건`;
  document.getElementById('noticeViewList').innerHTML = page.map(n => {
    const open = Number(n.noticeId) === noticeOpenId;
    return `<article class="nf-item ${open ? 'open' : ''}" data-notice="${n.noticeId}">
      <div class="nf-head" onclick="toggleNoticeView(${n.noticeId})">
        <h6 class="nf-title">
          ${n.pinned ? '<span class="notice-pin" title="상단 고정"><i class="fas fa-thumbtack"></i></span>' : ''}
          <span>${SAFETY.escapeHtml(n.title)}</span>
        </h6>
        <span class="nf-date">${formatDateOnly(n.createdAt)}</span>
        <span class="nf-caret"><i class="fas fa-chevron-down"></i></span>
      </div>
      ${open ? `<div class="nf-body${n.content ? '' : ' empty'}">${
        SAFETY.escapeHtml(n.content || '내용이 없습니다.')}</div>` : ''}
    </article>`;
  }).join('');

  const to = from + page.length;
  document.getElementById('noticeViewPager').innerHTML = `
    <span class="nf-range">${from + 1}–${to} / ${notices.length}건</span>
    <span class="nf-pages">
      <button class="nf-page" ${noticeViewPage === 0 ? 'disabled' : ''}
              onclick="goNoticePage(${noticeViewPage - 1})"><i class="fas fa-chevron-left"></i></button>
      ${Array.from({ length: totalPages }, (_, i) =>
        `<button class="nf-page ${i === noticeViewPage ? 'active' : ''}" onclick="goNoticePage(${i})">${i + 1}</button>`).join('')}
      <button class="nf-page" ${noticeViewPage === totalPages - 1 ? 'disabled' : ''}
              onclick="goNoticePage(${noticeViewPage + 1})"><i class="fas fa-chevron-right"></i></button>
    </span>`;
  document.getElementById('noticeViewScroll').scrollTop = 0;
}

/** 머리글을 누르면 그 공지만 펼친다 (이미 펼쳐져 있으면 접는다) */
function toggleNoticeView(noticeId) {
  noticeOpenId = (Number(noticeId) === noticeOpenId) ? null : Number(noticeId);
  renderNoticeView();
}

function goNoticePage(page) {
  noticeViewPage = page;
  noticeOpenId = null;   // 페이지를 넘기면 모두 접힌 상태로 시작한다
  renderNoticeView();
}

function openNoticeModal(noticeId) {
  const notice = noticeId ? notices.find(n => Number(n.noticeId) === Number(noticeId)) : null;
  document.getElementById('notice-id').value = notice ? notice.noticeId : '';
  document.getElementById('notice-title').value = notice ? notice.title : '';
  document.getElementById('notice-content').value = notice ? (notice.content || '') : '';
  document.getElementById('notice-pinned').checked = notice ? !!notice.pinned : false;
  document.getElementById('noticeModalTitle').textContent = notice ? '공지사항 수정' : '공지사항 등록';
  new bootstrap.Modal(document.getElementById('noticeModal')).show();
}

async function saveNotice() {
  const id = document.getElementById('notice-id').value;
  const title = document.getElementById('notice-title').value.trim();
  if (!title) { SAFETY.toast('공지 제목을 입력하세요.', false); return; }
  const body = {
    title,
    content: document.getElementById('notice-content').value,
    pinned: document.getElementById('notice-pinned').checked,
  };
  try {
    if (id) await SAFETY.api('/safety-api/notices/' + id, { method: 'PUT', body });
    else await SAFETY.api('/safety-api/notices', { method: 'POST', body });
    bootstrap.Modal.getInstance(document.getElementById('noticeModal')).hide();
    SAFETY.toast('저장되었습니다.');
    await loadNotices();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

async function deleteNotice(noticeId) {
  if (!confirm('이 공지사항을 삭제하시겠습니까?')) return;
  try {
    await SAFETY.api('/safety-api/notices/' + noticeId, { method: 'DELETE' });
    SAFETY.toast('삭제되었습니다.');
    await loadNotices();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
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
/** 분류 추가. parentId 가 null 이면 대분류, 아니면 그 분류의 하위로 만든다. (좌측 트리에서만 호출) */
let categoryParentId = null;
function openCategoryCreateModal(parentId) {
  const parent = findNode(parentId);
  if (parent && parent.levelNo === 2) { SAFETY.toast('중분류 아래에는 분류를 추가할 수 없습니다.', false); return; }
  categoryParentId = parent ? Number(parent.categoryId) : null;
  document.getElementById('cat-id').value = '';
  document.getElementById('cat-name').value = '';
  document.getElementById('cat-sort').value = 0;
  const labels = { 1: '아래 중분류' };
  document.getElementById('cat-context-text').textContent =
    parent ? (pathOf(parent.categoryId).join(' > ') + ' ' + labels[parent.levelNo]) : '최상위(대분류)';
  document.getElementById('categoryModalTitle').textContent = parent ? '하위 분류 추가' : '대분류 추가';
  new bootstrap.Modal(document.getElementById('categoryModal')).show();
}

function openCategoryEditModal(categoryId) {
  const node = findNode(categoryId);
  if (!node) return;
  categoryParentId = null;
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
      await SAFETY.api('/safety-api/categories', { method: 'POST', body: { name, parentId: categoryParentId, sortOrder } });
      if (categoryParentId !== null) expanded.add(Number(categoryParentId));
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
/** 트리 전체의 중분류를 "대 > 중" 경로로 모아 준다 (매뉴얼/엑셀 업로드의 분류 선택지) */
function minorCategoryOptions() {
  const options = [];
  nodeIndex.forEach(node => {
    if (node.levelNo === 2) options.push({ id: Number(node.categoryId), path: pathOf(node.categoryId).join(' > ') });
  });
  return options.sort((a, b) => a.path.localeCompare(b.path));
}

function openManualCreateModal() {
  const options = minorCategoryOptions();
  if (!options.length) {
    SAFETY.toast('먼저 좌측 분류에서 중분류까지 만들어 주세요. 매뉴얼은 중분류에만 등록할 수 있습니다.', false);
    return;
  }
  const select = document.getElementById('man-category');
  select.innerHTML = options.map(o => `<option value="${o.id}">${SAFETY.escapeHtml(o.path)}</option>`).join('');
  // 지금 보고 있는 분류가 중분류면 그걸 기본값으로
  const current = findNode(selectedId);
  select.value = (current && current.levelNo === 2) ? String(current.categoryId) : String(options[0].id);

  document.getElementById('man-id').value = '';
  document.getElementById('man-title').value = '';
  document.getElementById('man-sort').value = 0;
  document.getElementById('man-context-note').textContent =
    '저장하면 바로 상세 화면이 열립니다. 거기서 "단계 추가"로 공정 사진과 내용을 하나씩 넣으세요.';
  document.getElementById('manualModalTitle').textContent = '내용 추가 (매뉴얼)';
  new bootstrap.Modal(document.getElementById('manualModal')).show();
}

async function saveManual() {
  const title = document.getElementById('man-title').value.trim();
  if (!title) { SAFETY.toast('매뉴얼 제목을 입력하세요.', false); return; }
  const categoryId = Number(document.getElementById('man-category').value);
  if (!categoryId) { SAFETY.toast('등록할 분류를 선택하세요.', false); return; }
  const sortOrder = Number(document.getElementById('man-sort').value) || 0;
  try {
    const created = await SAFETY.api('/safety-api/manuals', {
      method: 'POST',
      body: { categoryId, title, sortOrder, steps: [] },
    });
    bootstrap.Modal.getInstance(document.getElementById('manualModal')).hide();
    SAFETY.toast('매뉴얼이 등록되었습니다. 이어서 단계를 추가하세요.');
    // 방금 만든 매뉴얼이 보이도록 그 분류로 이동한 뒤, 상세를 열어 바로 단계를 넣게 한다.
    await loadTree();
    await selectCategory(categoryId);
    if (created && created.manualId) await openDetail(created.manualId);
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
  const step = stepId ? (currentDetail && (currentDetail.steps || []).find(x => Number(x.stepId) === Number(stepId))) : null;
  document.getElementById('stepModalTitle').textContent = step ? '행 수정' : '행 추가';
  document.getElementById('step-id').value = step ? step.stepId : '';
  document.getElementById('step-photo-file').value = '';
  document.getElementById('step-no').value = step ? step.stepNo : nextStepNo();
  document.getElementById('step-sort').value = step ? step.sortOrder : nextStepNo();
  renderStepModalFields(step);
  new bootstrap.Modal(document.getElementById('stepModal')).show();
}

function nextStepNo() {
  const steps = (currentDetail && currentDetail.steps) || [];
  return steps.length ? Math.max(...steps.map(s => s.stepNo || 0)) + 1 : 1;
}

/** 입력 칸을 매뉴얼의 열 정의대로 만든다 (사진 열은 아래 파일 첨부로 대신한다) */
function renderStepModalFields(step) {
  const columns = (currentDetail && currentDetail.columns) || [];
  const byColumn = {};
  ((step && step.values) || []).forEach(v => { byColumn[v.columnId] = v; });

  const holder = document.getElementById('step-fields');
  holder.innerHTML = columns.filter(c => c.columnType !== 'PHOTO').map(c => {
    const value = byColumn[c.columnId];
    if (c.columnType === 'CHECK') {
      return `<div class="form-check mb-2">
        <input class="form-check-input step-field" type="checkbox" id="stepcol-${c.columnId}"
               data-column="${c.columnId}" data-type="CHECK" ${value && value.checked ? 'checked' : ''}>
        <label class="form-check-label" for="stepcol-${c.columnId}" style="font-size:.85rem">
          ${SAFETY.escapeHtml(c.label)}</label>
      </div>`;
    }
    return `<div class="mb-2">
      <label class="form-label-modern">${SAFETY.escapeHtml(c.label)}</label>
      <textarea class="form-control step-field" id="stepcol-${c.columnId}"
                data-column="${c.columnId}" data-type="TEXT" rows="2">${
        SAFETY.escapeHtml((value && value.text) || '')}</textarea>
    </div>`;
  }).join('');

  // 사진을 넣을 수 있는 칸: 사진 열 + 모든 글 열 (체크 열은 제외)
  // 예전에는 사진 열이 있는 매뉴얼에서만 첨부가 보였는데,
  // 비고처럼 글 칸에도 사진을 넣을 수 있어야 해서 글 열이 하나라도 있으면 보여준다.
  const targets = photoTargetColumns();
  document.getElementById('step-photo-upload-wrap').style.display = targets.length ? '' : 'none';

  const select = document.getElementById('step-photo-column');
  select.innerHTML = targets.map(c =>
    `<option value="${c.columnId}">${SAFETY.escapeHtml(c.label)}</option>`).join('');

  renderStepPhotoList(step);
}

/** 사진을 넣을 수 있는 열 (사진 열 먼저, 그다음 글 열) */
function photoTargetColumns() {
  const columns = (currentDetail && currentDetail.columns) || [];
  return [
    ...columns.filter(c => c.columnType === 'PHOTO'),
    ...columns.filter(c => c.columnType === 'TEXT'),
  ];
}

/** 이미 올라가 있는 사진 목록 — 칸을 바꾸거나 삭제할 수 있다 */
function renderStepPhotoList(step) {
  const holder = document.getElementById('step-photo-list');
  const photos = (step && step.photos) || [];
  if (!photos.length) { holder.innerHTML = ''; return; }

  const targets = photoTargetColumns();
  const defaultPhotoColumn = targets.find(c => c.columnType === 'PHOTO');
  holder.innerHTML = photos.map(p => {
    // columnId 가 없는 사진(예전 데이터)은 기본 사진 열에 있는 것으로 보여준다
    const current = p.columnId != null ? p.columnId : (defaultPhotoColumn ? defaultPhotoColumn.columnId : '');
    const options = targets.map(c =>
      `<option value="${c.columnId}" ${c.columnId === current ? 'selected' : ''}>${
        SAFETY.escapeHtml(c.label)}</option>`).join('');
    return `<div class="step-photo-item">
      <img src="${SAFETY.escapeHtml(p.url)}" alt="">
      <span class="spi-name" title="${SAFETY.escapeHtml(p.originalName || '')}">${
        SAFETY.escapeHtml(p.originalName || '')}</span>
      <select onchange="moveStepPhoto(${p.photoId}, this.value)" title="이 사진을 넣을 칸">${options}</select>
      <button type="button" class="btn btn-sm btn-outline-danger"
              onclick="deleteStepPhoto(${p.photoId})" title="사진 삭제"><i class="fas fa-trash"></i></button>
    </div>`;
  }).join('');
}

/** 사진을 다른 칸으로 옮긴다 */
async function moveStepPhoto(photoId, columnId) {
  try {
    await SAFETY.api('/safety-api/photos/' + photoId + '/column',
      { method: 'PUT', body: { columnId: Number(columnId) } });
    SAFETY.toast('사진 위치를 옮겼습니다.');
    await reloadStepModalPhotos();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 사진 삭제 — 표의 x 버튼과 단계 수정 창 양쪽에서 쓴다 */
async function deleteStepPhoto(photoId, event) {
  if (event) event.stopPropagation();
  if (!confirm('이 사진을 삭제하시겠습니까?')) return;
  try {
    await SAFETY.api('/safety-api/photos/' + photoId, { method: 'DELETE' });
    SAFETY.toast('사진을 삭제했습니다.');
    await reloadStepModalPhotos();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 사진을 바꾼 뒤 상세와 수정 창의 목록을 함께 새로 고친다 */
async function reloadStepModalPhotos() {
  await loadDetail();
  // 표에서 지운 경우에는 단계 수정 창이 열려 있지 않다
  const holder = document.getElementById('step-photo-list');
  const idField = document.getElementById('step-id');
  if (!holder || !idField || !idField.value) return;
  const step = (currentDetail.steps || []).find(s => s.stepId === Number(idField.value));
  renderStepPhotoList(step);
}

async function saveStep() {
  const stepId = document.getElementById('step-id').value;
  const values = [...document.querySelectorAll('.step-field')].map(el => ({
    columnId: Number(el.dataset.column),
    text: el.dataset.type === 'TEXT' ? el.value : null,
    checked: el.dataset.type === 'CHECK' ? el.checked : false,
  }));
  const payload = {
    stepNo: Number(document.getElementById('step-no').value) || 0,
    sortOrder: Number(document.getElementById('step-sort').value) || 0,
    values,
  };
  try {
    let savedStepId = stepId;
    if (stepId) {
      await SAFETY.api('/safety-api/steps/' + stepId, { method: 'PUT', body: payload });
    } else {
      const created = await SAFETY.api('/safety-api/manuals/' + currentManualId + '/steps', { method: 'POST', body: payload });
      savedStepId = created.stepId;
    }
    const files = [...document.getElementById('step-photo-file').files];
    const columnId = document.getElementById('step-photo-column').value;
    for (const file of files) {
      if (!savedStepId) break;
      await SAFETY.uploadMultipart('/safety-api/steps/' + savedStepId + '/photos',
        columnId ? { file, columnId } : { file });
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
let euSelected = { major: null, middle: null };
/** 시트 index -> 사용자가 그 행에서 직접 고른 중분류 id. 여기 없으면 상단 기본 분류를 따른다. */
let euRowCategory = {};
/** 분할 업로드로 서버에 올려 둔 파일의 식별자. 미리보기와 확정이 이 파일을 함께 쓴다. */
let euUploadId = null;

/**
 * 파일을 조각내서 올린다.
 *
 * <p>앞단 웹서버(nginx client_max_body_size 등)가 요청 본문 크기를 제한하고 있어,
 * 사진이 많은 50~90MB 매뉴얼 파일을 한 번에 보내면 413 으로 잘린다.
 * 조각 하나가 4MB 라 어떤 제한에도 걸리지 않는다.
 * 파일은 여기서 한 번만 올라가고, 확정 업로드는 서버에 있는 그 파일을 그대로 쓴다.
 */
const EU_CHUNK_SIZE = 4 * 1024 * 1024;

async function euUploadInChunks(file, onProgress) {
  const totalChunks = Math.max(1, Math.ceil(file.size / EU_CHUNK_SIZE));
  let uploadId = '';
  for (let i = 0; i < totalChunks; i++) {
    const blob = file.slice(i * EU_CHUNK_SIZE, Math.min(file.size, (i + 1) * EU_CHUNK_SIZE));
    const res = await SAFETY.uploadMultipart('/safety-api/excel-upload/chunk', {
      uploadId, chunkIndex: String(i), totalChunks: String(totalChunks), fileName: file.name,
      file: new File([blob], file.name),
    });
    uploadId = res.uploadId;
    onProgress(i + 1, totalChunks);
  }
  return uploadId;
}

/** 서버에 올려 둔 임시 파일을 버린다 (다른 파일을 다시 고르거나 창을 열 때) */
async function euDiscardStaged() {
  if (!euUploadId) return;
  const id = euUploadId;
  euUploadId = null;
  try {
    await SAFETY.api('/safety-api/excel-upload/discard-staged', { method: 'POST', body: { uploadId: id } });
  } catch (e) {
    // 임시 파일은 서버가 주기적으로 정리하므로 실패해도 넘어간다
  }
}

function openExcelModal() {
  document.getElementById('eu-file').value = '';
  document.getElementById('euFileStatus').textContent = '';
  document.getElementById('euStep2').style.display = 'none';
  document.getElementById('eu-confirm-btn').classList.add('d-none');
  euDiscardStaged();
  euPreviewData = [];
  euSelected = { major: null, middle: null };
  euRowCategory = {};
  document.getElementById('eu-add-major-form').classList.add('d-none');
  document.getElementById('eu-add-middle-form').classList.add('d-none');
  euFillMajorSelect();
  euPreselectFromTree();
  new bootstrap.Modal(document.getElementById('excelModal')).show();
}

/** 좌측 트리에서 이미 고른 분류가 있으면 업로드 모달의 대/중 선택을 미리 채워 준다 */
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
}

function euFillMajorSelect() {
  const sel = document.getElementById('eu-major');
  const sorted = sortNodes(tree);
  sel.innerHTML = '<option value="">선택</option>' + sorted.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  sel.value = '';
  document.getElementById('eu-middle').innerHTML = '<option value="">대분류를 먼저 선택</option>';
  document.getElementById('eu-middle').disabled = true;
}

function euOnMajorChange() {
  const majorId = document.getElementById('eu-major').value;
  euSelected.major = majorId || null;
  euSelected.middle = null;
  const midSel = document.getElementById('eu-middle');
  if (!majorId) { midSel.innerHTML = '<option value="">대분류를 먼저 선택</option>'; midSel.disabled = true; euUpdateSummary(); return; }
  const children = sortNodes((findNode(majorId) || {}).children);
  midSel.innerHTML = '<option value="">선택</option>' + children.map(c => `<option value="${c.categoryId}">${SAFETY.escapeHtml(c.name)}</option>`).join('');
  midSel.disabled = false;
  euUpdateSummary();
}

function euOnMiddleChange() {
  euSelected.middle = document.getElementById('eu-middle').value || null;
  euUpdateSummary();
}

function euToggleAdd(level) {
  document.getElementById('eu-add-' + level + '-form').classList.toggle('d-none');
}

async function euCreateCategory(level) {
  const nameInput = document.getElementById('eu-add-' + level + '-name');
  const name = nameInput.value.trim();
  if (!name) { SAFETY.toast('분류명을 입력하세요.', false); return; }
  const parentId = (level === 'middle') ? euSelected.major : null;
  if (level !== 'major' && !parentId) { SAFETY.toast('상위 분류를 먼저 선택하세요.', false); return; }
  try {
    const created = await SAFETY.api('/safety-api/categories', { method: 'POST', body: { name, parentId, sortOrder: 0 } });
    await loadTree();
    renderTree();
    euRefreshRowCategoryOptions();
    nameInput.value = '';
    document.getElementById('eu-add-' + level + '-form').classList.add('d-none');
    SAFETY.toast('분류가 추가되었습니다.');
    if (level === 'major') {
      euFillMajorSelect();
      document.getElementById('eu-major').value = created.categoryId;
      euOnMajorChange();
    } else {
      euOnMajorChange();
      document.getElementById('eu-middle').value = created.categoryId;
      euOnMiddleChange();
    }
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

/** 시트별 분류 선택지 (매뉴얼 등록 모달과 같은 목록을 쓴다) */
function euMinorOptions() {
  return minorCategoryOptions();
}

/** 그 행에 실제로 적용될 분류 id (직접 고른 값 우선, 없으면 상단 기본 분류) */
function euCategoryOf(idx) {
  if (euRowCategory[idx] != null) return Number(euRowCategory[idx]);
  return euSelected.middle ? Number(euSelected.middle) : null;
}

function euOnRowCategoryChange(idx, value) {
  if (value) euRowCategory[idx] = Number(value);
  else delete euRowCategory[idx];
  euRefreshRowCategories();
  euUpdateSummary();
}

/** 행별 분류 선택 상태를 다시 칠한다 (기본값 반영 + 변경된 행 강조) */
function euRefreshRowCategories() {
  document.querySelectorAll('.eu-row-cat').forEach(sel => {
    const idx = Number(sel.dataset.idx);
    const changed = euRowCategory[idx] != null;
    const applied = euCategoryOf(idx);
    sel.value = applied != null ? String(applied) : '';
    sel.classList.toggle('changed', changed);
    const reset = document.querySelector(`.eu-cat-reset[data-idx="${idx}"]`);
    if (reset) reset.style.display = changed ? '' : 'none';
  });
}

/** 분류를 새로 추가한 뒤, 이미 그려진 행 셀렉트의 선택지만 갱신한다 (체크 상태는 유지) */
function euRefreshRowCategoryOptions() {
  const selects = document.querySelectorAll('.eu-row-cat');
  if (!selects.length) return;
  const optionHtml = '<option value="">기본 분류 사용</option>' +
    euMinorOptions().map(o => `<option value="${o.id}">${SAFETY.escapeHtml(o.path)}</option>`).join('');
  selects.forEach(sel => { sel.innerHTML = optionHtml; });
  euRefreshRowCategories();
}

function euResetRowCategory(idx) {
  delete euRowCategory[idx];
  euRefreshRowCategories();
  euUpdateSummary();
}

function euUpdateSummary() {
  const box = document.getElementById('euSummary');
  const btn = document.getElementById('eu-confirm-btn');
  euRefreshRowCategories();

  const base = euSelected.middle
    ? `<i class="fas fa-folder-tree me-1"></i>기본 등록 위치: <b>${SAFETY.escapeHtml(pathOf(euSelected.middle).join(' > '))}</b>`
    : '<i class="fas fa-folder-tree me-1"></i>기본 등록 위치를 고르거나, 시트마다 등록 분류를 직접 선택하세요.';
  const changedCount = Object.keys(euRowCategory).length;
  box.innerHTML = base + (changedCount ? ` · 개별 지정 <b>${changedCount}</b>건` : '');
  box.style.display = '';

  // 선택된 시트가 모두 분류를 갖고 있어야 업로드할 수 있다
  const checked = Array.from(document.querySelectorAll('.eu-sheet-chk:checked'));
  const ready = euPreviewData.length && checked.length
    && checked.every(c => euCategoryOf(Number(c.dataset.idx)) != null);
  btn.classList.toggle('d-none', !ready);
}

/** 파일을 고르면 자동으로 호출된다 (별도 "형식 확인" 버튼 없음) */
async function euDoPreview() {
  const status = document.getElementById('euFileStatus');
  const file = document.getElementById('eu-file').files[0];
  if (!file) {
    await euDiscardStaged();
    status.textContent = '';
    document.getElementById('euStep2').style.display = 'none';
    return;
  }
  await euDiscardStaged();
  euPreviewData = [];
  euRowCategory = {};
  document.getElementById('eu-confirm-btn').classList.add('d-none');

  const sizeMb = (file.size / 1024 / 1024).toFixed(1);
  try {
    // 1) 파일을 조각내서 올린다 (여기서 딱 한 번만 전송한다)
    euUploadId = await euUploadInChunks(file, (done, total) => {
      const pct = Math.round(done / total * 100);
      status.innerHTML = `<i class="fas fa-spinner fa-spin me-1"></i>`
        + `파일을 올리는 중입니다... ${pct}% (${done}/${total}, ${sizeMb}MB)`;
    });

    // 2) 올라간 파일로 형식만 확인한다 (파일을 다시 보내지 않는다)
    status.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>형식을 확인하는 중입니다...';
    euPreviewData = await SAFETY.api('/safety-api/excel-upload/preview-staged',
      { method: 'POST', body: { uploadId: euUploadId } });
    const recognized = euPreviewData.filter(s => s.recognized).length;
    status.innerHTML = `<i class="fas fa-circle-check text-success me-1"></i>`
      + `${SAFETY.escapeHtml(file.name)} — 시트 ${euPreviewData.length}개 중 ${recognized}개 인식됨`;
    euRenderPreview();
    document.getElementById('euStep2').style.display = '';
    euUpdateSummary();
  } catch (e) {
    await euDiscardStaged();
    status.innerHTML = `<span class="text-danger"><i class="fas fa-circle-exclamation me-1"></i>${SAFETY.escapeHtml(e.message)}</span>`;
    document.getElementById('euStep2').style.display = 'none';
    SAFETY.toast(e.message, false);
  }
}

function euRenderPreview() {
  const recognizedCount = euPreviewData.filter(s => s.recognized).length;
  const options = euMinorOptions();
  const optionHtml = '<option value="">기본 분류 사용</option>' +
    options.map(o => `<option value="${o.id}">${SAFETY.escapeHtml(o.path)}</option>`).join('');

  document.getElementById('eu-check-all').checked = true;
  document.getElementById('euPreviewRows').innerHTML = euPreviewData.map((s, idx) => `
    <tr class="${s.recognized ? '' : 'excluded'}">
      <td><input type="checkbox" class="eu-sheet-chk" data-idx="${idx}" ${s.selected ? 'checked' : ''} ${s.recognized ? '' : 'disabled'}
             onchange="euUpdateSummary()"></td>
      <td>${SAFETY.escapeHtml(s.sheetName)}</td>
      <td>${s.recognized ? '<span class="badge-ok">인식됨</span>' : `<span class="badge-no">제외</span>`}</td>
      <td>${SAFETY.escapeHtml(s.detectedTitle || '')}${!s.recognized && s.reason ? `<div class="small text-muted">${SAFETY.escapeHtml(s.reason)}</div>` : ''}</td>
      <td>
        <div class="d-flex align-items-center gap-1">
          <select class="eu-row-cat" data-idx="${idx}" ${s.recognized ? '' : 'disabled'}
                  onchange="euOnRowCategoryChange(${idx}, this.value)">${optionHtml}</select>
          <button class="eu-cat-reset" data-idx="${idx}" style="display:none" title="기본 분류로 되돌리기"
                  onclick="euResetRowCategory(${idx})"><i class="fas fa-rotate-left"></i></button>
        </div>
      </td>
      <td class="text-center">${s.stepCount}</td>
      <td class="text-center">${s.photoCount}</td>
      <td class="small">${(s.stepPreviewLines || []).slice(0, 2).map(l => SAFETY.escapeHtml(l)).join('<br>')}
        ${s.recognized ? `<button class="btn-preview" onclick="openSheetPreview(${idx})"
             title="등록될 표를 그대로 봅니다"><i class="fas fa-eye"></i>미리보기</button>` : ''}</td>
    </tr>`).join('');

  const note = document.querySelector('#euStep2 .eu-note');
  if (note) {
    note.textContent = `총 ${euPreviewData.length}개 시트 중 ${recognizedCount}개가 매뉴얼로 인식되어 기본 선택되었습니다. `
      + '필요 없는 시트는 체크를 해제하고, 다른 분류에 넣을 시트는 "등록 분류"에서 직접 고르세요.';
  }
  euRefreshRowCategories();
}

// ================================================================
// 엑셀 미리보기 — 시트 하나가 어떤 표로 등록될지 그대로 보여준다
//  사진은 올려 둔 임시 파일에서 화면에 보이는 것만 낱장으로 가져온다.
// ================================================================
let sheetPreviewModal = null;
let sheetPreviewBlobs = [];      // 창을 닫을 때 정리할 blob URL

async function openSheetPreview(idx) {
  const sheet = euPreviewData[idx];
  if (!sheet || !sheet.recognized || !euUploadId) return;

  const body = document.getElementById('sheetPreviewBody');
  document.getElementById('sheetPreviewTitle').textContent = sheet.sheetName;
  document.getElementById('sheetPreviewSub').textContent =
    `${sheet.formTypeName || ''} · 단계 ${sheet.stepCount}개 · 사진 ${sheet.photoCount}장`;
  body.innerHTML = '<div class="text-center text-muted py-5"><i class="fas fa-spinner fa-spin me-2"></i>불러오는 중...</div>';
  if (!sheetPreviewModal) {
    sheetPreviewModal = new bootstrap.Modal(document.getElementById('sheetPreviewModal'));
    document.getElementById('sheetPreviewModal').addEventListener('hidden.bs.modal', releaseSheetPreviewBlobs);
  }
  sheetPreviewModal.show();

  try {
    const detail = await SAFETY.api('/safety-api/excel-upload/preview-sheet',
      { method: 'POST', body: { uploadId: euUploadId, sheetName: sheet.sheetName } });
    renderSheetPreview(detail);
  } catch (e) {
    body.innerHTML = `<div class="text-danger py-4">${SAFETY.escapeHtml(e.message)}</div>`;
  }
}

function renderSheetPreview(detail) {
  const cols = detail.columns || [];
  const metaHtml = (detail.meta || []).length
    ? `<div class="sp-meta">${detail.meta.map(m =>
        `<span><b>${SAFETY.escapeHtml(m.label)}</b> ${SAFETY.escapeHtml(m.value || '')}</span>`).join('')}</div>`
    : '';

  const head = '<tr><th class="sp-no">No.</th>' + cols.map(c =>
    `<th>${SAFETY.escapeHtml(c.label)}${
      c.type === 'PHOTO' ? ' <i class="fas fa-image sp-type"></i>'
      : c.type === 'CHECK' ? ' <i class="fas fa-square-check sp-type"></i>' : ''}</th>`).join('') + '</tr>';

  const rows = (detail.rows || []).map(row =>
    '<tr><td class="sp-no">' + row.stepNo + '</td>' + row.cells.map((cell, i) => {
      const type = (cols[i] || {}).type;
      if (type === 'CHECK') {
        return `<td class="text-center">${cell.checked
          ? '<i class="fas fa-square-check text-success"></i>' : '<i class="fas fa-square text-muted"></i>'}</td>`;
      }
      const photos = (cell.photoIndexes || []).map(n =>
        `<img class="sp-photo" data-photo="${n}" data-sheet="${SAFETY.escapeHtml(detail.sheetName)}" alt="사진 ${n}">`).join('');
      const text = cell.text ? SAFETY.escapeHtml(cell.text) : '';
      return `<td>${text}${photos ? `<div class="sp-photos">${photos}</div>` : ''}</td>`;
    }).join('') + '</tr>').join('');

  document.getElementById('sheetPreviewBody').innerHTML = metaHtml + `
    <div class="sp-note">아래 내용 그대로 매뉴얼로 등록됩니다. 제목은 시트명(<b>${
      SAFETY.escapeHtml(detail.title)}</b>)이 됩니다.</div>
    <div class="table-responsive"><table class="sp-table">
      <thead>${head}</thead><tbody>${rows}</tbody></table></div>`;

  loadPreviewPhotos();
}

/** 화면에 들어온 사진만 그때그때 받아 온다 (시트에 60장이 든 파일도 있다) */
function loadPreviewPhotos() {
  const imgs = [...document.querySelectorAll('#sheetPreviewBody .sp-photo')];
  if (!imgs.length) return;

  const fetchOne = async (img) => {
    if (img.dataset.loaded) return;
    img.dataset.loaded = '1';
    try {
      const res = await fetch('/safety-api/excel-upload/preview-photo', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + SAFETY.getToken(), 'Content-Type': 'application/json' },
        body: JSON.stringify({
          uploadId: euUploadId, sheetName: img.dataset.sheet, photoIndex: Number(img.dataset.photo),
        }),
      });
      if (!res.ok) throw new Error('불러오기 실패');
      const url = URL.createObjectURL(await res.blob());
      sheetPreviewBlobs.push(url);
      img.src = url;
    } catch (e) {
      img.replaceWith(Object.assign(document.createElement('span'),
        { className: 'sp-photo-fail', textContent: '사진 없음' }));
    }
  };

  if (!('IntersectionObserver' in window)) { imgs.forEach(fetchOne); return; }
  const io = new IntersectionObserver((entries, obs) => {
    entries.filter(e => e.isIntersecting).forEach(e => { obs.unobserve(e.target); fetchOne(e.target); });
  }, { root: document.getElementById('sheetPreviewBody'), rootMargin: '200px' });
  imgs.forEach(img => io.observe(img));
}

function releaseSheetPreviewBlobs() {
  sheetPreviewBlobs.forEach(url => URL.revokeObjectURL(url));
  sheetPreviewBlobs = [];
}

function euToggleAll(box) {
  document.querySelectorAll('.eu-sheet-chk:not(:disabled)').forEach(c => { c.checked = box.checked; });
  euUpdateSummary();
}

async function euDoConfirm() {
  if (!euUploadId) { SAFETY.toast('엑셀 파일이 없습니다. 파일을 다시 선택해 주세요.', false); return; }

  const checked = Array.from(document.querySelectorAll('.eu-sheet-chk:checked'));
  if (!checked.length) { SAFETY.toast('가져올 시트를 하나 이상 선택하세요.', false); return; }

  const assignments = [];
  const missing = [];
  checked.forEach(c => {
    const idx = Number(c.dataset.idx);
    const categoryId = euCategoryOf(idx);
    if (categoryId == null) missing.push(euPreviewData[idx].sheetName);
    else assignments.push({ sheetName: euPreviewData[idx].sheetName, categoryId });
  });
  if (missing.length) {
    SAFETY.toast(`등록 분류가 지정되지 않은 시트가 있습니다: ${missing.join(', ')}`, false);
    return;
  }

  // 어느 분류에 몇 건이 들어가는지 확인시켜 준다 (시트마다 다를 수 있으므로)
  const countByCategory = {};
  assignments.forEach(a => { countByCategory[a.categoryId] = (countByCategory[a.categoryId] || 0) + 1; });
  const summary = Object.entries(countByCategory)
    .map(([id, count]) => `- ${pathOf(id).join(' > ')} : ${count}건`).join('\n');
  if (!confirm(`선택한 ${assignments.length}개 시트를 아래 분류에 등록합니다.\n\n${summary}\n\n진행할까요?`)) return;

  // 파일은 이미 서버에 올라가 있으므로 식별자와 시트별 분류만 보낸다
  const btn = document.getElementById('eu-confirm-btn');
  const label = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>등록하는 중...';
  try {
    const result = await SAFETY.api('/safety-api/excel-upload/confirm-staged',
      { method: 'POST', body: { uploadId: euUploadId, assignments } });
    euUploadId = null;   // 서버가 확정과 함께 임시 파일을 지웠다
    await refreshAll();
    showUploadResult(result);
  } catch (e) {
    SAFETY.toast(e.message, false);
  } finally {
    btn.disabled = false;
    btn.innerHTML = label;
  }
}

/** 업로드 결과를 별도 팝업으로 보여준다 (엑셀 모달 위에 겹쳐 뜬다) */
let uploadDoneModal = null;
function showUploadResult(result) {
  const imported = result.importedCount || 0;
  const manuals = result.manuals || [];
  const skipped = result.skipped || [];

  let html = `<div class="ud-count ${imported ? '' : 'none'}">
      <i class="fas ${imported ? 'fa-circle-check' : 'fa-circle-exclamation'}"></i>
      <span><span class="num">${imported}</span>건이 등록되었습니다.</span>
    </div>`;

  if (manuals.length) {
    html += `<div class="ud-section">
      <h6><i class="fas fa-file-lines"></i>등록된 매뉴얼</h6>
      <ul class="ud-list">${manuals.map(m => `<li>${SAFETY.escapeHtml(m.title)}
        <span class="ud-where">${SAFETY.escapeHtml(m.categoryPath || '')}</span></li>`).join('')}</ul>
    </div>`;
  }
  if (skipped.length) {
    html += `<div class="ud-section">
      <h6><i class="fas fa-circle-minus text-muted"></i>건너뛴 시트 ${skipped.length}건</h6>
      <ul class="ud-list skipped">${skipped.map(x => `<li>${SAFETY.escapeHtml(x)}</li>`).join('')}</ul>
    </div>`;
  }
  if (!manuals.length && !skipped.length) {
    html += '<div class="eu-note">가져온 시트가 없습니다. 시트 선택과 등록 분류를 확인해 주세요.</div>';
  }

  document.getElementById('uploadDoneBody').innerHTML = html;
  document.getElementById('eu-confirm-btn').classList.add('d-none');

  const el = document.getElementById('uploadDoneModal');
  if (!uploadDoneModal) {
    uploadDoneModal = new bootstrap.Modal(el);
    // 두 번째로 열리는 모달의 배경은 부트스트랩이 1050 으로 만들어서 업로드 창(1055) 아래에 깔린다.
    // 완료 팝업(1080) 바로 밑으로 올려야 업로드 창이 어두워지고 팝업이 앞에 있는 것으로 보인다.
    el.addEventListener('shown.bs.modal', () => {
      const backdrops = document.querySelectorAll('.modal-backdrop');
      if (backdrops.length > 1) backdrops[backdrops.length - 1].style.zIndex = '1075';
    });
  }
  uploadDoneModal.show();
}

/** 완료 팝업의 "확인" — 팝업과 업로드 창을 함께 닫는다 */
function closeUploadFlow() {
  if (uploadDoneModal) uploadDoneModal.hide();
  const excelModal = bootstrap.Modal.getInstance(document.getElementById('excelModal'));
  if (excelModal) excelModal.hide();
}
