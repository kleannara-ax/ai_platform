document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-fluidized-improvement.html") return;

  const CELL_TABLE = "table_cell_value";
  const TABLES_BASE = "/tables";
  const MONTH_SUFFIX = "00";
  const PERIOD_KEY = "steamlog:shared-period";
  const MONTHS = Array.from({ length: 12 }, (_, index) => String(index + 1).padStart(2, "0"));
  const CATEGORY_OPTIONS = [
    "\uC124\uBE44\uAC1C\uC120",
    "\uC720\uC9C0\uBCF4\uC218",
    "\uCD94\uAC00 \uC791\uC5C5\uC0AC\uD56D",
  ];
  const STATUS_OPTIONS = [
    "",
    "\uC644\uB8CC",
    "\uC9C4\uD589\uC911",
  ];
  const TABS = [
    { id: "monthly", label: "\uC6D4\uBCC4\uAC1C\uC120" },
    { id: "item", label: "\uD56D\uBAA9\uAC1C\uC120" },
  ];

  const yearSelect = document.getElementById("filterYear");
  const submitButton = document.getElementById("filterSubmit");
  const tabsRoot = document.getElementById("improvementTabs");
  const title = document.getElementById("improvementPanelTitle");
  const tableWrap = document.getElementById("improvementTableWrap");
  const topActions = document.getElementById("improvementTopActions");
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));

  if (!yearSelect || !submitButton || !tabsRoot || !title || !tableWrap || !topActions) return;

  let activeTab = "monthly";
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

  function parseNumber(value) {
    if (value === null || value === undefined) return null;
    if (typeof value === "number") return Number.isFinite(value) ? value : null;
    const raw = String(value).replace(/,/g, "").trim();
    if (!raw) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "";
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: digits,
    }).format(numeric);
  }

  function sum(values) {
    return values.reduce((acc, value) => acc + (Number.isFinite(value) ? value : 0), 0);
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

  function getCellRow(tableName, rowKey, colIndex) {
    const uniqueKey = buildUniqueKey(tableName, rowKey, colIndex);
    return cellRows.find((row) => {
      return buildUniqueKey(row.table_name, row.row_key, Number(row.col_index || 0)) === uniqueKey;
    }) || null;
  }

  function getCellValue(tableName, rowKey, colIndex) {
    return String(getCellRow(tableName, rowKey, colIndex)?.cell_value ?? "");
  }

  function setCellRow(saved) {
    const uniqueKey = buildUniqueKey(saved.table_name, saved.row_key, Number(saved.col_index || 0));
    const index = cellRows.findIndex((row) => {
      return buildUniqueKey(row.table_name, row.row_key, Number(row.col_index || 0)) === uniqueKey;
    });
    if (index >= 0) {
      cellRows[index] = saved;
    } else {
      cellRows.push(saved);
    }
  }

  function removeRowCells(rowKey) {
    cellRows = cellRows.filter((row) => !(row.table_name === CELL_TABLE && row.row_key === rowKey));
  }

  function monthlyRowKey(id) {
    return `improvement-monthly:${id}`;
  }

  function itemRowKey(id) {
    return `improvement-item:${id}`;
  }

  function monthLabel(month) {
    return `${Number(month)}\uC6D4`;
  }

  function topActionButton(id, label) {
    return `<button type="button" class="improvement-button" id="${id}">${label}</button>`;
  }

  function inputHtml(kind, rowId, key, value, numeric = false) {
    const extraClass = key === "usage"
      ? " usage-input"
      : (key === "payment" || key === "spent" ? " money-input" : "");
    const rawValue = numeric ? parseNumber(value) : null;
    return `
      <input
        class="improvement-input${extraClass}"
        type="text"
        ${numeric ? 'data-align="right"' : ""}
        inputmode="${numeric ? "numeric" : "text"}"
        data-kind="${kind}"
        data-row-id="${rowId}"
        data-key="${key}"
        ${numeric ? `data-raw-value="${escapeHtml(rawValue === null ? "" : String(rawValue))}"` : ""}
        value="${escapeHtml(numeric ? formatNumber(parseNumber(value), 0) : value)}"
      />
    `;
  }

  function selectHtml(kind, rowId, key, value, options) {
    const extraClass = key === "month" ? " month-select" : "";
    const tabIndexAttr = key === "month" ? ' tabindex="-1"' : "";
    return `
      <select class="improvement-select${extraClass}" data-kind="${kind}" data-row-id="${rowId}" data-key="${key}"${tabIndexAttr}>
        ${options.map((option) => {
          const label = key === "month" ? monthLabel(option) : (option || "-");
          return `<option value="${escapeHtml(option)}"${option === value ? " selected" : ""}>${escapeHtml(label)}</option>`;
        }).join("")}
      </select>
    `;
  }

  function calcHtml(value) {
    return `<span class="improvement-calc">${escapeHtml(formatNumber(value, 0))}</span>`;
  }

  function dragHandleHtml(kind, rowId) {
    return `
      <button
        type="button"
        class="improvement-drag-handle"
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
        class="improvement-delete-button"
        data-delete-kind="${kind}"
        data-delete-row-id="${rowId}"
      >\uC0AD\uC81C</button>
    `;
  }

  function decodeMonthlyRows() {
    const ids = Array.from(new Set(
      cellRows
        .filter((row) => row.table_name === CELL_TABLE && String(row.row_key || "").startsWith("improvement-monthly:"))
        .map((row) => String(row.row_key).replace("improvement-monthly:", ""))
    ));

    return ids
      .map((id) => {
        const rowKey = monthlyRowKey(id);
        return {
          id,
          month: getCellValue(CELL_TABLE, rowKey, 1) || "01",
          payment: getCellValue(CELL_TABLE, rowKey, 2),
          spent: getCellValue(CELL_TABLE, rowKey, 3),
          vendor: getCellValue(CELL_TABLE, rowKey, 4),
          usage: getCellValue(CELL_TABLE, rowKey, 5),
          sortOrder: parseNumber(getCellValue(CELL_TABLE, rowKey, 6)) || 0,
        };
      })
      .sort((a, b) => Number(a.month) - Number(b.month) || a.sortOrder - b.sortOrder || a.id.localeCompare(b.id, "ko"));
  }

  function decodeItemRows() {
    const ids = Array.from(new Set(
      cellRows
        .filter((row) => row.table_name === CELL_TABLE && String(row.row_key || "").startsWith("improvement-item:"))
        .map((row) => String(row.row_key).replace("improvement-item:", ""))
    ));

    return ids
      .map((id) => {
        const rowKey = itemRowKey(id);
        return {
          id,
          category: getCellValue(CELL_TABLE, rowKey, 1) || CATEGORY_OPTIONS[0],
          no: getCellValue(CELL_TABLE, rowKey, 2),
          item: getCellValue(CELL_TABLE, rowKey, 3),
          planCost: getCellValue(CELL_TABLE, rowKey, 4),
          actualCost: getCellValue(CELL_TABLE, rowKey, 5),
          status: getCellValue(CELL_TABLE, rowKey, 6),
          note: getCellValue(CELL_TABLE, rowKey, 7),
          sortOrder: parseNumber(getCellValue(CELL_TABLE, rowKey, 8)) || 0,
        };
      })
      .sort((a, b) => {
        const categoryDiff = CATEGORY_OPTIONS.indexOf(a.category) - CATEGORY_OPTIONS.indexOf(b.category);
        if (categoryDiff !== 0) return categoryDiff;
        return a.sortOrder - b.sortOrder || a.id.localeCompare(b.id, "ko");
      });
  }

  function renderTabs() {
    tabsRoot.innerHTML = TABS.map((tab) => {
      return `
        <button type="button" class="period-tab-button${tab.id === activeTab ? " active" : ""}" data-tab="${tab.id}">
          ${escapeHtml(tab.label)}
        </button>
      `;
    }).join("");
  }

  function renderMonthlyTable() {
    title.textContent = "\uC6D4\uBCC4\uAC1C\uC120";
    topActions.innerHTML = topActionButton("monthlyAddButton", "\uD56D\uBAA9 \uCD94\uAC00");

    const rows = decodeMonthlyRows();
    const monthCounts = rows.reduce((acc, row) => {
      acc[row.month] = (acc[row.month] || 0) + 1;
      return acc;
    }, {});
    const renderedMonths = new Set();
    let runningBalance = 0;

    const body = rows.map((row, index) => {
      runningBalance += (parseNumber(row.payment) || 0) - (parseNumber(row.spent) || 0);
      const monthCell = renderedMonths.has(row.month)
        ? ""
        : (() => {
            renderedMonths.add(row.month);
            return `<th class="sticky-col month-col" scope="rowgroup" rowspan="${monthCounts[row.month]}">${selectHtml("monthly", row.id, "month", row.month, MONTHS)}</th>`;
          })();

      return `
        <tr class="improvement-row" data-row-kind="monthly" data-row-id="${row.id}" data-row-month="${row.month}">
          ${monthCell}
          <td class="drag-col">${dragHandleHtml("monthly", row.id)}</td>
          <th class="sticky-col-2 no-col" scope="row">${index + 1}</th>
          <td class="money-col">${inputHtml("monthly", row.id, "payment", row.payment, true)}</td>
          <td class="money-col">${inputHtml("monthly", row.id, "spent", row.spent, true)}</td>
          <td>${calcHtml(runningBalance)}</td>
          <td>${inputHtml("monthly", row.id, "vendor", row.vendor)}</td>
          <td class="usage-col">${inputHtml("monthly", row.id, "usage", row.usage)}</td>
          <td class="action-col">${deleteButtonHtml("monthly", row.id)}</td>
        </tr>
      `;
    }).join("");

    const totalPayment = sum(rows.map((row) => parseNumber(row.payment)));
    const totalSpent = sum(rows.map((row) => parseNumber(row.spent)));

    tableWrap.innerHTML = `
      <table class="improvement-table">
        <thead>
          <tr>
            <th class="sticky-col month-col">\uC6D4</th>
            <th class="drag-col">\uC774\uB3D9</th>
            <th class="sticky-col-2 no-col">No.</th>
            <th class="money-col">\uC9C0\uAE09\uC561</th>
            <th class="money-col">\uC0AC\uC6A9\uC561</th>
            <th>\uC794\uC561</th>
            <th>\uC5C5\uCCB4\uBA85</th>
            <th class="usage-col">\uC0AC\uC6A9\uCC98</th>
            <th class="action-col">\uC0AD\uC81C</th>
          </tr>
        </thead>
        <tbody>
          ${body}
          <tr class="summary-row">
            <th class="sticky-col month-col" scope="row" colspan="3">\uD569\uACC4</th>
            <td>${calcHtml(totalPayment)}</td>
            <td>${calcHtml(totalSpent)}</td>
            <td>${calcHtml(totalPayment - totalSpent)}</td>
            <td></td>
            <td></td>
            <td></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function renderItemGroupRows(rows) {
    const counts = rows.reduce((acc, row) => {
      acc[row.category] = (acc[row.category] || 0) + 1;
      return acc;
    }, {});
    const rendered = new Set();

    return rows.map((row) => {
      const categoryCell = rendered.has(row.category)
        ? ""
        : (() => {
            rendered.add(row.category);
            return `<th class="sticky-col month-col" scope="rowgroup" rowspan="${counts[row.category]}">${selectHtml("item", row.id, "category", row.category, CATEGORY_OPTIONS)}</th>`;
          })();

      return `
        <tr class="improvement-row" data-row-kind="item" data-row-id="${row.id}" data-row-category="${escapeHtml(row.category)}">
          ${categoryCell}
          <td class="drag-col">${dragHandleHtml("item", row.id)}</td>
          <td class="sticky-col-2 no-col">${inputHtml("item", row.id, "no", row.no)}</td>
          <td>${inputHtml("item", row.id, "item", row.item)}</td>
          <td>${inputHtml("item", row.id, "planCost", row.planCost, true)}</td>
          <td>${inputHtml("item", row.id, "actualCost", row.actualCost, true)}</td>
          <td>${selectHtml("item", row.id, "status", row.status, STATUS_OPTIONS)}</td>
          <td>${inputHtml("item", row.id, "note", row.note)}</td>
          <td class="action-col">${deleteButtonHtml("item", row.id)}</td>
        </tr>
      `;
    }).join("");
  }

  function renderItemTable() {
    title.textContent = "\uD56D\uBAA9\uAC1C\uC120";
    topActions.innerHTML = topActionButton("itemAddButton", "\uD56D\uBAA9 \uCD94\uAC00");

    const rows = decodeItemRows();
    const mainRows = rows.filter((row) => row.category === CATEGORY_OPTIONS[0] || row.category === CATEGORY_OPTIONS[1]);
    const extraRows = rows.filter((row) => row.category === CATEGORY_OPTIONS[2]);
    const mainDoneRows = mainRows.filter((row) => row.status === STATUS_OPTIONS[1]);
    const extraDoneRows = extraRows.filter((row) => row.status === STATUS_OPTIONS[1]);

    tableWrap.innerHTML = `
      <table class="improvement-table">
        <thead>
          <tr>
            <th class="sticky-col month-col">\uAD6C\uBD84</th>
            <th class="drag-col">\uC774\uB3D9</th>
            <th class="sticky-col-2 no-col">No.</th>
            <th>\uD56D\uBAA9</th>
            <th>\uACC4\uD68D\uBE44\uC6A9</th>
            <th>\uC2E4\uBE44\uC6A9</th>
            <th>\uC644\uB8CC\uC5EC\uBD80</th>
            <th>\uBE44\uACE0</th>
            <th class="action-col">\uC0AD\uC81C</th>
          </tr>
        </thead>
        <tbody>
          ${renderItemGroupRows(mainRows)}
          <tr class="meta-row">
            <th class="sticky-col month-col" scope="row" colspan="3">\uCD94\uC9C4\uAC74\uC218</th>
            <td>${mainRows.length}</td>
            <th>\uC644\uB8CC\uAC74\uC218</th>
            <td>${mainDoneRows.length}</td>
            <th>\uC2E4\uBE44\uC6A9 \uD569\uACC4</th>
            <td>${calcHtml(sum(mainRows.map((row) => parseNumber(row.actualCost))))}</td>
            <td></td>
          </tr>
          ${renderItemGroupRows(extraRows)}
          <tr class="meta-row">
            <th class="sticky-col month-col" scope="row" colspan="3">\uCD94\uAC00\uAC74\uC218</th>
            <td>${extraRows.length}</td>
            <th>\uC644\uB8CC\uAC74\uC218</th>
            <td>${extraDoneRows.length}</td>
            <th>\uC2E4\uBE44\uC6A9 \uD569\uACC4</th>
            <td>${calcHtml(sum(extraRows.map((row) => parseNumber(row.actualCost))))}</td>
            <td></td>
          </tr>
          <tr class="summary-row">
            <th class="sticky-col month-col" scope="row" colspan="4">\uD569\uACC4</th>
            <td>${calcHtml(sum(rows.map((row) => parseNumber(row.planCost))))}</td>
            <td>${calcHtml(sum(rows.map((row) => parseNumber(row.actualCost))))}</td>
            <td>${rows.filter((row) => row.status === STATUS_OPTIONS[1]).length}\uAC74</td>
            <td></td>
            <td></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function clearDropClasses() {
    tableWrap.querySelectorAll(".improvement-row").forEach((row) => {
      row.classList.remove("drop-before", "drop-after", "is-dragging");
    });
  }

  function buildFocusSignature(target) {
    if (!(target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLButtonElement)) return null;
    return {
      tag: target.tagName,
      kind: target.dataset.kind || "",
      rowId: target.dataset.rowId || "",
      key: target.dataset.key || "",
      id: target.id || "",
    };
  }

  function sameFocusSignature(target, signature) {
    if (!signature || !(target instanceof HTMLElement)) return false;
    return target.tagName === signature.tag
      && (target.dataset.kind || "") === signature.kind
      && (target.dataset.rowId || "") === signature.rowId
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

  async function deleteRowGroup(rowKey) {
    const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}/row-group?month=${encodeURIComponent(monthKey())}&rowKey=${encodeURIComponent(rowKey)}`, {
      method: "DELETE",
    });
    if (!response.ok) return false;
    removeRowCells(rowKey);
    return true;
  }

  async function saveField(kind, id, key, rawValue) {
    let rowKey;
    let colIndex = 0;

    if (kind === "monthly") {
      rowKey = monthlyRowKey(id);
      const fieldMap = { month: 1, payment: 2, spent: 3, vendor: 4, usage: 5, sortOrder: 6 };
      colIndex = fieldMap[key] || 0;
    } else {
      rowKey = itemRowKey(id);
      const fieldMap = { category: 1, no: 2, item: 3, planCost: 4, actualCost: 5, status: 6, note: 7, sortOrder: 8 };
      colIndex = fieldMap[key] || 0;
    }

    if (!colIndex) return;

    const existing = getCellRow(CELL_TABLE, rowKey, colIndex);
    const payload = {
      month: monthKey(),
      year_no: yearValue(),
      month_no: 0,
      table_name: CELL_TABLE,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: rawValue,
    };
    const saved = await upsertCell(payload, existing?.id);
    if (saved) setCellRow(saved);
  }

  async function addMonthlyRow() {
    const rows = decodeMonthlyRows();
    const defaultMonth = rows.length ? rows[rows.length - 1].month : "01";
    const nextSort = rows.filter((row) => row.month === defaultMonth).length + 1;
    const id = `${Date.now()}`;

    await Promise.all([
      saveField("monthly", id, "month", defaultMonth),
      saveField("monthly", id, "payment", ""),
      saveField("monthly", id, "spent", ""),
      saveField("monthly", id, "vendor", ""),
      saveField("monthly", id, "usage", ""),
      saveField("monthly", id, "sortOrder", String(nextSort)),
    ]);
    await fetchRows();
    render();
  }

  async function addItemRow() {
    const rows = decodeItemRows();
    const defaultCategory = rows.length ? rows[rows.length - 1].category : CATEGORY_OPTIONS[0];
    const groupRows = rows.filter((row) => row.category === defaultCategory);
    const id = `${Date.now()}`;

    await Promise.all([
      saveField("item", id, "category", defaultCategory),
      saveField("item", id, "no", String(groupRows.length + 1)),
      saveField("item", id, "item", ""),
      saveField("item", id, "planCost", ""),
      saveField("item", id, "actualCost", ""),
      saveField("item", id, "status", ""),
      saveField("item", id, "note", ""),
      saveField("item", id, "sortOrder", String(groupRows.length + 1)),
    ]);
    await fetchRows();
    render();
  }

  async function moveMonthlyRow(rowId, targetRowId, dropAfter) {
    const rows = decodeMonthlyRows();
    const source = rows.find((row) => row.id === rowId);
    const target = rows.find((row) => row.id === targetRowId);
    if (!source || !target || source.id === target.id) return;

    const targetMonthRows = rows.filter((row) => row.month === target.month);
    const lastTargetMonthRow = targetMonthRows[targetMonthRows.length - 1];
    const targetMonthIndex = MONTHS.indexOf(target.month);
    const nextMonth = targetMonthIndex >= 0 && targetMonthIndex < MONTHS.length - 1
      ? MONTHS[targetMonthIndex + 1]
      : target.month;

    let nextMonthValue = target.month;
    if (dropAfter && lastTargetMonthRow?.id === target.id && nextMonth !== target.month) {
      nextMonthValue = nextMonth;
    }

    const remaining = rows.filter((row) => row.id !== source.id);
    source.month = nextMonthValue;

    let insertIndex;
    if (nextMonthValue !== target.month && dropAfter && lastTargetMonthRow?.id === target.id) {
      insertIndex = remaining.findIndex((row) => Number(row.month) > Number(target.month));
      if (insertIndex < 0) insertIndex = remaining.length;
    } else {
      insertIndex = remaining.findIndex((row) => row.id === target.id);
      if (insertIndex < 0) insertIndex = remaining.length;
      if (dropAfter) insertIndex += 1;
    }

    remaining.splice(insertIndex, 0, source);

    const grouped = new Map();
    remaining.forEach((row) => {
      const list = grouped.get(row.month) || [];
      list.push(row);
      grouped.set(row.month, list);
    });

    const saves = [];
    MONTHS.forEach((month) => {
      const group = grouped.get(month) || [];
      group.forEach((row, index) => {
        saves.push(saveField("monthly", row.id, "month", month));
        saves.push(saveField("monthly", row.id, "sortOrder", String(index + 1)));
      });
    });

    await Promise.all(saves);
    await fetchRows();
    render();
  }

  async function moveItemRow(rowId, targetRowId, dropAfter) {
    const rows = decodeItemRows();
    const source = rows.find((row) => row.id === rowId);
    const target = rows.find((row) => row.id === targetRowId);
    if (!source || !target || source.id === target.id) return;

    const targetCategoryRows = rows.filter((row) => row.category === target.category);
    const lastTargetCategoryRow = targetCategoryRows[targetCategoryRows.length - 1];
    const targetCategoryIndex = CATEGORY_OPTIONS.indexOf(target.category);
    const nextCategory = targetCategoryIndex >= 0 && targetCategoryIndex < CATEGORY_OPTIONS.length - 1
      ? CATEGORY_OPTIONS[targetCategoryIndex + 1]
      : target.category;

    let nextCategoryValue = target.category;
    if (dropAfter && lastTargetCategoryRow?.id === target.id && nextCategory !== target.category) {
      nextCategoryValue = nextCategory;
    }

    const remaining = rows.filter((row) => row.id !== source.id);
    source.category = nextCategoryValue;

    let insertIndex;
    if (nextCategoryValue !== target.category && dropAfter && lastTargetCategoryRow?.id === target.id) {
      insertIndex = remaining.findIndex((row) => CATEGORY_OPTIONS.indexOf(row.category) > targetCategoryIndex);
      if (insertIndex < 0) insertIndex = remaining.length;
    } else {
      insertIndex = remaining.findIndex((row) => row.id === target.id);
      if (insertIndex < 0) insertIndex = remaining.length;
      if (dropAfter) insertIndex += 1;
    }

    remaining.splice(insertIndex, 0, source);

    const grouped = new Map();
    remaining.forEach((row) => {
      const list = grouped.get(row.category) || [];
      list.push(row);
      grouped.set(row.category, list);
    });

    const saves = [];
    CATEGORY_OPTIONS.forEach((category) => {
      const group = grouped.get(category) || [];
      group.forEach((row, index) => {
        saves.push(saveField("item", row.id, "category", category));
        saves.push(saveField("item", row.id, "sortOrder", String(index + 1)));
        saves.push(saveField("item", row.id, "no", String(index + 1)));
      });
    });

    await Promise.all(saves);
    await fetchRows();
    render();
  }

  function render() {
    renderTabs();
    if (activeTab === "monthly") {
      renderMonthlyTable();
    } else {
      renderItemTable();
    }
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
    if (target.id === "monthlyAddButton") {
      await addMonthlyRow();
    }
    if (target.id === "itemAddButton") {
      await addItemRow();
    }
  });

  tableWrap.addEventListener("dragstart", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement) || !target.dataset.dragKind || !target.dataset.dragRowId) return;
    dragState = {
      kind: target.dataset.dragKind,
      rowId: target.dataset.dragRowId,
    };
    const row = target.closest(".improvement-row");
    row?.classList.add("is-dragging");
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
    const row = event.target instanceof HTMLElement ? event.target.closest(".improvement-row") : null;
    if (!row || row.dataset.rowId === dragState.rowId || row.dataset.rowKind !== dragState.kind) return;
    event.preventDefault();
    clearDropClasses();
    const rect = row.getBoundingClientRect();
    const dropAfter = event.clientY > rect.top + rect.height / 2;
    row.classList.add(dropAfter ? "drop-after" : "drop-before");
  });

  tableWrap.addEventListener("drop", async (event) => {
    if (!dragState) return;
    const row = event.target instanceof HTMLElement ? event.target.closest(".improvement-row") : null;
    if (!row || row.dataset.rowId === dragState.rowId || row.dataset.rowKind !== dragState.kind) return;
    event.preventDefault();
    const rect = row.getBoundingClientRect();
    const dropAfter = event.clientY > rect.top + rect.height / 2;
    clearDropClasses();

    if (dragState.kind === "monthly") {
      await moveMonthlyRow(dragState.rowId, row.dataset.rowId, dropAfter);
    } else {
      await moveItemRow(dragState.rowId, row.dataset.rowId, dropAfter);
    }
    dragState = null;
  });

  tableWrap.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || !target.dataset.deleteKind || !target.dataset.deleteRowId) return;
    const rowKey = target.dataset.deleteKind === "monthly"
      ? monthlyRowKey(target.dataset.deleteRowId)
      : itemRowKey(target.dataset.deleteRowId);
    const deleted = await deleteRowGroup(rowKey);
    if (!deleted) return;
    await fetchRows();
    render();
  });

  tableWrap.addEventListener("change", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement || target instanceof HTMLSelectElement)) return;
    const kind = target.dataset.kind;
    const rowId = target.dataset.rowId;
    const key = target.dataset.key;
    if (!kind || !rowId || !key) return;

    const rawValue = ["payment", "spent", "planCost", "actualCost", "sortOrder"].includes(key)
      ? (() => {
          const parsed = parseNumber(target.value);
          const nextValue = parsed === null ? "" : String(parsed);
          target.dataset.rawValue = nextValue;
          target.value = parsed === null ? "" : formatNumber(parsed);
          return nextValue;
        })()
      : target.value;

    await saveField(kind, rowId, key, rawValue);

    if (kind === "item" && key === "category") {
      const rows = decodeItemRows();
      const saves = [];
      CATEGORY_OPTIONS.forEach((category) => {
        rows
          .filter((row) => row.category === category)
          .forEach((row, index) => {
            saves.push(saveField("item", row.id, "sortOrder", String(index + 1)));
            saves.push(saveField("item", row.id, "no", String(index + 1)));
          });
      });
      await Promise.all(saves);
    }

    if (kind === "monthly" && key === "month") {
      const rows = decodeMonthlyRows();
      const saves = [];
      MONTHS.forEach((month) => {
        rows
          .filter((row) => row.month === month)
          .forEach((row, index) => {
            saves.push(saveField("monthly", row.id, "sortOrder", String(index + 1)));
          });
      });
      await Promise.all(saves);
    }

    await fetchRows();
    render();
    restorePendingTabMove();
  });

  tableWrap.addEventListener("focusin", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.dataset.rawValue && target.dataset.rawValue !== "") return;
    target.value = target.dataset.rawValue;
  });

  tableWrap.addEventListener("focusout", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.dataset.rawValue && target.dataset.rawValue !== "") return;
    const parsed = parseNumber(target.value);
    target.dataset.rawValue = parsed === null ? "" : String(parsed);
    target.value = parsed === null ? "" : formatNumber(parsed);
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
