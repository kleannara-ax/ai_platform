document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  // \uC138 \uD398\uC774\uC9C0\uC5D0\uC11C \uB3D9\uC77C \uB85C\uC9C1 \uC0AC\uC6A9 \u2014 \uC720\uB3D9\uC0C1 \uC0AC\uACE0\uC774\uB825 / SRF Invoice \uC0AC\uACE0\uC774\uB825 / \uC18C\uAC01\uB85C \uC704\uD0C1\uC6B4\uC601 \uC0AC\uACE0\uC774\uB825.
  // RECORD_TYPES \uC811\uB450\uC5B4\uB97C \uB2E4\uB974\uAC8C \uD574\uC11C \uB3D9\uC77C table_cell_value \uC548\uC5D0\uC11C \uBD84\uB9AC \uC800\uC7A5.
  const IS_SRF = page === "srf-boiler-invoice-accident.html";
  const IS_INCINERATOR = page === "incinerator-accident.html";
  if (page !== "unit-fluidized-accident.html" && !IS_SRF && !IS_INCINERATOR) return;

  const CELL_TABLE = "table_cell_value";
  const TABLES_BASE = "/tables";
  const MONTH_SUFFIX = "00";
  const PERIOD_KEY = "steamlog:shared-period";
  // SRF / \uC18C\uAC01\uB85C \uD398\uC774\uC9C0\uB294 \uC0AC\uACE0\uC774\uB825\uB9CC, \uC720\uB3D9\uC0C1 \uD398\uC774\uC9C0\uB294 \uC0AC\uACE0\uC774\uB825+\uC815\uBE44\uC774\uB825 \uB458 \uB2E4.
  const TABS = IS_SRF
    ? [{ id: "accident", label: "\uC0AC\uACE0\uC774\uB825" }]
    : [
        { id: "accident", label: "\uC0AC\uACE0\uC774\uB825" },
        { id: "maintenance", label: "\uC815\uBE44\uC774\uB825" },
      ];
  const RECORD_TYPES = IS_SRF
    ? { accident: "srf-accident-record" }
    : IS_INCINERATOR
    ? { accident: "incinerator-accident-record", maintenance: "incinerator-maintenance-record" }
    : { accident: "accident-record", maintenance: "maintenance-record" };
  const COLUMN_CONFIG = {
    accident: [
      { key: "date", label: "\uC77C\uC790", inputClass: "mid-input", widthClass: "date-col sticky-col-2" },
      { key: "reason", label: "\uC0AC\uC720", inputClass: "wide-input", widthClass: "wide-col" },
      { key: "action", label: "\uC870\uCE58\uB0B4\uC5ED", inputClass: "wide-input", widthClass: "wide-col" },
      { key: "downtime", label: "\uC6B4\uD734\uC2DC\uAC04(Hr)", inputClass: "mid-input", widthClass: "mid-col" },
      { key: "loss", label: "\uC190\uC2E4\uAE08\uC561(\uBC31\uB9CC\uC6D0)", inputClass: "mid-input", widthClass: "mid-col" },
      { key: "note", label: "\uBE44\uACE0", inputClass: "mid-input", widthClass: "note-col" },
    ],
    maintenance: [
      { key: "date", label: "\uC77C\uC790", inputClass: "mid-input", widthClass: "date-col sticky-col-2" },
      { key: "reason", label: "\uC0AC\uC720", inputClass: "wide-input", widthClass: "wide-col" },
      { key: "action", label: "\uC870\uCE58\uB0B4\uC5ED", inputClass: "wide-input", widthClass: "wide-col" },
      { key: "downtime", label: "\uC6B4\uD734\uC2DC\uAC04(Hr)", inputClass: "mid-input", widthClass: "mid-col" },
      { key: "note", label: "\uBE44\uACE0", inputClass: "mid-input", widthClass: "note-col" },
    ],
  };

  const yearSelect = document.getElementById("filterYear");
  const submitButton = document.getElementById("filterSubmit");
  const tabsRoot = document.getElementById("accidentTabs");
  const title = document.getElementById("accidentPanelTitle");
  const tableWrap = document.getElementById("accidentTableWrap");
  const topActions = document.getElementById("accidentTopActions");
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));

  if (!yearSelect || !submitButton || !tabsRoot || !title || !tableWrap || !topActions) return;

  let activeTab = "accident";
  let cellRows = [];
  let dragState = null;
  let pendingTabMove = null;

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function monthKey() {
    return `${yearSelect.value}-${MONTH_SUFFIX}`;
  }

  function yearValue() {
    return Number(yearSelect.value || new Date().getFullYear());
  }

  function buildUniqueKey(tableName, rowKey, colIndex) {
    return `${monthKey()}::${tableName}::${rowKey}::${colIndex}`;
  }

  function rowKey(kind, id) {
    return `${RECORD_TYPES[kind]}:${id}`;
  }

  function getCellRow(tableName, currentRowKey, colIndex) {
    const uniqueKey = buildUniqueKey(tableName, currentRowKey, colIndex);
    return cellRows.find((row) => buildUniqueKey(row.table_name, row.row_key, Number(row.col_index || 0)) === uniqueKey) || null;
  }

  function getCellValue(tableName, currentRowKey, colIndex) {
    return String(getCellRow(tableName, currentRowKey, colIndex)?.cell_value ?? "");
  }

  function normalizeDateValue(value) {
    const text = String(value ?? "").trim();
    if (!/^\d{5}$/.test(text)) return text;
    const serial = Number(text);
    if (!Number.isFinite(serial) || serial < 30000 || serial > 60000) return text;
    const epoch = Date.UTC(1899, 11, 30);
    const date = new Date(epoch + serial * 86400000);
    return date.toISOString().slice(0, 10);
  }

  function setCellRow(saved) {
    const uniqueKey = buildUniqueKey(saved.table_name, saved.row_key, Number(saved.col_index || 0));
    const index = cellRows.findIndex((row) => buildUniqueKey(row.table_name, row.row_key, Number(row.col_index || 0)) === uniqueKey);
    if (index >= 0) {
      cellRows[index] = saved;
    } else {
      cellRows.push(saved);
    }
  }

  function removeRowCells(currentRowKey) {
    cellRows = cellRows.filter((row) => !(row.table_name === CELL_TABLE && row.row_key === currentRowKey));
  }

  function topActionButton(id, label) {
    return `<button type="button" class="accident-button" id="${id}">${label}</button>`;
  }

  function inputHtml(kind, rowId, key, value, inputClass) {
    return `
      <input
        class="accident-input ${inputClass}"
        type="text"
        data-kind="${kind}"
        data-row-id="${rowId}"
        data-key="${key}"
        value="${escapeHtml(value)}"
      />
    `;
  }

  function dragHandleHtml(kind, rowId) {
    return `
      <button
        type="button"
        class="accident-drag-handle"
        draggable="true"
        tabindex="-1"
        data-drag-kind="${kind}"
        data-drag-row-id="${rowId}"
        title="\uD589 \uC774\uB3D9"
      >\uC774\uB3D9</button>
    `;
  }

  function deleteButtonHtml(kind, rowId) {
    return `
      <button
        type="button"
        class="accident-delete-button"
        data-delete-kind="${kind}"
        data-delete-row-id="${rowId}"
      >\uC0AD\uC81C</button>
    `;
  }

  function decodeRows(kind) {
    const prefix = `${RECORD_TYPES[kind]}:`;
    const ids = Array.from(new Set(
      cellRows
        .filter((row) => row.table_name === CELL_TABLE && String(row.row_key || "").startsWith(prefix))
        .map((row) => String(row.row_key).replace(prefix, ""))
    ));

    return ids
      .map((id) => {
        const currentRowKey = rowKey(kind, id);
        return {
          id,
          date: normalizeDateValue(getCellValue(CELL_TABLE, currentRowKey, 1)),
          reason: getCellValue(CELL_TABLE, currentRowKey, 2),
          action: getCellValue(CELL_TABLE, currentRowKey, 3),
          downtime: getCellValue(CELL_TABLE, currentRowKey, 4),
          loss: getCellValue(CELL_TABLE, currentRowKey, 5),
          // 비고: 일반 텍스트. 과거 "0" 으로 저장된 값은 빈 값으로 표시 (사용자가 새로 입력해 덮어쓸 수 있도록).
          note: ((v) => (v === "0" ? "" : v))(getCellValue(CELL_TABLE, currentRowKey, 6)),
          sortOrder: Number(getCellValue(CELL_TABLE, currentRowKey, 7) || 0) || 0,
        };
      })
      .sort((a, b) => a.sortOrder - b.sortOrder || a.id.localeCompare(b.id, "ko"));
  }

  function renderTabs() {
    tabsRoot.innerHTML = TABS.map((tab) => `
      <button type="button" class="period-tab-button${tab.id === activeTab ? " active" : ""}" data-tab="${tab.id}">
        ${escapeHtml(tab.label)}
      </button>
    `).join("");
  }

  function renderTable(kind) {
    const columns = COLUMN_CONFIG[kind];
    const rows = decodeRows(kind);
    title.textContent = kind === "accident" ? "\uC0AC\uACE0\uC774\uB825" : "\uC815\uBE44\uC774\uB825";
    topActions.innerHTML = topActionButton("recordAddButton", "\uD56D\uBAA9 \uCD94\uAC00");

    const head = columns.map((column) => `<th class="${column.widthClass}">${column.label}</th>`).join("");
    const body = rows.map((row, index) => {
      const cells = columns.map((column) => `
        <td class="${column.widthClass}">
          ${inputHtml(kind, row.id, column.key, row[column.key], column.inputClass)}
        </td>
      `).join("");

      return `
        <tr class="accident-row" data-row-kind="${kind}" data-row-id="${row.id}">
          <td class="drag-col">${dragHandleHtml(kind, row.id)}</td>
          <th class="sticky-col no-col" scope="row">${index + 1}</th>
          ${cells}
          <td class="action-col">${deleteButtonHtml(kind, row.id)}</td>
        </tr>
      `;
    }).join("");

    tableWrap.innerHTML = `
      <table class="accident-table">
        <thead>
          <tr>
            <th class="drag-col">\uC774\uB3D9</th>
            <th class="sticky-col no-col">No.</th>
            ${head}
            <th class="action-col">\uC0AD\uC81C</th>
          </tr>
        </thead>
        <tbody>
          ${body}
          <tr class="summary-row">
            <th class="sticky-col" scope="row" colspan="2">\uD569\uACC4</th>
            <td class="sticky-col-2">${rows.length}\uAC74</td>
            <td colspan="${Math.max(columns.length - 1, 1)}"></td>
            <td></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function clearDropClasses() {
    tableWrap.querySelectorAll(".accident-row").forEach((row) => {
      row.classList.remove("drop-before", "drop-after", "is-dragging");
    });
  }

  function buildFocusSignature(target) {
    if (!(target instanceof HTMLInputElement || target instanceof HTMLButtonElement)) return null;
    return {
      tag: target.tagName,
      kind: target.dataset.kind || target.dataset.deleteKind || target.dataset.dragKind || "",
      rowId: target.dataset.rowId || target.dataset.deleteRowId || target.dataset.dragRowId || "",
      key: target.dataset.key || "",
      id: target.id || "",
    };
  }

  function sameFocusSignature(target, signature) {
    if (!signature || !(target instanceof HTMLElement)) return false;
    const kind = target.dataset.kind || target.dataset.deleteKind || target.dataset.dragKind || "";
    const rowId = target.dataset.rowId || target.dataset.deleteRowId || target.dataset.dragRowId || "";
    return target.tagName === signature.tag
      && kind === signature.kind
      && rowId === signature.rowId
      && (target.dataset.key || "") === signature.key
      && (target.id || "") === signature.id;
  }

  function getTabbables() {
    return Array.from(document.querySelectorAll("input:not([disabled]):not([tabindex='-1']), select:not([disabled]):not([tabindex='-1']), button:not([disabled]):not([tabindex='-1'])"))
      .filter((element) => element instanceof HTMLElement && element.offsetParent !== null);
  }

  function restorePendingTabMove() {
    if (!pendingTabMove) return;
    const move = pendingTabMove;
    pendingTabMove = null;
    requestAnimationFrame(() => {
      const tabbables = getTabbables();
      const currentIndex = tabbables.findIndex((element) => sameFocusSignature(element, move.signature));
      if (currentIndex < 0) return;
      const nextIndex = currentIndex + (move.forward ? 1 : -1);
      const nextTarget = tabbables[nextIndex];
      if (nextTarget instanceof HTMLElement) {
        nextTarget.focus();
        if (nextTarget instanceof HTMLInputElement) {
          nextTarget.select();
        }
      }
    });
  }

  async function fetchRows() {
    const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}?page=1&limit=2000&month=${encodeURIComponent(monthKey())}`);
    if (!response.ok) {
      cellRows = [];
      return;
    }
    const payload = await response.json();
    cellRows = Array.isArray(payload?.data) ? payload.data : [];
  }

  async function upsertCell(payload, existingId) {
    const url = existingId ? `${TABLES_BASE}/${CELL_TABLE}/${existingId}` : `${TABLES_BASE}/${CELL_TABLE}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) return null;
    return response.json();
  }

  async function deleteRowGroup(currentRowKey) {
    const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}/row-group?month=${encodeURIComponent(monthKey())}&rowKey=${encodeURIComponent(currentRowKey)}`, {
      method: "DELETE",
    });
    if (!response.ok) return false;
    removeRowCells(currentRowKey);
    return true;
  }

  async function saveField(kind, id, key, rawValue) {
    const currentRowKey = rowKey(kind, id);
    const fieldMap = { date: 1, reason: 2, action: 3, downtime: 4, loss: 5, note: 6, sortOrder: 7 };
    const colIndex = fieldMap[key] || 0;
    if (!colIndex) return;

    const existing = getCellRow(CELL_TABLE, currentRowKey, colIndex);
    const payload = {
      month: monthKey(),
      year_no: yearValue(),
      month_no: 0,
      table_name: CELL_TABLE,
      row_key: currentRowKey,
      col_index: colIndex,
      cell_value: rawValue,
    };
    const saved = await upsertCell(payload, existing?.id);
    if (saved) setCellRow(saved);
  }

  async function addRow(kind) {
    const rows = decodeRows(kind);
    const id = `${Date.now()}`;
    const saves = [
      saveField(kind, id, "date", ""),
      saveField(kind, id, "reason", ""),
      saveField(kind, id, "action", ""),
      saveField(kind, id, "downtime", ""),
      saveField(kind, id, "note", ""),
      saveField(kind, id, "sortOrder", String(rows.length + 1)),
    ];
    if (kind === "accident") {
      saves.push(saveField(kind, id, "loss", ""));
    }
    await Promise.all(saves);
    await fetchRows();
    render();
  }

  async function moveRow(kind, rowId, targetRowId, dropAfter) {
    const rows = decodeRows(kind);
    const source = rows.find((row) => row.id === rowId);
    const target = rows.find((row) => row.id === targetRowId);
    if (!source || !target || source.id === target.id) return;

    const remaining = rows.filter((row) => row.id !== source.id);
    let insertIndex = remaining.findIndex((row) => row.id === target.id);
    if (insertIndex < 0) insertIndex = remaining.length;
    if (dropAfter) insertIndex += 1;
    remaining.splice(insertIndex, 0, source);

    await Promise.all(remaining.map((row, index) => saveField(kind, row.id, "sortOrder", String(index + 1))));
    await fetchRows();
    render();
  }

  function render() {
    renderTabs();
    renderTable(activeTab);
  }

  function bindSidebar() {
    if (window.CommonSidebar?.isManaged?.()) return;
    sidebarGroups.forEach((group) => {
      const toggle = group.querySelector(".sidebar-parent-toggle");
      const state = group.dataset.sidebarState || toggle?.dataset.sidebarState;
      if (!toggle || !state) return;
      const key = `steamlog:sidebar:${state}`;
      const saved = localStorage.getItem(key);
      const isOpen = saved === "1" || group.classList.contains("is-open");
      group.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
      toggle.addEventListener("click", () => {
        const nextOpen = group.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
        localStorage.setItem(key, nextOpen ? "1" : "0");
      });
    });
  }

  function populateYearSelect() {
    const currentYear = new Date().getFullYear();
    let savedYear = String(currentYear);
    try {
      const raw = localStorage.getItem(PERIOD_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) savedYear = String(parsed.year);
      }
    } catch (_) {}
    yearSelect.innerHTML = "";
    for (let year = currentYear + 1; year >= currentYear - 5; year -= 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = String(year);
      option.selected = String(year) === savedYear;
      yearSelect.appendChild(option);
    }
  }

  function persistSharedYear() {
    let month = "01";
    try {
      const raw = localStorage.getItem(PERIOD_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^(0[1-9]|1[0-2])$/.test(String(parsed?.month || ""))) month = String(parsed.month);
      }
    } catch (_) {}
    localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: yearSelect.value, month }));
  }

  tabsRoot.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || !target.dataset.tab) return;
    activeTab = target.dataset.tab;
    render();
  });

  topActions.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement)) return;
    if (target.id === "recordAddButton") {
      await addRow(activeTab);
    }
  });

  tableWrap.addEventListener("dragstart", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement) || !target.dataset.dragKind || !target.dataset.dragRowId) return;
    dragState = { kind: target.dataset.dragKind, rowId: target.dataset.dragRowId };
    target.closest(".accident-row")?.classList.add("is-dragging");
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", `${dragState.kind}:${dragState.rowId}`);
    }
  });

  tableWrap.addEventListener("dragend", () => {
    dragState = null;
    clearDropClasses();
  });

  tableWrap.addEventListener("dragover", (event) => {
    if (!dragState) return;
    const row = event.target instanceof HTMLElement ? event.target.closest(".accident-row") : null;
    if (!row || row.dataset.rowId === dragState.rowId || row.dataset.rowKind !== dragState.kind) return;
    event.preventDefault();
    clearDropClasses();
    const rect = row.getBoundingClientRect();
    row.classList.add(event.clientY > rect.top + rect.height / 2 ? "drop-after" : "drop-before");
  });

  tableWrap.addEventListener("drop", async (event) => {
    if (!dragState) return;
    const row = event.target instanceof HTMLElement ? event.target.closest(".accident-row") : null;
    if (!row || row.dataset.rowId === dragState.rowId || row.dataset.rowKind !== dragState.kind) return;
    event.preventDefault();
    const rect = row.getBoundingClientRect();
    const dropAfter = event.clientY > rect.top + rect.height / 2;
    clearDropClasses();
    await moveRow(dragState.kind, dragState.rowId, row.dataset.rowId, dropAfter);
    dragState = null;
  });

  tableWrap.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || !target.dataset.deleteKind || !target.dataset.deleteRowId) return;
    const currentRowKey = rowKey(target.dataset.deleteKind, target.dataset.deleteRowId);
    const deleted = await deleteRowGroup(currentRowKey);
    if (!deleted) return;
    await fetchRows();
    render();
  });

  tableWrap.addEventListener("change", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    const kind = target.dataset.kind;
    const rowId = target.dataset.rowId;
    const key = target.dataset.key;
    if (!kind || !rowId || !key) return;
    await saveField(kind, rowId, key, target.value);
    await fetchRows();
    render();
    restorePendingTabMove();
  });

  tableWrap.addEventListener("keydown", (event) => {
    const target = event.target;
    if (event.key !== "Tab" || !(target instanceof HTMLElement)) return;
    const signature = buildFocusSignature(target);
    if (!signature) return;
    pendingTabMove = {
      forward: !event.shiftKey,
      signature,
    };
  });

  submitButton.addEventListener("click", async () => {
    persistSharedYear();
    await fetchRows();
    render();
  });
  yearSelect.addEventListener("change", persistSharedYear);

  async function boot() {
    populateYearSelect();
    bindSidebar();
    await fetchRows();
    render();
  }

  boot();
});
