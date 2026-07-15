(function () {
  const root = document.getElementById('dashboardRoot');
  const mode = document.body.dataset.dashboard;

  document.querySelectorAll('[data-link]').forEach((button) => {
    button.addEventListener('click', () => go(button.dataset.link || '/'));
  });

  function esc(value) {
    if (value === null || value === undefined) return '';
    const div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
  }

  function attr(value) {
    return esc(value).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function go(path) {
    if (!path) return;
    window.location.href = path;
  }

  async function api(method, url) {
    const headers = window.FireWebCsrf
      ? window.FireWebCsrf.headers({ Accept: 'application/json' }, method)
      : { Accept: 'application/json' };
    const response = await fetch(url, { method, credentials: 'same-origin', headers });
    if (response.status === 401 || response.status === 403) {
      if (window.FireWebCsrf && typeof window.FireWebCsrf.goLogin === 'function') window.FireWebCsrf.goLogin();
      return { success: false, data: null, message: '인증이 필요합니다.' };
    }
    const json = await response.json().catch(() => null);
    if (json && typeof json === 'object' && 'success' in json) return json;
    return { success: response.ok, data: json, message: response.ok ? 'SUCCESS' : response.statusText };
  }

  function content(apiResponse) {
    if (!apiResponse || !apiResponse.success) return [];
    const data = apiResponse.data || {};
    return Array.isArray(data) ? data : (data.content || []);
  }

  function totalElements(apiResponse, fallbackItems) {
    if (!apiResponse || !apiResponse.success) return fallbackItems.length;
    const data = apiResponse.data || {};
    return data.totalElements !== undefined ? data.totalElements : fallbackItems.length;
  }

  function parseDate(value) {
    if (!value) return null;
    if (Array.isArray(value)) return new Date(value[0], (value[1] || 1) - 1, value[2] || 1);
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  function fmtDate(value) {
    if (!value) return '-';
    if (Array.isArray(value)) {
      return value[0] + '-' + String(value[1]).padStart(2, '0') + '-' + String(value[2]).padStart(2, '0');
    }
    return String(value).substring(0, 10);
  }

  function badge(label, tone) {
    return '<span class="badge-soft badge-' + tone + '">' + esc(label) + '</span>';
  }

  function statCard(label, value, sub, tone) {
    return '<article class="stat-card ' + (tone || '') + '"><div class="label">' + esc(label) + '</div><div class="value">' + value + '</div><div class="sub">' + sub + '</div></article>';
  }

  function donutCard(summary, slices) {
    const denom = Math.max(summary.denom || summary.total || 0, 0);
    const normal = slices.find((slice) => slice.key === 'normal') || slices[0];
    const normalRate = denom ? Math.round((normal.count || 0) / denom * 100) : 0;
    let offset = 0;
    const segments = slices.map((slice) => {
      const count = Math.max(Number(slice.count || 0), 0);
      const pct = denom ? count / denom * 100 : 0;
      const rounded = denom ? Math.round(pct) : 0;
      let segment = '';
      if (pct > 0) {
        segment = '<circle class="pie-segment pie-hover-target" cx="60" cy="60" r="44" pathLength="100" fill="none" stroke="' + slice.color + '" stroke-width="24" stroke-dasharray="' + pct.toFixed(3) + ' ' + Math.max(0, 100 - pct).toFixed(3) + '" stroke-dashoffset="-' + offset.toFixed(3) + '" transform="rotate(-90 60 60)" data-label="' + attr(slice.label) + '" data-rate="' + rounded + '%" tabindex="0"><title>' + esc(slice.label + ': ' + count + '개 (' + rounded + '%)') + '</title></circle>';
      }
      offset += pct;
      return segment;
    }).join('') || '<circle cx="60" cy="60" r="44" pathLength="100" fill="none" stroke="#e2e8f0" stroke-width="24"></circle>';
    const legend = slices.map((slice) => {
      const count = Math.max(Number(slice.count || 0), 0);
      const pct = denom ? Math.round(count / denom * 100) : 0;
      return '<span class="pie-legend-item pie-hover-target" data-label="' + attr(slice.label) + '" data-rate="' + pct + '%"><span><b class="legend-dot" style="background:' + slice.color + '"></b>' + esc(slice.label) + '</span><b>' + count + '</b></span>';
    }).join('');
    return '<article class="fire-pie-card"><div class="pie-visual"><div class="pie-shadow"></div><svg class="pie-svg" viewBox="0 0 120 120" aria-label="' + attr(summary.label) + ' 설비 현황"><defs><filter id="pieShadow-' + attr(summary.type || summary.label) + '" x="-30%" y="-30%" width="160%" height="170%"><feDropShadow dx="0" dy="10" stdDeviation="5" flood-color="#0f172a" flood-opacity="0.22"/></filter></defs><circle class="pie-depth" cx="60" cy="66" r="44" fill="none" stroke="rgba(15,23,42,.18)" stroke-width="24"></circle><g filter="url(#pieShadow-' + attr(summary.type || summary.label) + ')">' + segments + '</g><circle cx="60" cy="60" r="27" fill="#fff" stroke="rgba(15,23,42,.08)" stroke-width="1"></circle></svg><div class="pie-center" data-default-label="정상" data-default-rate="' + normalRate + '%"><b class="pie-rate">' + normalRate + '%</b><span class="pie-label">정상</span></div></div><div class="pie-info"><div class="pie-title-row"><b>' + esc(summary.label) + '</b><span>총 ' + denom + '개</span></div><div class="legend pie-legend">' + legend + '</div></div></article>';
  }

  function cardPanel(title, sub, bodyHtml, noPadding) {
    return '<section class="card-panel"><div class="card-head"><h2>' + esc(title) + '</h2><span>' + esc(sub || '') + '</span></div><div class="card-body"' + (noPadding ? ' style="padding:0"' : '') + '>' + bodyHtml + '</div></section>';
  }

  function table(headers, rows, emptyText) {
    const head = '<thead><tr>' + headers.map((h) => '<th>' + esc(h) + '</th>').join('') + '</tr></thead>';
    const body = rows.length ? rows.join('') : '<tr><td colspan="' + headers.length + '" style="text-align:center;color:var(--dash-muted);padding:26px;">' + esc(emptyText) + '</td></tr>';
    return '<div class="table-scroll"><table class="data-table">' + head + '<tbody>' + body + '</tbody></table></div>';
  }

  function detailUrl(type, id, embed) {
    if (!type || !id) return '';
    const params = new URLSearchParams();
    if (embed) params.set('embedDetails', '1');
    params.set('details', String(id));
    if (type === 'ext') return '/extinguishers.html?' + params.toString();
    if (type === 'hyd') return '/hydrants.html?' + params.toString();
    if (type === 'receiver') return '/receivers.html?' + params.toString();
    if (type === 'pump') return '/pumps.html?' + params.toString();
    if (type === 'aircon') return '/facility/air-conditioners?' + params.toString();
    if (type === 'water') return '/facility/water-purifiers?' + params.toString();
    return '';
  }

  function ensureDetailModal() {
    let modal = document.getElementById('dashboardDetailModal');
    if (modal) return modal;
    modal = document.createElement('div');
    modal.id = 'dashboardDetailModal';
    modal.className = 'dashboard-modal-overlay';
    modal.innerHTML = '<section class="dashboard-modal" role="dialog" aria-modal="true" aria-labelledby="dashboardDetailTitle"><header class="dashboard-modal-head"><h2 id="dashboardDetailTitle">설비 상세</h2><button type="button" class="dashboard-modal-close" aria-label="닫기">×</button></header><div class="dashboard-modal-body"><iframe title="설비 상세" src="about:blank"></iframe></div></section>';
    document.body.appendChild(modal);
    modal.querySelector('.dashboard-modal-close').addEventListener('click', closeDetailModal);
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closeDetailModal();
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && modal.classList.contains('show')) closeDetailModal();
    });
    return modal;
  }

  function openDetailModal(url, title) {
    if (!url) return;
    const modal = ensureDetailModal();
    const titleEl = modal.querySelector('#dashboardDetailTitle');
    const iframe = modal.querySelector('iframe');
    if (titleEl) titleEl.textContent = title || '설비 상세';
    if (iframe) iframe.src = url;
    modal.classList.add('show');
    document.body.classList.add('modal-open');
  }

  function closeDetailModal() {
    const modal = document.getElementById('dashboardDetailModal');
    if (!modal) return;
    modal.classList.remove('show');
    document.body.classList.remove('modal-open');
    const iframe = modal.querySelector('iframe');
    if (iframe) iframe.src = 'about:blank';
  }

  function bindChartTooltips() {
    root.querySelectorAll('.fire-pie-card').forEach((card) => {
      const center = card.querySelector('.pie-center');
      if (!center) return;
      const rateEl = center.querySelector('.pie-rate');
      const labelEl = center.querySelector('.pie-label');
      function setCenter(rate, label) {
        if (rateEl) rateEl.textContent = rate || center.dataset.defaultRate || '-';
        if (labelEl) labelEl.textContent = label || center.dataset.defaultLabel || '-';
      }
      function setSegmentActive(label, active) {
        card.querySelectorAll('.pie-segment').forEach((segment) => {
          if ((segment.getAttribute('data-label') || '') !== label) return;
          segment.setAttribute('stroke-width', active ? '28' : '24');
          segment.style.filter = active ? 'drop-shadow(0 8px 6px rgba(15,23,42,.35))' : 'drop-shadow(0 4px 3px rgba(15,23,42,.22))';
        });
      }
      card.querySelectorAll('.pie-hover-target').forEach((target) => {
        target.addEventListener('mouseenter', () => {
          const label = target.getAttribute('data-label') || center.dataset.defaultLabel || '-';
          setCenter(target.getAttribute('data-rate'), label);
          setSegmentActive(label, true);
          if (target.classList.contains('pie-legend-item')) target.classList.add('active');
        });
        target.addEventListener('mouseleave', () => {
          const label = target.getAttribute('data-label') || '';
          setCenter(center.dataset.defaultRate, center.dataset.defaultLabel);
          setSegmentActive(label, false);
          if (target.classList.contains('pie-legend-item')) target.classList.remove('active');
        });
        target.addEventListener('focus', () => {
          const label = target.getAttribute('data-label') || center.dataset.defaultLabel || '-';
          setCenter(target.getAttribute('data-rate'), label);
          setSegmentActive(label, true);
        });
        target.addEventListener('blur', () => {
          const label = target.getAttribute('data-label') || '';
          setCenter(center.dataset.defaultRate, center.dataset.defaultLabel);
          setSegmentActive(label, false);
        });
      });
    });
  }

  function bindRouteButtons() {
    root.querySelectorAll('[data-go]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        go(button.dataset.go);
      });
    });
    root.querySelectorAll('[data-modal-url]').forEach((row) => {
      row.addEventListener('click', () => openDetailModal(row.dataset.modalUrl, row.dataset.modalTitle));
      row.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openDetailModal(row.dataset.modalUrl, row.dataset.modalTitle);
        }
      });
    });
    root.querySelectorAll('[data-row-go]').forEach((row) => {
      row.addEventListener('click', () => go(row.dataset.rowGo));
      row.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          go(row.dataset.rowGo);
        }
      });
    });
    bindChartTooltips();
  }

  async function renderFireDashboard() {
    try {
      let stats = null;
      try {
        const statsRes = await api('GET', '/fire-api/dashboard/stats');
        if (statsRes.success) stats = statsRes.data;
      } catch (_) {}

      const [extRes, hydRes, pumpRes, recvRes] = await Promise.all([
        api('GET', '/fire-api/extinguishers?page=0&size=2000&sort=updatedAt,desc'),
        api('GET', '/fire-api/hydrants?page=0&size=2000&sort=updatedAt,desc'),
        api('GET', '/fire-api/pumps?page=0&size=2000&sort=updatedAt,desc'),
        api('GET', '/fire-api/receivers?page=0&size=2000&sort=updatedAt,desc')
      ]);

      const extItems = content(extRes);
      const hydItems = content(hydRes);
      const pumpItems = content(pumpRes);
      const recvItems = content(recvRes);
      const counts = {
        ext: stats && stats.extinguisherCount !== undefined ? stats.extinguisherCount : totalElements(extRes, extItems),
        hyd: stats && stats.hydrantCount !== undefined ? stats.hydrantCount : totalElements(hydRes, hydItems),
        pump: stats && stats.pumpCount !== undefined ? stats.pumpCount : totalElements(pumpRes, pumpItems),
        receiver: stats && stats.receiverCount !== undefined ? stats.receiverCount : totalElements(recvRes, recvItems)
      };
      const total = stats && stats.totalEquipment !== undefined ? stats.totalEquipment : counts.ext + counts.hyd + counts.pump + counts.receiver;
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const oneMonthAgo = new Date(today.getTime() - 30 * 24 * 3600 * 1000);
      const replaceThreshold = new Date(today.getTime() + 30 * 24 * 3600 * 1000);

      function getId(type, item) {
        if (type === 'ext') return item.extinguisherId;
        if (type === 'hyd') return item.hydrantId;
        if (type === 'receiver') return item.receiverId;
        return item.pumpId;
      }
      function isInspectDue(item) {
        const date = parseDate(item.lastInspectionDate || item.lastInspection);
        return !date || date < oneMonthAgo;
      }
      function isReplaceDue(item) {
        const due = parseDate(item.replacementDueDate);
        return String(item.status || '').toUpperCase() === 'EXPIRED' || !!(due && due <= replaceThreshold);
      }
      function isAbnormal(type, item) {
        if (type === 'ext' || type === 'hyd') return item.lastIsFaulty === true;
        const status = String(item.lastInspectionStatus || '').toUpperCase();
        return status === 'FAULTY' || status === 'MAINTENANCE';
      }
      function nameText(type, item) {
        if (type === 'ext') return item.modelName || item.serialNumber || item.extinguisherType || '소화기 #' + (item.extinguisherId || '-');
        if (type === 'hyd') return item.modelName || item.serialNumber || item.hydrantType || '소화전 #' + (item.hydrantId || '-');
        if (type === 'receiver') return item.modelName || item.name || item.serialNumber || '수신기 #' + (item.receiverId || '-');
        return item.modelName || item.name || item.serialNumber || '소방펌프 #' + (item.pumpId || '-');
      }
      function locationText(item) {
        return [item.buildingName, item.floorName, item.locationDescription || item.location].filter(Boolean).join(' / ') || '-';
      }
      function shortcutUrl(type, item) {
        const id = getId(type, item);
        if (!id) return '';
        const params = new URLSearchParams();
        if (item.buildingName) params.set('buildingName', item.buildingName);
        if (item.floorName) params.set('floorName', item.floorName);
        if (item.buildingId != null) params.set('buildingId', String(item.buildingId));
        if (item.floorId != null) params.set('floorId', String(item.floorId));
        params.set('focusType', type);
        params.set('focusId', String(id));
        return '/maps/floor.html?' + params.toString();
      }
      function summarize(type, label, count, items, listUrl) {
        let abnormal = 0, inspect = 0, replace = 0, normal = 0;
        let graphNormal = 0, graphInspect = 0, graphReplace = 0, graphAbnormal = 0, graphMaintenance = 0, graphFaulty = 0;
        const abnormalItems = [];
        items.forEach((item) => {
          const a = isAbnormal(type, item);
          const i = isInspectDue(item);
          const r = type === 'ext' ? isReplaceDue(item) : false;
          const status = String(item.lastInspectionStatus || '').toUpperCase();
          const m = (type === 'receiver' || type === 'pump') && status === 'MAINTENANCE';
          const f = (type === 'receiver' || type === 'pump') && status === 'FAULTY';
          if (a) {
            abnormal += 1;
            abnormalItems.push({
              type, label, id: getId(type, item), name: nameText(type, item), location: locationText(item),
              status: item.lastInspectionStatus || item.status || (item.lastIsFaulty === true ? 'FAULTY' : 'NORMAL'),
              reason: item.lastFaultReason || item.faultReason || item.lastInspectionMemo || item.memo || (m ? '점검 결과 요정비' : '점검 결과 비정상'),
              date: item.lastInspectionDate || item.updatedAt || item.createdAt,
              shortcut: shortcutUrl(type, item)
            });
          }
          if (i) inspect += 1;
          if (r) replace += 1;
          if (!a && !i && !r) normal += 1;
          if (type === 'receiver' || type === 'pump') {
            if (f) graphFaulty += 1;
            else if (m) graphMaintenance += 1;
            else if (i) graphInspect += 1;
            else graphNormal += 1;
          } else if (a) graphAbnormal += 1;
          else if (r) graphReplace += 1;
          else if (i) graphInspect += 1;
          else graphNormal += 1;
        });
        return { type, label, total: count || items.length, denom: items.length || count || 0, abnormal, inspect, replace, normal, graphNormal, graphInspect, graphReplace, graphAbnormal, graphMaintenance, graphFaulty, abnormalItems, listUrl };
      }

      const summaries = [
        summarize('ext', '소화기', counts.ext, extItems, '/extinguishers.html'),
        summarize('hyd', '소화전', counts.hyd, hydItems, '/hydrants.html'),
        summarize('receiver', '수신기', counts.receiver, recvItems, '/receivers.html'),
        summarize('pump', '소방펌프', counts.pump, pumpItems, '/pumps.html')
      ];
      const abnormalItems = summaries.flatMap((summary) => summary.abnormalItems).sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')));
      const inspectTotal = summaries.reduce((sum, summary) => sum + summary.inspect, 0);
      const replaceTotal = summaries.reduce((sum, summary) => sum + summary.replace, 0);
      const abnormalTotal = summaries.reduce((sum, summary) => sum + summary.abnormal, 0);
      const normalGraphTotal = summaries.reduce((sum, summary) => sum + summary.graphNormal, 0);
      const inspectGraphTotal = summaries.reduce((sum, summary) => sum + summary.graphInspect, 0);
      const yellowGraphTotal = summaries.reduce((sum, summary) => sum + summary.graphReplace + summary.graphMaintenance, 0);
      const redGraphTotal = summaries.reduce((sum, summary) => sum + summary.graphAbnormal + summary.graphFaulty, 0);

      const chartHtml = summaries.map((summary) => {
        const slices = summary.type === 'receiver' || summary.type === 'pump'
          ? [
            { key: 'normal', label: '정상', count: summary.graphNormal, color: 'var(--dash-green)' },
            { key: 'inspect', label: '점검필요', count: summary.graphInspect, color: 'var(--dash-orange)' },
            { key: 'maintenance', label: '요정비', count: summary.graphMaintenance, color: 'var(--dash-yellow)' },
            { key: 'faulty', label: '불량', count: summary.graphFaulty, color: 'var(--dash-red)' }
          ]
          : [
            { key: 'normal', label: '정상', count: summary.graphNormal, color: 'var(--dash-green)' },
            { key: 'inspect', label: '점검필요', count: summary.graphInspect, color: 'var(--dash-orange)' },
            { key: 'replace', label: '교체필요', count: summary.graphReplace, color: 'var(--dash-yellow)' },
            { key: 'abnormal', label: '이상설비', count: summary.graphAbnormal, color: 'var(--dash-red)' }
          ];
        return donutCard(summary, slices);
      }).join('');

      const rows = summaries.map((summary) => {
        return '<tr><td><strong>' + esc(summary.label) + '</strong></td><td>' + summary.total + '</td><td>' + badge(summary.abnormal, summary.abnormal > 0 ? 'red' : 'green') + '</td><td>' + badge(summary.inspect + '개', summary.inspect > 0 ? 'orange' : 'green') + '</td><td><button class="table-action" data-go="' + attr(summary.listUrl) + '">목록</button></td></tr>';
      });
      const abnormalRows = abnormalItems.map((item) => {
        const detail = detailUrl(item.type, item.id, true);
        const rowTitle = item.label + ' 상세 - ' + item.name;
        const rowAttrs = detail ? ' class="clickable-row" data-modal-url="' + attr(detail) + '" data-modal-title="' + attr(rowTitle) + '" tabindex="0" title="상세정보 보기"' : '';
        return '<tr' + rowAttrs + '><td>' + esc(item.label) + '</td><td><strong>' + esc(item.name) + '</strong></td><td>' + esc(item.location) + '</td><td>' + statusBadge(item.status) + ' <span style="color:var(--dash-muted)">' + esc(item.reason) + '</span></td><td>' + fmtDate(item.date) + '</td><td>' + (item.shortcut ? '<button class="table-action" data-go="' + attr(item.shortcut) + '">바로가기</button>' : '') + '</td></tr>';
      });

      root.innerHTML = ''
        + '<div class="stats-grid">'
        + statCard('전체 소방설비', total, '소방시설 총 수량', '')
        + statCard('점검 필요 설비', inspectTotal, '최근 30일 내 점검 이력 없음', 'orange')
        + statCard('교체필요', replaceTotal, '소화기 교체 예정/만료', 'yellow')
        + statCard('이상설비', abnormalTotal, '점검 결과 비정상/요정비', 'red')
        + '</div>'
        + cardPanel('설비 현황', '정상 ' + normalGraphTotal + ' / 점검필요 ' + inspectGraphTotal + ' / 교체·요정비 ' + yellowGraphTotal + ' / 이상·불량 ' + redGraphTotal, '<div class="chart-grid fire-chart-grid">' + chartHtml + '</div>')
        + cardPanel('장비 유형별 현황', '점검 필요 설비는 최근 30일 기준입니다.', table(['유형', '등록 수량', '이상설비', '점검 필요 설비', ''], rows, '등록된 설비가 없습니다.'), true)
        + cardPanel('이상설비', '목록 행을 클릭하면 상세정보로 이동합니다. 바로가기는 도면 위치로 연결됩니다.', table(['유형', '이름/모델', '위치', '이상 내용', '점검일', ''], abnormalRows, '점검 결과 비정상 또는 요정비로 기록된 이상설비가 없습니다.'), true);
      bindRouteButtons();
    } catch (error) {
      root.innerHTML = '<div class="error-box">소방설비 대시보드를 불러오지 못했습니다. ' + esc(error.message || '') + '</div>';
    }
  }

  function statusBadge(status) {
    const key = String(status || '').toUpperCase();
    const labels = { NORMAL: '정상', FAULTY: '비정상', MAINTENANCE: '요정비', ACTIVE: '사용중', INACTIVE: '미사용', EXPIRED: '만료', PENDING: '대기' };
    if (key === 'NORMAL' || key === 'ACTIVE') return badge(labels[key], 'green');
    if (key === 'PENDING' || key === 'MAINTENANCE') return badge(labels[key] || status, 'orange');
    return badge(labels[key] || status || '-', 'red');
  }

  async function renderFacilityDashboard() {
    try {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      const oneMonthAgo = new Date(today.getTime() - 30 * 24 * 3600 * 1000);
      const [airRes, waterRes] = await Promise.all([
        api('GET', '/facility-api/air-conditioners?page=0&size=2000'),
        api('GET', '/facility-api/water-purifiers?page=0&size=2000')
      ]);
      const airItems = content(airRes);
      const waterItems = content(waterRes);

      function isInspectionDue(type, item) {
        if (type === 'aircon') return item.inspectionRequested === true;
        const date = parseDate(item.lastInspectionDate);
        return !date || date < oneMonthAgo;
      }
      function nameText(item) {
        return item.serialNumber || item.equipmentType || '기타설비 #' + (item.equipmentId || '-');
      }
      function locationText(item) {
        return [item.buildingName, item.floorName].filter(Boolean).join(' / ') || '-';
      }
      function shortcutUrl(type, item) {
        if (!item.equipmentId) return '';
        const params = new URLSearchParams();
        if (item.buildingName) params.set('buildingName', item.buildingName);
        if (item.floorName) params.set('floorName', item.floorName);
        if (item.buildingId != null) params.set('buildingId', String(item.buildingId));
        if (item.floorId != null) params.set('floorId', String(item.floorId));
        params.set('focusType', type === 'aircon' ? 'aircon' : 'water');
        params.set('focusId', String(item.equipmentId));
        return '/facility/floor.html?' + params.toString();
      }
      function summarize(type, label, items, listUrl) {
        let inspect = 0, normal = 0;
        const inspectItems = [];
        items.forEach((item) => {
          const due = isInspectionDue(type, item);
          if (due) {
            inspect += 1;
            inspectItems.push({
              type, label, id: item.equipmentId, name: nameText(item), location: locationText(item),
              status: type === 'aircon' ? '점검요청' : '점검필요',
              reason: type === 'aircon' ? '접수된 점검 요청' : '최근 30일 기준 점검 대상',
              date: item.lastInspectionDate || item.createdAt, shortcut: shortcutUrl(type, item)
            });
          } else {
            normal += 1;
          }
        });
        return { type, label, total: items.length, denom: items.length, inspect, normal, inspectItems, listUrl };
      }

      const summaries = [
        summarize('aircon', '에어컨', airItems, '/facility/air-conditioners'),
        summarize('water', '정수기', waterItems, '/facility/water-purifiers')
      ];
      const total = summaries.reduce((sum, summary) => sum + summary.total, 0);
      const normalTotal = summaries.reduce((sum, summary) => sum + summary.normal, 0);
      const inspectTotal = summaries.reduce((sum, summary) => sum + summary.inspect, 0);
      const inspectItems = summaries.flatMap((summary) => summary.inspectItems).sort((a, b) => String(a.date || '').localeCompare(String(b.date || ''))).slice(0, 30);
      const chartHtml = summaries.map((summary) => donutCard(summary, [
        { key: 'normal', label: '정상', count: summary.normal, color: 'var(--dash-green)' },
        { key: 'inspect', label: summary.type === 'aircon' ? '점검요청' : '점검필요', count: summary.inspect, color: summary.type === 'aircon' ? 'var(--dash-primary)' : 'var(--dash-orange)' }
      ])).join('');
      const rows = summaries.map((summary) => '<tr><td><strong>' + esc(summary.label) + '</strong></td><td>' + summary.total + '</td><td style="color:var(--dash-green);font-weight:900;">' + summary.normal + '</td><td>' + badge((summary.type === 'aircon' ? '점검요청 ' : '점검필요 ') + summary.inspect, summary.inspect > 0 ? (summary.type === 'aircon' ? 'blue' : 'orange') : 'green') + '</td><td><button class="table-action" data-go="' + attr(summary.listUrl) + '">목록</button></td></tr>');
      const inspectRows = inspectItems.map((item) => {
        const detail = detailUrl(item.type, item.id);
        return '<tr><td>' + esc(item.label) + '</td><td><strong>' + esc(item.name) + '</strong></td><td>' + esc(item.location) + '</td><td>' + badge(item.status, item.type === 'aircon' ? 'blue' : 'orange') + '</td><td>' + esc(item.reason) + '</td><td>' + fmtDate(item.date) + '</td><td>' + (detail ? '<button class="table-action" data-go="' + attr(detail) + '">상세</button> ' : '') + (item.shortcut ? '<button class="table-action" data-go="' + attr(item.shortcut) + '">도면</button>' : '') + '</td></tr>';
      });

      root.innerHTML = ''
        + '<div class="stats-grid">'
        + statCard('전체 기타설비', total, '에어컨 ' + airItems.length + ' / 정수기 ' + waterItems.length, '')
        + statCard('정상설비', normalTotal, '점검요청/점검필요 제외', 'green')
        + statCard('점검요청/필요', inspectTotal, '에어컨은 점검요청, 정수기는 점검필요', 'orange')
        + '</div>'
        + cardPanel('기타설비 현황', '교체필요/이상설비 상태는 표시하지 않습니다.', '<div class="chart-grid">' + chartHtml + '</div>')
        + cardPanel('장비 유형별 현황', '기타설비는 에어컨과 정수기로 분리 집계합니다.', table(['유형', '등록 수량', '정상', '점검 상태', ''], rows, '등록된 기타설비가 없습니다.'), true)
        + cardPanel('점검요청/점검필요 설비', '바로가기를 누르면 기타설비 층별 도면의 해당 마커가 선택됩니다.', table(['유형', '설비', '위치', '상태', '기준', '최종 점검', ''], inspectRows, '점검요청/점검필요 설비가 없습니다.'), true);
      bindRouteButtons();
    } catch (error) {
      root.innerHTML = '<div class="error-box">기타설비 대시보드를 불러오지 못했습니다. ' + esc(error.message || '') + '</div>';
    }
  }

  if (mode === 'fire') renderFireDashboard();
  else if (mode === 'facility') renderFacilityDashboard();
  else if (root) root.innerHTML = '<div class="error-box">대시보드 유형을 확인할 수 없습니다.</div>';
})();
