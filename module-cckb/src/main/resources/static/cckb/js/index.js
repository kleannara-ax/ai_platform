/* =========================================================================
 * CCKB 후면 색상 검사 — 페이지 로직
 *
 * 원본 "CCKB 후면 색상 검사 시스템_V4.html" 의 단일 <script> 를 플랫폼 이관판으로 포팅.
 * localStorage 저장이었던 부분을 모두 /cckb-api/** 서버 호출로 교체했고, 색상 계산
 * (rgbToLab/averageLabFromImage/deltaE2000/classifyResult)은 서버(ColorMathUtil)로
 * 이전되어 이 파일에는 더 이상 없다. 화면 구조·ID·동작(탭 전환/재검사/페이지네이션/
 * 이미지 미리보기)은 원본과 100% 동일하게 유지한다.
 * ========================================================================= */

document.addEventListener('DOMContentLoaded', () => {
  if (!CCKB.requireAuth()) return;
  init();
});

// ===== 현재 활성 기준값 / 판정 임계값 (서버 /cckb-api/standards/active 에서 로드) =====
let REF_LAB = { L: 0, a: 0, b: 0 };
let THRESHOLDS = {
  normalDE: 0, normalL: 0, normalA: 0, normalB: 0,
  defectDE: 0, defectL: 0, defectA: 0, defectB: 0
};

// 판정 코드 → 화면 표시 클래스 매핑 (원본 classifyResult() 의 className/badgeClass 를 그대로 유지)
const JUDGMENT_CLASS = {
  NORMAL: { status: 'status-normal', badge: 'hist-badge-good' },
  BOUNDARY: { status: 'status-boundary', badge: 'hist-badge-warn' },
  DEFECT: { status: 'status-defect', badge: 'hist-badge-bad' }
};

function init() {
  // ===== 탭 전환 =====
  const tabButtons = document.querySelectorAll('.tab-btn');
  const tabPanels = {
    inspect: document.getElementById('tab-inspect'),
    history: document.getElementById('tab-history'),
    settings: document.getElementById('tab-settings')
  };
  function switchTab(name) {
    tabButtons.forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === name));
    Object.entries(tabPanels).forEach(([key, el]) => el.classList.toggle('active', key === name));
    if (name === 'history') renderHistoryList();
    if (name === 'settings') refreshSettingsUI();
  }
  tabButtons.forEach((btn) => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));
  window.__cckbSwitchTab = switchTab;

  // ===== 검사 이력 탭 =====
  document.getElementById('histSearchBtn').addEventListener('click', () => { historyCurrentPage = 0; renderHistoryList(); });
  document.getElementById('histResetBtn').addEventListener('click', () => {
    document.getElementById('histKeyword').value = '';
    document.getElementById('histFrom').value = '';
    document.getElementById('histTo').value = '';
    historyCurrentPage = 0;
    renderHistoryList();
  });
  document.getElementById('histKeyword').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { historyCurrentPage = 0; renderHistoryList(); }
  });
  document.getElementById('histList').addEventListener('click', (e) => {
    const btn = e.target.closest('.hist-reinspect-btn');
    if (!btn) return;
    const inspectionId = btn.dataset.inspectionid || '알수없음';
    startReinspect(inspectionId);
  });

  // ===== ⚙ 기준값 설정 탭 =====
  document.getElementById('saveRefBtn').addEventListener('click', onSaveSettings);

  // ===== 재검사 취소 =====
  document.getElementById('reinspectCancelBtn').addEventListener('click', cancelReinspect);

  // ===== 이미지 업로드/초기화/판정 실행 =====
  const galleryInput = document.getElementById('galleryInput');
  galleryInput.addEventListener('change', (e) => handleFile(e.target.files?.[0]));
  document.getElementById('resetImageBtn').addEventListener('click', () => {
    state.cmp.image = null;
    state.cmp.file = null;
    galleryInput.value = '';
    setText('uploadedFileInfo', '아직 업로드된 이미지가 없습니다.');
    resetGalleryUploadCard();
    renderPanel(state.cmp, '검사 사진을 업로드하세요');
    resetResultDisplay();
  });
  document.getElementById('analyzeBtn').addEventListener('click', analyze);

  renderPanel(state.cmp, '검사 사진을 업로드하세요');
  resetResultDisplay();

  // ===== 초기 데이터 로드 =====
  loadActiveStandard();
}

