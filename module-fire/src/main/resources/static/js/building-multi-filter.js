(() => {
  'use strict';

  const normalize = (values) => [...new Set((values || []).map(String).filter(Boolean))];

  function ensureStyles() {
    if (document.getElementById('building-multi-filter-styles')) return;
    const style = document.createElement('style');
    style.id = 'building-multi-filter-styles';
    style.textContent = `
      .building-multi-filter { position: relative; width: 100%; }
      .building-multi-filter-menu { position: relative; width: 100%; }
      .building-multi-filter-menu > summary { list-style: none; cursor: pointer; min-height: 38px; }
      .building-multi-filter-menu > summary::-webkit-details-marker { display: none; }
      .building-multi-filter-menu[open] > summary { border-color: #86b7fe; box-shadow: 0 0 0 .25rem rgba(13,110,253,.15); }
      .building-multi-filter-panel { position: absolute; z-index: 1080; top: calc(100% + 4px); left: 0; right: 0; max-height: 280px; overflow-y: auto; padding: .35rem 0; background: #fff; border: 1px solid rgba(0,0,0,.15); border-radius: .375rem; }
      .building-multi-filter-option { display: flex; align-items: center; gap: .55rem; min-height: 34px; margin: 0; padding: .35rem .75rem; cursor: pointer; white-space: nowrap; }
      .building-multi-filter-option:hover { background: #f1f5f9; }
      .building-multi-filter-option input { flex: 0 0 auto; margin: 0; }
      .building-multi-filter-all { font-weight: 600; border-bottom: 1px solid #e9ecef; }
      @media (max-width: 767.98px) { .building-multi-filter-panel { max-height: 220px; } }
    `;
    document.head.appendChild(style);
  }

  function updateLabel(host) {
    const selected = Array.from(host.querySelectorAll('input[type="checkbox"][data-building-id]:checked'));
    const label = host.querySelector('[data-role="label"]');
    if (!label) return;
    if (!selected.length) label.textContent = '-- 건물 전체 --';
    else if (selected.length === 1) label.textContent = selected[0].dataset.buildingName || selected[0].value;
    else label.textContent = `${selected.length}개 건물 선택`;
  }

  function syncAll(host) {
    const rows = Array.from(host.querySelectorAll('input[type="checkbox"][data-building-id]'));
    const all = host.querySelector('[data-role="all"]');
    if (!all) return;
    const checked = rows.filter(row => row.checked).length;
    all.checked = rows.length > 0 && checked === rows.length;
    all.indeterminate = checked > 0 && checked < rows.length;
  }

  function dispatch(host) {
    updateLabel(host);
    syncAll(host);
    host.dispatchEvent(new CustomEvent('building-multi-filter:change', { bubbles: true, detail: { values: values(host.id) } }));
  }

  function mount(id, buildings, selectedValues) {
    ensureStyles();
    const host = document.getElementById(id);
    if (!host) return;
    const selected = new Set(normalize(selectedValues || values(id)));
    host.className = 'building-multi-filter dropdown';
    host.innerHTML = '';

    const details = document.createElement('details');
    details.className = 'building-multi-filter-menu';
    const summary = document.createElement('summary');
    summary.className = 'form-select d-flex align-items-center justify-content-between';
    summary.innerHTML = '<span data-role="label">-- 건물 전체 --</span><span aria-hidden="true">▾</span>';
    details.appendChild(summary);

    const panel = document.createElement('div');
    panel.className = 'building-multi-filter-panel shadow';
    const allLabel = document.createElement('label');
    allLabel.className = 'building-multi-filter-option building-multi-filter-all';
    allLabel.innerHTML = '<input type="checkbox" data-role="all"><span>전체 선택</span>';
    panel.appendChild(allLabel);

    (buildings || []).forEach(building => {
      const value = String(building.buildingId ?? '');
      if (!value) return;
      const row = document.createElement('label');
      row.className = 'building-multi-filter-option';
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.value = value;
      input.checked = selected.has(value);
      input.dataset.buildingId = value;
      input.dataset.buildingName = building.buildingName || value;
      const text = document.createElement('span');
      text.textContent = building.buildingName || value;
      row.append(input, text);
      panel.appendChild(row);
    });
    details.appendChild(panel);
    host.appendChild(details);

    panel.addEventListener('change', event => {
      const target = event.target;
      if (target.matches('[data-role="all"]')) {
        panel.querySelectorAll('input[type="checkbox"][data-building-id]').forEach(input => { input.checked = target.checked; });
      }
      dispatch(host);
    });
    updateLabel(host);
    syncAll(host);
  }

  function values(id) {
    const host = document.getElementById(id);
    if (!host) return [];
    return Array.from(host.querySelectorAll('input[type="checkbox"][data-building-id]:checked')).map(input => input.value);
  }

  function clear(id) {
    const host = document.getElementById(id);
    if (!host) return;
    host.querySelectorAll('input[type="checkbox"][data-building-id]').forEach(input => { input.checked = false; });
    dispatch(host);
  }

  window.FireWebBuildingMultiFilter = { mount, values, clear };
})();
