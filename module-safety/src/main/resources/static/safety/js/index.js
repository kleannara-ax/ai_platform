/* 분류/매뉴얼 목록 + 상세 화면 로직 */
let categories = [];       // 트리 원본
let flatCategories = [];   // select box 용 평탄화 목록
let currentCategoryId = null;
let currentManualId = null;
let isAdminUser = false;

document.addEventListener('DOMContentLoaded', async () => {
  if (!SAFETY.requireAuth()) return;
  SAFETY.renderNav('index.html');
  isAdminUser = await SAFETY.isAdmin();
  if (isAdminUser) {
    document.getElementById('btn-add-category').classList.remove('d-none');
    document.getElementById('btn-add-manual').classList.remove('d-none');
    document.getElementById('btn-upload-link').classList.remove('d-none');
  }
  await loadTree();
});

async function loadTree() {
  try {
    categories = await SAFETY.api('/safety-api/categories');
    flatCategories = [];
    flatten(categories, 0);
    renderTree();
    fillParentSelect();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function flatten(nodes, depth) {
  (nodes || []).forEach(n => {
    flatCategories.push({ ...n, depth });
    if (n.children && n.children.length) flatten(n.children, depth + 1);
  });
}

function renderTree() {
  const holder = document.getElementById('categoryTree');
  if (!flatCategories.length) {
    holder.innerHTML = '<div class="p-3 text-muted small">등록된 분류가 없습니다.</div>';
    return;
  }
  holder.innerHTML = flatCategories.map(c => `
    <a href="#" class="list-group-item list-group-item-action py-2 small ${c.categoryId === currentCategoryId ? 'active' : ''}"
       style="padding-left:${14 + c.depth * 16}px" onclick="selectCategory(${c.categoryId}); return false;">
      ${c.depth > 0 ? '<i class="fas fa-angle-right me-1 text-muted"></i>' : '<i class="fas fa-folder me-1 text-warning"></i>'}${SAFETY.escapeHtml(c.name)}
    </a>`).join('');
}

function fillParentSelect() {
  const sel = document.getElementById('cat-parent');
  sel.innerHTML = '<option value="">(최상위)</option>' +
    flatCategories.map(c => `<option value="${c.categoryId}">${'　'.repeat(c.depth)}${SAFETY.escapeHtml(c.name)}</option>`).join('');
}

async function selectCategory(categoryId) {
  currentCategoryId = categoryId;
  renderTree();
  closeDetail();
  const cat = flatCategories.find(c => c.categoryId === categoryId);
  document.getElementById('manualListTitle').textContent = (cat ? cat.name : '') + ' - 매뉴얼 목록';
  try {
    const manuals = await SAFETY.api('/safety-api/categories/' + categoryId + '/manuals');
    renderManualList(manuals);
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderManualList(manuals) {
  const tbody = document.getElementById('manualRows');
  if (!manuals || !manuals.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">등록된 매뉴얼이 없습니다.</td></tr>';
    return;
  }
  tbody.innerHTML = manuals.map(m => `
    <tr>
      <td><a href="#" onclick="openDetail(${m.manualId}); return false;">${SAFETY.escapeHtml(m.title)}</a></td>
      <td class="small text-muted">${m.sourceSheetName ? '엑셀(' + SAFETY.escapeHtml(m.sourceSheetName) + ')' : '직접등록'}</td>
      <td class="small text-muted">${(m.updatedAt || '').replace('T', ' ').substring(0, 16)}</td>
      <td><button class="btn btn-sm btn-outline-secondary" onclick="openDetail(${m.manualId})"><i class="fas fa-eye"></i></button></td>
    </tr>`).join('');
}

async function openDetail(manualId) {
  currentManualId = manualId;
  document.getElementById('manualListPane').style.display = 'none';
  document.getElementById('manualDetailPane').style.display = '';
  if (isAdminUser) {
    document.getElementById('btn-add-step').classList.remove('d-none');
    document.getElementById('btn-delete-manual').classList.remove('d-none');
  }
  await loadDetail();
}

function closeDetail() {
  currentManualId = null;
  document.getElementById('manualListPane').style.display = '';
  document.getElementById('manualDetailPane').style.display = 'none';
}

async function loadDetail() {
  try {
    const d = await SAFETY.api('/safety-api/manuals/' + currentManualId);
    document.getElementById('detailTitle').textContent = d.title;
    document.getElementById('detailMeta').textContent =
      (d.categoryName ? '분류: ' + d.categoryName : '') + (d.sourceSheetName ? ' · 출처 시트: ' + d.sourceSheetName : '');
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
      <td class="text-center">${s.stepNo}</td>
      <td>${(s.photos || []).map(p => `<img src="${p.url}" style="max-width:100%;max-height:110px;display:block;margin-bottom:4px;border:1px solid #eee;border-radius:4px" alt="${SAFETY.escapeHtml(p.originalName)}">`).join('') || '<span class="text-muted small">-</span>'}</td>
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

// ---- 분류 등록 ----
function openCategoryModal() {
  document.getElementById('cat-name').value = '';
  document.getElementById('cat-parent').value = currentCategoryId || '';
  document.getElementById('cat-sort').value = 0;
  new bootstrap.Modal(document.getElementById('categoryModal')).show();
}

async function createCategory() {
  const name = document.getElementById('cat-name').value.trim();
  if (!name) { SAFETY.toast('분류명을 입력하세요.', false); return; }
  try {
    await SAFETY.api('/safety-api/categories', {
      method: 'POST',
      body: {
        name,
        parentId: document.getElementById('cat-parent').value || null,
        sortOrder: Number(document.getElementById('cat-sort').value) || 0,
      },
    });
    bootstrap.Modal.getInstance(document.getElementById('categoryModal')).hide();
    SAFETY.toast('분류가 등록되었습니다.');
    await loadTree();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ---- 매뉴얼 등록 ----
function openManualModal() {
  if (!currentCategoryId) { SAFETY.toast('먼저 좌측에서 분류를 선택하세요.', false); return; }
  document.getElementById('man-title').value = '';
  document.getElementById('man-sort').value = 0;
  new bootstrap.Modal(document.getElementById('manualModal')).show();
}

async function createManual() {
  const title = document.getElementById('man-title').value.trim();
  if (!title) { SAFETY.toast('매뉴얼 제목을 입력하세요.', false); return; }
  try {
    await SAFETY.api('/safety-api/manuals', {
      method: 'POST',
      body: {
        categoryId: currentCategoryId,
        title,
        sortOrder: Number(document.getElementById('man-sort').value) || 0,
        steps: [],
      },
    });
    bootstrap.Modal.getInstance(document.getElementById('manualModal')).hide();
    SAFETY.toast('매뉴얼이 등록되었습니다.');
    await selectCategory(currentCategoryId);
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
    await selectCategory(currentCategoryId);
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ---- 단계 등록/수정/삭제 ----
let editingStepId = null;

function openStepModal(stepId) {
  editingStepId = stepId || null;
  document.getElementById('stepModalTitle').textContent = stepId ? '단계 수정' : '단계 추가';
  document.getElementById('step-id').value = stepId || '';
  document.getElementById('step-photo-upload-wrap').style.display = stepId ? '' : 'none';
  if (!stepId) {
    document.getElementById('step-no').value = '';
    document.getElementById('step-sort').value = 0;
    document.getElementById('step-desc').value = '';
    document.getElementById('step-hazard').value = '';
    document.getElementById('step-equip').value = '';
    document.getElementById('step-remark').value = '';
  } else {
    // 현재 화면에 이미 로드된 값에서 채운다
    loadDetail().then(() => {}); // no-op, 값은 아래에서 별도 조회
    prefillStep(stepId);
  }
  document.getElementById('step-photo-file').value = '';
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