// ===== 이미지 패널 상태 (ROI 없음) =====
const state = {
  cmp: createPanelState('cmpCanvas')
};

function createPanelState(canvasId) {
  const canvas = document.getElementById(canvasId);
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const offscreen = document.createElement('canvas');
  const offctx = offscreen.getContext('2d', { willReadFrequently: true });
  return { canvas, ctx, offscreen, offctx, image: null, file: null, fit: null, fileName: '' };
}

function resetGalleryUploadCard() {
  document.getElementById('galleryUploadCard').innerHTML = `
    <div class="upload-icon">🗂️</div>
    <strong>이미지 업로드</strong>
    <span>저장된 이미지를 불러옵니다</span>
  `;
}

function handleFile(file) {
  if (!file || !file.type.startsWith('image/')) {
    alert('이미지 파일만 업로드할 수 있습니다.');
    return;
  }
  const panel = state.cmp;
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    panel.image = img;
    panel.file = file;
    panel.fileName = file.name;
    panel.offscreen.width = img.naturalWidth || img.width;
    panel.offscreen.height = img.naturalHeight || img.height;
    panel.offctx.clearRect(0, 0, panel.offscreen.width, panel.offscreen.height);
    panel.offctx.drawImage(img, 0, 0, panel.offscreen.width, panel.offscreen.height);
    renderPanel(panel);
    document.getElementById('galleryUploadCard').innerHTML =
      `<strong>${CCKB.escapeHtml(file.name)}</strong><span>${panel.offscreen.width} × ${panel.offscreen.height}px</span>`;
    setText('uploadedFileInfo', `${file.name} · ${panel.offscreen.width} × ${panel.offscreen.height}px`);
    resetResultDisplay();
  };
  img.onerror = () => alert('이미지를 불러오지 못했습니다.');
  img.src = url;
}

function renderPanel(panel, emptyMessage = '이미지를 업로드하세요') {
  const { canvas, ctx } = panel;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#0d1630';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  if (!panel.image) {
    drawCenterText(ctx, canvas, emptyMessage);
    panel.fit = null;
    return;
  }

  const fit = containRect(panel.offscreen.width, panel.offscreen.height, canvas.width, canvas.height, 22);
  panel.fit = fit;
  ctx.drawImage(panel.image, fit.x, fit.y, fit.w, fit.h);

  ctx.save();
  ctx.strokeStyle = 'rgba(255,255,255,0.18)';
  ctx.lineWidth = 1;
  ctx.strokeRect(fit.x, fit.y, fit.w, fit.h);
  ctx.restore();
}

function drawCenterText(ctx, canvas, message) {
  ctx.save();
  ctx.fillStyle = '#9fb0d9';
  ctx.font = '16px Inter, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(message, canvas.width / 2, canvas.height / 2);
  ctx.restore();
}

function containRect(srcW, srcH, dstW, dstH, padding = 0) {
  const aw = dstW - padding * 2;
  const ah = dstH - padding * 2;
  const scale = Math.min(aw / srcW, ah / srcH);
  const w = srcW * scale;
  const h = srcH * scale;
  return { x: (dstW - w) / 2, y: (dstH - h) / 2, w, h, scale };
}

function resetResultDisplay() {
  setText('refLabCard', '-');
  setText('cmpLabCard', '-');
  setText('deltaE00Card', '-');
  setText('statusCard', '대기 중');
  setText('statusDesc', '기준을 적용하면 정상/경계/불량이 표시됩니다');
  document.getElementById('statusStat').className = 'stat status-normal';
  ['deltaL', 'deltaA', 'deltaB'].forEach((id) => {
    const el = document.getElementById(id);
    el.textContent = '-';
    el.className = 'delta-value delta-zero';
  });
  ['refL', 'refA', 'refB', 'cmpL', 'cmpA', 'cmpB', 'diffL', 'diffA', 'diffB', 'diffE00', 'diffE76'].forEach((id) => setText(id, '-'));
  setText('interpL', '-'); setText('interpA', '-'); setText('interpB', '-');
  setText('interpE00', '최종 판정 기준');
  setText('interpE76', '참고용 직선 거리');
}

