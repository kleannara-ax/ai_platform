/**
 * 로그 업로드 창 (공통) — 대시보드 · 월별 점검 현황에서 같은 창을 띄운다.
 * 사용: SECLOG_UPLOAD.open({ ym: '202610', systemId: 12, onDone: (r) => {...} })
 * - ADMIN / MANAGER 만 사용 (SECLOG.canWrite()), 서버도 같은 권한으로 막음
 * - 필요: bootstrap.bundle.js, js/seclog.js
 */
(() => {
  const ALLOWED = ['xlsx', 'xls', 'csv', 'tsv', 'log', 'txt', 'json', 'out'];
  const st = { modal: null, file: null, onDone: null, ready: false };
  const $ = (id) => document.getElementById(id);

  const HTML = `
<div class="modal fade" id="uploadModal" tabindex="-1" aria-labelledby="umTitle" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-centered">
    <form class="modal-content" id="umForm" novalidate>
      <div class="modal-header">
        <h5 class="modal-title" id="umTitle"><i class="fas fa-upload me-2" style="color:var(--brand)"></i>로그 올리기</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="닫기"></button>
      </div>
      <div class="modal-body">
        <section id="umInput">
          <p class="month-ask" id="umAsk"></p>
          <p class="text-muted-m" style="font-size:.8rem">시스템의 한 달치 접속·행위 로그 파일을 올리면, 그 시스템에 적용된 룰셋(공통 + 전용)으로 바로 분석합니다. 같은 월·시스템에 같은 파일은 다시 올릴 수 없고, 다른 파일을 다시 올리면 <b>가장 최근 파일 1개만</b> 분석합니다.</p>
          <div class="row g-3">
            <div class="col-md-4"><label class="form-label" for="umYm">대상 연월<span class="req">*</span></label>
              <input type="month" class="form-control" id="umYm" required></div>
            <div class="col-md-4"><label class="form-label" for="umSystem">점검 대상 시스템<span class="req">*</span></label>
              <select class="form-select" id="umSystem" required></select></div>
            <div class="col-md-4"><label class="form-label" for="umLogType">로그 유형<span class="req">*</span></label>
              <select class="form-select" id="umLogType" required><option value="">선택</option></select></div>
            <div class="col-md-4"><label class="form-label" for="umCharset">파일 인코딩</label>
              <select class="form-select" id="umCharset"><option value="UTF-8">UTF-8 (기본)</option><option value="EUC-KR">EUC-KR</option><option value="MS949">MS949 (Windows 한글)</option></select></div>
            <div class="col-md-8"><label class="form-label" for="umRemark">비고</label>
              <input type="text" class="form-control" id="umRemark" maxlength="1000" placeholder="예: 10월 정기 점검분"></div>
            <div class="col-12">
              <label class="form-label">로그 파일<span class="req">*</span></label>
              <div class="dropzone" id="umDrop" tabindex="0" role="button" aria-label="로그 파일 선택">
                <i class="fas fa-cloud-arrow-up"></i>
                <div class="dz-title">파일을 끌어다 놓거나 클릭해서 선택하세요</div>
                <div class="dz-sub">.xlsx .xls .csv .tsv .log .txt .json .out · 텍스트 최대 50MB, 엑셀 최대 20MB</div>
              </div>
              <input type="file" id="umFile" accept=".xlsx,.xls,.csv,.tsv,.log,.txt,.json,.out" class="d-none">
              <div id="umFileInfo"></div>
            </div>
          </div>
        </section>
        <section id="umResult" class="hidden"></section>
      </div>
      <div class="modal-footer">
        <a href="upload.html" class="me-auto text-muted-m" style="font-size:.76rem" id="umFullPage">전체 화면에서 올리기 <i class="fas fa-arrow-up-right-from-square"></i></a>
        <button type="button" class="btn-m btn-gray-m" data-bs-dismiss="modal" id="umCancel">닫기</button>
        <button type="submit" class="btn-m btn-primary-m" id="umSubmit"><i class="fas fa-magnifying-glass-chart"></i>업로드 및 분석</button>
        <button type="button" class="btn-m btn-gray-m hidden" id="umAgain"><i class="fas fa-plus"></i>다른 파일 올리기</button>
        <a class="btn-m btn-primary-m hidden" id="umDetail"><i class="fas fa-arrow-right"></i>분석 결과 보기 / 검토</a>
      </div>
    </form>
  </div>
</div>`;

  function init() {
    if (st.ready) return;
    st.ready = true;
    document.body.insertAdjacentHTML('beforeend', HTML);
    st.modal = new bootstrap.Modal($('uploadModal'));
    SECLOG.LOG_TYPES.forEach(([v, l]) => $('umLogType').insertAdjacentHTML('beforeend', `<option value="${v}">${SECLOG.esc(l)}</option>`));
    $('umSystem').onchange = () => { const o = $('umSystem').selectedOptions[0]; if (o && o.dataset.logtype) $('umLogType').value = o.dataset.logtype; };
    const dz = $('umDrop');
    dz.onclick = () => $('umFile').click();
    dz.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); $('umFile').click(); } };
    $('umFile').onchange = (e) => setFile(e.target.files[0]);
    ['dragenter', 'dragover'].forEach(ev => dz.addEventListener(ev, (e) => { e.preventDefault(); dz.classList.add('drag'); }));
    ['dragleave', 'drop'].forEach(ev => dz.addEventListener(ev, (e) => { e.preventDefault(); dz.classList.remove('drag'); }));
    dz.addEventListener('drop', (e) => setFile(e.dataTransfer.files[0]));
    $('umForm').onsubmit = submit;
    $('umAgain').onclick = () => reset(true);
    $('umYm').addEventListener('change', askText);
  }

  function setFile(f) {
    if (!f) return;
    const ext = (f.name.split('.').pop() || '').toLowerCase();
    if (!ALLOWED.includes(ext)) return SECLOG.toast('허용되지 않는 확장자입니다: .' + ext, 'error');
    if (f.size === 0) return SECLOG.toast('빈 파일입니다.', 'error');
    if (f.size > 50 * 1024 * 1024) return SECLOG.toast('파일은 최대 50MB 까지 업로드할 수 있습니다.', 'error');
    if (['xlsx', 'xls'].includes(ext) && f.size > 20 * 1024 * 1024) return SECLOG.toast('엑셀은 최대 20MB 까지입니다. 더 크면 CSV 로 저장해 올려 주세요.', 'error');
    st.file = f;
    $('umFileInfo').innerHTML = `<div class="file-chip"><i class="fas fa-file-lines" style="color:var(--brand)"></i><b>${SECLOG.esc(f.name)}</b>
      <span class="text-muted-m">${SECLOG.fmtSize(f.size)}</span><button type="button" class="btn-m btn-danger-m btn-sm-m" id="umClear" aria-label="파일 빼기"><i class="fas fa-xmark"></i></button></div>`;
    $('umClear').onclick = () => { st.file = null; $('umFile').value = ''; $('umFileInfo').innerHTML = ''; };
  }

  function reset(keepSystem) {
    st.file = null; $('umFile').value = ''; $('umFileInfo').innerHTML = ''; $('umRemark').value = '';
    if (!keepSystem) $('umCharset').value = 'UTF-8';
    $('umInput').classList.remove('hidden'); $('umResult').classList.add('hidden');
    $('umSubmit').classList.remove('hidden'); $('umAgain').classList.add('hidden'); $('umDetail').classList.add('hidden');
  }

  function askText() {
    const v = $('umYm').value.replace('-', ''), ry = SECLOG.reviewYm();
    $('umAsk').innerHTML = v === ry ? `<i class="fas fa-bullhorn"></i> ${SECLOG.fmtYm(v)} 로그를 올려 주세요`
      : `<i class="fas fa-triangle-exclamation" style="color:#f97316"></i> ${SECLOG.fmtYm(v || ry)} 로그를 올립니다 <span class="text-muted-m" style="font-size:.78rem;font-weight:500">(이번 점검 대상은 ${SECLOG.fmtYm(ry)}입니다)</span>`;
  }

  /** 업로드 창 열기 */
  async function open({ ym, systemId = null, onDone = null } = {}) {
    if (!SECLOG.canWrite()) return SECLOG.toast('로그 올리기는 관리자·담당자만 할 수 있습니다.', 'error');
    init();
    st.onDone = onDone;
    reset(false);
    const y = ym || SECLOG.reviewYm();
    $('umYm').value = y.slice(0, 4) + '-' + y.slice(4);
    askText();
    try {
      await SECLOG.fillSystemSelect($('umSystem'), { selected: systemId, withDirect: false });
      // 점검 대상 시스템이 1개뿐이면 바로 선택
      const opts = [...$('umSystem').options].filter(o => o.value && o.value !== '__DIRECT__');
      if (!systemId && opts.length === 1) $('umSystem').value = opts[0].value;
      $('umSystem').dispatchEvent(new Event('change'));
    } catch (e) { return SECLOG.toast(e.message, 'error'); }
    $('umFullPage').href = `upload.html?ym=${y}${systemId ? '&systemId=' + encodeURIComponent(systemId) : ''}`;
    st.modal.show();
  }

  async function submit(e) {
    e.preventDefault();
    const ym = $('umYm').value.replace('-', '');
    if (!ym) return SECLOG.toast('대상 연월을 선택하세요.', 'error');
    if (!$('umSystem').value) return SECLOG.toast('점검 대상 시스템을 선택하세요.', 'error');
    if (!$('umLogType').value) return SECLOG.toast('로그 유형을 선택하세요.', 'error');
    if (!st.file) return SECLOG.toast('로그 파일을 선택하세요.', 'error');
    const fd = new FormData();
    fd.append('file', st.file); fd.append('targetYm', ym); fd.append('systemId', $('umSystem').value);
    fd.append('logType', $('umLogType').value); fd.append('fileCharset', $('umCharset').value);
    if ($('umRemark').value.trim()) fd.append('remark', $('umRemark').value.trim());
    const btn = $('umSubmit'), label = btn.innerHTML;
    btn.disabled = true; $('umCancel').disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>업로드 및 분석 중...';
    try {
      const r = await SECLOG.upload('/uploads', fd);
      const { esc, fmtYm, fmtNum, sevBadge } = SECLOG;
      $('umInput').classList.add('hidden'); $('umResult').classList.remove('hidden');
      $('umSubmit').classList.add('hidden'); $('umAgain').classList.remove('hidden');
      $('umDetail').classList.remove('hidden'); $('umDetail').href = 'detail.html?id=' + r.uploadId;
      $('umResult').innerHTML = `<div class="alert-m alert-info mb-3"><i class="fas fa-circle-check mt-1" style="color:#16a34a"></i>
          <div><b>${esc(r.systemName)}</b> · ${fmtYm(r.targetYm)} 로그 분석 완료<br><span class="mono">${esc(r.originalFileName)}</span></div></div>
        <div class="kpi-grid mb-0">
          <div class="kpi"><div class="k-label">분석 줄 수</div><div class="k-value">${fmtNum(r.totalLineCount)}</div></div>
          <div class="kpi"><div class="k-label">탐지</div><div class="k-value">${fmtNum(r.detectionCount)}</div><div class="k-sub">최고 위험도 ${sevBadge(r.maxSeverity)}</div></div>
          <div class="kpi k-critical"><div class="k-label">심각</div><div class="k-value">${r.criticalCount}</div></div>
          <div class="kpi k-high"><div class="k-label">높음</div><div class="k-value">${r.highCount}</div></div>
        </div>`;
      SECLOG.toast('업로드 및 분석이 완료되었습니다.');
      if (typeof st.onDone === 'function') await st.onDone(r);
    } catch (err) { SECLOG.toast(err.message, 'error'); }
    finally { btn.disabled = false; btn.innerHTML = label; $('umCancel').disabled = false; }
  }

  window.SECLOG_UPLOAD = { open };
})();
