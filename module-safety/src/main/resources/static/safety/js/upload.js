/* 엑셀 일괄업로드: 1단계(형식 확인/미리보기) → 2단계(선택 확정 업로드) */
let previewData = [];

document.addEventListener('DOMContentLoaded', async () => {
  if (!SAFETY.requireAuth()) return;
  SAFETY.renderNav('upload.html');
  const isAdmin = await SAFETY.isAdmin();
  if (!isAdmin) {
    document.querySelector('.container-fluid').innerHTML =
      '<div class="alert alert-danger mt-3">이 화면은 안전매뉴얼 관리자만 사용할 수 있습니다.</div>';
    return;
  }
  await loadCategories();
});

async function loadCategories() {
  try {
    const tree = await SAFETY.api('/safety-api/categories');
    const flat = [];
    const walk = (nodes, depth) => (nodes || []).forEach(n => { flat.push({ ...n, depth }); walk(n.children, depth + 1); });
    walk(tree, 0);
    const sel = document.getElementById('up-category');
    if (!flat.length) {
      sel.innerHTML = '<option value="">(등록된 분류가 없습니다 - 먼저 분류를 등록하세요)</option>';
      return;
    }
    sel.innerHTML = flat.map(c => `<option value="${c.categoryId}">${'　'.repeat(c.depth)}${SAFETY.escapeHtml(c.name)}</option>`).join('');
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

// ================================================================
// 1단계: 형식 확인 (DB 변경 없음)
// ================================================================
async function doPreview() {
  const file = document.getElementById('up-file').files[0];
  if (!file) { SAFETY.toast('엑셀 파일을 선택하세요.', false); return; }
  document.getElementById('resultCard').style.display = 'none';
  try {
    previewData = await SAFETY.uploadMultipart('/safety-api/excel-upload/preview', { file });
    renderPreview();
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderPreview() {
  const card = document.getElementById('previewCard');
  card.style.display = '';
  const recognizedCount = previewData.filter(s => s.recognized).length;
  document.getElementById('previewSummary').textContent =
    `총 ${previewData.length}개 시트 중 ${recognizedCount}개 매뉴얼로 인식됨`;

  document.getElementById('previewRows').innerHTML = previewData.map((s, idx) => `
    <tr class="${s.recognized ? '' : 'table-secondary text-muted'}">
      <td><input type="checkbox" class="sheet-chk" data-idx="${idx}" ${s.selected ? 'checked' : ''} ${s.recognized ? '' : 'disabled'}></td>
      <td>${SAFETY.escapeHtml(s.sheetName)}</td>
      <td>${s.recognized
        ? '<span class="badge bg-success">인식됨</span>'
        : `<span class="badge bg-secondary">제외</span><div class="small text-muted mt-1">${SAFETY.escapeHtml(s.reason || '')}</div>`}</td>
      <td>${SAFETY.escapeHtml(s.detectedTitle || '')}</td>
      <td class="text-center">${s.stepCount}</td>
      <td class="text-center">${s.photoCount}</td>
      <td class="small">${(s.stepPreviewLines || []).map(l => SAFETY.escapeHtml(l)).join('<br>')}</td>
    </tr>`).join('');
}

function toggleAll(box) {
  document.querySelectorAll('.sheet-chk:not(:disabled)').forEach(c => { c.checked = box.checked; });
}

// ================================================================
// 2단계: 확정 업로드 (선택된 시트만 실제 저장)
// ================================================================
async function doConfirm() {
  const categoryId = document.getElementById('up-category').value;
  if (!categoryId) { SAFETY.toast('등록할 분류를 선택하세요.', false); return; }
  const file = document.getElementById('up-file').files[0];
  if (!file) { SAFETY.toast('엑셀 파일이 없습니다. 다시 선택 후 형식 확인을 눌러주세요.', false); return; }

  const selectedNames = Array.from(document.querySelectorAll('.sheet-chk:checked'))
    .map(c => previewData[Number(c.dataset.idx)].sheetName);
  if (!selectedNames.length) { SAFETY.toast('가져올 시트를 하나 이상 선택하세요.', false); return; }

  if (!confirm(`선택한 ${selectedNames.length}개 시트를 매뉴얼로 등록하시겠습니까?`)) return;

  try {
    const result = await SAFETY.uploadMultipart('/safety-api/excel-upload/confirm', {
      file, categoryId, sheetNames: selectedNames.join(','),
    });
    renderResult(result);
    SAFETY.toast(result.importedCount + '개 매뉴얼이 등록되었습니다.');
  } catch (e) {
    SAFETY.toast(e.message, false);
  }
}

function renderResult(result) {
  const card = document.getElementById('resultCard');
  card.style.display = '';
  let html = `<p class="mb-2">총 <b>${result.importedCount}</b>건 등록됨</p>`;
  if (result.manuals && result.manuals.length) {
    html += '<ul class="mb-2">' + result.manuals.map(m => `<li>${SAFETY.escapeHtml(m.title)} (시트: ${SAFETY.escapeHtml(m.sourceSheetName || '')})</li>`).join('') + '</ul>';
  }
  if (result.skipped && result.skipped.length) {
    html += '<div class="text-muted small">건너뜀:</div><ul class="small text-muted">' +
      result.skipped.map(s => `<li>${SAFETY.escapeHtml(s)}</li>`).join('') + '</ul>';
  }
  html += '<a class="btn btn-outline-primary btn-sm mt-1" href="index.html">분류/매뉴얼 목록으로 이동</a>';
  document.getElementById('resultBody').innerHTML = html;
}