// ===== 재검사 상태 =====
// 검사 이력 탭에서 "재검사" 버튼을 누르면 대상 검사ID가 여기 담기고,
// 색상 검사 탭으로 이동해 새 판정을 실행하면 해당 기록의 재검사로 연결됩니다.
let reinspectTarget = null;
function startReinspect(inspectionId) {
  reinspectTarget = inspectionId;
  document.getElementById('reinspectTargetNo').textContent = inspectionId;
  document.getElementById('reinspectBanner').style.display = 'flex';
  window.__cckbSwitchTab('inspect');
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
function cancelReinspect() {
  reinspectTarget = null;
  document.getElementById('reinspectBanner').style.display = 'none';
}

// ===== 색상 검사 실행 (서버 /cckb-api/inspections/analyze 호출) =====
async function analyze() {
  if (!state.cmp.image || !state.cmp.file) {
    alert('검사 사진을 업로드해 주세요.');
    return;
  }
  const analyzeBtn = document.getElementById('analyzeBtn');
  analyzeBtn.disabled = true;
  analyzeBtn.textContent = '분석 중...';
  try {
    const record = await CCKB.uploadMultipart('/cckb-api/inspections/analyze', {
      file: state.cmp.file,
      originalInspectionId: reinspectTarget || undefined
    });
    renderAnalyzeResult(record);
    cancelReinspect();
    // 이력 탭을 다시 열 때 최신 데이터를 보도록 1페이지로 재설정
    historyCurrentPage = 0;
    if (document.getElementById('tab-history').classList.contains('active')) {
      renderHistoryList();
    }
  } catch (e) {
    CCKB.toast(e.message || '판정 실행 중 오류가 발생했습니다.', false);
  } finally {
    analyzeBtn.disabled = false;
    analyzeBtn.textContent = '판정 실행';
  }
}

function renderAnalyzeResult(r) {
  const refLab = { L: num(r.refL), a: num(r.refA), b: num(r.refB) };
  const cmpLab = { L: num(r.measuredL), a: num(r.measuredA), b: num(r.measuredB) };
  const dL = num(r.deltaL), dA = num(r.deltaA), dB = num(r.deltaB);
  const dE00 = num(r.deltaE00);
  const dE76 = Math.sqrt(dL * dL + dA * dA + dB * dB);
  const cls = JUDGMENT_CLASS[r.judgmentCode] || JUDGMENT_CLASS.NORMAL;
  const normalDE = num(r.normalDe00Max), defectDE = num(r.defectDe00Min);
  const normalL = num(r.normalDlMax), normalA = num(r.normalDaMax), normalB = num(r.normalDbMax);

  setText('refLabCard', formatLabLine(refLab));
  setText('cmpLabCard', formatLabLine(cmpLab));
  setText('deltaE00Card', dE00.toFixed(2));
  setText('statusCard', r.judgmentNameKr || r.judgmentCode);
  setText('statusDesc', r.judgmentReason || '');
  document.getElementById('statusStat').className = `stat ${cls.status}`;

  updateDeltaBlock('deltaL', 'deltaLDesc', dL, explainDelta('L', dL), normalL);
  updateDeltaBlock('deltaA', 'deltaADesc', dA, explainDelta('a', dA), normalA);
  updateDeltaBlock('deltaB', 'deltaBDesc', dB, explainDelta('b', dB), normalB);

  setText('refL', refLab.L.toFixed(2));
  setText('refA', refLab.a.toFixed(2));
  setText('refB', refLab.b.toFixed(2));
  setText('cmpL', cmpLab.L.toFixed(2));
  setText('cmpA', cmpLab.a.toFixed(2));
  setText('cmpB', cmpLab.b.toFixed(2));
  setText('diffL', signed(dL));
  setText('diffA', signed(dA));
  setText('diffB', signed(dB));
  setText('interpL', interpretChannel('L', dL));
  setText('interpA', interpretChannel('a', dA));
  setText('interpB', interpretChannel('b', dB));
  setText('diffE00', dE00.toFixed(2));
  setText('diffE76', dE76.toFixed(2));
  setText('interpE00', `${r.judgmentNameKr || r.judgmentCode} · 정상<${normalDE.toFixed(1)} / 불량>${defectDE.toFixed(1)}`);
  setText('interpE76', buildSummary(dL, dA, dB));
}

function explainDelta(channel, value) {
  if (channel === 'L') return value > 0 ? '검사 사진이 기준값보다 더 밝습니다.' : value < 0 ? '검사 사진이 기준값보다 더 어둡습니다.' : '밝기 차이가 거의 없습니다.';
  if (channel === 'a') return value > 0 ? '검사 사진이 기준값보다 더 붉은 방향입니다.' : value < 0 ? '검사 사진이 기준값보다 더 녹색 방향입니다.' : '적-녹 축 차이가 거의 없습니다.';
  return value > 0 ? '검사 사진이 기준값보다 더 노란 방향입니다.' : value < 0 ? '검사 사진이 기준값보다 더 파란 방향입니다.' : '청-황 축 차이가 거의 없습니다.';
}

function interpretChannel(channel, value) {
  if (Math.abs(value) < 0.5) return '거의 동일';
  if (channel === 'L') return value > 0 ? '기준보다 밝음' : '기준보다 어두움';
  if (channel === 'a') return value > 0 ? '기준보다 붉음' : '기준보다 녹색';
  return value > 0 ? '기준보다 노랑' : '기준보다 파랑';
}

function updateDeltaBlock(valueId, descId, value, desc, normalLimit) {
  const el = document.getElementById(valueId);
  el.textContent = signed(value);
  const isExceeded = Math.abs(value) > normalLimit;
  el.className = 'delta-value ' + (isExceeded ? 'delta-alert' : 'delta-zero');
  setText(descId, desc);
}

function buildSummary(dL, dA, dB) {
  return [
    Math.abs(dL) < 0.5 ? '밝기 유사' : dL > 0 ? '더 밝음' : '더 어두움',
    Math.abs(dA) < 0.5 ? '적록 유사' : dA > 0 ? '더 붉음' : '더 녹색',
    Math.abs(dB) < 0.5 ? '청황 유사' : dB > 0 ? '더 노랑' : '더 파랑'
  ].join(' · ');
}

function formatLabLine(lab) {
  return `${lab.L.toFixed(1)} / ${lab.a.toFixed(1)} / ${lab.b.toFixed(1)}`;
}

function signed(v) { return `${v >= 0 ? '+' : ''}${v.toFixed(2)}`; }
function setText(id, value) { document.getElementById(id).textContent = value; }
function num(v) { return v === null || v === undefined ? 0 : Number(v); }

// ===== 검사 이력 탭 (서버 페이지네이션, HISTORY_PAGE_SIZE=10) =====
const HISTORY_PAGE_SIZE = 10;
let historyCurrentPage = 0; // 0-base (서버 Pageable 과 동일)
let historyActiveBlobUrls = [];

async function renderHistoryList() {
  const container = document.getElementById('histList');
  const empty = document.getElementById('histEmpty');
  const paginationEl = document.getElementById('histPagination');

  const keyword = document.getElementById('histKeyword').value.trim();
  const from = document.getElementById('histFrom').value;
  const to = document.getElementById('histTo').value;

  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  if (from) params.set('from', `${from}T00:00:00`);
  if (to) params.set('to', `${to}T23:59:59`);
  params.set('page', String(historyCurrentPage));
  params.set('size', String(HISTORY_PAGE_SIZE));

  let pageData;
  try {
    pageData = await CCKB.api('/cckb-api/inspections?' + params.toString());
  } catch (e) {
    CCKB.toast(e.message || '검사 이력을 불러오지 못했습니다.', false);
    return;
  }

  // 이전에 만든 object URL 정리 (메모리 누수 방지)
  historyActiveBlobUrls.forEach((u) => URL.revokeObjectURL(u));
  historyActiveBlobUrls = [];

  const list = (pageData && pageData.content) || [];
  if (!list.length) {
    container.innerHTML = '';
    paginationEl.innerHTML = '';
    empty.style.display = 'block';
    return;
  }
  empty.style.display = 'none';

  container.innerHTML = list.map(renderHistoryCard).join('');
  renderHistoryPagination(pageData);
  loadHistoryThumbnails(list);
}

function renderHistoryPagination(pageData) {
  const paginationEl = document.getElementById('histPagination');
  const totalCount = pageData.totalElements || 0;
  const totalPages = Math.max(1, pageData.totalPages || 1);
  const currentDisplay = (pageData.page || 0) + 1;

  if (totalPages <= 1) {
    paginationEl.innerHTML = `<span class="pagination-info">전체 ${totalCount}건</span>`;
    return;
  }
  let html = '';
  html += `<button data-page="prev" ${pageData.first ? 'disabled' : ''}>이전</button>`;
  for (let p = 1; p <= totalPages; p++) {
    html += `<button data-page="${p}" class="${p === currentDisplay ? 'active' : ''}">${p}</button>`;
  }
  html += `<button data-page="next" ${pageData.last ? 'disabled' : ''}>다음</button>`;
  html += `<span class="pagination-info">전체 ${totalCount}건 · ${currentDisplay}/${totalPages}페이지</span>`;
  paginationEl.innerHTML = html;
  paginationEl.querySelectorAll('button[data-page]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const p = btn.dataset.page;
      if (p === 'prev') historyCurrentPage -= 1;
      else if (p === 'next') historyCurrentPage += 1;
      else historyCurrentPage = parseInt(p, 10) - 1;
      renderHistoryList();
      document.getElementById('tab-history').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });
}

