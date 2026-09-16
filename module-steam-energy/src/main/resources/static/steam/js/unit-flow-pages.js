document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-flow-daily.html") return;

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  if (!yearSelect || !monthSelect) return;

  const CELL_TABLE = "table_cell_value";
  const DAILY_ROW_COUNT = 31;
  const cellRows = [];
  let loaded = false;
  let loadedMonth = "";
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();

  const MONTHLY_READONLY = {
    // 단가 칸(유동상 col1 / 폐합성 col4 / 복합 col1 / 외부 col1)은 입력 가능해야 한다.
    // 비용(계산) 칸만 읽기전용: fluid=비용 2·5, combined/boiler=비용 2·스팀구매비 4.
    flow_m_fluid_incinerator: [2, 5],
    flow_m_combined: [2, 4],
    flow_m_boiler: [2, 4],
    flow_m_summary: [0, 1, 2, 3, 4],
  };

  function monthKey(year = yearSelect.value, month = monthSelect.value) {
    return `${year}-${month}`;
  }

  function monthMeta(year = yearSelect.value, month = monthSelect.value) {
    return {
      month: `${year}-${month}`,
      year_no: Number(year),
      month_no: Number(month),
    };
  }

  function daysInMonth() {
    return new Date(Number(yearSelect.value), Number(monthSelect.value), 0).getDate();
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return 0;
    const cleaned = String(value).replace(/[% ,]/g, "").trim();
    if (!cleaned) return 0;
    const number = Number(cleaned);
    return Number.isFinite(number) ? number : 0;
  }

  function formatInt(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0, minimumFractionDigits: 0 }).format(value);
  }

  function formatOne(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(value);
  }

  function escapeAttr(value) {
    return String(value ?? "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  function formatEditableValue(value) {
    const normalized = normalizeEditableValue(value);
    if (!normalized) return "";
    if (!/^[-+]?\d*(\.\d+)?$/.test(normalized)) return String(value ?? "");
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(Number(normalized));
  }

  function editableRawValue(input) {
    return document.activeElement === input ? normalizeEditableValue(input.value) : (input.dataset.rawValue ?? normalizeEditableValue(input.value));
  }

  function isReadonly(tableName, colIndex) {
    return (MONTHLY_READONLY[tableName] || []).includes(colIndex);
  }

  async function fetchPaged(tableName, month = "") {
    const data = [];
    let pageNo = 1;
    let totalPages = 1;
    while (pageNo <= totalPages) {
      const monthQuery = month ? `&month=${encodeURIComponent(month)}` : "";
      const response = await fetch(`tables/${tableName}?page=${pageNo}&limit=500${monthQuery}`);
      if (!response.ok) break;
      const payload = await response.json();
      const pageData = payload.data || [];
      const total = payload.total || pageData.length;
      const limit = payload.limit || 500;
      totalPages = Math.max(1, Math.ceil(total / limit));
      data.push(...pageData);
      pageNo += 1;
    }
    return data;
  }

  function getCellRow(tableName, month, rowKey, colIndex) {
    return cellRows.find((row) => row.table_name === tableName && row.month === month && String(row.row_key) === String(rowKey) && Number(row.col_index) === colIndex) || null;
  }

  function getCellValue(tableName, month, rowKey, colIndex) {
    return getCellRow(tableName, month, rowKey, colIndex)?.cell_value ?? "";
  }

  async function upsertCell(payload, existingId) {
    const url = existingId ? `tables/${CELL_TABLE}/${existingId}` : `tables/${CELL_TABLE}`;
    const method = existingId ? "PATCH" : "POST";
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: CELL_TABLE, ...payload }),
    });
    if (!response.ok) return null;
    return response.json();
  }

  async function saveCell(input) {
    if (!input || input.readOnly || input.classList.contains("calc-input")) return;
    const tableName = input.dataset.tableName;
    const rowKey = input.dataset.rowKey;
    const colIndex = Number(input.dataset.colIndex);
    if (!tableName || !rowKey || !Number.isFinite(colIndex)) return;

    const payload = {
      ...monthMeta(),
      table_name: tableName,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: editableRawValue(input),
    };
    const existing = getCellRow(tableName, payload.month, rowKey, colIndex);
    const saved = await upsertCell(payload, existing?.id);
    if (!saved) return;

    const index = cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) cellRows[index] = saved;
    else cellRows.push(saved);
    input.dataset.rawValue = normalizeEditableValue(saved.cell_value);
  }

  function buildInput(tableName, rowKey, colIndex, value, forceReadonly = false) {
    const ro = forceReadonly || isReadonly(tableName, colIndex);
    const displayValue = ro ? value : formatEditableValue(value);
    return `<input class="table-edit-input unit-align-number${ro ? " calc-input" : ""}" data-table-name="${tableName}" data-row-mode="daily" data-row-key="${rowKey}" data-col-index="${colIndex}" data-raw-value="${escapeAttr(normalizeEditableValue(value))}" value="${escapeAttr(displayValue)}"${ro ? ' readonly tabindex="-1"' : ""}>`;
  }

  function buildReadonlyInput(tableName, rowKey, colIndex, value) {
    return `<input class="table-edit-input unit-align-number calc-input" data-table-name="${tableName}" data-row-mode="daily" data-row-key="${rowKey}" data-col-index="${colIndex}" value="${escapeAttr(value)}" readonly tabindex="-1">`;
  }

  function isNumericText(value) {
    const text = String(value ?? "").replace(/[% ,]/g, "").trim();
    return /^-?\d+(\.\d+)?$/.test(text);
  }

  function alignDataCell(cell) {
    if (!(cell instanceof HTMLElement)) return;
    cell.classList.remove("unit-align-number", "unit-align-text");
    if (cell.cellIndex === 0) {
      cell.classList.add("unit-align-text");
      return;
    }
    const table = cell.closest("table");
    const headerText = table?.querySelector(`thead th:nth-child(${cell.cellIndex + 1})`)?.textContent?.trim() || "";
    const isTextColumn = /비고|구분|월|일/.test(headerText);
    const input = cell.querySelector("input");
    if (input instanceof HTMLInputElement) {
      input.classList.remove("unit-align-number", "unit-align-text");
      const className = isTextColumn ? "unit-align-text" : "unit-align-number";
      input.classList.add(className);
      cell.classList.add(className);
      return;
    }
    cell.classList.add(!isTextColumn && isNumericText(cell.textContent) ? "unit-align-number" : "unit-align-text");
  }

  function applyDataAlignment() {
    document.querySelectorAll(".data-table th").forEach((cell) => {
      cell.classList.remove("unit-align-number");
      cell.classList.add("unit-align-text");
    });
    document.querySelectorAll(".data-table td").forEach(alignDataCell);
  }

  function renderMonthlyTable(table) {
    const tableName = table.dataset.cellTable;
    const tbody = table.querySelector("tbody");
    const totalCols = table.querySelectorAll("thead th").length;
    if (!tbody || !tableName || totalCols < 2) return;

    const rows = [];
    const maxDays = daysInMonth();
    for (let day = 1; day <= DAILY_ROW_COUNT; day += 1) {
      const rowKey = String(day).padStart(2, "0");
      const inactiveDay = day > maxDays;
      const cells = [`<td>${rowKey}</td>`];
      for (let col = 0; col < totalCols - 1; col += 1) {
        cells.push(`<td>${buildInput(tableName, rowKey, col, getCellValue(tableName, monthKey(), rowKey, col), inactiveDay)}</td>`);
      }
      rows.push(`<tr data-row-key="${rowKey}"${inactiveDay ? ' class="invalid-day-row"' : ""}>${cells.join("")}</tr>`);
    }
    const totalCells = [`<td>합계</td>`];
    for (let col = 0; col < totalCols - 1; col += 1) {
      totalCells.push(`<td>${buildReadonlyInput(tableName, "total", col, "")}</td>`);
    }
    rows.push(`<tr data-row-key="total" class="summary-row">${totalCells.join("")}</tr>`);
    const avgCells = [`<td>평균</td>`];
    for (let col = 0; col < totalCols - 1; col += 1) {
      avgCells.push(`<td>${buildReadonlyInput(tableName, "avg", col, "")}</td>`);
    }
    rows.push(`<tr data-row-key="avg" class="summary-row">${avgCells.join("")}</tr>`);
    tbody.innerHTML = rows.join("");
  }

  function getInput(row, colIndex) {
    return row.querySelector(`input[data-col-index="${colIndex}"]`);
  }

  function setCalc(row, colIndex, value, digits = 0) {
    const input = getInput(row, colIndex);
    if (!input) return;
    input.value = value === "" || value === null || value === undefined ? "" : (digits > 0 ? formatOne(value) : formatInt(value));
    input.classList.add("unit-align-number");
    alignDataCell(input.closest("td"));
  }

  function recalcSourceRow(row) {
    const tableName = row.querySelector("input")?.dataset.tableName;
    if (!tableName) return;

    if (tableName === "flow_m_fluid_incinerator") {
      const fluidSteam = parseNumber(getInput(row, 0)?.value);
      const fluidPrice = parseNumber(getInput(row, 1)?.value);
      const incineratorSteam = parseNumber(getInput(row, 3)?.value);
      const incineratorPrice = parseNumber(getInput(row, 4)?.value);
      setCalc(row, 2, fluidSteam > 0 || fluidPrice > 0 ? (fluidSteam * fluidPrice) / 1000000 : "", 1);
      setCalc(row, 5, incineratorSteam > 0 || incineratorPrice > 0 ? (incineratorSteam * incineratorPrice) / 1000000 : "", 1);
      return;
    }

    if (tableName === "flow_m_combined" || tableName === "flow_m_boiler") {
      const steam = parseNumber(getInput(row, 0)?.value);
      const price = parseNumber(getInput(row, 1)?.value);
      const deduction = parseNumber(getInput(row, 3)?.value);
      const cost = steam > 0 || price > 0 ? (steam * price) / 1000000 : 0;
      setCalc(row, 2, cost > 0 || deduction > 0 ? cost : "", 1);
      setCalc(row, 4, cost > 0 || deduction > 0 ? cost - deduction : "", 1);
    }
  }

  function recalcSummaryRow(rowKey) {
    const summaryRow = document.querySelector(`input[data-table-name="flow_m_summary"][data-row-key="${rowKey}"]`)?.closest("tr");
    if (!summaryRow) return;

    const fluidCost = parseNumber(document.querySelector(`input[data-table-name="flow_m_fluid_incinerator"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value);
    const incineratorCost = parseNumber(document.querySelector(`input[data-table-name="flow_m_fluid_incinerator"][data-row-key="${rowKey}"][data-col-index="5"]`)?.value);
    const combinedPurchase = parseNumber(document.querySelector(`input[data-table-name="flow_m_combined"][data-row-key="${rowKey}"][data-col-index="4"]`)?.value);
    const boilerPurchase = parseNumber(document.querySelector(`input[data-table-name="flow_m_boiler"][data-row-key="${rowKey}"][data-col-index="4"]`)?.value);

    setCalc(summaryRow, 0, fluidCost > 0 ? fluidCost : "", 1);
    setCalc(summaryRow, 1, incineratorCost > 0 ? incineratorCost : "", 1);
    setCalc(summaryRow, 2, combinedPurchase > 0 ? combinedPurchase : "", 1);
    setCalc(summaryRow, 3, boilerPurchase > 0 ? boilerPurchase : "", 1);
    const total = fluidCost + incineratorCost + combinedPurchase + boilerPurchase;
    setCalc(summaryRow, 4, total > 0 ? total : "", 1);
  }

  function recalcAll() {
    const maxDays = daysInMonth();
    ["flow_m_fluid_incinerator", "flow_m_combined", "flow_m_boiler"].forEach((tableName) => {
      document.querySelectorAll(`input[data-table-name="${tableName}"]`).forEach((input) => {
        const row = input.closest("tr");
        if (!row || Number(row.dataset.rowKey) > maxDays) return;
        if (row) recalcSourceRow(row);
      });
    });
    for (let day = 1; day <= maxDays; day += 1) {
      recalcSummaryRow(String(day).padStart(2, "0"));
    }
    recalcMonthlySummaries();
  }

  function recalcMonthlySummaries() {
    const maxDays = daysInMonth();
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach((table) => {
      const tableName = table.dataset.cellTable;
      const totalRow = table.querySelector('tr[data-row-key="total"]');
      const avgRow = table.querySelector('tr[data-row-key="avg"]');
      if (!tableName || !totalRow || !avgRow) return;
      const colCount = table.querySelectorAll("thead th").length - 1;
      for (let col = 0; col < colCount; col += 1) {
        let total = 0;
        let hasValue = false;
        for (let day = 1; day <= maxDays; day += 1) {
          const rowKey = String(day).padStart(2, "0");
          const value = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${col}"]`)?.value ?? "";
          if (String(value).trim() !== "") hasValue = true;
          total += parseNumber(value);
        }
        const digits = [2, 4].includes(col) && ["flow_m_combined", "flow_m_boiler", "flow_m_summary"].includes(tableName) ? 1
          : [2, 5].includes(col) && tableName === "flow_m_fluid_incinerator" ? 1
          : 0;
        setCalc(totalRow, col, hasValue ? total : "", digits);
        setCalc(avgRow, col, hasValue ? total / maxDays : "", digits);
      }
    });
  }

  async function flushDirtyInputs() {
    if (!dirtyInputs.size) return;
    const inputs = Array.from(dirtyInputs);
    dirtyInputs.clear();
    inputs.forEach((input) => {
      const timer = pendingSaveTimers.get(input);
      if (timer) {
        clearTimeout(timer);
        pendingSaveTimers.delete(input);
      }
    });
    for (const input of inputs) {
      await saveCell(input);
    }
  }

  function queueSaveInput(input, delay = 120) {
    dirtyInputs.add(input);
    const existingTimer = pendingSaveTimers.get(input);
    if (existingTimer) clearTimeout(existingTimer);

    const timer = setTimeout(async () => {
      pendingSaveTimers.delete(input);
      if (!dirtyInputs.has(input)) return;
      dirtyInputs.delete(input);
      await saveCell(input);
    }, delay);

    pendingSaveTimers.set(input, timer);
  }

  function bindInputs() {
    document.querySelectorAll(".table-edit-input").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";

      input.addEventListener("input", () => {
        const row = input.closest("tr");
        if (row) {
          recalcSourceRow(row);
          recalcSummaryRow(input.dataset.rowKey);
        }
        queueSaveInput(input);
      });

      input.addEventListener("focus", () => {
        if (input.readOnly || input.classList.contains("calc-input")) return;
        input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
      });

      input.addEventListener("blur", () => {
        if (input.readOnly || input.classList.contains("calc-input")) return;
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
      });

      input.addEventListener("change", async () => {
        dirtyInputs.add(input);
        await flushDirtyInputs();
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
      });

      input.addEventListener("keydown", async (event) => {
        if (event.key !== "Tab") return;
        event.preventDefault();
        const editable = Array.from(document.querySelectorAll(".table-edit-input")).filter((item) => !item.readOnly && !item.classList.contains("calc-input"));
        const current = editable.indexOf(input);
        if (current < 0) return;
        dirtyInputs.add(input);
        await flushDirtyInputs();
        const next = editable[event.shiftKey ? current - 1 : current + 1];
        if (next) {
          next.focus();
          next.select?.();
        }
      });
    });
  }

  async function ensureLoaded() {
    const currentMonth = monthKey();
    if (loaded && loadedMonth === currentMonth) return;
    const cells = await fetchPaged(CELL_TABLE, currentMonth);
    cellRows.length = 0;
    cellRows.push(...cells);
    loaded = true;
    loadedMonth = currentMonth;
  }

  async function render() {
    await ensureLoaded();
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach(renderMonthlyTable);
    recalcAll();
    bindInputs();
    applyDataAlignment();
  }

  window.UnitPageSaveCoordinator?.registerFlush(flushDirtyInputs);

  yearSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });
  monthSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });
  submitButton?.addEventListener("click", async () => {
    await flushDirtyInputs();
    await render();
  });

  render();
});
