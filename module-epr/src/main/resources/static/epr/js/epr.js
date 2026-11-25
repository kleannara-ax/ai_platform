/* =========================================================================
 * EPR 포장재 중량 신고 — 화면 스크립트 (무빌드 순수 JS)
 *  - 로그인은 플랫폼 계정. 플랫폼 SPA 가 localStorage.fireweb_user 에 넣어 둔 JWT 를 그대로 쓴다.
 *    (module-kims 의 kims.js, module-cckb 의 cckb.js 와 같은 방식)
 *  - 데이터는 모두 /epr-api/** 에서 받는다. 응답은 플랫폼 ApiResponse { success, message, data }.
 *  - 엑셀 내려받기도 인증이 필요해서 <a href> 대신 fetch → blob 으로 받는다.
 * ========================================================================= */
(() => {
  // ───────────── 공통 ─────────────
  const number = (value, digits = 2) => new Intl.NumberFormat('ko-KR', { useGrouping: true, minimumFractionDigits: digits, maximumFractionDigits: digits }).format(Number(value ?? 0));
  const integer = (value) => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 }).format(Number(value ?? 0));
  const delta = (value, digits = 2) => value === null || value === undefined ? '—' : `${Number(value) > 0 ? '+' : ''}${number(value, digits)}`;
  const safe = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const plain = (value) => (value === null || value === undefined ? '' : String(value));
  const dateTime = (value) => (value ? new Date(value).toLocaleString('ko-KR') : '');
  const currentYear = new Date().getFullYear();

  // ───────────── 플랫폼 토큰 ─────────────
  // access token 은 1시간이면 끝난다. 플랫폼 화면은 자기 요청이 401 을 받을 때만 갱신하므로,
  // EPR 화면에만 오래 머물면 토큰이 끝난 채로 남는다. 그래서 401 이면 여기서 refresh token 으로 한 번 갱신하고 다시 보낸다.
  // (스팀 모듈 platform-iframe-compat.js 와 같은 방식. 갱신한 토큰은 플랫폼과 같은 저장소에 다시 넣어 둘이 같이 쓴다.)
  function readJson(storage, key) {
    try { return JSON.parse(storage.getItem(key) || 'null') || {}; } catch (e) { return {}; }
  }

  function platformToken() {
    return readJson(localStorage, 'fireweb_user').token || readJson(sessionStorage, 'auth').token || '';
  }

  function refreshTokenValue() {
    const auth = readJson(sessionStorage, 'auth');
    const user = readJson(localStorage, 'fireweb_user');
    return auth.refreshTk || auth.refreshToken || user.refreshTk || user.refreshToken || '';
  }

  function saveTokens(accessToken, refreshToken) {
    try {
      const auth = readJson(sessionStorage, 'auth');
      if (auth.token) {
        auth.token = accessToken;
        if (refreshToken) auth.refreshTk = refreshToken;
        sessionStorage.setItem('auth', JSON.stringify(auth));
      }
      const user = readJson(localStorage, 'fireweb_user');
      if (user.token) {
        user.token = accessToken;
        localStorage.setItem('fireweb_user', JSON.stringify(user));
      }
    } catch (e) { /* 저장 실패는 무시 — 이번 요청은 새 토큰으로 다시 보낸다 */ }
  }

  let refreshing = null;
  function refreshAccessToken() {
    if (refreshing) return refreshing;
    const refreshToken = refreshTokenValue();
    if (!refreshToken) return Promise.resolve(false);
    refreshing = fetch('/api/auth/refresh', { method: 'POST', headers: { 'X-Refresh-Token': refreshToken }, cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : null))
      .then((json) => {
        const data = json && json.success ? json.data : null;
        if (!data || !data.accessToken) return false;
        saveTokens(data.accessToken, data.refreshToken);
        return true;
      })
      .catch(() => false)
      .finally(() => { refreshing = null; });
    return refreshing;
  }

  /** 로그인 세션이 끝났다 — 플랫폼 화면에 알려 로그인 화면으로 보낸다 (소방·스팀 모듈과 같은 신호). */
  function sessionExpired() {
    try { if (window.parent && window.parent !== window) window.parent.postMessage({ type: 'FIRE_AUTH_EXPIRED' }, '*'); }
    catch (e) { /* 단독으로 연 경우 */ }
  }

  function authHeaders(extra) {
    const token = platformToken();
    return Object.assign(token ? { Authorization: 'Bearer ' + token } : {}, extra || {});
  }

  /** 인증 헤더를 붙여 보내고, 401 이면 토큰을 한 번 갱신해 다시 보낸다. */
  async function authFetch(path, init = {}) {
    const send = () => fetch(path, Object.assign({}, init, { headers: authHeaders(init.headers), cache: 'no-store' }));
    const response = await send();
    if (response.status !== 401) return response;
    if (!(await refreshAccessToken())) { sessionExpired(); return response; }
    const retried = await send();
    if (retried.status === 401) sessionExpired();
    return retried;
  }

  class ApiError extends Error {
    constructor(message, status) { super(message); this.status = status; }
  }

  async function readError(response) {
    let json = null;
    try { json = await response.json(); } catch (e) { /* 본문 없음 */ }
    if (response.status === 401) return new ApiError('로그인이 만료되었습니다. 플랫폼에서 다시 로그인하세요.', 401);
    if (response.status === 403) return new ApiError('EPR 메뉴 사용 권한이 없습니다. 관리자에게 접근 권한을 요청하세요.', 403);
    if (response.status === 413) return new ApiError('파일이 너무 커서 서버가 받지 못했습니다. (20MB 이하)', 413);
    // 입력 검증 오류는 칸별 메시지가 data 에 온다 — 일반 문구 대신 그 내용을 보여 준다.
    if (json && json.data && typeof json.data === 'object' && !Array.isArray(json.data)) {
      const fieldMessages = Object.values(json.data).filter((value) => typeof value === 'string');
      if (fieldMessages.length) return new ApiError(fieldMessages.join(' / '), response.status);
    }
    return new ApiError((json && json.message) || `요청을 처리하지 못했습니다. (HTTP ${response.status})`, response.status);
  }

  /** ApiResponse 를 풀어 data 를 돌려준다. body 가 FormData 면 그대로, 객체면 JSON 으로 보낸다. */
  async function api(path, { method = 'GET', body } = {}) {
    const isForm = body instanceof FormData;
    const response = await authFetch(path, {
      method,
      headers: body !== undefined && !isForm ? { 'Content-Type': 'application/json; charset=utf-8' } : null,
      body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body)),
    });
    if (!response.ok) throw await readError(response);
    let json = null;
    try { json = await response.json(); } catch (e) { /* 본문 없음 */ }
    if (json && json.success === false) throw new ApiError(json.message || '요청을 처리하지 못했습니다.', response.status);
    return json ? json.data : null;
  }

  /** 인증이 필요한 엑셀 내려받기 */
  async function download(path, button) {
    const oldHtml = button ? button.innerHTML : '';
    if (button) { button.disabled = true; button.textContent = '내려받는 중…'; }
    try {
      const response = await authFetch(path);
      if (!response.ok) throw await readError(response);
      const disposition = response.headers.get('Content-Disposition') || '';
      const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition) || /filename="?([^";]+)"?/i.exec(disposition);
      const filename = match ? decodeURIComponent(match[1]) : 'epr.xlsx';
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      showAlert('page-alert', 'danger', error.message);
    } finally {
      if (button) { button.disabled = false; button.innerHTML = oldHtml; }
    }
  }

  function showAlert(id, kind, message) {
    const box = document.getElementById(id);
    if (!box) { window.alert(message); return; }
    if (!message) { box.className = 'alert d-none'; box.textContent = ''; return; }
    box.className = `alert alert-${kind}`;
    box.textContent = message;
  }

  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-download]');
    if (!button) return;
    event.preventDefault();
    download(button.dataset.download, button);
  });

  // ───────────── 탭 (주소의 ?tab= 유지) ─────────────
  function selectInitialTab() {
    const params = new URLSearchParams(window.location.search);
    const wanted = ['sales', 'master', 'parts'].includes(params.get('tab')) ? params.get('tab') : 'sales';
    const button = document.querySelector(`[data-tab="${wanted}"]`);
    if (button && window.bootstrap) window.bootstrap.Tab.getOrCreateInstance(button).show();
    else if (button) {
      button.classList.add('active');
      document.querySelector(button.dataset.bsTarget)?.classList.add('show', 'active');
    }
    document.querySelectorAll('[data-tab]').forEach((tabButton) => tabButton.addEventListener('shown.bs.tab', () => {
      const url = new URL(window.location.href);
      url.searchParams.set('tab', tabButton.dataset.tab);
      window.history.replaceState({}, '', url);
    }));
  }

  // ───────────── 요약 · 파트 목록 ─────────────
  let parts = [];

  async function loadOverview() {
    const overview = await api('/epr-api/products/overview');
    document.getElementById('product-count').textContent = integer(overview.productCount);
    document.getElementById('master-count').textContent = integer(overview.productCount);
  }

  async function loadParts() {
    parts = await api('/epr-api/parts');
    renderParts();
    renderPartFilter();
  }

  function renderPartFilter() {
    const select = document.getElementById('master-part-filter');
    const selected = select.value;
    select.innerHTML = '<option value="">전체 파트</option>' + parts
      .map((part) => `<option value="${safe(part.partPrefix)}">${safe(part.partPrefix)} · ${safe(part.partName)}</option>`).join('');
    if (parts.some((part) => part.partPrefix === selected)) select.value = selected;
  }

  function renderParts() {
    const body = document.getElementById('parts-body');
    body.innerHTML = parts.length ? parts.map((part) => `<tr><td><span class="code-pill">${safe(part.partPrefix)}</span></td><td>${safe(part.partName)}</td><td class="text-end">${integer(part.skuCount)}</td><td class="text-end"><button class="btn btn-sm btn-outline-danger" type="button" data-delete-part="${safe(part.partPrefix)}">삭제</button></td></tr>`).join('')
      : '<tr class="empty-row"><td colspan="4">등록된 파트 코드가 없습니다.</td></tr>';
  }

  document.getElementById('parts-body').addEventListener('click', async (event) => {
    const button = event.target.closest('[data-delete-part]');
    if (!button) return;
    const prefix = button.dataset.deletePart;
    if (!window.confirm(`파트 매핑 ${prefix} 를 삭제할까요? 해당 파트를 사용하는 SKU가 있으면 삭제되지 않습니다.`)) return;
    try {
      await api(`/epr-api/parts/${encodeURIComponent(prefix)}`, { method: 'DELETE' });
      showAlert('parts-alert', 'success', '파트 매핑을 삭제했습니다.');
      await loadParts();
    } catch (error) {
      showAlert('parts-alert', 'danger', error.message);
    }
  });

  document.getElementById('part-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      await api('/epr-api/parts', { method: 'POST', body: { partPrefix: form.partPrefix.value, partName: form.partName.value } });
      showAlert('parts-alert', 'success', '파트 매핑을 저장했습니다.');
      form.reset();
      await Promise.all([loadParts(), loadProducts()]);
    } catch (error) {
      showAlert('parts-alert', 'danger', error.message);
    }
  });

  // ───────────── 제품 마스터 ─────────────
  const MASTER_FIELDS = ['productName', 'eaPerBag', 'bagPerBox', 'eaPerBox', 'bagPerPlt', 'boxPerPlt', 'bagWeight', 'boxWeight', 'companyType'];

  async function loadProducts() {
    const q = document.getElementById('master-keyword').value || '';
    const part = document.getElementById('master-part-filter').value || '';
    const products = await api(`/epr-api/products?q=${encodeURIComponent(q)}&part=${encodeURIComponent(part)}`);
    renderProducts(products);
  }

  function numberInput(name, value, step) {
    return `<td><input class="form-control form-control-sm" name="${name}" type="number" min="0"${step ? ` step="${step}"` : ''} value="${safe(plain(value))}"></td>`;
  }

  function renderProducts(products) {
    const body = document.getElementById('master-body');
    if (!products.length) {
      body.innerHTML = '<tr class="empty-row"><td colspan="11">검색 결과가 없습니다. 제품을 추가하거나 엑셀을 업로드하세요.</td></tr>';
      return;
    }
    body.innerHTML = products.map((product) => `<tr data-product-code="${safe(product.productCode)}">`
      + `<td><code>${safe(product.productCode)}</code><small class="d-block text-muted">${safe(product.partPrefix)} · ${safe(product.partName)}</small></td>`
      + `<td><input class="form-control form-control-sm" name="productName" type="text" value="${safe(product.productName)}" required></td>`
      + numberInput('eaPerBag', product.eaPerBag) + numberInput('bagPerBox', product.bagPerBox) + numberInput('eaPerBox', product.eaPerBox)
      + numberInput('bagPerPlt', product.bagPerPlt) + numberInput('boxPerPlt', product.boxPerPlt)
      + numberInput('bagWeight', product.bagWeight, '0.0001') + numberInput('boxWeight', product.boxWeight, '0.0001')
      + `<td><select class="form-select form-select-sm" name="companyType" aria-label="자사 또는 타사 구분"><option value="자사"${product.companyType === '자사' ? ' selected' : ''}>자사</option><option value="타사"${product.companyType === '타사' ? ' selected' : ''}>타사</option></select></td>`
      + '<td><button class="btn btn-sm btn-outline-primary" type="button" data-save-product>저장</button></td></tr>').join('');
  }

  function readProduct(container, productCode) {
    const value = (name) => container.querySelector(`[name="${name}"]`)?.value ?? '';
    const intOrZero = (name) => (value(name) === '' ? 0 : Number(value(name)));
    return {
      productCode,
      productName: value('productName'),
      eaPerBag: intOrZero('eaPerBag'), bagPerBox: intOrZero('bagPerBox'), eaPerBox: intOrZero('eaPerBox'),
      bagPerPlt: intOrZero('bagPerPlt'), boxPerPlt: intOrZero('boxPerPlt'),
      bagWeight: value('bagWeight') === '' ? 0 : value('bagWeight'),
      boxWeight: value('boxWeight') === '' ? 0 : value('boxWeight'),
      companyType: value('companyType') || '자사',
    };
  }

  document.getElementById('master-body').addEventListener('click', async (event) => {
    const button = event.target.closest('[data-save-product]');
    if (!button) return;
    const row = button.closest('tr');
    const code = row.dataset.productCode;
    button.disabled = true;
    try {
      await api('/epr-api/products', { method: 'POST', body: readProduct(row, code) });
      showAlert('master-alert', 'success', `${code} 제품 마스터를 저장했습니다.`);
    } catch (error) {
      showAlert('master-alert', 'danger', `${code}: ${error.message}`);
    } finally {
      button.disabled = false;
    }
  });

  document.getElementById('master-filter-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try { await loadProducts(); showAlert('master-alert'); }
    catch (error) { showAlert('master-alert', 'danger', error.message); }
  });

  document.getElementById('product-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      const saved = await api('/epr-api/products', { method: 'POST', body: readProduct(form, form.productCode.value) });
      window.bootstrap?.Modal.getOrCreateInstance(document.getElementById('product-modal')).hide();
      form.reset();
      showAlert('product-form-alert');
      showAlert('master-alert', 'success', `${saved.productCode} 제품 마스터를 저장했습니다.`);
      await Promise.all([loadProducts(), loadOverview(), loadParts()]);
    } catch (error) {
      showAlert('product-form-alert', 'danger', error.message);
    }
  });

  // ───────────── 마스터 · 포장중량 엑셀 업로드 ─────────────
  document.querySelectorAll('.api-upload-form').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const file = form.querySelector('input[type="file"]').files?.[0];
      if (!file) return;
      const feedback = form.parentElement.querySelector('.upload-feedback');
      const button = form.querySelector('button[type="submit"]');
      const oldText = button.textContent;
      button.disabled = true;
      button.textContent = '처리 중…';
      feedback.className = 'upload-feedback small mt-2 text-muted';
      feedback.textContent = '엑셀 데이터를 검증하고 저장하고 있습니다.';
      try {
        const formData = new FormData();
        formData.append('file', file);
        const data = await api(form.dataset.endpoint, { method: 'POST', body: formData });
        feedback.className = `upload-feedback small mt-2 ${data.failedRows ? 'text-warning' : 'text-success'}`;
        feedback.textContent = `${data.processedRows}행 처리 · ${data.insertedRows}건 신규 · ${data.updatedRows}건 반영 · ${data.failedRows}건 확인 필요`;
        if (data.issues?.length) {
          feedback.innerHTML += `<ul class="upload-issues mb-0">${data.issues.slice(0, 20).map((issue) => `<li>${safe(issue.rowNumber)}행 ${safe(issue.productCode)}: ${safe(issue.message)}</li>`).join('')}</ul>`;
        }
        form.reset();
        await Promise.all([loadProducts(), loadOverview(), loadParts()]);
      } catch (error) {
        feedback.className = 'upload-feedback small mt-2 text-danger';
        feedback.textContent = error.message;
      } finally {
        button.disabled = false;
        button.textContent = oldText;
      }
    });
  });

  // ───────────── 판매수량 계산 ─────────────
  document.getElementById('sales-upload-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const salesForm = event.currentTarget;
    const button = salesForm.querySelector('button[type="submit"]');
    const file = document.getElementById('sales-file').files?.[0];
    if (!file) return;
    showAlert('sales-alert');
    button.disabled = true;
    button.textContent = '계산 중…';
    try {
      const reportYear = document.getElementById('report-year').value;
      if (!reportYear || Number(reportYear) < 2000 || Number(reportYear) > 2200) throw new Error('신고 연도를 2000~2200 범위로 입력하세요.');
      const existsData = await api(`/epr-api/annual/${encodeURIComponent(reportYear)}/exists`);
      const replaceExisting = Boolean(existsData.exists);
      if (replaceExisting && !window.confirm(`${reportYear}년 판매 실적이 이미 저장되어 있습니다. 기존 연간 집계와 상세 내역을 이번 파일로 교체할까요?`)) return;
      const formData = new FormData();
      formData.append('file', file);
      formData.append('year', reportYear);
      formData.append('replaceExisting', String(replaceExisting));
      const result = await api('/epr-api/annual/calculate', { method: 'POST', body: formData });
      renderResult(result);
      document.getElementById('calculation-results').scrollIntoView({ behavior: 'smooth', block: 'start' });
      try {
        await loadAnnualComparison();
      } catch (comparisonError) {
        showAlert('sales-alert', 'warning', `계산은 완료됐지만 연도별 비교를 갱신하지 못했습니다. ${comparisonError.message}`);
      }
    } catch (error) {
      showAlert('sales-alert', 'danger', error.message);
    } finally {
      button.disabled = false;
      button.innerHTML = '연도 저장 및 계산 <span aria-hidden="true">→</span>';
    }
  });

  function updateSkuPartOptions(skuDetails) {
    const filter = document.getElementById('sku-part-filter');
    const selected = filter.value;
    const options = new Map();
    (skuDetails || []).forEach((item) => {
      const code = String(item.partPrefix ?? '');
      if (code) options.set(code, String(item.partName ?? ''));
    });
    const sorted = [...options.entries()].sort(([left], [right]) => left.localeCompare(right));
    filter.innerHTML = '<option value="">전체 파트</option>' + sorted
      .map(([code, name]) => `<option value="${safe(code)}">${safe(code)}${name ? ` · ${safe(name)}` : ''}</option>`).join('');
    if (options.has(selected)) filter.value = selected;
    applySkuPartFilter();
  }

  function applySkuPartFilter() {
    const selected = document.getElementById('sku-part-filter').value;
    document.querySelectorAll('#sku-detail-body tr[data-part-prefix]').forEach((row) => {
      row.hidden = Boolean(selected) && row.dataset.partPrefix !== selected;
    });
  }

  document.getElementById('sku-part-filter').addEventListener('change', applySkuPartFilter);

  let reportYearForDownload = currentYear;

  function setResultYear(year) {
    reportYearForDownload = year;
    document.getElementById('result-year').value = year;
  }

  function renderResult(result) {
    document.getElementById('calculated-report-year').textContent = `${result.reportYear}년 `;
    document.getElementById('report-year').value = result.reportYear;
    setResultYear(result.reportYear);
    showAlert('result-load-alert');
    document.getElementById('calculated-at').textContent = dateTime(result.calculatedAt);
    document.getElementById('grand-total').textContent = number(result.grandTotalWeightKg);
    document.getElementById('unique-count').textContent = integer(result.uniqueSkuCount);
    document.getElementById('unregistered-count').textContent = integer(result.unregisteredSkuCount);
    document.getElementById('missing-weight-count').textContent = integer(result.missingWeightSkuCount);

    const unknown = result.unregisteredSales || [];
    const unknownAlert = document.getElementById('unregistered-alert');
    if (unknown.length) {
      unknownAlert.classList.remove('d-none');
      unknownAlert.innerHTML = `<strong>미등록 제품코드가 있습니다.</strong> 해당 SKU는 파트 중량 합계에서 제외했습니다. 제품 마스터 등록 후 다시 계산해 주세요.<ul class="mb-0 mt-2">${unknown.map((item) => `<li><code>${safe(item.productCode)}</code> ${safe(item.uploadedProductName)} — ${number(item.salesEa, 0)} EA</li>`).join('')}</ul>`;
    } else unknownAlert.classList.add('d-none');
    document.getElementById('missing-weight-alert').classList.toggle('d-none', !(result.missingWeightSkuCount > 0));
    const issueAlert = document.getElementById('calculation-issues-alert');
    if (result.issues?.length) {
      issueAlert.classList.remove('d-none');
      document.getElementById('calculation-issues').innerHTML = result.issues.slice(0, 30).map((item) => `<li>${safe(item.rowNumber)}행 ${safe(item.productCode)}: ${safe(item.message)}</li>`).join('');
    } else issueAlert.classList.add('d-none');

    document.getElementById('part-summary-body').innerHTML = result.partSummaries.length ? result.partSummaries.map((item) => `<tr><td><span class="code-pill">${safe(item.partPrefix)}</span></td><td>${safe(item.partName)}</td><td class="text-end">${integer(item.skuCount)}</td><td class="text-end">${number(item.totalSalesEa)}</td><td class="text-end">${number(item.totalConvertedBag)}</td><td class="text-end">${number(item.totalBagReportWeightKg)}</td><td class="text-end">${number(item.totalConvertedBox)}</td><td class="text-end">${number(item.totalBoxReportWeightKg)}</td><td class="text-end fw-bold">${number(item.totalReportWeightKg)}</td></tr>`).join('')
      : '<tr class="empty-row"><td colspan="9">집계할 등록 SKU가 없습니다.</td></tr>';
    document.getElementById('sku-detail-body').innerHTML = result.skuDetails.length ? result.skuDetails.map((item) => `<tr data-part-prefix="${safe(item.partPrefix)}"><td>${safe(item.partPrefix)}</td><td><code>${safe(item.productCode)}</code></td><td>${safe(item.masterProductName)}</td><td>${safe(item.companyType || '—')}</td><td>${safe(item.uploadedProductName)}</td><td class="text-end">${number(item.salesEa)}</td><td class="text-end">${integer(item.eaPerBag)}</td><td class="text-end">${number(item.bagWeightG, 4)}</td><td class="text-end">${number(item.bagReportWeightKg)}</td><td class="text-end">${integer(item.eaPerBox)}</td><td class="text-end">${number(item.boxWeightG, 4)}</td><td class="text-end">${number(item.boxReportWeightKg)}</td><td class="text-end fw-bold">${number(item.totalReportWeightKg)}</td><td><span class="status-badge ${item.status === '정상' ? 'status-ok' : 'status-warn'}">${safe(item.status)}</span></td></tr>`).join('')
      : '<tr class="empty-row"><td colspan="14">계산 결과가 없습니다.</td></tr>';
    updateSkuPartOptions(result.skuDetails);
  }

  function renderEmptyResult(year, message) {
    document.getElementById('calculated-report-year').textContent = `${year}년 `;
    document.getElementById('calculated-at').textContent = message;
    ['grand-total'].forEach((id) => { document.getElementById(id).textContent = number(0); });
    ['unique-count', 'unregistered-count', 'missing-weight-count'].forEach((id) => { document.getElementById(id).textContent = integer(0); });
    document.getElementById('part-summary-body').innerHTML = '<tr class="empty-row"><td colspan="9">선택한 연도에 표시할 파트별 집계가 없습니다.</td></tr>';
    document.getElementById('sku-detail-body').innerHTML = '<tr class="empty-row"><td colspan="14">선택한 연도에 저장된 SKU 상세 내역이 없습니다.</td></tr>';
    updateSkuPartOptions([]);
    ['unregistered-alert', 'missing-weight-alert', 'calculation-issues-alert'].forEach((id) => document.getElementById(id).classList.add('d-none'));
    setResultYear(year);
    showAlert('result-load-alert', 'info', message);
  }

  async function loadAnnualResult(year) {
    if (!year || Number(year) < 2000 || Number(year) > 2200) throw new Error('조회 연도를 2000~2200 범위로 입력하세요.');
    showAlert('result-load-alert', 'info', `${year}년 신고중량 집계를 불러오는 중입니다.`);
    try {
      renderResult(await api(`/epr-api/annual/${encodeURIComponent(year)}`));
    } catch (error) {
      if (error.status === 404) { renderEmptyResult(Number(year), error.message || `${year}년에 저장된 집계 결과가 없습니다.`); return; }
      throw error;
    }
  }

  function rememberResultYear(year) {
    const url = new URL(window.location.href);
    url.searchParams.set('resultYear', year);
    window.history.replaceState({}, '', url);
  }

  document.getElementById('annual-result-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const year = document.getElementById('result-year').value;
    try {
      await loadAnnualResult(year);
      rememberResultYear(year);
    } catch (error) {
      showAlert('result-load-alert', 'danger', error.message);
    }
  });

  document.getElementById('report-download-btn').addEventListener('click', (event) => {
    const year = document.getElementById('result-year').value || reportYearForDownload;
    download(`/epr-api/annual/${encodeURIComponent(year)}/report`, event.currentTarget);
  });

  document.getElementById('annual-compare-body').addEventListener('click', async (event) => {
    const yearButton = event.target.closest('[data-report-year]');
    if (!yearButton) return;
    const year = yearButton.dataset.reportYear;
    document.getElementById('result-year').value = year;
    try {
      await loadAnnualResult(year);
      rememberResultYear(year);
      document.getElementById('calculation-results').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
      showAlert('result-load-alert', 'danger', error.message);
    }
  });

  async function loadAnnualComparison() {
    const fromYear = document.getElementById('from-year').value;
    const toYear = document.getElementById('to-year').value;
    if (!fromYear || !toYear) return;
    const rows = await api(`/epr-api/annual/compare?fromYear=${encodeURIComponent(fromYear)}&toYear=${encodeURIComponent(toYear)}`);
    document.getElementById('annual-compare-body').innerHTML = rows.length ? rows.map((row) => `<tr><td><button type="button" class="btn btn-link p-0 fw-bold annual-result-link" data-report-year="${row.reportYear}">${row.reportYear}년</button></td><td class="text-end">${number(row.totalSalesEa)}</td><td class="text-end">${delta(row.salesChangeEa)}</td><td class="text-end">${integer(row.uniqueSkuCount)}</td><td class="text-end">${integer(row.unregisteredSkuCount)}</td><td class="text-end fw-bold">${number(row.totalReportWeightKg)}</td><td class="text-end">${delta(row.reportWeightChangeKg)}</td><td>${dateTime(row.calculatedAt)}</td></tr>`).join('')
      : '<tr class="empty-row"><td colspan="8">선택한 기간에 저장된 연간 자료가 없습니다.</td></tr>';
  }

  document.getElementById('annual-compare-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    try { await loadAnnualComparison(); }
    catch (error) { showAlert('sales-alert', 'danger', error.message); }
  });

  // ───────────── 시작 ─────────────
  async function init() {
    selectInitialTab();
    const params = new URLSearchParams(window.location.search);
    const resultYear = Number(params.get('resultYear')) || currentYear;
    document.getElementById('report-year').value = currentYear;
    document.getElementById('from-year').value = Math.max(2000, currentYear - 4);
    document.getElementById('to-year').value = currentYear;
    setResultYear(resultYear);
    if (!platformToken()) {
      showAlert('page-alert', 'warning', '플랫폼 로그인 정보가 없습니다. 플랫폼 화면에서 로그인한 뒤 EPR 메뉴로 들어오세요.');
      return;
    }
    const tasks = [
      loadOverview(), loadParts().then(loadProducts), loadAnnualComparison(), loadAnnualResult(resultYear),
    ];
    const results = await Promise.allSettled(tasks);
    const failed = results.find((item) => item.status === 'rejected');
    if (failed) showAlert('page-alert', 'danger', failed.reason?.message || '화면 자료를 불러오지 못했습니다.');
  }

  init();
})();