function renderHistoryCard(r) {
  const cls = JUDGMENT_CLASS[r.judgmentCode] || JUDGMENT_CLASS.NORMAL;
  const dateStr = r.inspectionDatetime ? new Date(r.inspectionDatetime).toLocaleString('ko-KR') : '-';
  const refLab = { L: num(r.refL), a: num(r.refA), b: num(r.refB) };
  const cmpLab = { L: num(r.measuredL), a: num(r.measuredA), b: num(r.measuredB) };
  const normalDE = num(r.normalDe00Max), defectDE = num(r.defectDe00Min);
  const cmpImage = (r.images || []).find((im) => im.imageType === 'CMP') || (r.images || [])[0];
  const firstFileName = cmpImage ? cmpImage.fileName : '-';
  const reinspectTag = r.reinspected
    ? `<span class="hist-reinspect-tag">재검사 (원검사: ${CCKB.escapeHtml(r.originalInspectionId || '-')})</span>`
    : '';
  return `
    <div class="hist-card" data-inspection-id="${CCKB.escapeHtml(r.inspectionId)}">
      <div class="hist-media">
        <div class="hist-media-col">
          <div class="hist-thumb" data-thumb-for="${CCKB.escapeHtml(r.inspectionId)}" data-file-url="${cmpImage ? CCKB.escapeHtml(cmpImage.fileUrl) : ''}"></div>
          <div class="hist-media-label">${CCKB.escapeHtml(firstFileName || '-')}</div>
        </div>
      </div>
      <div class="hist-body">
        <div class="hist-header">
          <span class="hist-title">${CCKB.escapeHtml(r.inspectionId || '-')}${reinspectTag}</span>
          <span class="hist-badge ${cls.badge}">${CCKB.escapeHtml(r.judgmentNameKr || r.judgmentCode)}</span>
        </div>
        <div class="hist-row"><span>기준 LAB 값:</span>${formatLabLine(refLab)}</div>
        <div class="hist-row"><span>측정 LAB 값:</span>${formatLabLine(cmpLab)}</div>
        <div class="hist-row hist-metric"><span>ΔE00 (색차):</span><strong>${num(r.deltaE00).toFixed(2)}</strong></div>
        <div class="hist-row"><span>ΔL*/Δa*/Δb*:</span>${signed(num(r.deltaL))} / ${signed(num(r.deltaA))} / ${signed(num(r.deltaB))}</div>
        <div class="hist-row"><span>판정 기준:</span>정상 &lt; ${normalDE.toFixed(1)} · 불량 &gt; ${defectDE.toFixed(1)}</div>
        <div class="hist-row"><span>판정 사유:</span>${CCKB.escapeHtml(r.judgmentReason || '-')}</div>
        <div class="hist-row hist-time">${dateStr}</div>
        <div class="hist-actions">
          <button class="ghost hist-reinspect-btn" data-inspectionid="${CCKB.escapeHtml(r.inspectionId || '')}">🔁 재검사</button>
        </div>
      </div>
    </div>
  `;
}

/** 이력 카드 썸네일을 인증된 fetch()+blob 으로 비동기 로드한다 (dailyreport 패턴). */
function loadHistoryThumbnails(list) {
  document.querySelectorAll('.hist-thumb[data-file-url]').forEach((el) => {
    const fileUrl = el.dataset.fileUrl;
    if (!fileUrl) return;
    CCKB.loadImageBlob(fileUrl).then((objectUrl) => {
      historyActiveBlobUrls.push(objectUrl);
      const img = document.createElement('img');
      img.className = 'hist-thumb';
      img.alt = '검사 사진';
      img.src = objectUrl;
      el.replaceWith(img);
    }).catch((e) => {
      console.warn('썸네일 로드 실패', fileUrl, e);
    });
  });
}

// ===== ⚙ 기준값 설정 탭 =====
const SETTINGS_FIELD_IDS = ['setRefL', 'setRefA', 'setRefB', 'setNormalDE', 'setNormalL', 'setNormalA', 'setNormalB', 'setDefectDE', 'setDefectL', 'setDefectA', 'setDefectB'];
let cachedIsAdmin = false;

async function loadActiveStandard() {
  try {
    const standard = await CCKB.api('/cckb-api/standards/active');
    applyStandardToState(standard);
  } catch (e) {
    CCKB.toast(e.message || '기준값을 불러오지 못했습니다.', false);
  }
}

function applyStandardToState(standard) {
  REF_LAB = { L: num(standard.refL), a: num(standard.refA), b: num(standard.refB) };
  THRESHOLDS = {
    normalDE: num(standard.normalDe00Max), normalL: num(standard.normalDlMax),
    normalA: num(standard.normalDaMax), normalB: num(standard.normalDbMax),
    defectDE: num(standard.defectDe00Min), defectL: num(standard.defectDlMax),
    defectA: num(standard.defectDaMax), defectB: num(standard.defectDbMax)
  };
  applyRefLabToUI();
  applyThresholdsToUI();
}

function applyRefLabToUI() {
  const text = `L* ${REF_LAB.L.toFixed(1)} / a* ${REF_LAB.a.toFixed(1)} / b* ${REF_LAB.b.toFixed(1)}`;
  setText('heroRefVal', text);
  setText('refBoxL', REF_LAB.L.toFixed(1));
  setText('refBoxA', REF_LAB.a.toFixed(1));
  setText('refBoxB', REF_LAB.b.toFixed(1));
  setText('refLabCardSub', `L*, a*, b* (${REF_LAB.L.toFixed(1)} / ${REF_LAB.a.toFixed(1)} / ${REF_LAB.b.toFixed(1)})`);
  const setL = document.getElementById('setRefL');
  const setA = document.getElementById('setRefA');
  const setB = document.getElementById('setRefB');
  if (setL && document.activeElement !== setL) setL.value = REF_LAB.L;
  if (setA && document.activeElement !== setA) setA.value = REF_LAB.a;
  if (setB && document.activeElement !== setB) setB.value = REF_LAB.b;
}

function applyThresholdsToUI() {
  setText('infoNormalDE', `< ${THRESHOLDS.normalDE.toFixed(1)}`);
  setText('infoNormalL', `≤ ${THRESHOLDS.normalL.toFixed(1)}`);
  setText('infoNormalA', `≤ ${THRESHOLDS.normalA.toFixed(1)}`);
  setText('infoNormalB', `≤ ${THRESHOLDS.normalB.toFixed(1)}`);
  setText('infoDefectDE', `> ${THRESHOLDS.defectDE.toFixed(1)}`);
  setText('infoDefectL', `> ${THRESHOLDS.defectL.toFixed(1)}`);
  setText('infoDefectA', `> ${THRESHOLDS.defectA.toFixed(1)}`);
  setText('infoDefectB', `> ${THRESHOLDS.defectB.toFixed(1)}`);
  const fieldMap = {
    setNormalDE: THRESHOLDS.normalDE, setNormalL: THRESHOLDS.normalL, setNormalA: THRESHOLDS.normalA, setNormalB: THRESHOLDS.normalB,
    setDefectDE: THRESHOLDS.defectDE, setDefectL: THRESHOLDS.defectL, setDefectA: THRESHOLDS.defectA, setDefectB: THRESHOLDS.defectB
  };
  Object.entries(fieldMap).forEach(([id, value]) => {
    const el = document.getElementById(id);
    if (el && document.activeElement !== el) el.value = value;
  });
}

async function refreshSettingsUI() {
  const badge = document.getElementById('curUserBadge');
  badge.textContent = '권한 확인 중...';
  badge.className = 'role-badge role-readonly';

  cachedIsAdmin = await CCKB.isAdmin();
  badge.textContent = cachedIsAdmin ? '수정 가능 (CCKB_PERM 관리자)' : '조회 전용';
  badge.className = 'role-badge ' + (cachedIsAdmin ? 'role-edit' : 'role-readonly');
  SETTINGS_FIELD_IDS.forEach((fid) => {
    document.getElementById(fid).disabled = !cachedIsAdmin;
  });
  document.getElementById('saveRefBtn').disabled = !cachedIsAdmin;

  // 설정 탭을 열 때마다 최신값으로 새로고침 (다른 사용자가 바꿨을 수 있으므로)
  await loadActiveStandard();
}

async function onSaveSettings() {
  if (!cachedIsAdmin) {
    alert('기준값 수정 권한이 없습니다.');
    return;
  }
  const L = parseFloat(document.getElementById('setRefL').value);
  const a = parseFloat(document.getElementById('setRefA').value);
  const b = parseFloat(document.getElementById('setRefB').value);
  const normalDE = parseFloat(document.getElementById('setNormalDE').value);
  const normalL = parseFloat(document.getElementById('setNormalL').value);
  const normalA = parseFloat(document.getElementById('setNormalA').value);
  const normalB = parseFloat(document.getElementById('setNormalB').value);
  const defectDE = parseFloat(document.getElementById('setDefectDE').value);
  const defectL = parseFloat(document.getElementById('setDefectL').value);
  const defectA = parseFloat(document.getElementById('setDefectA').value);
  const defectB = parseFloat(document.getElementById('setDefectB').value);
  const allValues = [L, a, b, normalDE, normalL, normalA, normalB, defectDE, defectL, defectA, defectB];
  if (allValues.some((v) => Number.isNaN(v))) {
    alert('모든 값을 올바르게 입력해 주세요.');
    return;
  }

  const saveBtn = document.getElementById('saveRefBtn');
  saveBtn.disabled = true;
  try {
    const standard = await CCKB.api('/cckb-api/standards/active', {
      method: 'PUT',
      body: {
        refL: L, refA: a, refB: b,
        normalDe00Max: normalDE, normalDlMax: normalL, normalDaMax: normalA, normalDbMax: normalB,
        defectDe00Min: defectDE, defectDlMax: defectL, defectDaMax: defectA, defectDbMax: defectB
      }
    });
    applyStandardToState(standard);
    alert('기준값 및 판정 조건이 저장되었습니다.');
  } catch (e) {
    alert(e.message || '기준값 저장 중 오류가 발생했습니다.');
  } finally {
    saveBtn.disabled = !cachedIsAdmin;
  }
}
